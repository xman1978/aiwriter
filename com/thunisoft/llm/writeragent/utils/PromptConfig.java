package com.thunisoft.llm.writeragent.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileInputStream;
import org.yaml.snakeyaml.Yaml;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置文件读取工具类
 * 支持外部配置文件，监控配置文件变化，自动重新加载
 * 支持缓存机制，提高性能
 * 线程安全的配置读取，优化并发性能
 * 支持多个不同的 promptFileName，每个文件独立缓存
 */
public class PromptConfig {
    private static final Logger logger = LoggerFactory.getLogger(PromptConfig.class);

    /**
     * 缓存条目，包含配置内容和最后修改时间
     */
    private static class CacheEntry {
        final Map<String, Object> config;
        final long lastModifiedTime;
        
        CacheEntry(Map<String, Object> config, long lastModifiedTime) {
            this.config = config;
            this.lastModifiedTime = lastModifiedTime;
        }
    }
    
    // 为每个文件名维护独立的缓存，使用 ConcurrentHashMap 确保线程安全
    private static final ConcurrentHashMap<String, CacheEntry> cacheMap = new ConcurrentHashMap<>();
        
    private static boolean IS_RESOURCE_IN_JAR;
    private static final Path EXTERNAL_PROMPT_PATH;
    static {
        String externalPromptPath = null;
        try {
            externalPromptPath = System.getProperty("ai.prompt.file.path");

            if (StringUtils.isBlank(externalPromptPath)) {
                throw new RuntimeException("未指定 prompt 配置文件路径，请设置系统属性 ai.prompt.file.path");
            }

            Path promptPath = Paths.get(externalPromptPath);
            if (!IS_RESOURCE_IN_JAR && (!Files.exists(promptPath) || !Files.isDirectory(promptPath))) {
                throw new RuntimeException("外部配置文件目录不存在: " + externalPromptPath);
            }

            IS_RESOURCE_IN_JAR = false;
        } catch (Exception e2) {
            logger.warn("读取系统属性 ai.prompt.file.path 失败");
            try {
                // 从AIWriter.class 所在的包下读取prompt目录
                externalPromptPath = PromptConfig.class.getPackage().getName().replace(".", "/") + "/../prompt/";
                IS_RESOURCE_IN_JAR = true;
            } catch (Exception e1) {
                logger.warn("读取 jar 包中的 prompt 目录失败");
            }
        }

        EXTERNAL_PROMPT_PATH = Paths.get(externalPromptPath);
    }

    // 使用 volatile 确保可见性
    private static volatile String promptFileName;

    public static void setPromptFileName(String fileName) {
        promptFileName = fileName;
    }
    
    /**
     * 获取提示词配置
     * @param promptName 提示词名称
     * @return 提示词内容
     * @throws IllegalArgumentException 如果提示词名称为空或未找到
     * @throws RuntimeException 如果加载配置失败
     */
    public static String getPrompt(String promptName) {
        if (StringUtils.isBlank(promptName)) {
            throw new IllegalArgumentException("提示词名称不能为空");
        }
        
        try {
            Map<String, Object> prompt = loadPromptConfig();
            if (prompt == null || prompt.isEmpty()) {
                throw new RuntimeException("配置加载失败，提示词配置为空");
            }
            
            Object promptValue = prompt.get(promptName);
            if (promptValue == null) {
                throw new RuntimeException("未找到提示词: " + promptName);
            }

            String result = promptValue.toString();
            if (StringUtils.isBlank(result)) {
                throw new RuntimeException("提示词内容为空: " + promptName);
            }
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("获取提示词失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 实际执行配置加载操作
     * @return 提示词配置Map
     * @throws RuntimeException 如果加载配置失败
     */
    private static Map<String, Object> loadPromptConfig() throws RuntimeException {
        // 获取当前文件名，确保线程安全
        String currentFileName = promptFileName;
        if (StringUtils.isBlank(currentFileName)) {
            throw new RuntimeException("promptFileName 未设置");
        }
        
        Yaml yaml = new Yaml();
        
        try {
            Path promptPath = Paths.get(EXTERNAL_PROMPT_PATH.toString(), currentFileName);

            // 检查文件是否真的被修改了
            long currentModifiedTime = 0;
            if (!IS_RESOURCE_IN_JAR) {
                if (!Files.exists(promptPath) || !Files.isRegularFile(promptPath)) {
                    throw new RuntimeException("提示词文件不存在: " + promptPath.toString());
                }

                currentModifiedTime = Files.getLastModifiedTime(promptPath).toMillis();
                
                // 检查该文件的缓存是否有效
                CacheEntry cachedEntry = cacheMap.get(currentFileName);
                if (cachedEntry != null && currentModifiedTime <= cachedEntry.lastModifiedTime) {
                    return cachedEntry.config;
                }
            } else {
                // 对于 JAR 中的资源，检查缓存是否存在
                CacheEntry cachedEntry = cacheMap.get(currentFileName);
                if (cachedEntry != null) {
                    return cachedEntry.config;
                }
            }
    
            // 执行文件加载
            Map<String, Object> newCache;
            if (IS_RESOURCE_IN_JAR) {
                newCache = yaml.load(PromptConfig.class.getClassLoader().getResourceAsStream(promptPath.toString()));
            } else {
                try (FileInputStream fis = new FileInputStream(promptPath.toFile())) {
                    newCache = yaml.load(fis);
                }
            }
            
            // 更新该文件的缓存（线程安全）
            cacheMap.put(currentFileName, new CacheEntry(newCache, currentModifiedTime));

            return newCache;
            
        } catch (Exception e) {
            throw new RuntimeException("从外部文件读取提示词失败: " + e.getMessage(), e);
        } 
    }
    
    /**
     * 尝试获取配置，如果失败则返回默认值
     * @param promptName 提示词名称
     * @param defaultValue 默认值
     * @return 提示词内容或默认值
     */
    public static String getPromptOrDefault(String promptName, String defaultValue) {
        try {
            return getPrompt(promptName);
        } catch (Exception e) {
            logger.warn("获取提示词失败，使用默认值: {} - {}", promptName, e.getMessage());
            return defaultValue;
        }
    }

    // 本地测试用
    /*
    public static void main(String[] args) {
        PromptConfig.setPromptFileName("template.yml");
        System.out.println(PromptConfig.getPrompt("writerPrompt"));
    }
    */
}
