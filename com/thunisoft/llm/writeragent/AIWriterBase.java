package com.thunisoft.llm.writeragent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.thunisoft.llm.writeragent.utils.PromptConfig;
import com.thunisoft.llm.writeragent.utils.XCallLlm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(AIWriterBase.class);

    private final static String PROMPT_FILE_NAME = "config.yml";

    // 模型参数
    protected final int MAX_INPUT_TOKEN;
    protected final int THINKING_BUDGET;
    protected final boolean THINKING_ENABLE;

    // 修复JSON格式
    protected final String FIX_JSON_PROMPT;
    private static final Pattern THINK_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
    private static final Pattern LEADING_WHITESPACE_PATTERN = Pattern.compile("^[\\s\\u3000\\r\\n]+");
    private static final Pattern TRAILING_WHITESPACE_PATTERN = Pattern.compile("[\\s\\u3000\\r\\n]+$");
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    // 根据思考过程推理分析
    protected final String THINKING_RESULT_PROMPT;

    protected final XCallLlm callLlm;
    protected final boolean useThink;
    protected final int maxToken;
    protected final boolean isExchange;
    protected final String modelType;

    protected final JSONObject extParams;

    protected final int waitOutputTime;

    protected final static OutputStream nullOutputStream = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
        }
    };

    /**
     * 构造函数
     * @param callLlm CallLlm实例，不能为null
     * @param useThink 是否使用思考模式
     * @param isExchange 是否交换模式
     * @throws IllegalArgumentException 如果参数无效
     */
    public AIWriterBase(XCallLlm callLlm, boolean useThink, boolean isExchange) {
        if (callLlm == null) {
            throw new IllegalArgumentException("CallLlm实例不能为null");
        }

        this.THINKING_BUDGET = Integer.parseInt(PromptConfig.getPromptOrDefault("thinkBudget", "256", PROMPT_FILE_NAME));
        this.MAX_INPUT_TOKEN = Integer.parseInt(PromptConfig.getPromptOrDefault("maxInputToken", "4096", PROMPT_FILE_NAME));
        this.THINKING_ENABLE = Boolean.parseBoolean(PromptConfig.getPromptOrDefault("thinkEnable", "true", PROMPT_FILE_NAME));

        this.FIX_JSON_PROMPT = PromptConfig.getPrompt("fixJsonPrompt", PROMPT_FILE_NAME);
        this.THINKING_RESULT_PROMPT = PromptConfig.getPrompt("thinkingResultPrompt", PROMPT_FILE_NAME);

        this.callLlm = callLlm;
        this.useThink = useThink;
        // -1 表示输出不限制token数，由模型自行决定
        this.maxToken = Integer.parseInt(PromptConfig.getPromptOrDefault("maxOutputToken", "-1", PROMPT_FILE_NAME));; 
        this.isExchange = isExchange;
        
        this.modelType = this.useThink ? PromptConfig.getPromptOrDefault("thinkModel", "", PROMPT_FILE_NAME) : PromptConfig.getPromptOrDefault("baseModel", "", PROMPT_FILE_NAME);

        this.waitOutputTime = Integer.parseInt(PromptConfig.getPromptOrDefault("waitOutputTime", "200", PROMPT_FILE_NAME));

        this.extParams = new JSONObject();
        this.extParams.put("model_type", this.modelType);
        if (this.THINKING_BUDGET > 0) {
            this.extParams.put("thinking_budget", this.THINKING_BUDGET);
        }
        this.extParams.put("enable_thinking", this.THINKING_ENABLE);
    }

    /*
     * 去除 <think> ... </think> 部分，如果有的话
     * @param content 模型输出的内容
     * @return 去除 <think> ... </think> 部分后的内容
     */
    private String removeThink(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        
        return THINK_PATTERN.matcher(content).replaceAll("");
    }

    /*
     * 获取思考过程
     * @param content 模型输出的内容
     * @return 思考过程
     */
    private String getThink(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }

        StringBuffer thinkStep = new StringBuffer();
        Matcher matcher = THINK_PATTERN.matcher(content);
        if (matcher.find()) {
            thinkStep.append(matcher.group(1));
        }

        return thinkStep.toString().trim();
    }

    /*
     * 去除 ```json 和 ```，将中文冒号、逗号替换为英文冒号、逗号，去除前后空格和换行符，如果开头是 { 或 [，则添加 } 或 ]
     * @param jsonString JSON 字符串
     * @return 去除 ```json 和 ```，将中文冒号、逗号替换为英文冒号、逗号，去除前后空格和换行符，如果开头是 { 或 [，则添加 } 或 ]后的 JSON 字符串
     */
    private String removeBackticksAndReplaceColonAndComma(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }

        StringBuffer sb = new StringBuffer(jsonString);
        
        // 去除 ```json 和 ```
        int jsonIndex;
        while ((jsonIndex = sb.indexOf("```json")) != -1) {
            sb.delete(jsonIndex, jsonIndex + 7);
        }
        while ((jsonIndex = sb.indexOf("```")) != -1) {
            sb.delete(jsonIndex, jsonIndex + 3);
        }
        
        // 将中文冒号、逗号替换为英文冒号、逗号
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '：') {
                sb.setCharAt(i, ':');
            } else if (c == '，') {
                sb.setCharAt(i, ',');
            }
        }
        
        // 去除前后空格和换行符
        Matcher leadingMatcher = LEADING_WHITESPACE_PATTERN.matcher(sb);
        if (leadingMatcher.find()) {
            sb.delete(0, leadingMatcher.end());
        }
        Matcher trailingMatcher = TRAILING_WHITESPACE_PATTERN.matcher(sb);
        if (trailingMatcher.find()) {
            sb.delete(trailingMatcher.start(), sb.length());
        }
        // 去除非法字符
        Matcher illegalCharMatcher = ILLEGAL_CHAR_PATTERN.matcher(sb);
        if (illegalCharMatcher.find()) {
            sb.delete(illegalCharMatcher.start(), illegalCharMatcher.end());
        }

        if (sb.charAt(0) == '{' && sb.charAt(sb.length() - 1) != '}') {
            sb.append('}');
        }
        if (sb.charAt(0) == '[' && sb.charAt(sb.length() - 1) != ']') {
            sb.append(']');
        }

        return sb.toString();
    }

    private String fixJson(String jsonString, OutputStream outputStream) {
        if (StringUtils.isBlank(jsonString)) {
            return "{}";
        }

        jsonString = removeBackticksAndReplaceColonAndComma(jsonString);

        try {
            // 如果jsonString以 { 开头，以 } 结尾，则认为是JSONObject
            if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
                JSONObject jsonObject = JSON.parseObject(jsonString);
                jsonString = jsonObject.toJSONString();
            }
            // 如果jsonString以 [ 开头，以 ] 结尾，则认为是JSONArray
            if (jsonString.startsWith("[") && jsonString.endsWith("]")) {
                JSONArray jsonArray = JSON.parseArray(jsonString);
                jsonString = jsonArray.toJSONString();
            }

            return jsonString;
        } catch (Exception e) {
            logger.error("JSON 解析失败: {} \nJSON字符串: {}\n尝试修复 json 字符串 ... ...", e.getMessage(), jsonString);

            JSONArray prompt = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", this.FIX_JSON_PROMPT);
            prompt.add(system);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", String.format("【fastjson 报错信息】：\n%s\n【JSON 字符串】：\n%s", e.getMessage(), jsonString));
            prompt.add(user);

            extParams.put("response_format", "json_object");
            extParams.put("only_thinking", true);
            String fixedJsonString = this.callLlm.callOpenAiInterface(this.useThink, this.maxToken, this.isExchange, prompt, this.extParams, nullOutputStream);
            fixedJsonString = removeThink(fixedJsonString);

            logger.info("修复后的JSON字符串: {}", fixedJsonString);

            return fixedJsonString.trim();
        }
    }

    /**
     * 将数字转换为中文
     * 
     * @param number 1-99之间的数字
     * @return 1-99之间的数字对应的中文
     */
    protected String numberToChinese(int number) throws IllegalArgumentException {
        if (number < 1 || number > 99) {
            throw new IllegalArgumentException("数字必须在1-99之间");
        }
        String[] digit = { "零", "一", "二", "三", "四", "五", "六", "七", "八", "九" };
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
     * 生成 JSON 格式提示词
     * @param promptContent 系统提示词内容
     * @param content 用户输入内容
     * @return JSON 格式提示词
     */
    protected JSONArray buildJsonPrompt(String promptContent, String... content) 
        throws IllegalArgumentException {
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
        // userContent.append(promptContent);
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

    /**
     * 调用大模型
     * @param prompt 提示词
     * @param outputStream 输出流
     * @param onlyThinking 是否只输出思考过程
     * @param jsonObject 是否返回JSON对象
     * @return 大模型返回的结果
     */
    protected String invokeLlm(JSONArray prompt, OutputStream outputStream, boolean onlyThinking, boolean jsonObject) 
        throws RuntimeException {
        if (jsonObject) {
            this.extParams.put("response_format", "json_object");
        }
        this.extParams.put("only_thinking", onlyThinking);
        String result = this.callLlm.callOpenAiInterface(this.useThink, this.maxToken, this.isExchange, prompt, this.extParams, outputStream);
        if (StringUtils.isBlank(result) || result.contains("努力思考中！请稍后提问...")) {
            throw new RuntimeException("大模型返回的结果为空");
        }
        /*
        if (prompt.toString().contains("信息筛选助手")) {
            logger.info("大模型返回的结果: {}", result);
        }
        */
        // 去除<think> ... </think> 部分，修复JSON格式
        result = removeThink(result);
        
        // 如果返回结果显示由于长度超限导致被截断，则结合思考过程，重新调用大模型，生成完整结果
        if (result.contains(">>>finishReason:length<<<")) {
            String thinkStep = getThink(result);
            if (StringUtils.isNotBlank(thinkStep)) {
                logger.info("由于思考过程过长，导致结果被截断为空。结合思考过程，重新调用大模型生成推理分析结果。");
                String userPrompt = prompt.getJSONObject(1).getString("content");
                prompt.getJSONObject(0).put("content", this.THINKING_RESULT_PROMPT);
                prompt.getJSONObject(1).put("content", String.format("【思考过程】：\n%s\n【用户输入】：\n%s\n", thinkStep, userPrompt));
                result = this.callLlm.callOpenAiInterface(this.useThink, this.maxToken, this.isExchange, prompt, this.extParams, outputStream);
                result = removeThink(result);
            }
        }

        // 如果需要返回JSON对象，则修复JSON格式
        if (jsonObject) {
            result = fixJson(result, outputStream);
        }

        return result;
    }

    /**
     * 安全写入输出流
     * @param outputStream 输出流
     * @param content 要写入的内容
     * @param isThink 是否按思考过程输出
     */
    protected void safeWriteToStream(OutputStream outputStream, String content, boolean isThink) {
        if (outputStream == null || StringUtils.isBlank(content)) {
            logger.warn("输出流为空或内容为空，跳过写入");
            return;
        }
        
        try {
            // 等待 IO 缓冲区刷新
            Thread.sleep(this.waitOutputTime); 
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
            Thread.sleep(this.waitOutputTime); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException("写入输出流失败: " + e.getMessage(), e);
        }
    }
}
