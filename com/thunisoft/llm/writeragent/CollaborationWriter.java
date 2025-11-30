package com.thunisoft.llm.writeragent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import java.io.OutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thunisoft.llm.domain.ChatParams;
import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.writeragent.utils.SpliteText;
import com.thunisoft.llm.writeragent.utils.PromptConfig;

import com.thunisoft.llm.service.impl.CallLlm;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.util.Arrays;

/**
 * 协作写作
 * 根据范文和参考内容，生成文章
 * 1. 检查范文是否可作为写作模板
 * 2. 修改标题符合范文文体类别
 * 3. 创建单个章节的写作模板
 * 4. 拆分参考内容 为最小主题语义片段
 * 5. 筛选参考内容是否符合章节框架
 * 6. 依据参考内容，写作章节
 */
public class CollaborationWriter extends AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(CollaborationWriter.class);

    private final String articleTypePrompt;
    private final String modifyTitlePrompt;
    private final String chapterTemplatePrompt;
    private final String refrenceBreakdownPrompt;
    private final String referenceFilterPrompt;
    private final String chapterWritingPrompt;
    private final String articleCategory;
    private final String outlineTemplatePrompt;

    private JSONObject exemplaryArticleTypeJson;
    private JSONObject writingArticleTypeJson;

    /**
     * 构造函数
     * 
     * @param callLlm    LLM调用服务，不能为空
     * @param useThink   是否使用思考模式
     * @param maxToken   最大token数，必须大于0
     * @param isExchange 是否交换模式
     * @throws IllegalArgumentException 如果参数无效
     */
    public CollaborationWriter(ICallLlm callLlm, boolean useThink, int maxToken, boolean isExchange) {
        super(callLlm, useThink, maxToken, isExchange);

        PromptConfig.setPromptFileName("collaboration.yml");
        this.articleTypePrompt = PromptConfig.getPrompt("articleTypePrompt");
        this.modifyTitlePrompt = PromptConfig.getPrompt("modifyTitlePrompt");
        this.articleCategory = PromptConfig.getPrompt("articleCategory");
        this.chapterTemplatePrompt = PromptConfig.getPrompt("chapterTemplatePrompt");
        this.refrenceBreakdownPrompt = PromptConfig.getPrompt("refrenceBreakdownPrompt");
        this.referenceFilterPrompt = PromptConfig.getPrompt("referenceFilterPrompt");
        this.chapterWritingPrompt = PromptConfig.getPrompt("chapterWritingPrompt");
        this.outlineTemplatePrompt = PromptConfig.getPrompt("outlineTemplatePrompt");
    }

    /**
     * 获取文章体裁
     * 
     * @param content      文章内容
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
     * 
     * @param exemplaryArticle 范文
     * @return 是否可作为写作模板
     */
    private String getArticleTitle(String exemplaryArticle, String articleTitle, String articleType,
            OutputStream outputStream) throws RuntimeException {
        if (StringUtils.isBlank(exemplaryArticle)) {
            throw new IllegalArgumentException("范文不能为空");
        }

        if (StringUtils.isBlank(articleTitle)) {
            throw new IllegalArgumentException("文章标题不能为空");
        }
        // 范文截取
        String exemplaryArticleText = exemplaryArticle.substring(0,
                Math.min(exemplaryArticle.length(), (int) (this.MAX_REFERENCE_LENGTH * 0.6)));

        try {
            this.exemplaryArticleTypeJson = getArticleType(exemplaryArticleText, outputStream);
            this.writingArticleTypeJson = getArticleType(String.format("文章标题：%s\n文章类型：%s", articleTitle, articleType),
                    outputStream);

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
     * 根据范文章节，创建单个章节的写作模板
     * 
     * @param exemplaryChapterJson 范文章节
     * @param outputStream         输出流
     * @return 写作模板
     */
    private JSONObject buildWriterTemplateByChapter(JSONObject exemplaryChapterJson, OutputStream outputStream)
            throws RuntimeException {
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
            throw new RuntimeException("生成章节模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文章标题和内容要求，生成文章大纲和章节框架
     * 
     * @param articleTitle 文章标题
     * @param articleType  文章类型
     * @param outline      文章大纲
     * @param writingCause 内容要求
     * @param outputStream 输出流
     * @return 文章大纲和章节框架
     */
    private JSONArray buildArticleOutline(String articleTitle,
            String articleType, String writingCause, String outline, OutputStream outputStream) {
        try {
            if (StringUtils.isBlank(articleTitle)) {
                throw new IllegalArgumentException("文章标题不能为空");
            }

            if (StringUtils.isBlank(articleType)) {
                articleType = "";
            }

            if (StringUtils.isBlank(writingCause)) {
                writingCause = "";
            }

            if (StringUtils.isBlank(outline)) {
                outline = "";
            }

            JSONArray prompt = buildJsonPrompt(this.outlineTemplatePrompt,
                    String.format("\n【文章标题】：\n%s\n", articleTitle),
                    String.format("\n【文章类型】：\n%s\n", articleType),
                    String.format("\n【文章大纲】：\n%s\n", outline),
                    String.format("\n【内容要求】：\n%s\n", writingCause));
            String result = invokeLlm(prompt, outputStream, true, true);
            return JSON.parseArray(result);
        } catch (Exception e) {
            throw new RuntimeException("生成文章大纲和章节框架失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拆分参考内容 为主题语义片段
     * 
     * @param referenceContent 参考内容
     * @param outputStream     输出流
     * @return 最小主题语义片段
     */
    private JSONArray breakdownReference(JSONObject writingTemplate, JSONArray fragmentCollection, OutputStream outputStream) 
        throws RuntimeException {
        try {
            StringBuffer fragmentText = new StringBuffer();
            for (int i = 0; i < fragmentCollection.size(); i++) {
                JSONObject fragmentCollectionJson = fragmentCollection.getJSONObject(i);
                if (fragmentCollectionJson.containsKey("text")) {
                    fragmentText.append(fragmentCollectionJson.getString("text")).append("\n");
                }
            }

            JSONArray prompt = buildJsonPrompt(this.refrenceBreakdownPrompt,
                    String.format("\n【写作框架】：\n%s\n", writingTemplate.toJSONString()),
                    String.format("\n【参考内容】：\n%s\n", fragmentText.toString()));
            String result = invokeLlm(prompt, outputStream, true, true);
            
            return JSON.parseArray(result);
        } catch (Exception e) {
            throw new RuntimeException("拆分参考内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分析参考内容，每块不超过MAX_REFERENCE_LENGTH * 0.8
     * 
     * @param refrenceContent 参考内容
     * @param writingTemplate 写作模板
     * @param outputStream    输出流
     * @return 合并后的参考内容
     */
    private JSONArray analyzeReference(String refrenceContent, JSONObject writingTemplate, OutputStream outputStream) 
        throws RuntimeException {
        if (StringUtils.isBlank(refrenceContent)) 
            throw new IllegalArgumentException("参考内容不能为空");

        try {
            SpliteText spliteText = new SpliteText(refrenceContent, false, true, false,
                this.callLlm, this.useThink, this.maxToken, this.isExchange);

            // 参考内容分块，每块不超过MAX_REFERENCE_LENGTH
            JSONArray refrenceContentArray = new JSONArray();
            if (StringUtils.isNotBlank(refrenceContent)) {
                refrenceContentArray = spliteText.mergeParagraph(this.MAX_REFERENCE_LENGTH);
                if (refrenceContentArray.isEmpty()) {
                    throw new RuntimeException("参考内容分块失败");
                }
            }

            // 参考内容拆分为最小主题语义片段
            JSONArray referenceCollection = new JSONArray();
            for (int i = 0; i < refrenceContentArray.size(); i++) {
                JSONArray fragmentArray = refrenceContentArray.getJSONArray(i);
                if (fragmentArray != null && !fragmentArray.isEmpty()) {
                    referenceCollection.addAll(breakdownReference(writingTemplate, fragmentArray, outputStream));
                }
            }

            // logger.info("拆分参考内容成功: {}", referenceCollection.toJSONString());

            // 将最小语义片段按大小合并，每块不超过MAX_REFERENCE_LENGTH * 0.8
            int refLength = 0;
            JSONArray refArray = new JSONArray();
            JSONArray mergedReferenceCollection = new JSONArray();
            int maxRefLength = (int) (this.MAX_REFERENCE_LENGTH * 0.8);
            for (int i = 0; i < referenceCollection.size(); i++) {
                JSONObject refChunk = referenceCollection.getJSONObject(i);
                if (refChunk == null || StringUtils.isBlank(refChunk.getString("content")))
                    continue;
                refLength += refChunk.getString("content").length();
                if (refLength > maxRefLength) {
                    mergedReferenceCollection.add(refArray);
                    refLength = refChunk.getString("content").length();
                    refArray = new JSONArray();
                    refArray.add(refChunk);
                } else {
                    refArray.add(refChunk);
                }
            }
            if (!refArray.isEmpty()) {
                mergedReferenceCollection.add(refArray);
            }

            return mergedReferenceCollection;
        } catch (Exception e) {
            throw new RuntimeException("合并参考内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 筛选参考内容
     * 
     * @param fragmentCollection 最小主题语义片段
     * @param writingTemplate    写作模板s
     * @param outputStream       输出流
     * @return 筛选后的参考内容
     */
    private JSONArray filterReferenceContent(String articleTitle, JSONArray fragmentCollection,
            JSONObject writingTemplate, OutputStream outputStream) throws RuntimeException {
        if (fragmentCollection == null || fragmentCollection.isEmpty()) {
            throw new IllegalArgumentException("最小主题语义片段不能为空");
        }

        try {
            JSONArray prompt = buildJsonPrompt(this.referenceFilterPrompt,
                    String.format("\n【文章标题】：\n%s\n", articleTitle),
                    String.format("\n【章节框架】：\n%s\n", writingTemplate),
                    String.format("\n【最小主题语义片段】：\n%s\n", fragmentCollection));
            String result = invokeLlm(prompt, outputStream, true, true);

            return JSON.parseArray(result);
        } catch (Exception e) {
            throw new RuntimeException("筛选参考内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 写作章节
     * 
     * @param title           文章标题
     * @param writingTemplate 写作模板
     * @param refrence        参考内容
     * @param writingCause    内容要求
     * @param wordsLimit      字数限制
     * @param outputStream    输出流
     * @return 写作章节内容
     */
    private String writeChapter(String title, JSONObject writingTemplate, JSONArray referenceCollection,
            String writingCause, int wordsLimit, boolean onlyThinking, OutputStream outputStream) {
        String templateTitle = writingTemplate.getString("title");
        if ("引言".equals(templateTitle)) {
            writingCause = String.format("%s\n%s", "引言要求：开篇点题，背景+论点，一个自然段，≤200字。", writingCause);
            wordsLimit = -1;
        }

        String wordsLimitPrompt = "";
        if (wordsLimit > 0) {
            wordsLimitPrompt = String.format("\n【内容长度要求】：\n%d 字之间，但不能出现冗余的内容，也不能破坏内容的完整性。\n", wordsLimit);
        }

        JSONArray prompt = buildJsonPrompt(this.chapterWritingPrompt,
                String.format("\n【文章标题】：\n%s\n", title),
                String.format("\n【章节框架】：\n%s\n", writingTemplate),
                String.format("\n【参考内容】：\n%s\n", referenceCollection),
                String.format("\n【内容要求】：\n%s\n", writingCause),
                wordsLimitPrompt);

        String content = invokeLlm(prompt, outputStream, onlyThinking, false);

        return content;
    }

    /**
     * 将数字转换为中文
     * 
     * @param number 1-99之间的数字
     * @return 1-99之间的数字对应的中文
     */
    private String numberToChinese(int number) throws IllegalArgumentException {
        if (number < 1 || number > 99) {
            throw new IllegalArgumentException("数字必须在1-99之间");
        }
        String[] digit = {"零","一","二","三","四","五","六","七","八","九"};
        if (number < 10) {
            return digit[number];
        }
        int ten = number / 10, rem = number % 10;
        if (ten == 1) {
            return (rem == 0 ? "十" : "十" + digit[rem]);
        }
        return digit[ten] + "十" + (rem == 0 ? "" : digit[rem]);
    }

    /**
     * 验证ChatParams的完整性
     * 
     * @param chatParams 要验证的参数
     * @throws IllegalArgumentException 如果参数无效
     */
    private void validateChatParams(ChatParams chatParams) {
        if (chatParams == null) {
            throw new IllegalArgumentException("ChatParams不能为空");
        }

        if (StringUtils.isBlank(chatParams.getTitle())) {
            throw new IllegalArgumentException("标题不能为空");
        }

        if (chatParams.getReferences() == null || chatParams.getReferences().isEmpty()) {
            throw new IllegalArgumentException("参考内容不能为空");
        }
    }

    /**
     * 根据范文，生成文章
     * 
     * @param articleTitle        文章标题
     * @param articleType         文章类型
     * @param writingCause        内容要求
     * @param articleLength       文章长度
     * @param exemplaryArticle    范文
     * @param referenceCollection 参考内容
     * @param outputStream        输出流
     * @return 文章内容
     */
    private String writeArticleByExemplary(String articleTitle,
            String articleType, String writingCause, int articleLength, String exemplaryArticle,
            String refrenceContent, OutputStream outputStream) throws RuntimeException {
        try {
            StringBuffer contentBuffer = new StringBuffer();

            // 检查范文是否可作为写作模板，如果不能，则修改标题符合范文文体类别
            safeWriteToStream(outputStream, "\n【 检查写作模板是否可用于指导文章创作 ... 】\n", true);
            String title = getArticleTitle(exemplaryArticle, articleTitle, articleType, outputStream);
            if (!title.trim().equals(articleTitle.trim())) {
                safeWriteToStream(outputStream,
                        String.format("\n【 范文不能作为写作模板（文体、用途、主题不一致），修改标题为：%s 】\n\n【范文的体裁】：\n%s\n\n【创作文章的体裁】：\n%s\n",
                                title, this.exemplaryArticleTypeJson.getString("category"),
                                this.writingArticleTypeJson.getString("category")),
                        true);
                articleTitle = title;
            }

            logger.info("文章标题：{}", articleTitle);

            // 生成标题
            safeWriteToStream(outputStream, String.format("\n%s\n\n", articleTitle), false);
            contentBuffer.append(articleTitle).append("\n");

            // 范文分块
            SpliteText spliteText = new SpliteText(exemplaryArticle, true, false, false,
                    this.callLlm, this.useThink, this.maxToken, this.isExchange);
            JSONArray arrayJson = new JSONArray();
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(MAX_REFERENCE_LENGTH);
            }
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(MAX_REFERENCE_LENGTH);
            }

            // 生成正文
            boolean isPreface = false;
            for (int i = 0; i < arrayJson.size(); i++) {
                try {
                    JSONObject exemplaryChapterJson = arrayJson.getJSONObject(i);
                    if (exemplaryChapterJson == null) {
                        throw new IllegalArgumentException("范文章节为空");
                    }

                    // 创建章节的写作模板
                    String chapterTitle = exemplaryChapterJson.getString("title");
                    if (chapterTitle == null) {
                        chapterTitle = "段落";
                    }
                    safeWriteToStream(outputStream,
                            String.format("\n【 分析范文章节《%s》 ... 】\n", chapterTitle), true);
                    JSONObject writingTemplateJson = buildWriterTemplateByChapter(exemplaryChapterJson, nullOutputStream);

                    String subtitle = writingTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("范文章节的写作模板title为空");
                    }
                    if (subtitle.matches("^引言$")) {
                        isPreface = true;
                    }

                    logger.debug("范文章节的写作模板: {}", writingTemplateJson.toJSONString());

                    // 分析参考内容
                    safeWriteToStream(outputStream, String.format("\n【 分析范文章节《%s》的参考内容 ... 】\n", subtitle), true);
                    JSONArray referenceCollection = analyzeReference(refrenceContent, writingTemplateJson, outputStream);

                    // 筛选参考内容
                    safeWriteToStream(outputStream, String.format("\n【 筛选章节《%s》的参考内容 ... 】\n", subtitle), true);
                    JSONArray filteredReferenceCollection = new JSONArray();
                    for (int j = 0; j < referenceCollection.size(); j++) {
                        filteredReferenceCollection.addAll(filterReferenceContent(articleTitle,
                                referenceCollection.getJSONArray(j), writingTemplateJson, outputStream));
                    }

                    logger.debug("筛选章节《{}》的参考内容：{}", subtitle, filteredReferenceCollection.toJSONString());

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("\n【依据参考内容】：\n%s\n【章节<<%s>>写作中 ... 】\n",
                            filteredReferenceCollection.toJSONString(), subtitle), true);
                    if (!subtitle.matches("^(引言|段落\\s*)$")) {
                        if (!subtitle.matches("^[第]?[一二三四五六七八九十零〇]+[\\.\\s、章篇部分节]{1}.*$")) {
                            subtitle = (isPreface ? numberToChinese(i) : numberToChinese(i + 1)) + "、" + subtitle;
                        }
                        contentBuffer.append(subtitle).append("\n");
                        safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    }

                    String chapterContent = writeChapter(articleTitle, writingTemplateJson, filteredReferenceCollection,
                            writingCause, articleLength, false, outputStream);
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

    /**
     * 根据文章大纲，生成文章
     * 
     * @param articleTitle        文章标题
     * @param articleType         文章类型
     * @param writingCause        内容要求
     * @param articleLength       文章长度
     * @param outline             文章大纲
     * @param referenceCollection 参考内容
     * @param outputStream        输出流
     * @return 文章内容
     */
    private String writeArticleByOutline(String articleTitle,
            String articleType, String writingCause, int articleLength, String outline,
            String refrenceContent, OutputStream outputStream) throws RuntimeException {
        try {
            StringBuffer contentBuffer = new StringBuffer();

            // 生成标题
            contentBuffer.append(articleTitle).append("\n");
            safeWriteToStream(outputStream, String.format("%s\n\n", articleTitle), false);

            // 生成文章大纲和章节框架
            safeWriteToStream(outputStream, "\n【 生成文章大纲和章节框架 ... 】\n", true);
            JSONArray outlineCollection = buildArticleOutline(articleTitle, articleType, writingCause, outline,
                    outputStream);
            
            if (outlineCollection == null || outlineCollection.isEmpty()) {
                throw new RuntimeException("生成文章大纲和章节框架失败，返回结果为空");
            }

            logger.debug("文章大纲和章节框架：{}", outlineCollection.toJSONString());

            // 生成章节
            for (int i = 0; i < outlineCollection.size(); i++) {
                try {
                    JSONObject chapterTemplateJson = outlineCollection.getJSONObject(i);

                    String subtitle = chapterTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("文章大纲和章节框架title为空");
                    }

                    // 分析参考内容
                    safeWriteToStream(outputStream, String.format("\n【 分析章节《%s》的参考内容 ... 】\n", subtitle), true);
                    JSONArray referenceCollection = analyzeReference(refrenceContent, chapterTemplateJson, outputStream);

                    // 筛选参考内容
                    safeWriteToStream(outputStream, String.format("\n【 筛选章节《%s》的参考内容 ... 】\n", subtitle), true);
                    JSONArray filteredReferenceCollection = new JSONArray();
                    for (int j = 0; j < referenceCollection.size(); j++) {
                        filteredReferenceCollection.addAll(filterReferenceContent(articleTitle,
                                referenceCollection.getJSONArray(j), chapterTemplateJson, outputStream));
                    }

                    logger.debug("筛选章节《{}》的参考内容：{}", subtitle, filteredReferenceCollection.toJSONString());

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("\n【 生成章节《%s》 ... 】\n", subtitle), true);
                    if (!subtitle.matches("^(引言|段落\\s*)$")) {
                        subtitle = numberToChinese(i + 1) + "、" + subtitle;
                        contentBuffer.append(subtitle).append("\n");
                        safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    }

                    String chapterContent = writeChapter(articleTitle, chapterTemplateJson, filteredReferenceCollection,
                            writingCause, articleLength, false, outputStream);
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

    /**
     * 生成文章
     * 
     * @param chatParams   参数配置
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

        // 获取文章大纲
        String outline = chatParams.getOutline();

        // 根据范文或文章大纲，生成文章
        logger.info("根据范文或文章大纲，生成文章");
        String content = "";
        if (StringUtils.isNotBlank(exemplaryArticle)) {
            content = writeArticleByExemplary(articleTitle, articleType, writingCause, articleLength, 
                    exemplaryArticle, refrenceContent, outputStream);
        } else {
            content = writeArticleByOutline(articleTitle, articleType, writingCause, articleLength, 
                    outline, refrenceContent, outputStream);
        }

        return content;
    }

    // 本地测试用
    /*
    public static void main(String[] args){
        ICallLlm callLlm = new CallLlm();
        CollaborationWriter collaborationWriter = new CollaborationWriter(callLlm, false, 8192, false);

        ChatParams chatParams = new ChatParams();
        chatParams.setTitle("OA 系统研发部月度总结");
        chatParams.setGwwz("总结");
        chatParams.setCause("");
        chatParams.setArticleLength(1000);
        chatParams.setOutline("");
        chatParams.setImitative("");

        System.out.println("=======================生成文章====================");
        try {
            String refer1 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\1.txt"), "UTF-8");
            String refer2 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\2.txt"), "UTF-8");
            String refer3 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\3.txt"), "UTF-8");
            chatParams.setReferences(new ArrayList<>(Arrays.asList(refer1, refer2, refer3)));

            String content = collaborationWriter.writeArticle(chatParams, nullOutputStream);
            System.out.println(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
         */
}
