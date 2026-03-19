package com.jixu.sequence.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixu.sequence.mapper.SysSequenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Kafka DB 同步消费者。
 * <p>
 * 监听 {@code sequence-db-sync} Topic（可通过配置覆盖），
 * 消费同步消息并将最新序列号值写入 DB（UPSERT + GREATEST）。
 * <p>
 * 注意：此 Bean 只在 {@code syncMode = KAFKA} 且引入了 spring-kafka 时才会被注册。
 * 具体由 {@link com.jixu.sequence.config.SequenceAutoConfiguration} 条件控制。
 */
@Slf4j
public class KafkaDbSyncConsumer {

    private final SysSequenceMapper sequenceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaDbSyncConsumer(SysSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    /**
     * 消费序列号同步消息，写入 DB。
     * <p>
     * Topic 和 GroupId 通过 SpEL 从 Spring 环境变量读取，便于配置覆盖。
     */
    @KafkaListener(
            topics = "#{@sequenceProperties.kafka.topic}",
            groupId = "#{@sequenceProperties.kafka.groupId}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            KafkaDbSyncStrategy.SyncMessage msg =
                    objectMapper.readValue(record.value(), KafkaDbSyncStrategy.SyncMessage.class);

            sequenceMapper.upsertMaxValue(msg.getSeqKey(), msg.getDateStr(), msg.getValue());
            log.debug("Kafka 消费同步 DB 成功: seqKey={}, date={}, value={}",
                    msg.getSeqKey(), msg.getDateStr(), msg.getValue());
        } catch (Exception e) {
            log.error("Kafka 消费同步 DB 失败: record={}, error={}", record.value(), e.getMessage(), e);
            // 消费失败不 ack 抛出，交给 Kafka 重试机制处理
            throw new RuntimeException("序列号 DB 同步消费失败", e);
        }
    }
}
