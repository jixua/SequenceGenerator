package com.jixu.sequence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 虚拟的 Spring Boot 启动类，专为集成测试提供上下文加载入口。
 * <p>
 * 因为当前项目只是一个无 Main 方法的 Starter 组件，
 * 所以 @SpringBootTest 在运行时需要这样一个配置类来扫描并启动容器。
 */
@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
