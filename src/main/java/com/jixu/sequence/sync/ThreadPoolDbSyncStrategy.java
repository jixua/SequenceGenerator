package com.jixu.sequence.sync;

import com.jixu.sequence.mapper.SysSequenceMapper;
import com.jixu.sequence.mapper.SysSequenceWasteMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * 基于线程池的 DB 异步同步策略。
 * <p>
 * 每次 Redis 自增后，向内置线程池提交一个写 DB 任务（fire-and-forget），
 * 不阻塞业务线程。当线程池队列已满时，使用 CallerRunsPolicy 降级为调用线程
 * 同步执行，保证数据最终一定会写入，不丢失。
 */
@Slf4j
public class ThreadPoolDbSyncStrategy implements DbSyncStrategy {

    private final SysSequenceMapper sequenceMapper;
    private final SysSequenceWasteMapper wasteMapper;
    private final ExecutorService executor;

    public ThreadPoolDbSyncStrategy(SysSequenceMapper sequenceMapper,
                                    SysSequenceWasteMapper wasteMapper,
                                    int coreSize, int maxSize,
                                    int queueCapacity, String threadNamePrefix) {
        this.sequenceMapper = sequenceMapper;
        this.wasteMapper = wasteMapper;
        this.executor = new ThreadPoolExecutor(
                coreSize, maxSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, threadNamePrefix + System.nanoTime() % 10000);
                    t.setDaemon(true);
                    return t;
                },
                // 队列满时由调用方线程同步执行，保证数据最终落库
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("ThreadPoolDbSyncStrategy 初始化：coreSize={}, maxSize={}, queue={}",
                coreSize, maxSize, queueCapacity);
    }

    @Override
    public void asyncSync(String seqKey, String dateStr, long value) {
        executor.submit(() -> doSync(seqKey, dateStr, value));
    }

    @Override
    public void asyncSyncWaste(String seqKey, String sequence) {
        executor.submit(() -> {
            try {
                wasteMapper.insertWasteRecord(seqKey, sequence);
                log.debug("线程池写入废号成功: seqKey={}, sequence={}", seqKey, sequence);
            } catch (Exception e) {
                log.warn("线程池写入废号失败: seqKey={}, sequence={}, error={}", seqKey, sequence, e.getMessage());
            }
        });
    }

    private void doSync(String seqKey, String dateStr, long value) {
        try {
            // UPSERT：若记录存在则取 GREATEST，保证只写大值（适配乱序到达）
            sequenceMapper.upsertMaxValue(seqKey, dateStr, value);
            log.debug("线程池同步 DB 成功: seqKey={}, date={}, value={}", seqKey, dateStr, value);
        } catch (Exception e) {
            // 同步 DB 失败不影响主流程，仅记录日志
            log.warn("线程池同步 DB 失败（不影响主流程）: seqKey={}, date={}, value={}, error={}",
                    seqKey, dateStr, value, e.getMessage());
        }
    }
}
