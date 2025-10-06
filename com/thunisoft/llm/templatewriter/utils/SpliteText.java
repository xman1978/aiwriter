package com.thunisoft.llm.templatewriter.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文本分割处理类
 * 用于清洗和分割文本内容，去除标题、格式化文本
 */
public class SpliteText {
    private static final Logger logger = LoggerFactory.getLogger(SpliteText.class);

    // 常量定义
    private static final int MAX_TEXT_LENGTH = 1000000; // 最大文本长度限制
    private static final String BR_MARKER = "<<BR>>"; // 换行标记
    private static final String LINE_SEPARATOR = "\n"; // 行分隔符

    private String text;

    private static final Pattern END_WITH_PUNCTUATION = Pattern.compile(".*[。！？；：!?;:]$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^[（(]?[a-zA-Z0-9一二三四五六七八九十百千万]+[\\.)）、]?\\s*.+[^。？！；：\\.?!;:]$");
    
    private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("\\r?\\n");
    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("^[\\s\\u3000]+|[\\s\\u3000]+$");

    private static final Pattern PUNCTUATION_NEWLINE_PATTERN = Pattern.compile("([。！？!?])\\s*\\r?\\n");
    
    // 线程局部的StringBuilder，减少内存分配
    private static final ThreadLocal<StringBuilder> STRING_BUILDER_HOLDER = 
        ThreadLocal.withInitial(() -> new StringBuilder(1024));

    /**
     * 构造函数
     * @param text 要处理的文本，不能为null
     * @throws IllegalArgumentException 如果输入文本为null或过长
     */
    public SpliteText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为null");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            logger.warn("输入文本长度超过限制: {} > {}", text.length(), MAX_TEXT_LENGTH);
            throw new IllegalArgumentException("输入文本长度超过限制: " + MAX_TEXT_LENGTH);
        }
        this.text = text;
    }

    /**
     * 去除标题
     * @param text 输入文本
     * @return 去除标题后的文本
     */
    private String removeTitle(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        
        try {
            // 使用预编译的正则表达式减少内存分配
            String[] lines = LINE_SEPARATOR_PATTERN.split(text);
            StringBuilder sb = STRING_BUILDER_HOLDER.get();
            sb.setLength(0); // 重用StringBuilder
            sb.ensureCapacity(lines.length * 20); // 预分配容量

            boolean started = false; // 是否已经进入正文

            for (String line : lines) {
                if (line == null) {
                    continue; // 跳过null行
                }
                
                String trimmed = line.trim();

                if (!started) {
                    if (trimmed.isEmpty()) {
                        continue; // 跳过空行
                    }
                    // 如果这一行结尾没有标点，判定为标题 → 跳过
                    if (!END_WITH_PUNCTUATION.matcher(trimmed).matches()) {
                        continue;
                    }
                    // 否则遇到正文，开始保留
                    started = true;
                }

                sb.append(line).append(LINE_SEPARATOR);
            }

            String result = sb.toString().trim();
            return result.isEmpty() ? "" : result;
        } catch (Exception e) {
            logger.error("去除标题时发生异常", e);
            return text; // 发生异常时返回原文本
        }
    }

    /**
     * 去除一级、二级、三级标题
     * @param text 输入文本
     * @return 去除标题后的文本
     */
    private String removeHeading(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        
        try {
            StringBuilder sb = STRING_BUILDER_HOLDER.get();
            sb.setLength(0); // 重用StringBuilder
            String[] lines = LINE_SEPARATOR_PATTERN.split(text);
            sb.ensureCapacity(text.length()); // 预分配足够容量

            for (String line : lines) {
                if (line == null) continue;
        
                String cleanedLine = WHITE_SPACE_PATTERN.matcher(line).replaceAll("");
                
                if (StringUtils.isNotBlank(cleanedLine) && !HEADING_PATTERN.matcher(cleanedLine).matches()) {
                    sb.append(line).append(LINE_SEPARATOR);
                }
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? "" : result;
        } catch (Exception e) {
            logger.error("去除一级、二级、三级标题时发生异常", e);
            return text; // 发生异常时返回原文本
        }
    }


    /**
     * 去除句子中的换行和空格
     * @param text 输入文本
     * @return 格式化后的文本
     */
    private String removeNewlineAndSpace(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        
        try {
            // 临时标记：把标点后的换行替换为特殊符号
            String postString = PUNCTUATION_NEWLINE_PATTERN.matcher(text).replaceAll("$1" + BR_MARKER);

            // 其他换行替换为空格
            postString = LINE_SEPARATOR_PATTERN.matcher(postString).replaceAll(" ");

            // 多个空格压缩为一个
            postString = WHITE_SPACE_PATTERN.matcher(postString).replaceAll(" ").trim();

            return postString;
        } catch (Exception e) {
            logger.error("格式化文本时发生异常", e);
            return text; // 发生异常时返回原文本
        }
    }

    /**
     * 清洗文本
     * @param text 输入文本
     * @return 清洗后的文本
     */
    private String cleanText(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        
        try {
            return removeNewlineAndSpace(removeHeading(removeTitle(text)));
        } catch (Exception e) {
            logger.error("清洗文本时发生异常", e);
            return text; // 发生异常时返回原文本
        }
    }

    /**
     * 按段落分割文本
     * @return 包含文本段落的JSON数组
     */
    public JSONArray splitText() {
        JSONArray jsonArray = new JSONArray();

        try {           
            this.text = cleanText(this.text);
            
            if (StringUtils.isBlank(this.text)) {
                logger.warn("清洗后文本为空，返回空数组");
                return jsonArray;
            }

            String[] lines = this.text.split(BR_MARKER);
            for (int index = 0; index < lines.length; index++) {
                String line = WHITE_SPACE_PATTERN.matcher(lines[index]).replaceAll("");
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("index", index);
                jsonObject.put("text", line);
                jsonObject.put("length", line.length());
                jsonArray.add(jsonObject);
            }

            logger.debug("成功分割文本，共{}个段落", jsonArray.size());
        } catch (Exception e) {
            logger.error("分割文本失败: " + e.getMessage(), e);
        }

        return jsonArray;
    }

    /**
     * 合并文本
     * @param maxLength 最大长度
     * @return 合并后的文本
     */
    public JSONArray mergeBatch(int maxLength) {
        JSONArray resultArray = new JSONArray();

        try {
            JSONArray jsonArray = splitText();
            
            List<JSONObject> currentBatch = new ArrayList<>();
            int currentLength = 0;
            
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                int objectLength = jsonObject.getIntValue("length");
                
                // 如果添加当前对象会超过长度限制
                if (currentLength + objectLength > maxLength && !currentBatch.isEmpty()) {
                    // 保存当前批次
                    JSONArray batchArray = new JSONArray();
                    batchArray.addAll(currentBatch);
                    resultArray.add(batchArray);
                    
                    // 开始新批次
                    currentBatch.clear();
                    currentLength = 0;
                }
                
                currentBatch.add(jsonObject);
                currentLength += objectLength;
            }
            
            // 保存最后一批（如果有的话）
            if (!currentBatch.isEmpty()) {
                JSONArray batchArray = new JSONArray();
                batchArray.addAll(currentBatch);
                resultArray.add(batchArray);
            }

            logger.debug("成功合并文本，共{}个段落", resultArray.size());
        } catch (Exception e) {
            logger.error("合并文本失败: " + e.getMessage(), e);
        }

        return resultArray;
    }

    public static void main(String[] args) {
        try {
            Path path = Paths.get("C:\\Users\\xman\\Desktop\\test.txt");
            String text = Files.readAllLines(path, Charset.forName("GBK")).stream().collect(Collectors.joining("\n"));

            System.out.println("text length: " + text.length());
            
            SpliteText spliteText = new SpliteText(text);
            JSONArray jsonArray = spliteText.mergeBatch(4096);
            System.out.println(jsonArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
}
