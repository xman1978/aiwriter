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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private static final Pattern THINK_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
    private static final Pattern GW_PATTERN = Pattern.compile("^(?:通知|报告|请示|批复|意见|决定|决议|议案|函|通告|通报|纪要|公告|公报|令)$");

    // 模型参数
    private final int MAX_REFERENCE_LENGTH;
    private final int THINKING_BUDGET;
    private final int DEFAULT_MAX_TOKEN;
    private final boolean THINKING_ENABLE;

    private final String modelType;
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

        this.modelType = this.useThink ? PromptConfig.getPromptOrDefault("thinkModel", "") : PromptConfig.getPromptOrDefault("baseModel", "");

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

        // 构建系统消息，去除 system 提示词，避免模型兼容性问题
        /*
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", promptContent);
        prompt.add(system);
        */

        // 构建用户消息
        JSONObject user = new JSONObject();
        user.put("role", "user");
        StringBuilder userContent = new StringBuilder();
        userContent.append(promptContent);
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
     * 修复 JSON 字符串
     * @param jsonString JSON 字符串
     * @param endChar 结尾字符
     * @return 修复后的 JSON 字符串
     */
    private String fixJsonString(String jsonString, String endChar) {
        // 去除 <think> ... </think> 部分，如果有的话
        Matcher matcher = THINK_PATTERN.matcher(jsonString);
        jsonString = matcher.replaceAll("");

        // 清理 json 字符串中不合法的符号
        jsonString = jsonString.replaceAll("```json", "").replaceAll("```", "")
                .replaceAll("：", ":").replaceAll("，", ",")
                .replaceAll("“", "\"").replaceAll("”", "\"")
                .replaceAll("^[\\s\\u3000]+", "").replaceAll("[\\s\\u3000]+$", "")
                .replaceAll("\\r?\\n$", "");

        // 如果写作模板不以 } 结尾，则添加 }
        if (!jsonString.endsWith(endChar)) {
            jsonString += endChar;
        }

        return jsonString;
    }

        /*
     * 安全写入输出流
     * @param outputStream 输出流
     * @param content 要写入的内容
     */
    private void safeWriteToStream(OutputStream outputStream, String content, boolean isThink) {
        if (outputStream == null || StringUtils.isBlank(content)) {
            logger.warn("输出流为空或内容为空，跳过写入");
            return;
        }
        
        try {
            // 等待 IO 缓冲区刷新
            Thread.sleep(200); 
            // 分块写入输出流，避免写入过长的内容导致输出流阻塞（慢速网络情况下）
            for(int i = 0; i < content.length() / 3 + 1; i++){
                String chunk = content.substring(i * 3, Math.min((i * 3 + 3), content.length()));
                if(isThink){
                    outputStream.write(("<think>" + chunk + "</think>").getBytes(StandardCharsets.UTF_8));
                }else{
                    outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                }
                outputStream.flush();
            }
            // 等待 IO 缓冲区刷新
            Thread.sleep(200); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new RuntimeException("写入输出流失败: " + e.getMessage(), e);
        }
    }

    private String invokeLlm(JSONArray prompt, OutputStream outputStream, boolean onlyThinking, boolean jsonObject) throws RuntimeException {
        JSONObject extParams = new JSONObject();
        extParams.put("model_type", this.modelType);
        if (jsonObject) {
            extParams.put("response_format", "json_object");
        }
        extParams.put("only_thinking", onlyThinking);
        if (this.THINKING_BUDGET > 0) {
            extParams.put("thinking_budget", this.THINKING_BUDGET);
        }
        extParams.put("enable_thinking", this.THINKING_ENABLE);

        String result = this.callLlm.callOpenAiInterface(this.useThink, maxToken, isExchange, prompt, extParams, outputStream);
        if (StringUtils.isBlank(result)) {
            throw new RuntimeException("大模型返回的结果为空");
        }

        return result;
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
        
        JSONArray prompt = buildJsonPrompt(this.writerTemplatePrompt, 
                        String.format("\n【范文】：\n%s\n\n", exemplaryArticle));

        try {
            String template = invokeLlm(prompt, outputStream, true, true);

            template = fixJsonString(template, "}");

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
                        String.format("\n【写作模板】：\n%s\n", writingTemplate), 
                        String.format("\n【片段集合】：\n%s\n", fragmentCollection.toJSONString()));
        
        try {
            String filter = invokeLlm(prompt, outputStream, true, true);

            filter = fixJsonString(filter, "]");

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
            safeWriteToStream(outputStream, "\n【参考内容为空，由大模型生成参考内容】\n", true);
            // return "";
        }
        
        JSONArray prompt = buildJsonPrompt(this.refrenceExtractPrompt, 
                        String.format("\n【文章标题】：\n%s\n", title), 
                        String.format("\n【写作模板】：\n%s\n", writingTemplate), 
                        String.format("\n【参考内容】：\n%s\n", refrenceContent));
        
        try {
            String refrence = invokeLlm(prompt, outputStream, true, true);

            refrence = fixJsonString(refrence, "}");
            
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
                logger.warn("参考内容分块失败，返回空数组");
            }
        }

        // 内容要求
        String writingCause = chatParams.getCause();

        try {
            // 获取格式要求和标题
            String articleTitle = chatParams.getTitle();
            String articleType = chatParams.getGwwz();
            int articleLength = chatParams.getArticleLength();

            String content = "";

            // 生成标题
            try {
                safeWriteToStream(outputStream, String.format("\n【 生成标题 ... 】\n"), true);
                if (StringUtils.isNotBlank(articleType) && GW_PATTERN.matcher(articleType).matches()) {
                    JSONArray titlePrompt = buildJsonPrompt(this.writerTitlePrompt, 
                                                            String.format("\n【文章标题】：\n%s\n", articleTitle), 
                                                            String.format("\n【文章类型】：\n%s\n", articleType));

                    articleTitle = invokeLlm(titlePrompt, outputStream, true, false);
                }   
                content += articleTitle;
                safeWriteToStream(outputStream, String.format("\n%s\n\n", articleTitle), false);
            } catch (Exception e) {
                logger.error("生成标题失败", e);
                content += articleTitle;
                safeWriteToStream(outputStream, String.format("\n%s\n\n", articleTitle), false);
            }
            
            // 生成正文
            safeWriteToStream(outputStream, "\n【 依据范文，创建文章写作大纲 ... 】\n", true);
            JSONObject writingTemplateJson = buildWriterTemplate(exemplaryArticle, outputStream);
            JSONArray outline = writingTemplateJson.getJSONArray("outline");
            
            if (outline == null || outline.isEmpty()) {
                throw new RuntimeException("写作模板中没有找到outline");
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
                        safeWriteToStream(outputStream, String.format("\n【 初筛章节<<%s>>的参考内容 ... 】\n", subtitle), true);
                        JSONArray refrenceContentJson = refrenceContentArray.getJSONArray(j);
                        refrenceText += firstFilterRefrenceContent(refrenceContentJson, subwritingTemplate.toJSONString(), outputStream);
                    }
                    
                    // 萃取参考内容，供大模型写作使用
                    safeWriteToStream(outputStream, String.format("\n【 萃取章节<<%s>>的参考内容 ... 】\n", subtitle), true);
                    String refrence = extractRefrenceContent(articleTitle,refrenceText, subwritingTemplate.toJSONString(), outputStream);

                    // 章节写作
                    safeWriteToStream(outputStream, String.format("\n【依据参考内容】：\n%s\n【章节<<%s>>写作中 ... 】\n", refrence, subtitle), true);                                   
                    safeWriteToStream(outputStream, String.format("\n%s\n", subtitle), false);
                    String wordsLimit = "";
                    if (articleLength > 0) {
                        wordsLimit = String.format("\n要求内容长度：\n%d 字之间，不能太多，不能太少，一点要按照字数要求写作。\n", articleLength);
                    }
                    JSONArray writePrompt = buildJsonPrompt(writerPrompt, 
                                                            String.format("\n【文章标题】：\n%s\n", articleTitle), 
                                                            String.format("\n【写作模板】：\n%s\n", subwritingTemplate.toJSONString()), 
                                                            String.format("\n【参考内容】：\n%s\n", refrence),
                                                            String.format("\n【内容要求】：\n%s\n", writingCause),
                                                            wordsLimit);

                    content += invokeLlm(writePrompt, outputStream, false, false);
                } catch (Exception e) {
                    logger.error("生成第{}个章节失败，跳过该章节", i, e);
                    continue;
                }
            }

            // 生成其他要素
            if (StringUtils.isNotBlank(articleType) && GW_PATTERN.matcher(articleType).matches()) {
                try {
                    safeWriteToStream(outputStream, "\n【生成正文后要素 ... 】\n", true);
                    JSONArray otherPrompt = buildJsonPrompt(this.writerOtherPrompt, 
                                                            String.format("\n如正文之后要包含时间要素，请根据当前时间 %s 生成时间要素。\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))),
                                                            String.format("\n【文章标题】：\n%s\n", articleTitle),
                                                            String.format("\n【文章类型】：\n%s\n", articleType));

                    String other = invokeLlm(otherPrompt, outputStream, true, false);
                    if (StringUtils.isNotBlank(other)) {
                        safeWriteToStream(outputStream, String.format("\n%s\n", other), false);
                        content += other;
                    }
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
            Path path = Paths.get("C:\\Users\\xman\\Desktop\\test.txt");
            String text = Files.readAllLines(path, Charset.forName("UTF-8")).stream().collect(Collectors.joining("\n"));

            AIWriter aiWriter = new AIWriter(new CallLlm(), false, 8192, false);

            System.out.println("=======================生成写作模板====================");
            JSONObject writerTemplate = aiWriter.buildWriterTemplate(text, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            });
            System.out.println(writerTemplate.toJSONString());

            String subwritingTemplate = writerTemplate.getJSONArray("outline").getJSONObject(0).toJSONString();

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
            String refrence = aiWriter.extractRefrenceContent("全面提升干部能力素质专题党课讲稿", firstFilterRefrenceContent, subwritingTemplate, new OutputStream() {
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
