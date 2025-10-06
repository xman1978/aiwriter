package com.thunisoft.llm.templatewriter.utils;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;
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
 */
public class PromptConfig {
    private static final Logger logger = LoggerFactory.getLogger(PromptConfig.class);

    // 提示词缓存，使用原子引用确保可见性
    private static final AtomicReference<Map<String, Object>> promptCache = new AtomicReference<>();
    private static volatile long lastModifiedTime = 0;
    
    // 缓存失效标记，避免重复加载
    private static volatile boolean cacheInvalidated = false;
    
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
        Yaml yaml = new Yaml();
        
        // 本地测试用
        // String externalPromptPath = "D:\\Studio\\Document\\python\\code\\gwbj\\src\\com\\thunisoft\\llm\\templatewriter\\prompt.yml";
        String externalPromptPath = System.getProperty("ai.prompt.file.path");
        if (StringUtils.isBlank(externalPromptPath)) {
            throw new RuntimeException("未指定外部配置文件路径，请设置系统属性 ai.prompt.file.path");
        }

        Path path = Paths.get(externalPromptPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new RuntimeException("外部配置文件不存在: " + externalPromptPath);
        }
        
        try {
            long currentModifiedTime = Files.getLastModifiedTime(path).toMillis();
            
            // 检查文件是否真的被修改了
            if (promptCache.get() != null && currentModifiedTime <= lastModifiedTime && !cacheInvalidated) {
                return promptCache.get();
            }
    
            // 执行文件加载
            Map<String, Object> newCache;
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                newCache = yaml.load(fis);
            }
            
            // 原子性更新缓存
            promptCache.set(newCache);
            lastModifiedTime = currentModifiedTime;
            cacheInvalidated = false;
            
            logger.debug("从外部文件重新加载提示词配置: {}", externalPromptPath);
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
}
