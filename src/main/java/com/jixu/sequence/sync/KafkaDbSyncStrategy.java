package com.jixu.sequence.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 基于 Kafka 的 DB 异步同步策略（生产者端）。
 * <p>
 * 每次 Redis 自增后，向 Kafka Topic 发送一条同步消息（fire-and-forget）。
 * Kafka 消费者（{@link KafkaDbSyncConsumer}）独立消费这些消息并写入 DB，
 * 通过消息队列实现生产者与 DB 写操作的彻底解耦，具备更好的削峰与可靠性保证。
 * <p>
 * 依赖：{@code spring-kafka}（需在 pom.xml 中引入）。
 */
@Slf4j
public class KafkaDbSyncStrategy implements DbSyncStrategy {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final String wasteTopic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaDbSyncStrategy(KafkaTemplate<String, String> kafkaTemplate, 
                               String topic, String wasteTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.wasteTopic = wasteTopic;
        log.info("KafkaDbSyncStrategy 初始化：topic={}, wasteTopic={}", topic, wasteTopic);
    }

    @Override
    public void asyncSync(String seqKey, String dateStr, long value) {
        try {
            SyncMessage message = new SyncMessage(seqKey, dateStr, value);
            String json = objectMapper.writeValueAsString(message);
            // 以 seqKey 作为 Partition Key，保证同一业务的消息有序
            kafkaTemplate.send(topic, seqKey, json);
            log.debug("Kafka 消息发送成功: topic={}, seqKey={}, date={}, value={}",
                    topic, seqKey, dateStr, value);
        } catch (Exception e) {
            log.warn("Kafka 消息发送失败（不影响主流程）: seqKey={}, date={}, value={}, error={}",
                    seqKey, dateStr, value, e.getMessage());
        }
    }

    @Override
    public void asyncSyncWaste(String seqKey, String sequence) {
        try {
            WasteMessage message = new WasteMessage(seqKey, sequence);
            String json = objectMapper.writeValueAsString(message);
            // 以 seqKey 作为 Partition Key，保证同一业务的消息有序
            kafkaTemplate.send(wasteTopic, seqKey, json);
            log.debug("Kafka 废号记录发送成功: topic={}, seqKey={}, sequence={}",
                    wasteTopic, seqKey, sequence);
        } catch (Exception e) {
            log.warn("Kafka 废号记录发送失败: seqKey={}, sequence={}, error={}",
                    seqKey, sequence, e.getMessage());
        }
    }

    /**
     * Kafka 消息体，序列化为 JSON 传输。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncMessage {
        private String seqKey;
        private String dateStr;
        private long value;
    }

    /**
     * Kafka 废号消息体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WasteMessage {
        private String seqKey;
        private String sequence;
    }
}
