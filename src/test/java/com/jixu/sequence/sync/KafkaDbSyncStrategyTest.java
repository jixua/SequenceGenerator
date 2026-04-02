package com.jixu.sequence.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class KafkaDbSyncStrategyTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaDbSyncStrategy strategy;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        strategy = new KafkaDbSyncStrategy(kafkaTemplate, "test-topic", "test-waste-topic");
    }

    @Test
    void testAsyncSync() {
        strategy.asyncSync("ORDER", "2026-03-20", 15L);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("test-topic"), eq("ORDER"), captor.capture());
        assertTrue(captor.getValue().contains("\"value\":15"));
    }

    @Test
    void testAsyncSyncWaste() {
        strategy.asyncSyncWaste("PAY", "PAY12345");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("test-waste-topic"), eq("PAY"), captor.capture());
        assertTrue(captor.getValue().contains("\"sequence\":\"PAY12345\""));
    }
}
