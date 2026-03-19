package com.jixu.sequence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 序列号生成器配置属性。
 * <p>
 * 通过 application.yml 中 {@code sequence.generator.*} 前缀进行配置。
 *
 * <pre>
 * sequence:
 *   generator:
 *     prefix: JX
 *     seq-length: 3
 *     recovery-interval: 100
 *     expire-seconds: 172800
 *     max-retry: 5
 *     sync-mode: THREAD_POOL   # THREAD_POOL 或 KAFKA
 *     thread-pool:
 *       core-size: 2
 *       max-size: 8
 *       queue-capacity: 1000
 *     kafka:
 *       topic: sequence-sync
 *       bootstrap-servers: localhost:9092
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "sequence.generator")
public class SequenceProperties {

    /** 默认业务前缀（当调用方未指定 seqKey 时使用） */
    private String prefix = "SEQ";

    /** 序列号补齐长度，如 3 表示补齐为 001, 002, ... */
    private int seqLength = 3;

    /** 降级模式下，每间隔多少次请求尝试探测 Redis 是否恢复 */
    private int recoveryInterval = 100;

    /** Redis Key 过期时间（秒），默认 48 小时；永久模式的 Key 忽略此值 */
    private long expireSeconds = 172800L;

    /** DB 乐观锁最大重试次数（降级态下使用） */
    private int maxRetry = 5;

    /**
     * 异步同步 DB 的模式。
     * <ul>
     *   <li>{@code THREAD_POOL} — 使用内置线程池发起异步写 DB（默认，无额外依赖）</li>
     *   <li>{@code KAFKA}       — 通过 Kafka Topic 异步解耦写 DB（需引入 spring-kafka）</li>
     * </ul>
     */
    private SyncMode syncMode = SyncMode.THREAD_POOL;

    /** 线程池配置（syncMode = THREAD_POOL 时生效） */
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    /** Kafka 配置（syncMode = KAFKA 时生效） */
    private KafkaConfig kafka = new KafkaConfig();

    public enum SyncMode {
        THREAD_POOL, KAFKA
    }

    @Data
    public static class ThreadPoolConfig {
        /** 核心线程数 */
        private int coreSize = 2;
        /** 最大线程数 */
        private int maxSize = 8;
        /** 等待队列容量 */
        private int queueCapacity = 2000;
        /** 线程名前缀 */
        private String threadNamePrefix = "seq-db-sync-";
    }

    @Data
    public static class KafkaConfig {
        /** Kafka Topic 名称（生产者向此 Topic 发布同步消息） */
        private String topic = "sequence-db-sync";
        /** Kafka 消费者组 ID */
        private String groupId = "sequence-sync-consumer";
    }
}
