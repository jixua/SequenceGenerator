package com.jixu.sequence.sync;

import com.jixu.sequence.mapper.SysSequenceMapper;
import com.jixu.sequence.mapper.SysSequenceWasteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

class ThreadPoolDbSyncStrategyTest {

    private SysSequenceMapper sequenceMapper;
    private SysSequenceWasteMapper wasteMapper;
    private ThreadPoolDbSyncStrategy strategy;

    @BeforeEach
    void setUp() {
        sequenceMapper = Mockito.mock(SysSequenceMapper.class);
        wasteMapper = Mockito.mock(SysSequenceWasteMapper.class);
        strategy = new ThreadPoolDbSyncStrategy(sequenceMapper, wasteMapper, 2, 4, 100, "test-");
    }

    @Test
    void testAsyncSync() {
        strategy.asyncSync("ORDER", "2026-03-20", 10L);
        // Wait asynchronously until method is invoked
        verify(sequenceMapper, timeout(1000)).upsertMaxValue("ORDER", "2026-03-20", 10L);
    }

    @Test
    void testAsyncSyncWaste() {
        strategy.asyncSyncWaste("PAY", "PAY20260320005");
        verify(wasteMapper, timeout(1000)).insertWasteRecord("PAY", "PAY20260320005");
    }
}
