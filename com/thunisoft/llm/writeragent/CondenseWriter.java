package com.thunisoft.llm.writeragent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.OutputStream;

import com.thunisoft.llm.writeragent.utils.SpliteText;
import com.thunisoft.llm.writeragent.utils.PromptConfig;
import com.thunisoft.llm.writeragent.utils.XCallLlm;

import org.apache.commons.io.FileUtils;
import java.io.File;

/**
 * 精简/总结文章内容
 * 1. 创建单个章节的写作模板
 * 2. 拆分章节内容
 * 3. 精简章节内容/生成章节摘要/生成全文摘要
 */
public class CondenseWriter extends AIWriterBase{
    private static final Logger logger = LoggerFactory.getLogger(CondenseWriter.class);

    private static final String PROMPT_FILE_NAME = "condense.yml";

    private final String chapterTemplatePrompt;
    private final String BreakdownPrompt;
    private final String CondensePrompt;
    private final String ChapterSummaryPrompt;
    private final String SummaryPrompt;

    /**
     * 构造函数
     * @param callLlm CallLlm实例，不能为null
     * @param useThink 是否使用思考模式
     * @param isExchange 是否交换模式
     */
    public CondenseWriter(XCallLlm callLlm, boolean useThink, boolean isExchange) {
        super(callLlm, useThink, isExchange);

        this.chapterTemplatePrompt = PromptConfig.getPrompt("chapterTemplatePrompt", PROMPT_FILE_NAME);
        this.BreakdownPrompt = PromptConfig.getPrompt("BreakdownPrompt", PROMPT_FILE_NAME);
        this.CondensePrompt = PromptConfig.getPrompt("CondensePrompt", PROMPT_FILE_NAME);
        this.ChapterSummaryPrompt = PromptConfig.getPrompt("ChapterSummaryPrompt", PROMPT_FILE_NAME);
        this.SummaryPrompt = PromptConfig.getPrompt("SummaryPrompt", PROMPT_FILE_NAME);
    }

    /**
     * 根据范文章节，创建单个章节的写作模板
     * 
     * @param chapterJson 章节内容
     * @param outputStream         输出流
     * @return 写作模板
     */
    private JSONObject buildWriterTemplateByChapter(JSONObject chapterJson, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            String text = chapterJson.getString("text");
            if (StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("章节内容不能为空");
            }

            String title = chapterJson.getString("title");
            if (StringUtils.isNotBlank(title)) {
                text = title + "\n" + text;
            }

            safeWriteToStream(outputStream, String.format("\n【 生成章节《%s》 写作模板 ... 】\n", chapterJson.getString("title")), true);

            JSONArray prompt = buildJsonPrompt(this.chapterTemplatePrompt,
                    String.format("\n【章节内容】：\n%s\n", text));
            String template = invokeLlm(prompt, nullOutputStream, true, true);

            JSONObject templateJson = JSON.parseObject(template);
            if (chapterJson.containsKey("title")) {
                templateJson.put("title", chapterJson.getString("title"));
            } else {
                templateJson.put("title", "段落");
            }

            return templateJson;
        } catch (Exception e) {
            throw new RuntimeException("生成章节模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拆分章节内容
     * @param chapterJson 章节内容
     * @param chapterFrame 章节框架
     * @param outputStream 输出流
     * @return 拆分后的章节内容
     * @throws RuntimeException
     * @throws IllegalArgumentException
     */
    private String breakdownChapter(String chapterText, JSONObject chapterFrame, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            if (StringUtils.isBlank(chapterText)) {
                throw new IllegalArgumentException("章节内容不能为空");
            }

            if (chapterFrame == null || chapterFrame.isEmpty() || 
                !chapterFrame.containsKey("subject") || 
                !chapterFrame.containsKey("function")) {
                throw new IllegalArgumentException("章节框架不能为空");
            }

            safeWriteToStream(outputStream, "\n【 拆分章节内容，评估每个语义片段与章节主题及章节功能的关联强度... 】\n", true);

            JSONArray prompt = buildJsonPrompt(this.BreakdownPrompt,
                    String.format("\n【章节内容】：\n%s\n", chapterText),
                    String.format("\n【章节框架】：\n%s\n", chapterFrame.toJSONString()));
            String chunks = invokeLlm(prompt, outputStream, true, false);

            return chunks;
        } catch (Exception e) {
            throw new RuntimeException("拆分章节内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 精简章节内容
     * @param chunks 拆分后的章节内容
     * @param compressionRate 压缩率
     * @param outputStream 输出流
     * @return 精简后的章节内容
     * @throws RuntimeException
     * @throws IllegalArgumentException
     */
    private String condenseChapter(String chunks, double compressionRate, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            int condenseSize = (int) Math.floor(chunks.length() * compressionRate);

            safeWriteToStream(outputStream, String.format("\n【 精简章节内容，根据压缩率 %f 精简章节内容... 】\n", compressionRate), true);

            JSONArray prompt = buildJsonPrompt(this.CondensePrompt,
                    String.format("\n【片段列表】：\n%s\n", chunks),
                    String.format("\n【压缩率】：%f\n", compressionRate),
                    String.format("\n【字数限制】：%d\n", condenseSize));
            String result = invokeLlm(prompt, outputStream, false, false);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("精简章节内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成章节摘要
     * @param chapterFrame 章节框架
     * @param chunks 拆分后的章节内容
     * @param outputStream 输出流
     * @return 章节摘要
     * @throws RuntimeException
     * @throws IllegalArgumentException
     */
    private String buildChapterSummary(String chapterFrame, String chunks, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            safeWriteToStream(outputStream, "\n【 生成章节摘要 ... 】\n", true);

            JSONArray prompt = buildJsonPrompt(this.ChapterSummaryPrompt,
                    String.format("\n【章节内容】：\n%s\n", chapterFrame),
                    String.format("\n【片段列表】：\n%s\n", chunks));
            String result = invokeLlm(prompt, nullOutputStream, true, false);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("生成章节摘要失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成全文摘要
     * @param chapterSummaries 章节摘要列表
     * @param outputStream 输出流
     * @return 全文摘要
     * @throws RuntimeException
     * @throws IllegalArgumentException
     */
    private String buildSummary(String[] chapterSummaries, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            safeWriteToStream(outputStream, "\n【 生成全文摘要 ... 】\n", true);

            JSONArray prompt = buildJsonPrompt(this.SummaryPrompt,
                    String.format("\n【章节摘要列表】：\n%s\n", String.join("\n\n", chapterSummaries)));
            String result = invokeLlm(prompt, outputStream, false, false);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("生成全文摘要失败: " + e.getMessage(), e);
        }
    }

    /**
     * 精简文章内容
     * @param text 文章内容
     * @param outputStream 输出流
     * @return 精简后的文章内容
     * @throws RuntimeException
     * @throws IllegalArgumentException
     */
    public String condenseText(String text, int condenseSize, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            StringBuffer contentBuffer = new StringBuffer();

            // 计算压缩率
            double compressionRate = (double) condenseSize / text.length();
            compressionRate = Math.max(0.1, Math.min(0.9, compressionRate));

            // 文章内容分块
            SpliteText spliteText = new SpliteText(text, true, false, false, this.callLlm, this.useThink, this.isExchange);
            JSONArray arrayJson = new JSONArray();
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(MAX_INPUT_TOKEN);
            }
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(MAX_INPUT_TOKEN);
            }

            // 精简章节内容
            for (int i = 0; i < arrayJson.size(); i++) {
                JSONObject chapterJson = arrayJson.getJSONObject(i);
                if (chapterJson == null || chapterJson.isEmpty() || 
                    !chapterJson.containsKey("text") || 
                    StringUtils.isBlank(chapterJson.getString("text"))) {
                        logger.info("章节内容为空，跳过该章节");
                        continue;
                }

                String chapterTitle = chapterJson.getString("title");
                if (StringUtils.isBlank(chapterTitle)) {
                    chapterTitle = "段落";
                }

                boolean isPreface = false;
                try {
                    // 创建章节的写作模板
                    safeWriteToStream(outputStream, String.format("\n【 分析章节《%s》 ... 】\n", chapterTitle), true);
                    JSONObject writingTemplateJson = this.buildWriterTemplateByChapter(chapterJson, outputStream);

                    logger.info("章节《{}》写作模板: \n {}", chapterTitle, writingTemplateJson.toJSONString());

                    String subtitle = writingTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("范文章节的写作模板title为空");
                    }
                    if (subtitle.matches("^引言$")) {
                        isPreface = true;
                    }

                    // 拆分章节内容
                    safeWriteToStream(outputStream, String.format("\n【 精练章节《%s》 ... 】\n", chapterTitle), true);
                    String breakdown = this.breakdownChapter(chapterJson.getString("text"), writingTemplateJson, outputStream);

                    logger.info("章节《{}》拆分内容: \n {}", chapterTitle, breakdown);

                    // 精简章节内容
                    safeWriteToStream(outputStream, String.format("\n【 精简章节《%s》 ... 】\n", chapterTitle), true);
                    if (!subtitle.matches("^(引言|段落\\s*)$")) {
                        if (!subtitle.matches("^[第]?[一二三四五六七八九十零〇]+[\\.\\s、章篇部分节]{1}.*$")) {
                            subtitle = (isPreface ? numberToChinese(i) : numberToChinese(i + 1)) + "、" + subtitle;
                        }
                        contentBuffer.append(subtitle).append("\n");
                        safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    }
                    String condensedChapter = this.condenseChapter(breakdown, compressionRate, outputStream);
                    contentBuffer.append(condensedChapter).append("\n");
                } catch (Exception e) {
                    safeWriteToStream(outputStream, String.format("\n【 精简章节《%s》失败，跳过该章节，由于 %s 】\n", chapterTitle, e.getMessage()), true);
                    continue;
                }
            }

            return contentBuffer.toString();
        } catch (Exception e) {
            throw new RuntimeException("精简文章内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成摘要
     * @param text 文章内容
     * @param outputStream 输出流
     * @return 摘要
     * @throws RuntimeException
     * @throws IllegalArgumentException
     */
    public String summarizeText(String text, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        try {
            StringBuffer contentBuffer = new StringBuffer();
        
            // 文章内容分块
            safeWriteToStream(outputStream, String.format("\n【 文章内容分块 ... 】\n"), true);
            SpliteText spliteText = new SpliteText(text, true, false, false, this.callLlm, this.useThink, this.isExchange);
            JSONArray arrayJson = new JSONArray();
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(MAX_INPUT_TOKEN);
            }
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(MAX_INPUT_TOKEN);
            }
        
            // 章节内容摘要
            for (int i = 0; i < arrayJson.size(); i++) {
                JSONObject chapterJson = arrayJson.getJSONObject(i);
                if (chapterJson == null || chapterJson.isEmpty() || 
                    !chapterJson.containsKey("text") || 
                    StringUtils.isBlank(chapterJson.getString("text"))) {
                        logger.info("章节内容为空，跳过该章节");
                        continue;
                }
        
                String chapterTitle = chapterJson.getString("title");
                if (StringUtils.isBlank(chapterTitle)) {
                    chapterTitle = "段落";
                }
        
                try {
                    // 创建章节的写作模板
                    safeWriteToStream(outputStream, String.format("\n【 分析章节《%s》 ... 】\n", chapterTitle), true);
                    JSONObject writingTemplateJson = this.buildWriterTemplateByChapter(chapterJson, outputStream);
                    logger.info("章节《{}》写作模板: \n {}", chapterTitle, writingTemplateJson.toJSONString());

                    // 拆分章节内容
                    safeWriteToStream(outputStream, String.format("\n【 精练章节《%s》 ... 】\n", chapterTitle), true);
                    String breakdown = this.breakdownChapter(chapterJson.getString("text"), writingTemplateJson, outputStream);
                    logger.info("章节《{}》拆分内容: \n {}", chapterTitle, breakdown);

                    // 章节内容摘要
                    safeWriteToStream(outputStream, String.format("\n【 章节《%s》内容摘要生成 ... 】\n", chapterTitle), true);
                    String chapterSummary = this.buildChapterSummary(writingTemplateJson.toJSONString(), breakdown, outputStream);
                    contentBuffer.append(chapterSummary).append("<<CHAPTER_END>>");
                } catch (Exception e) {
                    safeWriteToStream(outputStream, String.format("\n【 章节《%s》内容摘要生成失败，跳过该章节，由于 %s 】\n", chapterTitle, e.getMessage()), true);
                    continue;
                }
            }
        
            // 全文摘要
            safeWriteToStream(outputStream, String.format("\n【 全文摘要生成 ... 】\n"), true);
            String summary = this.buildSummary(contentBuffer.toString().split("<<CHAPTER_END>>"), outputStream);
            contentBuffer.setLength(0);
            contentBuffer.append(summary);

            return contentBuffer.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成摘要失败: " + e.getMessage(), e);
        }
    }

    // 本地测试用
    /*
    public static void main(String[] args) {
        try {
            String text = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\万相公文建设立项材料.txt"), "UTF-8");
            
            XCallLlm callLlm = new XCallLlm();
            CondenseWriter condenseWriter = new CondenseWriter(callLlm, false, false);
            // String result = condenseWriter.summarizeText(text, nullOutputStream);
            String result = condenseWriter.condenseText(text, 2000, nullOutputStream);
            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
}
