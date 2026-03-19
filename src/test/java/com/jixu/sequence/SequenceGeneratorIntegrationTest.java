package com.jixu.sequence;

import com.jixu.sequence.core.SequenceGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 序列号生成器集成测试。
 * <p>
 * ⚠️ 运行前请确保 Redis 和 MySQL 服务已启动，
 * 并在 application.yml 中正确配置连接信息。
 */
@SpringBootTest(classes = TestApplication.class)
class SequenceGeneratorIntegrationTest {

    @Autowired
    private SequenceGenerator sequenceGenerator;

    /**
     * 测试：使用默认前缀生成序列号。
     */
    @Test
    void testNextIdWithDefaultPrefix() throws InterruptedException {
        String id = sequenceGenerator.nextId();

        assertNotNull(id, "生成的序列号不应为 null");
        assertTrue(id.length() > 0, "生成的序列号不应为空字符串");

        System.out.println("默认前缀序列号: " + id);
        Thread.sleep(500);

    }

    /**
     * 测试：使用自定义业务前缀生成序列号。
     */
    @Test
    void testNextIdWithCustomPrefix() {
        String id = sequenceGenerator.nextId("ORDER");

        assertNotNull(id);
        assertTrue(id.startsWith("ORDER"), "序列号应以 ORDER 开头");

        System.out.println("自定义前缀序列号: " + id);
    }

    /**
     * 测试：连续生成的序列号应严格递增。
     */
    @Test
    void testSequentialIncrement() {
        String seqKey = "TEST";
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            ids.add(sequenceGenerator.nextId(seqKey));
        }

        System.out.println("连续生成的序列号:");
        ids.forEach(System.out::println);

        // 验证严格递增
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i).compareTo(ids.get(i - 1)) > 0,
                    "序列号应严格递增: " + ids.get(i - 1) + " < " + ids.get(i));
        }
    }

    /**
     * 测试：并发场景下序列号不重复。
     */
    @Test
    void testConcurrentUniqueness() throws Exception {
        String seqKey = "CONCURRENT";
        int threadCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        List<Future<String>> futures = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.countDown();
                startLatch.await(); // 所有线程同时起跑
                return sequenceGenerator.nextId(seqKey);
            }));
        }

        Set<String> uniqueIds = new HashSet<>();
        for (Future<String> f : futures) {
            String id = f.get(10, TimeUnit.SECONDS);
            assertNotNull(id);
            assertTrue(uniqueIds.add(id), "发现重复序列号: " + id);
        }

        assertEquals(threadCount, uniqueIds.size(),
                "并发生成的序列号数量应等于线程数");

        executor.shutdown();

        System.out.println("并发测试通过，共生成 " + uniqueIds.size() + " 个唯一序列号");
    }
}
