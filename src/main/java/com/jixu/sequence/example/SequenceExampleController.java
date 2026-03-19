package com.jixu.sequence.example;

import com.jixu.sequence.core.SequenceGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 序列号生成器使用示例 Controller。
 * <p>
 * ⚠️ 此类仅为使用示例，不属于 Starter 本身。
 * 使用方项目中只需注入 {@link SequenceGenerator} 接口即可。
 *
 * <pre>
 * GET /sequence/next            → 使用默认前缀生成
 * GET /sequence/next/ORDER      → 使用 "ORDER" 前缀生成
 * GET /sequence/batch/ORDER/10  → 批量生成 10 个
 * GET /sequence/stress/ORDER/100 → 压力测试: 100 并发
 * </pre>
 */
@RestController
@RequestMapping("/sequence")
public class SequenceExampleController {

    @Autowired
    private SequenceGenerator sequenceGenerator;

    /**
     * 使用默认前缀生成单个序列号。
     */
    @GetMapping("/next")
    public String nextDefault() {
        return sequenceGenerator.nextId();
    }

    /**
     * 使用指定业务前缀生成单个序列号。
     */
    @GetMapping("/next/{seqKey}")
    public String next(@PathVariable String seqKey) {
        return sequenceGenerator.nextId(seqKey);
    }

    /**
     * 批量生成指定数量的序列号。
     */
    @GetMapping("/batch/{seqKey}/{count}")
    public List<String> batch(@PathVariable String seqKey, @PathVariable int count) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(sequenceGenerator.nextId(seqKey));
        }
        return result;
    }

    /**
     * 并发压力测试：模拟多线程同时请求序列号。
     *
     * @param seqKey      业务前缀
     * @param concurrency 并发线程数
     * @return 所有生成的序列号（验证无重复、有序）
     */
    @GetMapping("/stress/{seqKey}/{concurrency}")
    public List<String> stressTest(@PathVariable String seqKey,
                                   @PathVariable int concurrency) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(concurrency);
        List<Future<String>> futures = new ArrayList<>(concurrency);

        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await(); // 所有线程一起起跑
                return sequenceGenerator.nextId(seqKey);
            }));
        }

        List<String> results = new ArrayList<>(concurrency);
        for (Future<String> f : futures) {
            results.add(f.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();
        results.sort(String::compareTo);
        return results;
    }
}
