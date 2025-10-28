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
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import com.thunisoft.llm.templatewriter.AIWriterBase;
import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.service.impl.CallLlm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文本分割处理类
 * 用于清洗和分割文本内容，去除标题、格式化文本
 */
public class SpliteText extends AIWriterBase {
    private static final Logger logger = LoggerFactory.getLogger(SpliteText.class);

    // 常量定义
    private static final int MAX_TEXT_LENGTH = 1000000; // 最大文本长度限制
    private static final String BR_MARKER = "<<BR>>"; // 换行标记
    private static final String LINE_SEPARATOR = "\n"; // 行分隔符
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("^附[件]?[:：]?$");

    private static final String EXTRACT_CHUNK_OUTLINE_PROMPT = PromptConfig.getPrompt("extractChunkOutlinePrompt");
    private static final String EXTRACT_FIRST_LEVEL_TITLE_PROMPT = PromptConfig.getPrompt("extractFirstLevelTitlePrompt");

    private String text;
    private boolean removeTitle = true;
    private boolean removeHeading = true;

    private static final Pattern END_WITH_PUNCTUATION = Pattern.compile(".*[。！？；：!?;:]$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^[（(第]?[a-zA-Z0-9一二三四五六七八九十零〇]+[\\.\\s)）、章篇部分节]{1}[\\s\\u3000]*[^。？！.?!]+[^。？！；：\\.?!;:]$");
    private static final Pattern FIRST_HEADING_PATTERN = Pattern.compile("^[第]?[一二三四五六七八九十零〇]+[\\.\\s、章篇部分节]{1}[\\s\\u3000]*[^。？！.?!]+[^。？！；：\\.?!;:]$");
    private static final Pattern MULTI_LINE_FIRST_HEADING_PATTERN = Pattern.compile("[第]?[一二三四五六七八九十零〇]+[\\.\\s、章篇部分节]{1}[\\s\\u3000]*[^。？！.?!]+[^。？！；：\\.?!;:]", Pattern.DOTALL);

    private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("\\r?\\n");
    private static final Pattern HEADING_WHITE_SPACE_PATTERN = Pattern.compile("^[\\s\\u3000]+");
    private static final Pattern TAILING_WHITE_SPACE_PATTERN = Pattern.compile("[\\s\\u3000]+$");

    private static final Pattern PUNCTUATION_NEWLINE_PATTERN = Pattern.compile("([。！？!?])[\\s\\u3000]*\\r?\\n");

    // 线程局部的StringBuilder，减少内存分配
    private static final ThreadLocal<StringBuilder> STRING_BUILDER_HOLDER = 
        ThreadLocal.withInitial(() -> new StringBuilder(4096));

    private HashSet<String> firstLevelTitleSet = new HashSet<>();

    /**
     * 构造函数
     * @param text 要处理的文本，不能为null
     * @param removeTitle 是否去除标题
     * @param removeHeading 是否去除一级、二级、三级标题
     * @param extractFirstLevelTitle 是否提取一级标题
     * @param callLlm 大模型接口
     * @param useThink 是否使用思考
     * @param maxToken 最大Token
     * @param isExchange 是否使用Exchange
     * @param extParams 扩展参数
     * @param outputStream 输出流
     * @throws IllegalArgumentException 如果输入文本为null或过长
     */
    public SpliteText(String text, boolean extractFirstLevelTitle, boolean removeTitle, boolean removeHeading, 
        ICallLlm callLlm, boolean useThink, int maxToken, boolean isExchange) {
        super(callLlm, useThink, maxToken, isExchange);

        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为null");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            logger.warn("输入文本长度超过限制: {} > {}", text.length(), MAX_TEXT_LENGTH);
            throw new IllegalArgumentException("输入文本长度超过限制: " + MAX_TEXT_LENGTH);
        }
        this.text = text;
        this.removeTitle = removeTitle;
        this.removeHeading = removeHeading;

        // 提取一级标题
        if (extractFirstLevelTitle) 
            this.extractFirstLevelTitle(this.text, nullOutputStream);
    }

    /**
     * 检查是否提取了一级标题
     * @param text 输入文本
     * @return 是否提取了一级标题
     */
    public boolean checkIsHeading() {
        return this.firstLevelTitleSet.isEmpty() ? false : true;
    }

    /**
     * 去除标题
     * @param text 输入文本
     * @return 去除标题后的文本
     */
    private String removeTitle(String text, boolean done) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        
        if (!done) {
            return text;
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
                
                String trimmed = TAILING_WHITE_SPACE_PATTERN.matcher(line).replaceAll("");
                trimmed = HEADING_WHITE_SPACE_PATTERN.matcher(trimmed).replaceAll("");

                if (!started) {
                    if (trimmed.isEmpty()) {
                        continue; // 跳过空行
                    }
                    // 如果这一行结尾没有标点，判定为标题 → 跳过
                    if (!END_WITH_PUNCTUATION.matcher(trimmed).matches() && !HEADING_PATTERN.matcher(trimmed).matches()) {
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
    private String removeHeading(String text, boolean done) {
        if (StringUtils.isBlank(text)) {
            return "";
        }

        if (!done) {
            return text;
        }
        
        try {
            StringBuilder sb = STRING_BUILDER_HOLDER.get();
            sb.setLength(0); // 重用StringBuilder
            String[] lines = LINE_SEPARATOR_PATTERN.split(text);
            sb.ensureCapacity(text.length()); // 预分配足够容量

            for (String line : lines) {
                if (line == null) continue;
        
                String cleanedLine = HEADING_WHITE_SPACE_PATTERN.matcher(line).replaceAll("");
                
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
            StringBuilder sb = STRING_BUILDER_HOLDER.get();
            sb.setLength(0); // 重用StringBuilder
            sb.ensureCapacity(text.length()); // 预分配容量
            
            // 使用Matcher进行逐字符处理，避免创建中间字符串
            String processedText = PUNCTUATION_NEWLINE_PATTERN.matcher(text).replaceAll("$1" + BR_MARKER);
            
            // 处理换行和空格
            boolean inWhitespace = false;
            for (int i = 0; i < processedText.length(); i++) {
                char c = processedText.charAt(i);
                
                if (c == '\r' || c == '\n') {
                    // 换行符替换为空格
                    if (!inWhitespace) {
                        sb.append(' ');
                        inWhitespace = true;
                    }
                } else if (Character.isWhitespace(c) || c == '\u3000') {
                    // 其他空白字符
                    if (!inWhitespace) {
                        sb.append(' ');
                        inWhitespace = true;
                    }
                } else {
                    sb.append(c);
                    inWhitespace = false;
                }
            }
            
            // 去除首尾空格
            String result = sb.toString().trim();
            return result.isEmpty() ? "" : result;
        } catch (Exception e) {
            logger.error("格式化文本时发生异常", e);
        }

        return text; // 发生异常时返回原文本
    }

    /**
     * 清洗文本
     * @param text 输入文本
     * @return 清洗后的文本
     */
    private String cleanText(String text, boolean removeTitle, boolean removeHeading) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        
        try {
            String result = text;
            
            // 使用StringBuilder减少中间字符串创建
            if (removeTitle) {
                result = removeTitle(result, removeTitle);
            }
            if (removeHeading) {
                result = removeHeading(result, removeHeading);
            }
            
            return removeNewlineAndSpace(result);
        } catch (Exception e) {
            logger.error("清洗文本时发生异常", e);
            return text; // 发生异常时返回原文本
        }
    }

    /**
     * 按段落分割文本
     * @return 包含文本段落的JSON数组
     */
    public JSONArray splitTextByParagraph(int maxLength) throws Exception {
        JSONArray jsonArray = new JSONArray();

        try {           
            String cleanedText = cleanText(this.text, this.removeTitle, this.removeHeading);
            
            if (StringUtils.isBlank(cleanedText)) 
                throw new RuntimeException("清洗后文本为空，返回空字符串");

            String[] lines = cleanedText.split(BR_MARKER);
            StringBuilder sb = STRING_BUILDER_HOLDER.get();
            
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                if (line == null) {
                    continue;
                }
                
                // 使用StringBuilder处理空白字符，避免创建中间字符串
                sb.setLength(0);
                boolean inWhitespace = false;
                
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (Character.isWhitespace(c) || c == '\u3000') {
                        if (!inWhitespace) {
                            sb.append(' ');
                            inWhitespace = true;
                        }
                    } else {
                        sb.append(c);
                        inWhitespace = false;
                    }
                }
                
                String cleanedLine = sb.toString().trim();
                if (StringUtils.isBlank(cleanedLine)) {
                    continue;
                }
                
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("index", index);
                jsonObject.put("text", cleanedLine.substring(0, Math.min(cleanedLine.length(), maxLength)));
                jsonObject.put("length", Math.min(cleanedLine.length(), maxLength));
                jsonArray.add(jsonObject);
            }

            logger.debug("成功分割文本，共{}个段落", jsonArray.size());

            return jsonArray;
        } catch (Exception e) {
            throw new RuntimeException("分割文本失败: " + e.getMessage(), e);
        } 
    }

    /**
     * 通过大模型提取文章内容中的一级标题
     * @param content 文章内容
     * @return 一级标题集合
     */
    private void extractFirstLevelTitle(String content, OutputStream outputStream)
       throws RuntimeException {
        // 对内容多行匹配一级标题，如果匹配到，则认为是有显著的一级标题
        boolean isFirstHeading = false;
        int matchCount = 0;
        Matcher matcher = MULTI_LINE_FIRST_HEADING_PATTERN.matcher(content);
        while (matcher.find()) {
            matchCount++;
            if (matchCount > 1) {
                isFirstHeading = true;
                break;
            }
        }
        
        try {
            int chunkCount = 0;
            ArrayList<String> chunkOutlineList = new ArrayList<>();
            String[] lines = content.split("\n");
            StringBuffer sb = new StringBuffer();
            for (String line : lines) {
                // 去除首尾空格
                line = HEADING_WHITE_SPACE_PATTERN.matcher(line).replaceAll("");
                line = TAILING_WHITE_SPACE_PATTERN.matcher(line).replaceAll("");
                if (StringUtils.isBlank(line)) 
                    continue;

                if (isFirstHeading) {
                    if (ATTACHMENT_PATTERN.matcher(line).matches())
                        break;
                    if (FIRST_HEADING_PATTERN.matcher(line).matches()) {
                        firstLevelTitleSet.add(line);
                    }
                } else {
                    sb.append(line).append("\n");
                    if (sb.length() > maxToken * 0.8 || ATTACHMENT_PATTERN.matcher(line).matches()) {
                        // 如果内容长度超过maxToken * 0.8，则调用大模型提取局部大纲
                        JSONArray prompt = buildJsonPrompt(EXTRACT_CHUNK_OUTLINE_PROMPT, String.format("\n【文本分块】：\n%s\n", sb.toString()));
                        String chunkOutline = invokeLlm(prompt, outputStream, true, true);
                        logger.info("局部大纲：{}", chunkOutline);
                        chunkOutlineList.add(chunkOutline);                 
                        
                        sb.setLength(0);
                        chunkCount++;

                        if (ATTACHMENT_PATTERN.matcher(line).matches()) {
                            break;
                        }
                    }
                }
                
            }
            // 分块提取一级标题后，再提取全文一级标题
            if (firstLevelTitleSet.isEmpty() && !chunkOutlineList.isEmpty()) {
                if (chunkCount == 1) {
                    JSONObject firstLevelTitleJson = JSONObject.parseObject(chunkOutlineList.get(0));
                    JSONArray firstLevelTitles = firstLevelTitleJson.getJSONArray("firstLevelTitles");
                    if (firstLevelTitles.size() > 0) {
                        firstLevelTitleSet.addAll(firstLevelTitles.toJavaList(String.class));
                    }
                } else {
                    JSONArray prompt = buildJsonPrompt(EXTRACT_FIRST_LEVEL_TITLE_PROMPT, String.format("\n【局部大纲集合】：\n%s\n", chunkOutlineList.toString()));
                    String firstLevelTitle = invokeLlm(prompt, outputStream, true, true);
                    JSONObject firstLevelTitleJson = JSONObject.parseObject(firstLevelTitle);
                    JSONArray firstLevelTitles = firstLevelTitleJson.getJSONArray("firstLevelTitles");
                    firstLevelTitleSet.addAll(firstLevelTitles.toJavaList(String.class));
                }
            }
        } catch(Exception e) {
            throw new RuntimeException("大模型获取一级标题错误", e);
        }

        logger.info("一级标题集合：{}", firstLevelTitleSet.toString());
    } 

    private String numberToChinese(int number) {
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

    /*
     * 按章节拆分文本，如果章节下的长度超过maxLength，则按章节下的段落拆分
     * @param maxLength 最大长度
     * @return 拆分后的文本
     */
    public JSONArray splitTextByChapter(int maxLength) throws Exception {
        JSONArray resultArray = new JSONArray();
        try {
            String cleanedText = removeTitle(this.text, true);
            if (StringUtils.isBlank(cleanedText)) {
                throw new RuntimeException("清洗后文本为空，返回空数组");
            }

            String[] lines = LINE_SEPARATOR_PATTERN.split(cleanedText);
            StringBuilder sb = STRING_BUILDER_HOLDER.get();
            String chapterTitle = null;
            StringBuffer chapterText = new StringBuffer();
            int chapterIndex = 1;
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                if (line == null) 
                    continue;
                
                // 使用StringBuilder处理空白字符，避免创建中间字符串
                sb.setLength(0);
                boolean inWhitespace = false;
                
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (Character.isWhitespace(c) || c == '\u3000') {
                        if (!inWhitespace) {
                            sb.append(' ');
                            inWhitespace = true;
                        }
                    } else {
                        sb.append(c);
                        inWhitespace = false;
                    }
                }
                
                String cleanedLine = sb.toString().trim();
                if (StringUtils.isBlank(cleanedLine)) {
                    continue;
                }

                if (firstLevelTitleSet.contains(cleanedLine) && !cleanedLine.equals(chapterTitle)) {
                    if (chapterText.length() > 0) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("index", index);
                        if (chapterTitle == null || FIRST_HEADING_PATTERN.matcher(chapterTitle).matches()) {
                            jsonObject.put("title", chapterTitle == null ? "引言" : chapterTitle);
                        } else {
                            jsonObject.put("title", numberToChinese(chapterIndex++) + "、" + chapterTitle);
                        }
                        jsonObject.put("text", chapterText.toString().trim().substring(0, Math.min(chapterText.toString().trim().length(), maxLength)));
                        jsonObject.put("length", Math.min(chapterText.toString().trim().length(), maxLength));
                        resultArray.add(jsonObject);
                        chapterText.setLength(0);
                    }
                    chapterTitle = cleanedLine;
                    continue;
                }
                
                chapterText.append(cleanedLine).append(LINE_SEPARATOR);
            }

            if (chapterText.length() > 0) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("index", -1);
                if (chapterTitle == null || FIRST_HEADING_PATTERN.matcher(chapterTitle).matches()) {
                    jsonObject.put("title", chapterTitle == null ? "引言" : chapterTitle);
                } else {
                    jsonObject.put("title", numberToChinese(chapterIndex) + "、" + chapterTitle);
                }
                jsonObject.put("text", chapterText.toString().trim().substring(0, Math.min(chapterText.toString().trim().length(), maxLength)));
                jsonObject.put("length", Math.min(chapterText.toString().trim().length(), maxLength));
                resultArray.add(jsonObject);
                chapterText.setLength(0);
            }

            return resultArray;
        } catch (Exception e) {
           throw new RuntimeException("按章节拆分文本失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按段落合并文本，按段落合并后的文本长度不超过maxLength
     * @param maxLength 最大长度
     * @return 合并后的文本
     */
    public JSONArray mergeParagraph(int maxLength) {
        JSONArray resultArray = new JSONArray();

        try {
            JSONArray jsonArray = splitTextByParagraph(maxLength);
            
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

    // 本地测试用
    /*
    public static void main(String[] args) {
        try {
            Path path = Paths.get("C:\\Users\\xman\\Desktop\\test2.txt");
            String text = Files.readAllLines(path, Charset.forName("UTF-8")).stream().collect(Collectors.joining("\n"));

            System.out.println("text length: " + text.length());

            SpliteText spliteText = new SpliteText(text, true, true, false, new CallLlm(), true, 4096, false);
            if (spliteText.checkIsHeading()) {
                JSONArray jsonArray = spliteText.splitTextByChapter(4096);
                System.out.println("按章节合并文本: " + jsonArray);
            } else {
                JSONArray jsonArray = spliteText.mergeParagraph(4096);
                System.out.println("按段落合并文本: " + jsonArray);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
        */
}
