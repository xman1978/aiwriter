package com.thunisoft.llm.writeragent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.thunisoft.llm.writeragent.utils.PromptConfig;
import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.writeragent.utils.FixJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(AIWriterBase.class);

    // 模型参数
    protected final int MAX_REFERENCE_LENGTH;
    protected final int THINKING_BUDGET;
    protected final int DEFAULT_MAX_TOKEN;
    protected final boolean THINKING_ENABLE;

    protected final ICallLlm callLlm;
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
     * @param callLlm LLM调用服务，不能为空
     * @param useThink 是否使用思考模式
     * @param maxToken 最大token数，必须大于0
     * @param isExchange 是否交换模式
     * @throws IllegalArgumentException 如果参数无效
     */
    public AIWriterBase(ICallLlm callLlm, boolean useThink, int maxToken, boolean isExchange) {
        if (callLlm == null) {
            throw new IllegalArgumentException("ICallLlm不能为空");
        }
        
        PromptConfig.setPromptFileName("config.yml");
        this.THINKING_BUDGET = Integer.parseInt(PromptConfig.getPromptOrDefault("thinkBudget", "256"));
        this.DEFAULT_MAX_TOKEN = Integer.parseInt(PromptConfig.getPromptOrDefault("maxToken", "8192"));
        this.THINKING_ENABLE = Boolean.parseBoolean(PromptConfig.getPromptOrDefault("thinkEnable", "true"));

        this.MAX_REFERENCE_LENGTH = (int) (this.DEFAULT_MAX_TOKEN / 2);
        
        this.callLlm = callLlm;
        this.useThink = useThink;
        this.maxToken = maxToken > 0 ? maxToken : this.DEFAULT_MAX_TOKEN;
        this.isExchange = isExchange;
        
        this.modelType = this.useThink ? PromptConfig.getPromptOrDefault("thinkModel", "") : PromptConfig.getPromptOrDefault("baseModel", "");

        this.waitOutputTime = Integer.parseInt(PromptConfig.getPromptOrDefault("waitOutputTime", "200"));

        this.extParams = new JSONObject();
        this.extParams.put("model_type", this.modelType);
        if (this.THINKING_BUDGET > 0) {
            this.extParams.put("thinking_budget", this.THINKING_BUDGET);
        }
        this.extParams.put("enable_thinking", this.THINKING_ENABLE);
    }

    /**
     * 生成 JSON 格式提示词
     * @param promptContent 系统提示词内容
     * @param content 用户输入内容
     * @return JSON 格式提示词
     */
    protected JSONArray buildJsonPrompt(String promptContent, String... content) {
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
    protected String invokeLlm(JSONArray prompt, OutputStream outputStream, boolean onlyThinking, boolean jsonObject) throws RuntimeException {
        if (jsonObject) {
            this.extParams.put("response_format", "json_object");
        }
        this.extParams.put("only_thinking", onlyThinking);
        String result = this.callLlm.callOpenAiInterface(this.useThink, this.maxToken, this.isExchange, prompt, this.extParams, outputStream);
        if (StringUtils.isBlank(result)) {
            throw new RuntimeException("大模型返回的结果为空");
        }
        /*
        if (prompt.toString().contains("信息筛选助手")) {
            logger.info("大模型返回的结果: {}", result);
        }
        */
        // 去除<think> ... </think> 部分，修复JSON格式
        if (jsonObject) {
            result = new FixJson(this.callLlm).fixJson(result, this.useThink, this.maxToken, this.isExchange, this.extParams, outputStream);
        } else {
            result = new FixJson(this.callLlm).removeThink(result);
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
        } catch (IOException e) {
            throw new RuntimeException("写入输出流失败: " + e.getMessage(), e);
        }
    }
}
