package com.thunisoft.llm.writeragent.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSON;

import java.io.OutputStream;
import java.io.IOException;

import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.service.impl.CallLlm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixJson {
    private static final Logger logger = LoggerFactory.getLogger(FixJson.class);

    // 线程局部的StringBuilder，减少内存分配
    private final ThreadLocal<StringBuilder> STRING_BUILDER_HOLDER = 
        ThreadLocal.withInitial(() -> new StringBuilder(4096));

    private static final Pattern THINK_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
    private static final Pattern LEADING_WHITESPACE_PATTERN = Pattern.compile("^[\\s\\u3000\\r\\n]+");
    private static final Pattern TRAILING_WHITESPACE_PATTERN = Pattern.compile("[\\s\\u3000\\r\\n]+$");
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private static final String FIX_JSON_PROMPT = "你是一个JSON格式修复专家。任务：根据 fastjson 的报错信息，修复JSON字符串。\n输出要求：1. 仅输出修复后的 JSON；2. 不能输出任何解释说明。";
    
    private ICallLlm callLlm;

    public FixJson(ICallLlm callLlm) {
        this.callLlm = callLlm;
    }

    /*
     * 去除 <think> ... </think> 部分，如果有的话
     * @param jsonString JSON 字符串
     * @return 去除 <think> ... </think> 部分后的 JSON 字符串
     */
    public String removeThink(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }
        
        jsonString = THINK_PATTERN.matcher(jsonString).replaceAll("");

        return jsonString;
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

        StringBuilder sb = STRING_BUILDER_HOLDER.get();
        sb.setLength(0); // 重用StringBuilder
        sb.append(jsonString);
        
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

    public String fixJson(String jsonString, boolean useThink, int maxToken, boolean isExchange, JSONObject extParams, OutputStream outputStream) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }

        jsonString = removeThink(jsonString);
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
            system.put("content", FIX_JSON_PROMPT);
            prompt.add(system);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", String.format("报错信息：\n%s\n原始JSON字符串：\n%s", e.getMessage(), jsonString));
            prompt.add(user);

            extParams.put("response_format", "json_object");
            extParams.put("only_thinking", true);
            String fixedJsonString = this.callLlm.callOpenAiInterface(useThink, maxToken, isExchange, prompt, extParams, outputStream);
            fixedJsonString = removeThink(fixedJsonString);
            
            return fixedJsonString.trim();
        } finally {
            STRING_BUILDER_HOLDER.remove();
        }
    }

    /*
     * 本地测试用
    public static void main(String[] args) {
        FixJson fixJson = new FixJson(new CallLlm());
        String jsonString = "```json\n{\"name\":[\"张三\",\"李四\"],\"age\":18}\n```";
        String fixedJsonString = fixJson.fixJson(jsonString, true, 8192, false, new JSONObject(), new OutputStream() {
            @Override
            public void write(int b) throws IOException {
            }
        });
        System.out.println(fixedJsonString);
    }
    */
}
