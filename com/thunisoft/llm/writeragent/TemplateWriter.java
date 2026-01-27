package com.thunisoft.llm.writeragent;

import java.io.OutputStream;
import java.lang.String;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thunisoft.llm.domain.ChatParams;
import com.thunisoft.llm.writeragent.utils.SpliteText;
import com.thunisoft.llm.writeragent.utils.PromptConfig;
import com.thunisoft.llm.writeragent.utils.XCallLlm;

import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.util.Arrays;

/**
 * 根据范文和参考内容，生成文章
 */
public class TemplateWriter extends AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(TemplateWriter.class);

    private final static String PROMPT_FILE_NAME = "template.yml";

    // private static final String GW_PATTERN = "^(通知|决定|批复|意见|公告|通报|报告|请示|批复|函|纪要|意见|议案|通告|公报|令)$";

    private final String articleTypePrompt;
    private final String modifyTitlePrompt;
    private final String chapterTemplatePrompt;
    private final String firstFilterPrompt;
    private final String refrenceExtractPrompt;
    private final String chapterWritingPrompt;
    private final String evaluateQualityPrompt;
    private final String optimizeRules;

    private JSONObject exemplaryArticleTypeJson = null;
    private JSONObject writingArticleTypeJson = null;
    
    /**
     * 构造函数
     * @param callLlm CallLlm实例，不能为null
     * @param useThink 是否使用思考模式
     * @param isExchange 是否交换模式
     */
    public TemplateWriter(XCallLlm callLlm, boolean useThink, boolean isExchange) {
        super(callLlm, useThink, isExchange);

        this.refrenceExtractPrompt = PromptConfig.getPrompt("refrenceExtractPrompt", PROMPT_FILE_NAME);
        this.chapterWritingPrompt = PromptConfig.getPrompt("chapterWritingPrompt", PROMPT_FILE_NAME);
        this.firstFilterPrompt = PromptConfig.getPrompt("firstFilterPrompt", PROMPT_FILE_NAME);
        this.chapterTemplatePrompt = PromptConfig.getPrompt("chapterTemplatePrompt", PROMPT_FILE_NAME);
        this.articleTypePrompt = PromptConfig.getPrompt("articleTypePrompt", PROMPT_FILE_NAME);
        this.modifyTitlePrompt = PromptConfig.getPrompt("modifyTitlePrompt", PROMPT_FILE_NAME);
        this.evaluateQualityPrompt = PromptConfig.getPrompt("evaluateQualityPrompt", PROMPT_FILE_NAME);

        this.optimizeRules = PromptConfig.getPrompt("optimizeRules", PROMPT_FILE_NAME);
    }
    
    /**
     * 获取文章体裁
     * @param content 文章内容
     * @param outputStream 输出流
     * @return 文章体裁
     */
    private JSONObject getArticleType(String content, OutputStream outputStream) throws RuntimeException {
        try {
            safeWriteToStream(outputStream, "\n【 获取文章体裁 ... 】\n", true);

            JSONArray prompt = buildJsonPrompt(this.articleTypePrompt,
                                               String.format("\n【文章内容】：\n%s\n", content));
            String result = invokeLlm(prompt, nullOutputStream, true, true);
        
            logger.info("获取文章体裁成功: {}", result);
            return JSON.parseObject(result);
        } catch (Exception e) {
            throw new RuntimeException("获取文章体裁失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查范文是否可作为写作模板
     * @param exemplaryArticle 范文
     * @return 是否可作为写作模板
     */
    private String getArticleTitle(String exemplaryArticle, String articleTitle, String articleType,
        OutputStream outputStream) throws IllegalArgumentException {
        if (StringUtils.isBlank(articleTitle)) {
            throw new IllegalArgumentException("文章标题不能为空");
        }
        if (StringUtils.isBlank(exemplaryArticle)) {
            return articleTitle;
        }
        
        // 范文截取
        String exemplaryArticleText = exemplaryArticle.substring(0,
                Math.min(exemplaryArticle.length(), (int) (this.MAX_INPUT_TOKEN * 0.6)));

        try {
            this.writingArticleTypeJson = getArticleType(String.format("文章标题：%s\n文章类型：%s", articleTitle, articleType), outputStream);
            this.exemplaryArticleTypeJson = getArticleType(exemplaryArticleText, outputStream);

            /* 检查文体类别是否一致 */
            String exemplaryArticleCategory = this.exemplaryArticleTypeJson.getString("category");
            String writingArticleCategory = this.writingArticleTypeJson.getString("category");
            if (exemplaryArticleCategory != null && exemplaryArticleCategory.equals(writingArticleCategory))
                return articleTitle;

            safeWriteToStream(outputStream, String.format("\n【 修改标题，将标题 %s 修改为符合范文文体类别的标题 ... 】\n", articleTitle), true);

            /* 修改标题 */
            JSONArray prompt = buildJsonPrompt(this.modifyTitlePrompt,
                    String.format("\n【文体】：\n%s\n", this.exemplaryArticleTypeJson),
                    String.format("\n【文章标题】：\n%s\n", articleTitle));
            String result = invokeLlm(prompt, nullOutputStream, true, false);
            logger.info("修改标题成功: {}", result);

            return result;
        } catch (Exception e) {
            logger.error("检查范文是否可作为写作模板失败: " + e.getMessage(), e);
            return articleTitle;
        }
    }

    /**
     * 创建单个章节的写作模板
     * @param exemplaryChapterJson 范文章节
     * @param outputStream 输出流
     * @return 写作模板
     */
    private JSONObject buildWriterTemplateByChapter(JSONObject exemplaryChapterJson, OutputStream outputStream) 
        throws IllegalArgumentException {
        try {
            String text = exemplaryChapterJson.getString("text");
            if (StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("范文章节内容不能为空");
            }

            String title = exemplaryChapterJson.getString("title");
            if (StringUtils.isNotBlank(title)) {
                text = title + "\n" + text;
            }

            safeWriteToStream(outputStream, String.format("\n【 生成章节《%s》 写作模板 ... 】\n", exemplaryChapterJson.getString("title")), true);

            JSONArray prompt = buildJsonPrompt(this.chapterTemplatePrompt, String.format("\n【范文章节】：\n%s\n", text));
            String template = invokeLlm(prompt, nullOutputStream, true, true);

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
    private String firstFilterRefrenceContent(JSONArray fragmentCollection, String articleTitle, JSONObject writingTemplate, OutputStream outputStream) 
        throws RuntimeException {        
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

            safeWriteToStream(outputStream, String.format("\n【 初筛章节《%s》的参考内容 ... 】\n", writingTemplate.getString("title")), true);

            JSONArray prompt = buildJsonPrompt(this.firstFilterPrompt, 
                        String.format("\n【文章标题】：\n%s\n", articleTitle), 
                        String.format("\n【章节功能描述】：\n%s\n", writingTemplate.getString("function")), 
                        String.format("\n【章节关键信息槽位】：\n%s\n", writingTemplate.getJSONArray("required_slots").toJSONString()), 
                        String.format("\n【参考内容】：\n%s\n", fragmentText.toString()));

            String filter = invokeLlm(prompt, nullOutputStream, true, false);
            if (StringUtils.isBlank(filter)) {
                return "";
            }
            logger.debug("初筛参考内容成功: {}", filter);

            return filter;
        } catch (Exception e) {
            if (e.getMessage().contains("大模型返回的结果为空")) {
                return "";
            }
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
            safeWriteToStream(outputStream, String.format("\n【 萃取章节《%s》的参考内容 ... 】\n", writingTemplate.getString("title")), true);

            String refrence = invokeLlm(prompt, nullOutputStream, true, true);
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
    private String writeChapter(String title, JSONObject writingTemplate, String refrence, String writingCause, int wordsLimit, OutputStream outputStream) {
        String templateTitle = writingTemplate.getString("title");
        if ("引言".equals(templateTitle)) {
            writingCause = String.format("%s\n%s", "引言要求：开篇点题，背景+论点，一个自然段，≤200字。", writingCause);
            wordsLimit = -1;
        }

        String wordsLimitPrompt = "";
        if (wordsLimit > 0) {
            wordsLimitPrompt = String.format("\n【内容长度要求】：\n%d 字之间，但不能因为长度要求而出现冗余的内容，也不能破坏内容的完整性。\n", wordsLimit);
        }

        safeWriteToStream(outputStream, String.format("\n【章节《%s》写作中 ... 】\n", templateTitle), true);

        JSONArray prompt = buildJsonPrompt(this.chapterWritingPrompt, 
                            String.format("\n【文章标题】：\n%s\n", title), 
                            this.exemplaryArticleTypeJson == null ? "" : String.format("\n【语言风格】：\n%s\n", this.exemplaryArticleTypeJson.getString("style")),
                            this.exemplaryArticleTypeJson == null ? "" : String.format("\n【阅读对象】：\n%s\n", this.exemplaryArticleTypeJson.getString("reader")),
                            String.format("\n【写作模板】：\n%s\n", writingTemplate), 
                            String.format("\n【参考内容】：\n%s\n", refrence),
                            String.format("\n【内容要求】：\n%s\n", writingCause),
                            wordsLimitPrompt);

        String content = invokeLlm(prompt, outputStream, false, false);

        return content;
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
    public String writeArticle(ChatParams chatParams, OutputStream outputStream) 
        throws RuntimeException, IllegalArgumentException {
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

        SpliteText spliteText = new SpliteText(refrenceContent, false, true, true, this.callLlm, this.useThink, this.isExchange);

        // 参考内容分块，每块不超过MAX_REFERENCE_LENGTH
        JSONArray refrenceContentArray = new JSONArray();
        if (StringUtils.isNotBlank(refrenceContent)) {
            refrenceContentArray = spliteText.mergeParagraph(this.MAX_INPUT_TOKEN);
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
            boolean isPreface = false;
            spliteText = new SpliteText(exemplaryArticle, true, false, false, this.callLlm, this.useThink, this.isExchange);
            JSONArray arrayJson = new JSONArray();
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(MAX_INPUT_TOKEN);
                isPreface = true;
            }
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(MAX_INPUT_TOKEN);
                isPreface = false;
            }

            // 生成正文
            for (int i = 0; i < arrayJson.size(); i++) {
                try {
                    JSONObject exemplaryChapterJson = arrayJson.getJSONObject(i);
                    if (exemplaryChapterJson == null) {
                        throw new IllegalArgumentException("范文章节为空");
                    }

                    logger.info("范文章节: {}", exemplaryChapterJson.toJSONString());

                    // 创建章节的写作模板
                    String chapterTitle = exemplaryChapterJson.getString("title");
                    if (StringUtils.isBlank(chapterTitle)) {
                        chapterTitle = "段落";
                    }

                    safeWriteToStream(outputStream, String.format("\n【 分析范文章节《%s》 ... 】\n", chapterTitle), true);
                    JSONObject writingTemplateJson = buildWriterTemplateByChapter(exemplaryChapterJson, outputStream);
                    if (writingTemplateJson == null) {
                        throw new IllegalArgumentException("范文章节的写作模板为空");
                    }

                    String subtitle = writingTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("范文章节的写作模板title为空");
                    }

                    logger.debug("范文章节的写作模板: {}", writingTemplateJson.toJSONString());

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

                    logger.debug("萃取章节《{}》的参考内容: {}", subtitle, refrence);

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("\n【依据参考内容】：\n%s\n【章节<<%s>>写作中 ... 】\n", refrence, subtitle), true);
                    if (!subtitle.matches("^(引言|段落\\s*)$")) {
                        if (!subtitle.matches("^[第]?[一二三四五六七八九十零〇]+[\\.\\s、章篇部分节]{1}.*$")) {
                            subtitle = (isPreface ? numberToChinese(i) : numberToChinese(i + 1)) + "、" + subtitle;
                        }
                        contentBuffer.append(subtitle).append("\n");
                        safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    }

                    String chapterContent = writeChapter(articleTitle, writingTemplateJson, refrence, writingCause, articleLength, outputStream);
                    contentBuffer.append(chapterContent).append("\n");
                    
                } catch (Exception e) {
                    safeWriteToStream(outputStream, String.format(">>生成第 %d 个章节失败，跳过该章节，由于 %s<<", i, e.getMessage()), true);
                    logger.error("生成第{}个章节失败，跳过该章节", i, e);
                    continue;
                }
            }

            return contentBuffer.toString();
        } catch (Exception e) {
            safeWriteToStream(outputStream, ">>生成文章失败<<", true);
            throw new RuntimeException("生成文章失败: " + e.getMessage(), e);
        }
    }

    // 本地测试用
    /*
    public static void main(String[] args){
        TemplateWriter templateWriter = new TemplateWriter(new XCallLlm(), true, false);
        
        ChatParams chatParams = new ChatParams();
        chatParams.setTitle("产品研发部月度总结");
        chatParams.setGwwz("工作总结");
        chatParams.setCause("");
        chatParams.setArticleLength(1000);
        
        System.out.println("=======================生成文章====================");
        try {
            String refer2 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\2.txt"), "UTF-8");
            String refer1 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\1.txt"), "UTF-8");
            String refer3 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\3.txt"), "UTF-8");

            chatParams.setImitative(refer1);

            chatParams.setReferences(new ArrayList<>(Arrays.asList(refer1, refer2, refer3)));

            String content = templateWriter.writeArticle(chatParams, nullOutputStream);
            System.out.println(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
}
