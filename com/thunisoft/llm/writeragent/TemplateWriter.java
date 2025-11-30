package com.thunisoft.llm.writeragent;

import java.io.OutputStream;
import java.io.IOException;
import java.lang.String;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.util.stream.Collectors;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.domain.ChatParams;
import com.thunisoft.llm.service.impl.CallLlm;
import com.thunisoft.llm.writeragent.utils.SpliteText;
import com.thunisoft.llm.writeragent.utils.PromptConfig;

/**
 * 根据范文和参考内容，生成文章
 */
public class TemplateWriter extends AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(TemplateWriter.class);

    // private static final String GW_PATTERN = "^(通知|决定|批复|意见|公告|通报|报告|请示|批复|函|纪要|意见|议案|通告|公报|令)$";

    private final String articleTypePrompt;
    private final String modifyTitlePrompt;
    private final String chapterTemplatePrompt;
    private final String firstFilterPrompt;
    private final String refrenceExtractPrompt;
    private final String writerPrompt;
    private final String evaluateQualityPrompt;
    private final String articleCategory;
    private final String optimizeRules;

    private JSONObject exemplaryArticleTypeJson;
    private JSONObject writingArticleTypeJson;
    
    /**
     * 构造函数
     * @param callLlm LLM调用服务，不能为空
     * @param useThink 是否使用思考模式
     * @param maxToken 最大token数，必须大于0
     * @param isExchange 是否交换模式
     * @throws IllegalArgumentException 如果参数无效
     */
    public TemplateWriter(ICallLlm callLlm, boolean useThink, int maxToken, boolean isExchange) {
        super(callLlm, useThink, maxToken, isExchange);

        PromptConfig.setPromptFileName("template.yml");
        this.refrenceExtractPrompt = PromptConfig.getPrompt("refrenceExtractPrompt");
        this.writerPrompt = PromptConfig.getPrompt("writerPrompt");
        this.firstFilterPrompt = PromptConfig.getPrompt("firstFilterPrompt");
        this.chapterTemplatePrompt = PromptConfig.getPrompt("chapterTemplatePrompt");
        this.articleTypePrompt = PromptConfig.getPrompt("articleTypePrompt");
        this.modifyTitlePrompt = PromptConfig.getPrompt("modifyTitlePrompt");
        this.evaluateQualityPrompt = PromptConfig.getPrompt("evaluateQualityPrompt");

        this.articleCategory = PromptConfig.getPrompt("articleCategory");
        this.optimizeRules = PromptConfig.getPrompt("optimizeRules");
    }
    
    /**
     * 获取文章体裁
     * @param content 文章内容
     * @param outputStream 输出流
     * @return 文章体裁
     */
    private JSONObject getArticleType(String content, OutputStream outputStream) throws RuntimeException {
        JSONArray prompt = buildJsonPrompt(this.articleTypePrompt, 
                            String.format("\n【文章内容】：\n%s\n", content));
        String result = invokeLlm(prompt, outputStream, true, true);
        try {
            logger.info("获取文章体裁成功: {}", result);
            return JSON.parseObject(result);
        } catch (Exception e) {
            logger.error("获取文章体裁失败，解析JSON失败: {} \n", result, e);
            throw new RuntimeException("获取文章体裁失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查范文是否可作为写作模板
     * @param exemplaryArticle 范文
     * @return 是否可作为写作模板
     */
    private String getArticleTitle(String exemplaryArticle, String articleTitle, String articleType, OutputStream outputStream) throws RuntimeException {
        if (StringUtils.isBlank(exemplaryArticle)) {
            throw new IllegalArgumentException("范文不能为空");
        }

        if (StringUtils.isBlank(articleTitle)) {
            throw new IllegalArgumentException("文章标题不能为空");
        }
        // 范文截取
        String exemplaryArticleText = exemplaryArticle.substring(0, Math.min(exemplaryArticle.length(), (int)(this.MAX_REFERENCE_LENGTH * 0.6)));
        
        try {
            this.exemplaryArticleTypeJson = getArticleType(exemplaryArticleText, outputStream);
            this.writingArticleTypeJson = getArticleType(String.format("\n文章标题：%s\n文章类型：%s\n", articleTitle, articleType), outputStream);
            
            /* 检查文体类别是否一致 */
            String exemplaryArticleCategory = this.exemplaryArticleTypeJson.getString("category");
            String writingArticleCategory = this.writingArticleTypeJson.getString("category");
            if (exemplaryArticleCategory != null && exemplaryArticleCategory.equals(writingArticleCategory)) 
                return articleTitle;

            /* 修改标题 */
            JSONArray prompt = buildJsonPrompt(this.modifyTitlePrompt, 
                        String.format("\n【指定的文体类别】：\n%s\n", this.exemplaryArticleTypeJson.getString("category")),
                        String.format("\n【文体类别】：\n%s\n", this.articleCategory),
                        String.format("\n【创作标题】：\n%s\n", articleTitle));
            String result = invokeLlm(prompt, outputStream, true, false);
            logger.info("修改标题成功: {}", result);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("检查范文是否可作为写作模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建单个章节的写作模板
     * @param exemplaryChapterJson 范文章节
     * @param outputStream 输出流
     * @return 写作模板
     */
    private JSONObject buildWriterTemplateByChapter(JSONObject exemplaryChapterJson, OutputStream outputStream) throws RuntimeException {
        try {
            String text = exemplaryChapterJson.getString("text");
            if (StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("范文章节不能为空");
            }

            JSONArray prompt = buildJsonPrompt(this.chapterTemplatePrompt, 
                            String.format("\n【范文章节】：\n%s\n", text));
            String template = invokeLlm(prompt, outputStream, true, true);

            JSONObject templateJson = JSON.parseObject(template);
            if (exemplaryChapterJson.containsKey("title")) {
                templateJson.put("title", exemplaryChapterJson.getString("title"));
            } else {
                templateJson.put("title", "段落");
            }

            return templateJson;
        } catch (Exception e) {
            logger.error("生成章节模板失败: {} \n", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 快速从参考内容中筛选出符合要求的片段
     * @param fragmentCollection 片段集合
     * @param writingTemplate 写作模板
     * @return 筛选出的参考内容
     */
    private String firstFilterRefrenceContent(JSONArray fragmentCollection, String articleTitle, JSONObject writingTemplate, OutputStream outputStream) throws RuntimeException {        
        if (fragmentCollection == null || fragmentCollection.isEmpty()) {
            logger.warn("片段集合为空，返回空字符串");
            return "";
        }
        
        try {
            StringBuffer fragmentText = new StringBuffer();
            for (int i = 0; i < fragmentCollection.size(); i++) {
                JSONObject fragmentCollectionJson = fragmentCollection.getJSONObject(i);
                if (fragmentCollectionJson.containsKey("text")) {
                    fragmentText.append(fragmentCollectionJson.getString("text")).append("\n");
                }
            }

            JSONArray prompt = buildJsonPrompt(this.firstFilterPrompt, 
                        String.format("\n【文章标题】：\n%s\n", articleTitle), 
                        String.format("\n【章节功能描述】：\n%s\n", writingTemplate.getString("function")), 
                        String.format("\n【章节关键信息槽位】：\n%s\n", writingTemplate.getJSONArray("required_slots").toJSONString()), 
                        String.format("\n【参考内容】：\n%s\n", fragmentText.toString()));

            String filter = invokeLlm(prompt, outputStream, true, false);
            if (StringUtils.isBlank(filter)) {
                return "";
            }
            logger.debug("初筛参考内容成功: {}", filter);

            return filter;
        } catch (Exception e) {
            logger.error("筛选片段失败: {} \n", e.getMessage(), e);
        }

        return "";
    }

    /**
     * 萃取参考内容
     * @param title 文章标题
     * @param refrenceContent 参考内容
     * @param writingTemplate 写作模板
     * @return 参考内容提取
     */
    private String extractRefrenceContent(String refrenceContent, JSONObject writingTemplate, OutputStream outputStream) throws RuntimeException {
        if (StringUtils.isBlank(refrenceContent)) {
            logger.warn("参考内容为空，返回大模型生成的参考内容");
            safeWriteToStream(outputStream, "\n【参考内容为空，由大模型生成参考内容】\n", true);
            // return "";
        }
        
        JSONArray prompt = buildJsonPrompt(this.refrenceExtractPrompt, 
                        String.format("\n【章节关键信息槽位】：\n%s\n", writingTemplate.getJSONArray("required_slots").toJSONString()), 
                        String.format("\n【参考内容】：\n%s\n", refrenceContent));
        
        try {
            String refrence = invokeLlm(prompt, outputStream, true, true);
            logger.debug("提取参考内容成功: {}", refrence);

            return refrence;
        } catch (Exception e) {
            logger.error("提取参考内容失败: " + e.getMessage(), e);
        }

        return "";
    }

    /**
     * 写作章节
     * @param title 文章标题
     * @param writingTemplate 写作模板
     * @param refrence 参考内容
     * @param writingCause 内容要求
     * @param wordsLimit 字数限制
     * @param outputStream 输出流
     * @return 写作章节内容
     */
    private String writeChapter(String title, JSONObject writingTemplate, String refrence, String writingCause, int wordsLimit, boolean onlyThinking, OutputStream outputStream) {
        String templateTitle = writingTemplate.getString("title");
        if ("引言".equals(templateTitle)) {
            writingCause = String.format("%s\n%s", "引言要求：开篇点题，背景+论点，一个自然段，≤200字。", writingCause);
            wordsLimit = -1;
        }

        String wordsLimitPrompt = "";
        if (wordsLimit > 0) {
            wordsLimitPrompt = String.format("\n【内容长度要求】：\n%d 字之间，但不能出现冗余的内容，不能破坏内容的完整性。\n", wordsLimit);
        }

        JSONArray prompt = buildJsonPrompt(this.writerPrompt, 
                                            String.format("\n【文章标题】：\n%s\n", title), 
                                            String.format("\n【语言风格】：\n%s\n", this.exemplaryArticleTypeJson.getString("style")),
                                            String.format("\n【阅读对象】：\n%s\n", this.writingArticleTypeJson.getString("reader")),
                                            String.format("\n【写作模板】：\n%s\n", writingTemplate), 
                                            String.format("\n【参考内容】：\n%s\n", refrence),
                                            String.format("\n【内容要求】：\n%s\n", writingCause),
                                            wordsLimitPrompt);

        String content = invokeLlm(prompt, outputStream, onlyThinking, false);

        return content;
    }

    /**
     * 评估写作质量
     * @param content 文章内容
     * @param outputStream 输出流
     * @return 评估结果
     */
    private JSONObject evaluateWritingQuality(String content, String writingTemplate, OutputStream outputStream) throws RuntimeException {
        JSONArray prompt = buildJsonPrompt(this.evaluateQualityPrompt, 
                        String.format("\n【文章内容】：\n%s\n", content),
                        String.format("\n【写作模板】：\n%s\n", writingTemplate),
                        String.format("\n【语言风格】：\n%s\n", this.exemplaryArticleTypeJson.getString("style")),
                        String.format("\n【优化规则】：\n%s\n", this.optimizeRules));
        String result = invokeLlm(prompt, outputStream, true, true);
        try {
            return JSON.parseObject(result);
        } catch (Exception e) {
            logger.error("评估写作质量失败，解析JSON失败: {} \n", result, e);
            throw new RuntimeException("评估写作质量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证ChatParams的完整性
     * @param chatParams 要验证的参数
     * @throws IllegalArgumentException 如果参数无效
     */
    private void validateChatParams(ChatParams chatParams) {
        if (chatParams == null) {
            throw new IllegalArgumentException("ChatParams不能为空");
        }
        
        if (StringUtils.isBlank(chatParams.getImitative())) {
            throw new IllegalArgumentException("范文内容不能为空");
        }

        // logger.info("范文内容: {}", chatParams.getImitative());
        
        if (StringUtils.isBlank(chatParams.getTitle())) {
            throw new IllegalArgumentException("标题不能为空");
        }
        
        // 参考内容可以为空，但需要检查是否为null
        if (chatParams.getReferences() == null) {
            logger.warn("参考内容列表为null，将使用空列表");
        }
    }

    /*
     * 生成文章
     * @param chatParams 参数配置
     * @param outputStream 输出流
     */
    public String writeArticle(ChatParams chatParams, OutputStream outputStream) throws RuntimeException {
        // 输入参数验证
        validateChatParams(chatParams);
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream不能为空");
        }
        
        String exemplaryArticle = chatParams.getImitative();

        // 拼接参考内容
        String refrenceContent = "";
        if (chatParams.getReferences() != null) {
            for (String reference : chatParams.getReferences()) {
                if (StringUtils.isNotBlank(reference)) {
                    refrenceContent += reference.trim() + "\n";
                }
            }
        }
        if (StringUtils.isNotBlank(chatParams.getBriefReference())) {
            refrenceContent += chatParams.getBriefReference().trim() + "\n";
        }

        SpliteText spliteText = new SpliteText(refrenceContent, false, true, true,
                this.callLlm, this.useThink, this.maxToken, this.isExchange);

        // 参考内容分块，每块不超过MAX_REFERENCE_LENGTH
        JSONArray refrenceContentArray = new JSONArray();
        if (StringUtils.isNotBlank(refrenceContent)) {
            refrenceContentArray = spliteText.mergeParagraph(this.MAX_REFERENCE_LENGTH);
            if (refrenceContentArray.isEmpty()) {
                logger.warn("参考内容分块失败，返回空数组");
            }
        }

        // 内容要求
        StringBuffer writingCauseBuffer = new StringBuffer();
        if (StringUtils.isBlank(chatParams.getCause())) {
            writingCauseBuffer.append("");
        } else {
            writingCauseBuffer.append(chatParams.getCause()).append("\n");
        }
        String writingCause = writingCauseBuffer.toString();


        // 获取格式要求和标题
        String articleTitle = chatParams.getTitle();
        String articleType = chatParams.getGwwz();
        if (StringUtils.isBlank(articleType)) {
            articleType = "";
        }

        // 文章长度要求
        int articleLength = chatParams.getArticleLength();
        
        try {
            StringBuffer contentBuffer = new StringBuffer();

            // 检查范文是否可作为写作模板，如果不能，则修改标题符合范文文体类别
            safeWriteToStream(outputStream, "\n【 检查写作模板是否可用于指导文章创作 ... 】\n", true);
            String title = getArticleTitle(exemplaryArticle, articleTitle, articleType, outputStream);
            if (!title.trim().equals(articleTitle.trim())) {
                safeWriteToStream(outputStream, 
                    String.format("\n【 范文不能作为写作模板（文体、用途、主题不一致），修改标题为：%s 】\n\n【范文的体裁】：\n%s\n\n【创作文章的体裁】：\n%s\n", 
                                title, this.exemplaryArticleTypeJson.getString("category"), this.writingArticleTypeJson.getString("category")), 
                    true);
                articleTitle = title;
            }

            // 生成标题
            safeWriteToStream(outputStream, String.format("\n%s\n\n", articleTitle), false);
            contentBuffer.append(articleTitle).append("\n");

            // 范文分块
            spliteText = new SpliteText(exemplaryArticle, true, false, false,
                this.callLlm, this.useThink, this.maxToken, this.isExchange);
            JSONArray arrayJson = new JSONArray();
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(MAX_REFERENCE_LENGTH);
            }
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(MAX_REFERENCE_LENGTH);
            }

            // 生成正文
            for (int i = 0; i < arrayJson.size(); i++) {
                try {
                    JSONObject exemplaryChapterJson = arrayJson.getJSONObject(i);
                    if (exemplaryChapterJson == null) {
                        throw new IllegalArgumentException("范文章节为空");
                    }

                    // 创建章节的写作模板
                    String chapterTitle = exemplaryChapterJson.getString("title");
                    if (StringUtils.isBlank(chapterTitle)) {
                        chapterTitle = "段落";
                    }
                    safeWriteToStream(outputStream, String.format("\n【 分析范文章节《%s》 ... 】\n", chapterTitle), true);
                    JSONObject writingTemplateJson = buildWriterTemplateByChapter(exemplaryChapterJson, nullOutputStream);
                    if (writingTemplateJson == null) {
                        throw new IllegalArgumentException("范文章节的写作模板为空");
                    }

                    String subtitle = writingTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("范文章节的写作模板title为空");
                    }

                    logger.info("范文章节的写作模板: {}", writingTemplateJson.toJSONString());

                    // 初筛参考内容，避免参考内容过多导致大模型无法处理
                    String refrenceText = "";
                    for (int j = 0; j < refrenceContentArray.size(); j++) {
                        safeWriteToStream(outputStream, String.format("\n【 初筛章节《%s》的参考内容 ... 】\n", subtitle), true);
                        JSONArray refrenceContentJson = refrenceContentArray.getJSONArray(j);
                        refrenceText += firstFilterRefrenceContent(refrenceContentJson, articleTitle, writingTemplateJson, outputStream);
                    }
                    
                    // 萃取参考内容，供大模型写作使用
                    safeWriteToStream(outputStream, String.format("\n【 萃取章节《%s》的参考内容 ... 】\n", subtitle), true);
                    String refrence = extractRefrenceContent(refrenceText, writingTemplateJson, outputStream);

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("\n【依据参考内容】：\n%s\n【章节<<%s>>写作中 ... 】\n", refrence, subtitle), true);
                    if (!subtitle.matches("^(引言|段落\\s*)$")) {          
                        contentBuffer.append(subtitle).append("\n");
                        safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    }

                    String chapterContent = writeChapter(articleTitle, writingTemplateJson, refrence, writingCause, articleLength, false, outputStream);
                    contentBuffer.append(chapterContent).append("\n");
                    
                } catch (Exception e) {
                    logger.error("生成第{}个章节失败，跳过该章节", i, e);
                    continue;
                }
            }

            return contentBuffer.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成文章失败: " + e.getMessage(), e);
        }
    }

    // 本地测试用
    /*
    public static void main(String[] args) {
        try {
            String title = "安徽省民政厅万相公文项目建设方案";
            Path path = Paths.get("C:\\Users\\xman\\Desktop\\test2.txt");
            String text = Files.readAllLines(path, Charset.forName("UTF-8")).stream().collect(Collectors.joining("\n"));

            AIWriter aiWriter = new AIWriter(new CallLlm(),true, 8192, false);

            System.out.println("=======================检查范文是否可作为写作模板====================");
            String newTitle = aiWriter.getArticleTitle(text, title, "", nullOutputStream);
            System.out.println("范文" + (newTitle.equals(title) ? "可作为" : "不可作为") + "文章《" + title + "》创作的模板，修改后的标题为：" + newTitle);
            if (!newTitle.equals(title)) {
                title = newTitle;
            }

            JSONArray arrayJson = new JSONArray();
            SpliteText spliteText = new SpliteText(text, true, false, false, new CallLlm(), true, 4096, false);
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(4096);
            } 
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(4096);
            }

            System.out.println("=======================生成写作模板====================");
            JSONObject writerTemplate = aiWriter.buildWriterTemplateByChapter(arrayJson.getJSONObject(0), nullOutputStream);
            System.out.println(writerTemplate.toJSONString());
            

            System.out.println("=======================分块参考内容====================");
            JSONArray refrenceBatchArray = spliteText.mergeParagraph(4096);
            System.out.println(refrenceBatchArray);
            JSONArray refrenceContentArray = refrenceBatchArray.getJSONArray(0);

            System.out.println("=======================初筛参考内容====================");
            System.out.println("章节标题：" + writerTemplate.getString("title"));
            String firstFilterRefrenceContent = aiWriter.firstFilterRefrenceContent(refrenceContentArray, title, writerTemplate, nullOutputStream);
            System.out.println(firstFilterRefrenceContent);

            System.out.println("=======================萃取参考内容====================");
            String refrence = aiWriter.extractRefrenceContent(firstFilterRefrenceContent, writerTemplate, nullOutputStream);
            System.out.println(refrence);

            // String refrence = "";
            System.out.println("=======================章节写作====================");
            String content = aiWriter.writeChapter(title, writerTemplate, refrence, "", -1, false, nullOutputStream);
            System.out.println(content);

            System.out.println("=======================评估写作质量====================");
            JSONObject evaluateQuality = aiWriter.evaluateWritingQuality(content, writerTemplate.toJSONString(), nullOutputStream);
            System.out.println(evaluateQuality.toJSONString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
}
