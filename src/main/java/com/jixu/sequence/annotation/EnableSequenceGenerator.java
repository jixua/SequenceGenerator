package com.jixu.sequence.annotation;

import com.jixu.sequence.config.SequenceAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用序列号生成器中间件。
 * <p>
 * 在 Spring Boot 应用主类上添加此注解，手动触发自动装配。
 * 若已通过 {@code spring.factories} 自动装配，则此注解为可选。
 *
 * <pre>
 * &#064;SpringBootApplication
 * &#064;EnableSequenceGenerator
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SequenceAutoConfiguration.class)
public @interface EnableSequenceGenerator {
}
