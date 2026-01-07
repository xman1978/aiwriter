package com.thunisoft.llm.writeragent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import java.io.OutputStream;
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
 * 协作写作，基于参考内容，生成文章
 * 1. 检查范文是否可作为写作模板
 * 2. 修改标题符合范文文体类别
 * 3. 创建单个章节的写作模板
 * 4. 拆分参考内容 为最小主题语义片段
 * 5. 筛选参考内容是否符合章节框架
 * 6. 依据参考内容，写作章节
 */
public class CollaborationWriter extends AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(CollaborationWriter.class);

    private final static String TITLE_SPLIT_MARK = "<<ARTICLE_MARK>>";

    private final static String PROMPT_FILE_NAME = "collaboration.yml";

    private final String outlineTemplatePrompt;
    private final String articleTypePrompt;
    private final String modifyTitlePrompt;
    private final String chapterTemplatePrompt;
    private final String referenceFilterPrompt;
    private final String refrenceBreakdownPrompt;
    private final String referenceFinalizedPrompt;
    private final String chapterStructurePlanPrompt;
    private final String chapterWritingPrompt;

    private JSONObject exemplaryArticleTypeJson;
    private JSONObject writingArticleTypeJson;

    /**
     * 构造函数
     * 
     * @param callLlm    CallLlm实例，不能为null
     * @param useThink   是否使用思考模式
     * @param maxToken   最大token数，必须大于0
     * @param isExchange 是否交换模式
     */
    public CollaborationWriter(XCallLlm callLlm, boolean useThink, boolean isExchange) {
        super(callLlm, useThink, isExchange);

        this.chapterWritingPrompt = PromptConfig.getPrompt("chapterWritingPrompt", PROMPT_FILE_NAME);
        this.outlineTemplatePrompt = PromptConfig.getPrompt("outlineTemplatePrompt", PROMPT_FILE_NAME);
        this.articleTypePrompt = PromptConfig.getPrompt("articleTypePrompt", PROMPT_FILE_NAME);
        this.modifyTitlePrompt = PromptConfig.getPrompt("modifyTitlePrompt", PROMPT_FILE_NAME);
        this.chapterTemplatePrompt = PromptConfig.getPrompt("chapterTemplatePrompt", PROMPT_FILE_NAME);
        this.referenceFilterPrompt = PromptConfig.getPrompt("referenceFilterPrompt", PROMPT_FILE_NAME);
        this.refrenceBreakdownPrompt = PromptConfig.getPrompt("refrenceBreakdownPrompt", PROMPT_FILE_NAME);
        this.referenceFinalizedPrompt = PromptConfig.getPrompt("referenceFinalizedPrompt", PROMPT_FILE_NAME);
        this.chapterStructurePlanPrompt = PromptConfig.getPrompt("chapterStructurePlanPrompt", PROMPT_FILE_NAME);
    }

    /**
     * 获取文章体裁
     * 
     * @param content      文章内容
     * @param outputStream 输出流
     * @return 文章体裁
     */
    private JSONObject getArticleType(String content, OutputStream outputStream) throws RuntimeException {
        try {
            JSONArray prompt = buildJsonPrompt(this.articleTypePrompt,
                                               String.format("\n【文章内容】：\n%s\n", content));
            String result = invokeLlm(prompt, outputStream, true, true);
        
            logger.info("获取文章体裁成功: {}", result);
            return JSON.parseObject(result);
        } catch (Exception e) {
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
            OutputStream outputStream) throws IllegalArgumentException {
        if (StringUtils.isBlank(articleTitle)) {
            safeWriteToStream(outputStream, ">>文章标题不能为空<<", true);
            throw new IllegalArgumentException("文章标题不能为空");
        }
        if (StringUtils.isBlank(exemplaryArticle)) {
            return articleTitle;
        }

        // 范文截取
        String exemplaryArticleText = exemplaryArticle.substring(0, Math.min(exemplaryArticle.length(), (int) (this.MAX_INPUT_TOKEN * 0.6)));

        try {
            this.exemplaryArticleTypeJson = getArticleType(exemplaryArticleText, outputStream);
            this.writingArticleTypeJson = getArticleType(String.format("文章标题：%s\n文章类型：%s", articleTitle, articleType), outputStream);

            /* 检查文体类别是否一致 */
            String writingArticleCategory = this.writingArticleTypeJson.getString("category");
            String exemplaryArticleCategory = this.exemplaryArticleTypeJson.getString("category");
            if (exemplaryArticleCategory != null && exemplaryArticleCategory.equals(writingArticleCategory))
                return articleTitle;

            /* 修改标题 */
            JSONArray prompt = buildJsonPrompt(this.modifyTitlePrompt,
                    String.format("\n【文体】：\n%s\n", this.exemplaryArticleTypeJson),
                    String.format("\n【文章标题】：\n%s\n", articleTitle));
            String result = invokeLlm(prompt, outputStream, true, false);
            logger.info("修改标题成功: {}", result);

            return result;
        } catch (Exception e) {
            logger.error("检查范文是否可作为写作模板失败: " + e.getMessage(), e);
            return articleTitle;
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
            throws RuntimeException, IllegalArgumentException {
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
    private JSONArray buildArticleOutline(String articleTitle, String articleType, String writingCause, String outline, 
        OutputStream outputStream) throws RuntimeException {
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
     * 筛选参考内容
     * 
     * @param articleTitle 文章标题
     * @param writingTemplate 写作模板
     * @param fragmentCollection 最小主题语义片段
     * @param outputStream 输出流
     * @return 筛选后的参考内容字符串
     * @throws RuntimeException
     */
    private String filterReferenceContent(JSONObject writingTemplate, String referenceContent,
            OutputStream outputStream) throws RuntimeException {
        try {
            JSONArray prompt = buildJsonPrompt(this.referenceFilterPrompt,
                    String.format("\n【章节标题】：\n%s\n", writingTemplate.getString("title")),
                    String.format("\n【章节主题】：\n%s\n", writingTemplate.getString("subject")),
                    String.format("\n【参考内容】：\n%s\n", referenceContent));

            String result = invokeLlm(prompt, outputStream, true, false);

            // logger.info(" 原始参考内容：{}", referenceContent);
            logger.info(" 章节 {} 筛选后的参考内容：{}", writingTemplate.getString("title"), result);

            return result;
        } catch (Exception e) {
            if (e.getMessage().contains("大模型返回的结果为空")) {
                return "";
            }
            throw new RuntimeException("筛选参考内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析参考内容 为主题语义片段
     * 
     * @param referenceContent 参考内容
     * @param outputStream     输出流
     * @return 最小主题语义片段
     */
    private JSONArray breakdownReference(JSONObject writingTemplate, JSONArray fragmentCollection, OutputStream outputStream) {
        try {
            StringBuffer fragmentText = new StringBuffer();
            for (int i = 0; i < fragmentCollection.size(); i++) {
                JSONObject fragmentCollectionJson = fragmentCollection.getJSONObject(i);
                if (fragmentCollectionJson.containsKey("text")) {
                    fragmentText.append(fragmentCollectionJson.getString("text"));
                }
            }

            // logger.info("参考内容: {}", fragmentText.toString());

            String filteredReferenceContent = filterReferenceContent(writingTemplate, fragmentText.toString(), outputStream);
            if (StringUtils.isBlank(filteredReferenceContent)) {
                return new JSONArray();
            }

            safeWriteToStream(outputStream, String.format("【筛选出的参考内容】\n%s\n", filteredReferenceContent), true);

            // logger.info("过滤参考内容成功: {}", filteredReferenceContent);

            JSONArray prompt = buildJsonPrompt(this.refrenceBreakdownPrompt,
                    String.format("\n【章节框架】：\n%s\n", writingTemplate.toJSONString()),
                    String.format("\n【参考内容】：\n%s\n", filteredReferenceContent));
            String result = invokeLlm(prompt, outputStream, true, true);

            // logger.info("拆分参考内容成功: {}", result);

            return JSON.parseArray(result);
        } catch (Exception e) {
            logger.error("解析参考内容失败: {}", e.getMessage(), e);
        }

        return new JSONArray();
    }

    /**
     * 拆分参考内容（按文章段落拆分）
     * @param refrenceContent 参考内容
     * @return 拆分后的参考内容
     */
    private JSONArray splitReferenceContent(String refrenceContent) throws RuntimeException {
        try {
            String[] articleContents = refrenceContent.split(TITLE_SPLIT_MARK);
            JSONArray jsonArray = new JSONArray();
            for (String articleContent : articleContents) {
                if (StringUtils.isBlank(articleContent)) continue;
                SpliteText spliteText = new SpliteText(articleContent, false, true, false, this.callLlm, this.useThink, this.isExchange);
                JSONArray articleContentArray = spliteText.mergeParagraph(this.MAX_INPUT_TOKEN);
                if (articleContentArray.isEmpty()) continue;
                jsonArray.addAll(articleContentArray);
            }
            return jsonArray;
        } catch (Exception e) {
            throw new RuntimeException("按文章段落拆分参考内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合并参考内容的最小语义块，每块不超过MAX_REFERENCE_LENGTH * 0.8
     * 
     * @param refrenceContent 参考内容
     * @param writingTemplate 写作模板
     * @param outputStream    输出流
     * @return 合并后的参考内容
     */
    private JSONArray mergeReference(String refrenceContent, JSONObject writingTemplate, OutputStream outputStream)
            throws RuntimeException, IllegalArgumentException {
        if (StringUtils.isBlank(refrenceContent))
            throw new IllegalArgumentException("参考内容不能为空");

        try {
            JSONArray refrenceContentArray = splitReferenceContent(refrenceContent);

            // logger.info("参考内容分块成功: {}", refrenceContentArray.toJSONString());

            // 参考内容拆分为最小主题语义片段
            JSONArray referenceCollection = new JSONArray();
            for (int i = 0; i < refrenceContentArray.size(); i++) {
                JSONArray fragmentArray = refrenceContentArray.getJSONArray(i);
                if (fragmentArray != null && !fragmentArray.isEmpty()) {
                    referenceCollection.addAll(breakdownReference(writingTemplate, fragmentArray, outputStream));
                }
            }

            if (referenceCollection.isEmpty()) {
                return new JSONArray();
            }

            // logger.info("拆分参考内容成功: {}", referenceCollection.toJSONString());

            // 将最小语义片段按大小合并，每块不超过MAX_REFERENCE_LENGTH * 0.8
            int refLength = 0;
            JSONArray refArray = new JSONArray();
            JSONArray mergedReferenceCollection = new JSONArray();
            int maxRefLength = (int) (this.MAX_INPUT_TOKEN * 0.8);
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
            throw new RuntimeException("合并参考内容语义段失败: " + e.getMessage(), e);
        }
    }

    /**
     * 参考内容去重和冲突处理
     * 
     * @param fragmentCollection 最小主题语义片段
     * @param writingTemplate    写作模板s
     * @param outputStream       输出流
     * @return 去重和冲突处理后的参考内容
     */
    private JSONArray finalizeReference(JSONArray fragmentCollection,
            JSONObject writingTemplate, OutputStream outputStream) throws RuntimeException {
        if (fragmentCollection == null || fragmentCollection.isEmpty()) {
            return new JSONArray();
        }

        try {
            JSONArray prompt = buildJsonPrompt(this.referenceFinalizedPrompt,
                    String.format("\n【章节框架】：\n%s\n", writingTemplate),
                    String.format("\n【最小主题语义片段】：\n%s\n", fragmentCollection));

            String result = invokeLlm(prompt, outputStream, true, true);

            return JSON.parseArray(result);
        } catch (Exception e) {
            if (e.getMessage().contains("大模型返回的结果为空")) {
                return new JSONArray();
            }
            throw new RuntimeException("参考内容语义去重和冲突处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 规划章节结构
     * 
     * @param articleTitle        文章标题
     * @param writingTemplate     写作模板
     * @param referenceCollection 参考内容
     * @param writingCause        内容要求
     * @param outputStream        输出流
     * @return 规划后的章节的单元结构，JSON字符串
     */
    private String planChapterStructure(String articleTitle, JSONObject writingTemplate, JSONArray referenceCollection,
            String writingCause, OutputStream outputStream) throws RuntimeException {
        if (referenceCollection == null || referenceCollection.isEmpty()) {
            return "";
        }

        try {
            JSONArray prompt = buildJsonPrompt(this.chapterStructurePlanPrompt,
                    String.format("\n【文章标题】：\n%s\n", articleTitle),
                    String.format("\n【章节框架】：\n%s\n", writingTemplate),
                    String.format("\n【参考内容】：\n%s\n", referenceCollection),
                    String.format("\n【内容要求】：\n%s\n", writingCause));

            String result = invokeLlm(prompt, outputStream, true, false);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("规划章节结构失败: " + e.getMessage(), e);
        }
    }

    /**
     * 章节写作
     * 
     * @param title            文章标题
     * @param writingTemplate  写作模板
     * @param chapterStructure 章节结构
     * @param refrence         参考内容
     * @param writingCause     内容要求
     * @param wordsLimit       字数限制
     * @param outputStream     输出流
     * @return 写作章节内容
     */
    private String writeChapter(String title, JSONObject writingTemplate, String chapterStructure,
            JSONArray referenceCollection, String writingCause, int wordsLimit, OutputStream outputStream) {
        String templateTitle = writingTemplate.getString("title");
        if ("引言".equals(templateTitle)) {
            writingCause = String.format("%s\n%s", "引言要求：开篇点题，背景+论点，一个自然段，无二级和三级标题，≤250字。\n", writingCause);
            wordsLimit = -1;
        }

        String wordsLimitPrompt = "";
        if (wordsLimit > 0) {
            wordsLimitPrompt = String.format("\n【内容长度要求】：\n%d 字之间，但不能因为长度要求而出现冗余的内容，也不能破坏内容的完整性。\n", wordsLimit);
        }

        JSONArray prompt = buildJsonPrompt(this.chapterWritingPrompt,
                String.format("\n【文章标题】：\n%s\n", title),
                String.format("\n【章节框架】：\n%s\n", writingTemplate),
                String.format("\n【章节结构】：\n%s\n", chapterStructure),
                String.format("\n【参考内容】：\n%s\n", referenceCollection.isEmpty() ? "" : referenceCollection.toJSONString()),
                String.format("\n【内容要求】：\n%s\n", writingCause),
                wordsLimitPrompt);

        String content = invokeLlm(prompt, outputStream, false, false);

        logger.info("协同写作：章节《{}》写作内容：{}", title, content);

        return content;
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
            SpliteText spliteText = new SpliteText(exemplaryArticle, true, false, false, this.callLlm, this.useThink, this.isExchange);
            JSONArray arrayJson = new JSONArray();
            if (spliteText.checkIsHeading()) {
                arrayJson = spliteText.splitTextByChapter(MAX_INPUT_TOKEN);
            }
            if (!spliteText.checkIsHeading() || arrayJson.isEmpty()) {
                arrayJson = spliteText.splitTextByParagraph(MAX_INPUT_TOKEN);
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
                    safeWriteToStream(outputStream, String.format("\n【 分析范文章节《%s》 ... 】\n", chapterTitle), true);
                    JSONObject writingTemplateJson = buildWriterTemplateByChapter(exemplaryChapterJson, nullOutputStream);

                    String subtitle = writingTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("范文章节的写作模板title为空");
                    }
                    if (subtitle.matches("^引言$")) {
                        isPreface = true;
                    }

                    logger.info("根据范文生成章节《{}》模板: {}", subtitle, writingTemplateJson.toJSONString());

                    // 拆分参考内容为最小语义块
                    safeWriteToStream(outputStream, String.format("\n【 分析范文章节《%s》的参考内容 ... 】\n", subtitle), true);
                    JSONArray referenceCollection = mergeReference(refrenceContent, writingTemplateJson, nullOutputStream);

                    String chapterStructure = "";
                    JSONArray filteredReferenceCollection = new JSONArray();
                    if (referenceCollection != null && ! referenceCollection.isEmpty()) {
                        // 筛选参考内容
                        safeWriteToStream(outputStream, String.format("\n【 筛选章节《%s》的参考内容 ... 】\n", subtitle), true);
                        for (int j = 0; j < referenceCollection.size(); j++) {
                            filteredReferenceCollection.addAll(finalizeReference(referenceCollection.getJSONArray(j), writingTemplateJson, nullOutputStream));
                        }

                        logger.debug("筛选章节《{}》的参考内容：{}", subtitle, filteredReferenceCollection.toJSONString());

                        // 规划章节结构
                        safeWriteToStream(outputStream, String.format("\n【 规划章节《%s》的结构 ... 】\n", subtitle), true);
                        chapterStructure = planChapterStructure(articleTitle, writingTemplateJson,
                                filteredReferenceCollection, writingCause, nullOutputStream);

                        logger.debug("章节结构：{}", chapterStructure);
                    }

                    logger.info("处理参考内容");

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

                    String chapterContent = writeChapter(articleTitle, writingTemplateJson, chapterStructure,
                            filteredReferenceCollection,
                            writingCause, articleLength, outputStream);
                    contentBuffer.append(chapterContent).append("\n");

                    logger.info("生成第{}个章节《{}》成功", i, subtitle);
                } catch (Exception e) {
                    safeWriteToStream(outputStream, String.format(">>生成第 {} 个章节失败，跳过该章节，由于 %s<<", i, e.getMessage()), true);
                    logger.error("生成第{}个章节失败，跳过该章节", i, e);
                    continue;
                }
            }

            return contentBuffer.toString();
        } catch (Exception e) {
            safeWriteToStream(outputStream, String.format(">>生成文章失败，由于 %s<<", e.getMessage()), true);
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

            logger.info("根据文章标题和大纲，生成章节框架：{}", outlineCollection.toJSONString());

            // 生成章节
            boolean isPreface = false;
            for (int i = 0; i < outlineCollection.size(); i++) {
                try {
                    JSONObject chapterTemplateJson = outlineCollection.getJSONObject(i);

                    String subtitle = chapterTemplateJson.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        throw new IllegalArgumentException("文章大纲和章节框架title为空");
                    }
                    if (subtitle.matches("^引言$")) {
                        isPreface = true;
                    }

                    // 拆分参考内容为最小语义块
                    safeWriteToStream(outputStream, String.format("\n【 分析章节《%s》的参考内容 ... 】\n", subtitle), true);
                    JSONArray referenceCollection = mergeReference(refrenceContent, chapterTemplateJson, nullOutputStream);

                    String chapterStructure = "";
                    JSONArray filteredReferenceCollection = new JSONArray();
                    if (referenceCollection != null && ! referenceCollection.isEmpty()) {
                        // 参考内容去重和冲突处理
                        safeWriteToStream(outputStream, String.format("\n【 筛选章节《%s》的参考内容 ... 】\n", subtitle), true);
                        for (int j = 0; j < referenceCollection.size(); j++) {
                            filteredReferenceCollection.addAll(finalizeReference(referenceCollection.getJSONArray(j), chapterTemplateJson, nullOutputStream));
                        }

                        // logger.info("筛选章节《{}》的参考内容：{}", subtitle, filteredReferenceCollection.toJSONString());

                        // 规划章节结构
                        safeWriteToStream(outputStream, String.format("\n【 规划章节《%s》的结构 ... 】\n", subtitle), true);
                        chapterStructure = planChapterStructure(articleTitle, chapterTemplateJson,
                                filteredReferenceCollection, writingCause, nullOutputStream);

                        logger.info("规划章节《{}》的结构：{}", subtitle, chapterStructure);
                    }

                    logger.info("处理参考内容");

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("\n【 生成章节《%s》 ... 】\n", subtitle), true);
                    if (!subtitle.matches("^(引言|段落\\s*)$")) {
                        if (!subtitle.matches("^[第]?[一二三四五六七八九十零〇]+[\\.\\s、章篇部分节]{1}.*$")) {
                            subtitle = (isPreface ? numberToChinese(i) : numberToChinese(i + 1)) + "、" + subtitle;
                        }
                        contentBuffer.append(subtitle).append("\n");
                        safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    }

                    String chapterContent = writeChapter(articleTitle, chapterTemplateJson, chapterStructure,
                            filteredReferenceCollection,
                            writingCause, articleLength, outputStream);
                    contentBuffer.append(chapterContent).append("\n");

                    logger.info("生成第{}个章节《{}》成功", i, subtitle);
                } catch (Exception e) {
                    safeWriteToStream(outputStream, String.format(">>生成第 %d 个章节失败，跳过该章节，由于 %s<<", i, e.getMessage()), true);
                    logger.error("生成第{}个章节失败，跳过该章节", i, e);
                    continue;
                }
            }

            return contentBuffer.toString();
        } catch (Exception e) {
            safeWriteToStream(outputStream, String.format(">>生成文章失败，由于 %s<<", e.getMessage()), true);
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
        StringBuffer refrenceContent = new StringBuffer();
        if (chatParams.getReferences() != null) {
            for (String reference : chatParams.getReferences()) {
                if (StringUtils.isNotBlank(reference)) {
                    refrenceContent.append(reference.trim()).append(TITLE_SPLIT_MARK);
                }
            }
        }
        if (StringUtils.isNotBlank(chatParams.getBriefReference())) {
            refrenceContent.append(chatParams.getBriefReference().trim()).append("\n");
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
                    exemplaryArticle, refrenceContent.toString(), outputStream);
        } else {
            content = writeArticleByOutline(articleTitle, articleType, writingCause, articleLength,
                    outline, refrenceContent.toString(), outputStream);
        }

        return content;
    }

    // 本地测试用
    /*
    public static void main(String[] args){
        CollaborationWriter collaborationWriter = new CollaborationWriter(new XCallLlm(), false, false);
        
        ChatParams chatParams = new ChatParams();
        chatParams.setTitle("OA 产品研发部月度工作总结");
        chatParams.setGwwz("工作总结");
        chatParams.setCause("");
        chatParams.setArticleLength(1000);
        chatParams.setOutline("一、本月工作总结\n二、重点工作进度\n三、存在问题及改进措施\n四、下月工作计划");
        chatParams.setImitative("");
        
        System.out.println("=======================生成文章====================");
        try {
            
            String refer2 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\2.txt"), "UTF-8");
            String refer1 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\1.txt"), "UTF-8");
            String refer3 = FileUtils.readFileToString(new File("C:\\Users\\xman\\Desktop\\3.txt"), "UTF-8");

            // chatParams.setImitative(refer1);

            chatParams.setReferences(new ArrayList<>(Arrays.asList(refer1, refer2, refer3)));

            String content = collaborationWriter.writeArticle(chatParams,nullOutputStream);
            System.out.println(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
}
