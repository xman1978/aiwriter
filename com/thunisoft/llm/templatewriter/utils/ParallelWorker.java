package com.thunisoft.llm.templatewriter.utils;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 并行工作器
 * 支持并发执行任务，提供结果缓存和超时控制
 * 线程安全，支持资源自动清理
 * @param <K> 任务键类型
 * @param <V> 结果值类型
 */
public class ParallelWorker<K, V> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ParallelWorker.class);

    // 常量定义
    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 默认超时时间5分钟
    private static final int CLEANUP_INTERVAL_SECONDS = 60; // 清理间隔1分钟

    private final ConcurrentHashMap<K, ResultHolder<V>> resultMap = new ConcurrentHashMap<>(); // 存储结果
    private final ConcurrentHashMap<K, CountDownLatch> latchMap = new ConcurrentHashMap<>(); // 存储等待结果的计数器
    private final ExecutorService pool; // 线程池
    private final Semaphore semaphore; // 信号量
    private final AtomicBoolean shutdown = new AtomicBoolean(false); // 关闭标志
    private final ScheduledExecutorService cleanupExecutor; // 清理任务执行器

    /**
     * 默认构造，使用 CPU 核心数作为并发数，线程名前缀 "pw"
     */
    public ParallelWorker() {
        this(Runtime.getRuntime().availableProcessors(), "pw");
    }

    /**
     * 自定义并发数 + 线程名前缀
     */
    public ParallelWorker(int maxConcurrency, String threadNamePrefix) {
        this(maxConcurrency, maxConcurrency * 2,
                90L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadNamePrefix);
    }

    /**
     * 高级构造：完全自定义线程池
     */
    public ParallelWorker(int corePoolSize,
                          int maxPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          String threadNamePrefix) {
        this.pool = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                namedThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.semaphore = new Semaphore(corePoolSize);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
                namedThreadFactory(threadNamePrefix + "-cleanup"));
        startCleanupTask();
    }

    /**
     * 使用外部线程池构造
     */
    public ParallelWorker(ExecutorService pool, int maxConcurrency) {
        this.pool = pool;
        this.semaphore = new Semaphore(maxConcurrency);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
                namedThreadFactory("pw-cleanup"));
        startCleanupTask();
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleWithFixedDelay(() -> {
            try {
                cleanupExpiredResults();
            } catch (Exception e) {
                logger.warn("清理过期结果时发生异常", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 清理过期的结果
     */
    private void cleanupExpiredResults() {
        long currentTime = System.currentTimeMillis();
        resultMap.entrySet().removeIf(entry -> {
            ResultHolder<V> holder = entry.getValue();
            return holder != null && holder.isExpired(currentTime);
        });
        
        // 清理未完成的latch
        latchMap.entrySet().removeIf(entry -> {
            CountDownLatch latch = entry.getValue();
            return latch != null && latch.getCount() == 0;
        });
    }

    /**
     * 批量提交任务
     * @param keys 任务键列表
     * @param computer 任务执行函数
     * @throws IllegalStateException 如果工作器已关闭
     */
    public void submitAll(List<K> keys, Function<K, V> computer) {
        if (shutdown.get()) {
            throw new IllegalStateException("ParallelWorker已关闭，无法提交新任务");
        }
        
        if (keys == null || keys.isEmpty()) {
            logger.warn("任务列表为空，跳过提交");
            return;
        }
        
        if (computer == null) {
            throw new IllegalArgumentException("任务执行函数不能为null");
        }
        
        for (K k : keys) {
            if (k == null) {
                logger.warn("跳过null任务键");
                continue;
            }
            
            pool.execute(() -> {
                boolean acquired = false;
                try {
                    semaphore.acquire();
                    acquired = true;

                    V v = computer.apply(k);
                    resultMap.put(k, ResultHolder.success(v));
                    logger.debug("任务完成: {}", k);
                } catch (Exception e) {
                    logger.error("任务执行失败: {}", k, e);
                    resultMap.put(k, ResultHolder.failure(e));
                } finally {
                    if (acquired) {
                        semaphore.release();
                    }
                    CountDownLatch latch = latchMap.remove(k);
                    if (latch != null) {
                        latch.countDown();
                    }
                }
            });
        }
    }

    /**
     * 获取结果，如果结果不存在，则等待结果返回
     * @param key 任务键
     * @return 任务结果
     * @throws InterruptedException 如果等待被中断
     * @throws ExecutionException 如果任务执行失败
     * @throws TimeoutException 如果等待超时
     * @throws IllegalStateException 如果工作器已关闭
     */
    public V getResult(K key) throws InterruptedException, ExecutionException, TimeoutException {
        return getResult(key, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 获取结果，如果结果不存在，则等待结果返回，如果超时，则抛出TimeoutException
     * @param key 任务键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 任务结果
     * @throws InterruptedException 如果等待被中断
     * @throws ExecutionException 如果任务执行失败
     * @throws TimeoutException 如果等待超时
     * @throws IllegalStateException 如果工作器已关闭
     */
    public V getResult(K key, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (shutdown.get()) {
            throw new IllegalStateException("ParallelWorker已关闭");
        }
        
        if (key == null) {
            throw new IllegalArgumentException("任务键不能为null");
        }
        
        ResultHolder<V> holder = resultMap.get(key);
        if (holder != null) {
            return holder.get();
        }
        
        CountDownLatch latch = latchMap.computeIfAbsent(key, k -> new CountDownLatch(1));
        if (!latch.await(timeout, unit)) {
            latchMap.remove(key); // 清理超时的latch
            throw new TimeoutException("等待任务结果超时: " + key);
        }
        
        holder = resultMap.get(key);
        if (holder == null) {
            throw new ExecutionException(new RuntimeException("任务结果丢失: " + key));
        }
        
        return holder.get();
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            logger.info("开始关闭ParallelWorker");
            cleanupExecutor.shutdown();
            pool.shutdown();
        }
    }

    /**
     * 等待线程池终止
     * @param timeout 超时时间
     * @param unit 时间单位
     * @throws InterruptedException 如果等待被中断
     */
    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (!shutdown.get()) {
            shutdown();
        }
        
        try {
            if (!pool.awaitTermination(timeout, unit)) {
                logger.warn("线程池未能在指定时间内终止，强制关闭");
                pool.shutdownNow();
            }
            
            if (!cleanupExecutor.awaitTermination(timeout, unit)) {
                logger.warn("清理执行器未能在指定时间内终止，强制关闭");
                cleanupExecutor.shutdownNow();
            }
        } finally {
            // 清理资源
            resultMap.clear();
            latchMap.clear();
        }
    }

    /**
     * 实现AutoCloseable接口，支持try-with-resources
     */
    @Override
    public void close() {
        try {
            awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("关闭ParallelWorker时被中断", e);
        }
    }

    /**
     * 工具方法：带前缀的线程工厂
     */
    private static ThreadFactory namedThreadFactory(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger idx = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName(prefix + "-" + idx.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };
    }

    /**
     * 结果包装器：支持成功和异常，支持过期检查
     */
    private static class ResultHolder<V> {
        private final V value;
        private final Exception exception;
        private final long createTime;
        private static final long EXPIRY_TIME_MS = 30 * 60 * 1000; // 30分钟过期

        private ResultHolder(V value, Exception exception) {
            this.value = value;
            this.exception = exception;
            this.createTime = System.currentTimeMillis();
        }

        static <V> ResultHolder<V> success(V value) {
            return new ResultHolder<>(value, null);
        }

        static <V> ResultHolder<V> failure(Exception e) {
            return new ResultHolder<>(null, e);
        }

        V get() throws ExecutionException {
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return value;
        }
        
        boolean isExpired(long currentTime) {
            return (currentTime - createTime) > EXPIRY_TIME_MS;
        }
    }
}
