package com.thunisoft.llm.templatewriter;

import java.io.OutputStream;
import java.io.IOException;
import java.lang.String;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.Thread;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.domain.ChatParams;
import com.thunisoft.llm.service.impl.CallLlm;
import com.thunisoft.llm.templatewriter.utils.SpliteText;
import com.thunisoft.llm.templatewriter.utils.PromptConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

/**
 * AI写作器，用于根据范文和参考内容生成文章
 */
public class AIWriter {
    private static final Logger logger = LoggerFactory.getLogger(AIWriter.class);
    
    // 常量定义
    private static final String THINK_PATTERN = "<think>(.*?)</think>";

    // 模型参数
    private final int MAX_REFERENCE_LENGTH;
    private final int THINKING_BUDGET;
    private final int DEFAULT_MAX_TOKEN;
    private final boolean THINKING_ENABLE;

    private final String writerTemplatePrompt;
    private final String refrenceExtractPrompt;
    private final String writerPrompt;
    private final String writerTitlePrompt;
    private final String writerOtherPrompt;
    private final String firstFilterPrompt;

    private final boolean useThink;
    private final int maxToken;
    private final boolean isExchange;
    private final ICallLlm callLlm;
    
    /**
     * 构造函数
     * @param callLlm LLM调用服务，不能为空
     * @param useThink 是否使用思考模式
     * @param maxToken 最大token数，必须大于0
     * @param isExchange 是否交换模式
     * @throws IllegalArgumentException 如果参数无效
     */
    public AIWriter(ICallLlm callLlm, boolean useThink, int maxToken, boolean isExchange) {
        if (callLlm == null) {
            throw new IllegalArgumentException("ICallLlm不能为空");
        }
        
        this.callLlm = callLlm;
        this.useThink = useThink;
        this.MAX_REFERENCE_LENGTH = Integer.parseInt(PromptConfig.getPromptOrDefault("maxReferenceLength", "4096"));
        this.THINKING_BUDGET = Integer.parseInt(PromptConfig.getPromptOrDefault("thinkBudget", "256"));
        this.DEFAULT_MAX_TOKEN = Integer.parseInt(PromptConfig.getPromptOrDefault("maxToken", "8192"));
        this.THINKING_ENABLE = Boolean.parseBoolean(PromptConfig.getPromptOrDefault("thinkEnable", "true"));
        this.isExchange = isExchange;
        this.maxToken = maxToken > 0 ? maxToken : this.DEFAULT_MAX_TOKEN;

        this.writerTemplatePrompt = PromptConfig.getPrompt("writerTemplatePrompt");
        this.refrenceExtractPrompt = PromptConfig.getPrompt("refrenceExtractPrompt");
        this.writerPrompt = PromptConfig.getPrompt("writerPrompt");
        this.writerTitlePrompt = PromptConfig.getPrompt("writerTitlePrompt");
        this.writerOtherPrompt = PromptConfig.getPrompt("writerOtherPrompt");
        this.firstFilterPrompt = PromptConfig.getPrompt("firstFilterPrompt");
    }

    

    /*
     * 生成 JSON 格式提示词
     * @param promptContent 系统提示词内容
     * @param content 用户输入内容
     * @return JSON 格式提示词
     */
    private JSONArray buildJsonPrompt(String promptContent, String... content) {
        if (StringUtils.isBlank(promptContent)) {
            throw new IllegalArgumentException("系统提示词不能为空");
        }
        
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("用户内容不能为空");
        }
        
        JSONArray prompt = new JSONArray();

        // 构建系统消息
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", promptContent);
        prompt.add(system);

        // 构建用户消息
        JSONObject user = new JSONObject();
        user.put("role", "user");
        StringBuilder userContent = new StringBuilder();
        for (String item : content) {
            if (item != null) {
                userContent.append(item);
            }
        }
        
        if (userContent.length() == 0) {
            throw new IllegalArgumentException("用户内容不能为空");
        }
        
        user.put("content", userContent.toString());
        prompt.add(user);

        return prompt;
    }

    /*
     * 生成写作模板
     * @param exemplaryArticle 范文
     * @return 写作模板
     */
    private JSONObject buildWriterTemplate(String exemplaryArticle, OutputStream outputStream) throws RuntimeException {
        if (StringUtils.isBlank(exemplaryArticle)) {
            throw new IllegalArgumentException("范文内容不能为空");
        }
        
        JSONArray prompt = buildJsonPrompt(this.writerTemplatePrompt, "范文：\n" + exemplaryArticle + "\n\n");

        try {
            // 模型返回json格式
            JSONObject extParams = new JSONObject();
            extParams.put("response_format", "json_object");
            extParams.put("only_thinking", true);
            if (this.THINKING_BUDGET > 0) {
                extParams.put("thinking_budget", this.THINKING_BUDGET);
            }
            extParams.put("enable_thinking", this.THINKING_ENABLE);
            String template = this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, prompt, extParams, outputStream);

            if (StringUtils.isBlank(template)) {
                throw new RuntimeException("大模型返回的写作模板为空");
            }

            // 去除 <think> ... </think> 部分，如果有的话
            Pattern pattern = Pattern.compile(THINK_PATTERN, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(template);
            template = matcher.replaceAll("").trim();
            // 清理 json 字符串中不合法的符号
            template = template.replaceAll("```json", "").replaceAll("```", "")
                    .replaceAll("：", ":").replaceAll("，", ",");
            // 如果写作模板不以 } 结尾，则添加 }
            if (!template.endsWith("}")) {
                template += "}";
            }

            // 验证返回的模板是否为有效JSON
            try {
                JSONObject templateJson = JSON.parseObject(template);

                if (templateJson == null) {
                    throw new IllegalArgumentException("写作模板JSON解析失败");
                }
                
                if (!templateJson.containsKey("outline")) {
                    throw new IllegalArgumentException("写作模板中缺少outline字段");
                }

                logger.debug("生成写作模板成功: {}", template);

                return templateJson;
            } catch (Exception e) {
                logger.error("大模型返回的写作模板不是有效JSON，返回原始内容: {} \n", template);
                throw new RuntimeException("大模型返回的写作模板格式错误", e);
            }
        } catch (Exception e) {
            throw new RuntimeException("生成写作模板失败: " + e.getMessage(), e);
        }
    }

    /*
     * 快速从参考内容中筛选出符合要求的片段
     * @param fragmentCollection 片段集合
     * @param writingTemplate 写作模板
     * @return 筛选出的参考内容
     */
    private String firstFilterRefrenceContent(JSONArray fragmentCollection, String writingTemplate, OutputStream outputStream) throws RuntimeException {
        if (StringUtils.isBlank(writingTemplate)) {
            throw new IllegalArgumentException("写作模板不能为空");
        }
        
        if (fragmentCollection == null || fragmentCollection.isEmpty()) {
            logger.warn("片段集合为空，返回空字符串");
            return "";
        }

        JSONArray prompt = buildJsonPrompt(this.firstFilterPrompt, 
            "写作模板：\n" + writingTemplate + "\n\n", 
            "片段集合：\n" + fragmentCollection.toJSONString() + "\n\n");
        
        try {
            JSONObject extParams = new JSONObject();
            extParams.put("response_format", "json_object");
            extParams.put("only_thinking", true);
            if (this.THINKING_BUDGET > 0) {
                extParams.put("thinking_budget", this.THINKING_BUDGET);
            }
            extParams.put("enable_thinking", this.THINKING_ENABLE);
            String filter = this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, prompt, extParams, outputStream);
            if (StringUtils.isBlank(filter)) {
                throw new RuntimeException("大模型返回的片段筛选为空");
            }

            // 去除 <think> ... </think> 部分，如果有的话
            Pattern pattern = Pattern.compile(THINK_PATTERN, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(filter);
            filter = matcher.replaceAll("").trim();
            // 清理 json 字符串中不合法的符号
            filter = filter.replaceAll("```json", "").replaceAll("```", "")
                    .replaceAll("：", ":").replaceAll("，", ",");
            // 如果写作模板不以 } 结尾，则添加 }
            if (!filter.endsWith("]")) {
                filter += "]";
            }

             try {
                JSONArray filterArray = JSON.parseArray(filter);
                if (filterArray == null || filterArray.isEmpty()) 
                    return "";

                StringBuilder filterText = new StringBuilder();
                for (int i = 0; i < filterArray.size(); i++) {
                    JSONObject filterFragmentJson = filterArray.getJSONObject(i);
                    int index = filterFragmentJson.getInteger("index");
                    for (int j = 0; j < fragmentCollection.size(); j++) {
                        JSONObject fragmentCollectionJson = fragmentCollection.getJSONObject(j);
                        if (fragmentCollectionJson.getInteger("index") == index) {
                            filterText.append(fragmentCollectionJson.getString("text")).append("\n");
                        }
                    }
                }

                logger.debug("筛选片段成功: {}", filterText.toString());

                return filterText.toString();
            } catch (Exception e) {
                logger.error("大模型返回的片段筛选不是有效JSON，返回原始内容: {} \n", filter, e);
            }
            
        } catch (Exception e) {
            logger.error("筛选片段失败: {} \n", e.getMessage(), e);
        }

        return "";
    }

    /*
     * 提取参考内容
     * @param title 文章标题
     * @param refrenceContent 参考内容
     * @param writingTemplate 写作模板
     * @return 参考内容提取
     */
    private String extractRefrenceContent(String title, String refrenceContent, String writingTemplate, OutputStream outputStream) throws RuntimeException {
        if (StringUtils.isBlank(refrenceContent)) {
            logger.warn("参考内容为空，返回大模型生成的参考内容");
            safeWriteToStream(outputStream, "<think>\n【参考内容为空，由大模型生成参考内容】\n</think>");
            // return "";
        }
        
        JSONArray prompt = buildJsonPrompt(this.refrenceExtractPrompt, 
            "文章标题：\n" + title + "\n\n", 
            "写作模板：\n" + writingTemplate + "\n\n", 
            "参考内容：\n" + refrenceContent + "\n\n");
        
        try {
            // 模型返回json格式
            JSONObject extParams = new JSONObject();
            extParams.put("only_thinking", true);
            if (this.THINKING_BUDGET > 0) {
                extParams.put("thinking_budget", this.THINKING_BUDGET);
            }
            extParams.put("enable_thinking", this.THINKING_ENABLE);
            String refrence = this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, prompt, extParams, outputStream);

            if (StringUtils.isBlank(refrence)) {
                logger.warn("大模型返回的参考内容为空");
                return "";
            }

            // 去除 <think> ... </think> 部分，如果有的话
            Pattern pattern = Pattern.compile(THINK_PATTERN, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(refrence);
            refrence = matcher.replaceAll("").trim();
            
            logger.debug("提取参考内容成功: {}", refrence);

            return refrence;
        } catch (Exception e) {
            logger.error("提取参考内容失败: " + e.getMessage(), e);
        }

        return "";
    }

    /*
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
        
        if (StringUtils.isBlank(chatParams.getTitle())) {
            throw new IllegalArgumentException("标题不能为空");
        }
        
        // 参考内容可以为空，但需要检查是否为null
        if (chatParams.getReferences() == null) {
            logger.warn("参考内容列表为null，将使用空列表");
        }
    }

    /*
     * 安全写入输出流
     * @param outputStream 输出流
     * @param content 要写入的内容
     */
    private void safeWriteToStream(OutputStream outputStream, String content) {
        if (outputStream == null || StringUtils.isBlank(content)) {
            logger.warn("输出流为空或内容为空，跳过写入");
            return;
        }
        
        try {
            Thread.sleep(200); // 等待 IO 缓冲区刷新
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new RuntimeException("写入输出流失败: " + e.getMessage(), e);
        }
    }

    /*
     * 生成文章
     * @param chatParams 参数配置
     * @param outputStream 输出流
     */
    public String writerArticle(ChatParams chatParams, OutputStream outputStream) throws RuntimeException {
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

        // 参考内容分块，每块不超过MAX_REFERENCE_LENGTH
        JSONArray refrenceContentArray = new JSONArray();
        if (StringUtils.isNotBlank(refrenceContent)) {
            SpliteText spliteText = new SpliteText(refrenceContent);
            refrenceContentArray = spliteText.mergeBatch(this.MAX_REFERENCE_LENGTH);
            if (refrenceContentArray.isEmpty()) {
                logger.error("参考内容分块失败，返回空数组");
            }
        }

        try {
            // 获取格式要求和标题
            String title = chatParams.getTitle();
            String type = chatParams.getGwwz();
            int articleLength = chatParams.getArticleLength();

            String content = "";

            // 生成标题
            try {
                safeWriteToStream(outputStream, String.format("<think>\n【 生成标题 ... 】\n</think>"));
                if (StringUtils.isNotBlank(type)) {
                    JSONArray titlePrompt = buildJsonPrompt(this.writerTitlePrompt, 
                                                            String.format("\n标题：\n%s\n", title), 
                                                            String.format("\n文章类型：\n%s\n", type));
                    JSONObject extParams = new JSONObject();
                    if (this.THINKING_BUDGET > 0) {
                        extParams.put("thinking_budget", this.THINKING_BUDGET);
                    }
                    content += this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, titlePrompt, extParams, outputStream);
                } else {
                    content += title;
                    safeWriteToStream(outputStream, String.format("\n%s\n\n", title));
                }
            } catch (Exception e) {
                logger.error("生成标题失败", e);
                content += title;
                safeWriteToStream(outputStream, String.format("\n%s\n\n", title));
            }
            
            // 生成正文
            // safeWriteToStream(outputStream, "<think>\n【 生成写作模板 ... 】\n</think>");
            JSONObject writingTemplateJson = buildWriterTemplate(exemplaryArticle, outputStream);
            JSONArray outline = writingTemplateJson.getJSONArray("outline");
            
            if (outline == null || outline.isEmpty()) {
                logger.warn("写作模板中没有找到outline，跳过正文生成");
                return content;
            }

            for (int i = 0; i < outline.size(); i++) {
                try {
                    JSONObject subwritingTemplate = outline.getJSONObject(i);
                    if (subwritingTemplate == null) {
                        logger.warn("第{}个outline元素为空，跳过", i);
                        continue;
                    }
                    
                    String subtitle = subwritingTemplate.getString("title");
                    if (StringUtils.isBlank(subtitle)) {
                        logger.warn("第{}个outline元素的title为空，跳过", i);
                        continue;
                    }

                    // 初筛参考内容，避免参考内容过多导致大模型无法处理
                    String refrenceText = "";
                    for (int j = 0; j < refrenceContentArray.size(); j++) {
                        safeWriteToStream(outputStream, String.format("<think>\n【 初筛章节<<%s>>的参考内容 ... 】\n</think>", subtitle));
                        JSONArray refrenceContentJson = refrenceContentArray.getJSONArray(j);
                        refrenceText += firstFilterRefrenceContent(refrenceContentJson, subwritingTemplate.toJSONString(), outputStream);
                    }
                    
                    // 萃取参考内容，供大模型写作使用
                    safeWriteToStream(outputStream, String.format("<think>\n【 萃取章节<<%s>>的参考内容 ... 】\n</think>", subtitle));
                    String refrence = extractRefrenceContent(title,refrenceText, subwritingTemplate.toJSONString(), outputStream);

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("<think>\n【依据参考内容】：\n%s\n【章节<<%s>>写作中 ... 】\n</think>", refrence, subtitle));                                   
                    safeWriteToStream(outputStream, String.format("\n%s\n", subtitle));
                    String wordsLimit = "";
                    if (articleLength > 0) {
                        wordsLimit = String.format("\n要求内容长度：\n%d 字之间，不能太多，不能太少，一点要按照字数要求写作。\n", articleLength);
                    }
                    JSONArray writePrompt = buildJsonPrompt(writerPrompt, 
                                                            String.format("\n文章标题：\n%s\n", title), 
                                                            String.format("\n写作模板：\n%s\n", subwritingTemplate.toJSONString()), 
                                                            String.format("\n参考内容：\n%s\n", refrence),
                                                            wordsLimit);
                    JSONObject extParams = new JSONObject();
                    if (this.THINKING_BUDGET > 0) {
                        extParams.put("thinking_budget", this.THINKING_BUDGET);
                    }
                    content += this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, writePrompt, extParams, outputStream);
                } catch (Exception e) {
                    logger.error("生成第{}个章节失败，跳过该章节", i, e);
                    continue;
                }
            }

            // 生成其他要素
            if (StringUtils.isNotBlank(type)) {
                try {
                    safeWriteToStream(outputStream, String.format("<think>\n【生成其他要素 ... 】\n</think>"));
                    JSONArray otherPrompt = buildJsonPrompt(this.writerOtherPrompt, 
                                                                String.format("\n文章类型：\n%s\n", type));
                    JSONObject extParams = new JSONObject();
                    if (this.THINKING_BUDGET > 0) {
                        extParams.put("thinking_budget", this.THINKING_BUDGET);
                    }
                    safeWriteToStream(outputStream, "\n\n");
                    content += this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, otherPrompt, extParams, outputStream);
                } catch (Exception e) {
                    logger.error("生成其他要素失败", e);
                }
            }

            return content;
        } catch (Exception e) {
            throw new RuntimeException("生成文章失败: " + e.getMessage(), e);
        }
    }

    // 本地测试用
    /*
    public static void main(String[] args) {

        try {
            Path path = Paths.get("C:\\Users\\xman\\Desktop\\test1.txt");
            String text = Files.readAllLines(path, Charset.forName("GBK")).stream().collect(Collectors.joining("\n"));

            AIWriter aiWriter = new AIWriter(new CallLlm(), true, 8192, false);

            System.out.println("=======================生成写作模板====================");
            JSONObject writerTemplate = aiWriter.buildWriterTemplate(text, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            });
            System.out.println(writerTemplate.toJSONString());

            String subwritingTemplate = writerTemplate.getJSONArray("outline").getJSONObject(2).toJSONString();

            System.out.println("=======================分块参考内容====================");
            SpliteText spliteText = new SpliteText(text);
            JSONArray refrenceBatchArray = spliteText.mergeBatch(4096);
            System.out.println(refrenceBatchArray);
            JSONArray refrenceContentArray = refrenceBatchArray.getJSONArray(0);

            System.out.println("=======================初筛参考内容====================");
            String firstFilterRefrenceContent = aiWriter.firstFilterRefrenceContent(refrenceContentArray, subwritingTemplate, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            });
            System.out.println(firstFilterRefrenceContent);
            

            System.out.println("=======================萃取参考内容====================");
            String refrence = aiWriter.extractRefrenceContent("加快推动“四链”深度融合催生农业新质生产力", firstFilterRefrenceContent, subwritingTemplate, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            });
            System.out.println(refrence);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
         */
}
