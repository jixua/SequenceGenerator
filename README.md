# SequenceGenerator 接口文档

[SequenceGenerator](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/SequenceGenerator.java#26-84) 是高并发有序顺序号生成中间件的核心对外暴露接口。调用方只需注入此接口即可获取顺序号，无需关心底层 Redis / DB 的状态机流转与降级逻辑。

接口根据业务需求分为**两大模式**，共计 6 个核心方法：

1. **日期模式（完整 ID）**：`业务前缀 + 日期 + 补齐序号`，每日重置。
2. **永久模式（纯序号）**：仅需`递增序号`，永不重置，永久无极递增。

---

## 一、 日期模式（每日重置的完整 ID）

**核心特征：**
* **返回格式**：`{前缀}{yyyyMMdd}{补齐序号}` (例如: `ORDER20260319001`)
* **重置周期**：**按天重置**（每天的序号从头开始计算）
* **底层机制 (Redis)**：Key = `seq:{seqKey}:{yyyyMMdd}`，带有过期时间 (TTL, 默认48小时)。
* **底层机制 (DB降级)**：主键 `curr_date` 为当天实际日期（如 `2026-03-19`）。

### 1. [nextId(String seqKey)](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/SequenceGenerator.java#30-37)
**功能**：使用**指定**的业务前缀生成下一个完整的带日期的顺序号。
```java
/**
 * @param seqKey 业务标识（如 "ORDER", "USER"）
 * @return 例子：ORDER20260319001
 */
String nextId(String seqKey);
```

### 2. [nextId()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/SequenceGenerator.java#30-37)
**功能**：使用**默认**业务前缀生成下一个完整的带日期的顺序号。（默认前缀在 `application.yml` 中通过 `sequence.generator.prefix` 配置）。
```java
/**
 * @return 例子：SEQ20260319001
 */
String nextId();
```

---

## 二、 永久模式（不按日重置的纯递增序号）

**核心特征：**
* **返回格式**：纯数字递增序号。可以定长补齐为字符串 (如 `"001"`)，也可以是原始数值 (如 `1L`)。
* **重置周期**：**永不重置，永久递增**。
* **底层机制 (Redis)**：Key = `seq:{seqKey}`，**无日期后缀，不设置过期时间 (TTL)**。
* **底层机制 (DB降级)**：主键 `curr_date` 使用**固定的哨兵日期 `9999-12-31`**，确保与日期模式共享同一张表的结构而互相不干扰。

### 3. [nextValue(String seqKey)](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#78-82)
**功能**：使用**指定**的业务标识，生成**永久递增、并在左侧定长补齐**的字符串序号。
```java
/**
 * @param seqKey 业务标识（如 "USER_SN"）
 * @return 例子："001", "002" ... "999", "1000"
 */
String nextValue(String seqKey);
```

### 4. [nextValue()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#78-82)
**功能**：使用**默认**的业务标识，生成**永久递增并定长补齐**的字符串序号。
```java
/**
 * @return 例子："001"
 */
String nextValue();
```

### 5. [nextRawValue(String seqKey)](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#95-100)
**功能**：使用**指定**的业务标识，获取**最原始的永久递增 long 类型数值**。
**适用场景**：调用方不需要中间件进行格式化，而是希望拿到 `long` 数值后用自己的特定规则进行组合转换（如转化为36进制，或者进行雪花算法的位移）。
```java
/**
 * @param seqKey 业务标识（如 "USER_SN"）
 * @return 例子：1, 2, 3...
 */
long nextRawValue(String seqKey);
```

### 6. [nextRawValue()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#95-100)
**功能**：使用**默认**的业务标识，获取最原始的永久递增 `long` 类型数值。
```java
/**
 * @return 例子：1, 2, 3...
 */
long nextRawValue();
```

---

## 三、 使用示例汇总

假设在 `application.yml` 中的配置如下：
```yaml
sequence:
  generator:
    prefix: JX        # 默认前缀
    seq-length: 4     # 补齐长度 4 位
```

在 2026 年 3 月 19 日当天的调用结果对比：

| 调用方法 | 内部业务标识 | 是否带有日期 | 是否按天重置 | 输出结果示例 |
|---|---|---|---|---|
| [nextId("ORDER")](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/SequenceGenerator.java#30-37) | `ORDER` | ✅ 是 (`20260319`) | ✅ 是 | `"ORDER202603190001"` |
| [nextId()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/SequenceGenerator.java#30-37) | `JX` (默认) | ✅ 是 (`20260319`) | ✅ 是 | `"JX202603190001"` |
| [nextValue("VIP")](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#78-82)| `VIP` | ❌ 否 | ❌ 否 (永久递增) | `"0001"` (第1次), `"0002"` |
| [nextValue()](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#78-82) | `JX` (默认) | ❌ 否 | ❌ 否 (永久递增) | `"0001"` (第1次), `"0002"` |
| [nextRawValue("PAY")](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#95-100)|`PAY` | ❌ 否 | ❌ 否 (永久递增) | `1` (类型为 long), `2` |

---

## 四、 底层数据存储对照参考

为您排查问题时参考：

**Redis 层面：**
* 完整 ID 对应的 Redis Key: `seq:ORDER:20260319` (有 TTL 过期时间)
* 纯序号 对应的 Redis Key: `seq:VIP` (无过期时间，永不失效)

**MySQL DB 降级层面 (`sys_sequence` 表)：**
| seq_key | curr_date | curr_value | version | 说明 |
|---------|-----------|------------|---------|------|
| ORDER   | 2026-03-19 | 15         | 15      | [nextId("ORDER")](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/SequenceGenerator.java#30-37) 产生的降级/同步记录 |
| VIP     | 9999-12-31 | 582        | 582     | [nextValue("VIP")](file:///Users/jixu/Project/Java/SequenceGenerator/src/main/java/com/yourorg/sequence/core/StateMachineManager.java#78-82) 产生的降级/同步记录，日期为固定哨兵值 |
