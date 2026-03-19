# 序列号生成器 (Sequence Generator) API 和配置手册

## 1. 快速接入

在您的 [pom.xml](file:///Users/jixu/Project/Java/SequenceGenerator/pom.xml) 中引入由于本工程打出的 Starter 包后，无需提供任何 `@Bean`，所有的客户端都可以直接注入 [SequenceGenerator](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/DbSequenceGenerator.java#22-124)。

```java
import com.jixu.sequence.core.SequenceGenerator;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrderService {
    
    @Autowired
    private SequenceGenerator sequenceGenerator;
    
    public void createOrder() {
        // 使用默认前缀生成带日期的单号: JX20260319001
        String orderNo = sequenceGenerator.nextId();
    }
}
```

## 2. 接口定义

整个组件暴露了 **2 种模式**、共计 **6 个方法** 供业务使用。

### 2.1 日期重置模式 (完整 ID)

**特点**：按日重置序号，Redis Key 每天过期。
**存储结构**：`{前缀}{yyyyMMdd}{补齐序号}` (如："ORDER20260319001")

* [nextId(String seqKey)](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/StateMachineManager.java#68-72): 使用自定义的前缀和业务标识（如 `ORDER`）。
* [nextId()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/StateMachineManager.java#68-72): 使用 [application.yml](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/resources/application.yml) 里 `sequence.generator.prefix` 配置的默认前缀。

### 2.2 永久递增模式 (纯序号)

**特点**：Redis 序号永不过期，DB 中的日期采用统一的哨兵值 `9999-12-31`，序号长期累加。

* [nextValue(String seqKey)](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/StateMachineManager.java#84-88): 返回**补齐好**的定长字符串序号。如："001", "002"。
* [nextValue()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/StateMachineManager.java#84-88): 使用默认业务标识，返回补齐的序号字符串。
* [nextRawValue(String seqKey)](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/StateMachineManager.java#101-106): 返回**原始未格式化**的 `long` 类型的序号，如 `1L, 2L`。
* [nextRawValue()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/jixu/sequence/core/StateMachineManager.java#101-106): 使用默认标识返回原始 `long` 序号。

## 3. 核心配置 [application.yml](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/resources/application.yml) 参数一览

```yaml
sequence:
  generator:
    # ------------------ 基础配置 ------------------
    prefix: JX                      # 默认的业务前缀 (nextId() 等空参方法使用)
    seq-length: 3                   # 序号补全位数，不足会用 0 填充 (如 001)
    recovery-interval: 100          # DB 降级态下，每隔多少次请求探活一次 Redis 是否恢复
    expire-seconds: 172800          # Redis Key 默认的过期时间 (48小时)
    max-retry: 5                    # DB 乐观锁自增在遇到冲突时的最大重试次数
    
    # ------------------ DB 同步配置 ------------------
    # 负责控制 Redis 主数据每次自增后，如何异步传递给 MySQL 作为防抖备份。
    # 可选值: THREAD_POOL 或 KAFKA
    sync-mode: THREAD_POOL
    
    # sync-mode = THREAD_POOL 时的专有配置
    thread-pool:
      core-size: 2                  # 线程池常驻核心线程数
      max-size: 8                   # 线程池最大上限
      queue-capacity: 2000          # 任务队列容量。打满后将降级由调用者线程直接入库，防丢失
      thread-name-prefix: seq-db-sync- 
      
    # sync-mode = KAFKA 时的专有配置 (注意：使用此模式必须确保 pom.xml 引入了 spring-kafka)
    kafka:
      topic: sequence-db-sync       # 生产者投递消息、消费者监听的共同 Topic
      group-id: sequence-sync-consumer # 消费者属组
```

## 4. 数据库降级核心表结构 (DDL)

如果您尚未创建防灾同步表，请在您配置的被连入的 MySQL 执行如下脚本：

```sql
CREATE TABLE IF NOT EXISTS `sys_sequence` (
    `seq_key`     VARCHAR(50)  NOT NULL COMMENT '业务标识，如：JX, ORDER',
    `curr_date`   DATE         NOT NULL COMMENT '当前日期，如：2026-03-19',
    `curr_value`  INT          NOT NULL DEFAULT 0 COMMENT '当前已使用的最大序列号',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`seq_key`, `curr_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局顺序号生成表';
```
