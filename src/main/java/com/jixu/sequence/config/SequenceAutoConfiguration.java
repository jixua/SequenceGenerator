package com.jixu.sequence.config;

import com.jixu.sequence.core.DbSequenceGenerator;
import com.jixu.sequence.core.RedisSequenceGenerator;
import com.jixu.sequence.core.SequenceGenerator;
import com.jixu.sequence.core.StateMachineManager;
import com.jixu.sequence.mapper.SysSequenceMapper;
import com.jixu.sequence.mapper.SysSequenceWasteMapper;
import com.jixu.sequence.sync.DbSyncStrategy;
import com.jixu.sequence.sync.KafkaDbSyncConsumer;
import com.jixu.sequence.sync.KafkaDbSyncStrategy;
import com.jixu.sequence.sync.ThreadPoolDbSyncStrategy;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 序列号生成器自动装配配置类。
 * <p>
 * 按配置自动选择 DB 同步策略：
 * <ul>
 *   <li>{@code sequence.generator.sync-mode=THREAD_POOL}（默认）→ 使用内置线程池</li>
 *   <li>{@code sequence.generator.sync-mode=KAFKA} → 使用 Kafka 消息队列</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SequenceProperties.class)
@MapperScan("com.jixu.sequence.mapper")
public class SequenceAutoConfiguration {

    // ==================== Redis 生成器 ====================

    @Bean
    @ConditionalOnMissingBean
    public RedisSequenceGenerator redisSequenceGenerator(StringRedisTemplate stringRedisTemplate,
                                                         SequenceProperties properties) {
        log.info("初始化 RedisSequenceGenerator");
        return new RedisSequenceGenerator(stringRedisTemplate, properties);
    }

    // ==================== 异步同步策略（二选一） ====================

    /**
     * 线程池同步策略（默认，syncMode=THREAD_POOL 时生效）。
     */
    @Bean
    @ConditionalOnMissingBean(DbSyncStrategy.class)
    @ConditionalOnProperty(
            prefix = "sequence.generator",
            name = "sync-mode",
            havingValue = "THREAD_POOL",
            matchIfMissing = true   // 未配置 syncMode 时默认启用
    )
    public DbSyncStrategy threadPoolDbSyncStrategy(SysSequenceMapper sequenceMapper,
                                                   SysSequenceWasteMapper wasteMapper,
                                                   SequenceProperties properties) {
        SequenceProperties.ThreadPoolConfig cfg = properties.getThreadPool();
        log.info("初始化 ThreadPoolDbSyncStrategy: coreSize={}, maxSize={}, queue={}",
                cfg.getCoreSize(), cfg.getMaxSize(), cfg.getQueueCapacity());
        return new ThreadPoolDbSyncStrategy(
                sequenceMapper,
                wasteMapper,
                cfg.getCoreSize(),
                cfg.getMaxSize(),
                cfg.getQueueCapacity(),
                cfg.getThreadNamePrefix()
        );
    }

    /**
     * Kafka 同步策略（syncMode=KAFKA 时生效，需引入 spring-kafka 依赖）。
     */
    @Bean
    @ConditionalOnMissingBean(DbSyncStrategy.class)
    @ConditionalOnProperty(
            prefix = "sequence.generator",
            name = "sync-mode",
            havingValue = "KAFKA"
    )
    public DbSyncStrategy kafkaDbSyncStrategy(KafkaTemplate<String, String> kafkaTemplate,
                                              SequenceProperties properties) {
        String topic = properties.getKafka().getTopic();
        String wasteTopic = properties.getKafka().getWasteTopic();
        log.info("初始化 KafkaDbSyncStrategy: topic={}, wasteTopic={}", topic, wasteTopic);
        return new KafkaDbSyncStrategy(kafkaTemplate, topic, wasteTopic);
    }

    /**
     * Kafka 消费者（syncMode=KAFKA 时生效，负责消费消息并写入 DB）。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "sequence.generator",
            name = "sync-mode",
            havingValue = "KAFKA"
    )
    public KafkaDbSyncConsumer kafkaDbSyncConsumer(SysSequenceMapper sequenceMapper,
                                                   SysSequenceWasteMapper wasteMapper) {
        log.info("初始化 KafkaDbSyncConsumer");
        return new KafkaDbSyncConsumer(sequenceMapper, wasteMapper);
    }

    // ==================== DB 生成器 ====================

    @Bean
    @ConditionalOnMissingBean
    public DbSequenceGenerator dbSequenceGenerator(SysSequenceMapper sequenceMapper,
                                                   SequenceProperties properties,
                                                   DbSyncStrategy syncStrategy) {
        log.info("初始化 DbSequenceGenerator，同步策略: {}", syncStrategy.getClass().getSimpleName());
        return new DbSequenceGenerator(sequenceMapper, properties, syncStrategy);
    }

    // ==================== 核心状态机 ====================

    @Bean
    @ConditionalOnMissingBean(SequenceGenerator.class)
    public SequenceGenerator sequenceGenerator(RedisSequenceGenerator redisGenerator,
                                               DbSequenceGenerator dbGenerator,
                                               RedissonClient redisson,
                                               SequenceProperties properties) {
        log.info("初始化 StateMachineManager | prefix={}, seqLength={}, recoveryInterval={}, syncMode={}",
                properties.getPrefix(), properties.getSeqLength(),
                properties.getRecoveryInterval(), properties.getSyncMode());
        return new StateMachineManager(redisGenerator, dbGenerator, redisson, properties);
    }
}
