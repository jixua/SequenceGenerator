package com.jixu.sequence.core;

/**
 * 顶层序列号生成器接口。
 * <p>
 * 使用方只需注入此接口，即可透明地获取有序顺序号。
 * 底层路由（Redis / DB / 恢复同步）由 {@link StateMachineManager} 自动调度。
 * <p>
 * 提供两类方法：
 * <ul>
 *   <li>{@code nextId} — 返回完整 ID（业务前缀 + 日期 + 补齐序号），如 {@code ORDER20260319001}</li>
 *   <li>{@code nextValue} — 仅返回补齐后的纯序号部分，如 {@code 001}</li>
 * </ul>
 *
 * <pre>
 * &#064;Autowired
 * private SequenceGenerator sequenceGenerator;
 *
 * // 完整 ID: ORDER20260319001
 * String fullId = sequenceGenerator.nextId("ORDER");
 *
 * // 仅序号: 001
 * String seqOnly = sequenceGenerator.nextValue("ORDER");
 * </pre>
 */
public interface SequenceGenerator {

    // ==================== 完整 ID（前缀 + 日期 + 序号） ====================

    /**
     * 使用指定的业务标识生成下一个完整顺序号。
     *
     * @param seqKey 业务标识（如 "JX", "ORDER"）
     * @return 完整的顺序号字符串，格式：{seqKey}{yyyyMMdd}{补齐序号}
     */
    String nextId(String seqKey);

    /**
     * 使用默认业务前缀生成下一个完整顺序号。
     * <p>
     * 默认前缀通过 {@code sequence.generator.prefix} 配置，缺省为 "SEQ"。
     *
     * @return 完整的顺序号字符串
     */
    String nextId();

    // ==================== 纯序号（仅补齐后的递增数字） ====================

    /**
     * 使用指定的业务标识生成下一个纯序号。
     * <p>
     * 仅返回补齐后的递增数字部分，不含业务前缀和日期。
     * 例如：{@code "001"}, {@code "002"}, {@code "099"}
     *
     * @param seqKey 业务标识（如 "JX", "ORDER"）
     * @return 补齐后的纯序号字符串
     */
    String nextValue(String seqKey);

    /**
     * 使用默认业务前缀生成下一个纯序号。
     *
     * @return 补齐后的纯序号字符串
     */
    String nextValue();

    /**
     * 使用指定的业务标识获取下一个原始序号数值（不补齐）。
     * <p>
     * 返回自增后的原始 long 值，适用于调用方需要自行格式化的场景。
     *
     * @param seqKey 业务标识
     * @return 原始序号数值（如 1, 2, 3...）
     */
    long nextRawValue(String seqKey);

    /**
     * 使用默认业务前缀获取下一个原始序号数值（不补齐）。
     *
     * @return 原始序号数值
     */
    long nextRawValue();

    // ==================== 废号记录与补偿 ====================

    /**
     * 报告废弃的序列号（使用指定的业务标识），执行异步落库记录。
     * <p>
     * 适用于业务在获取序列号后发生异常（如数据库回滚），不再使用该序列号时，
     * 主动将此序列号存入废号表中，以保证数据链路可追溯。
     *
     * @param seqKey   业务标识（如 "ORDER"）
     * @param sequence 废弃的序列号完整凭证
     */
    void reportWaste(String seqKey, String sequence);

    /**
     * 报告废弃的序列号（使用默认配置前缀）。
     *
     * @param sequence 废弃的序列号完整凭证
     */
    void reportWaste(String sequence);
}
