package com.jixu.sequence.sync;

/**
 * DB 异步同步策略接口。
 * <p>
 * 正常态下，每次从 Redis 取得序列号后，通过此接口异步将最新值同步到 DB，
 * 使 DB 始终持有接近最新的序列号，确保 Redis 宕机后降级使用 DB 时不会产生回退。
 * <p>
 * 提供两种实现：
 * <ul>
 *   <li>{@code ThreadPoolDbSyncStrategy}  — 内置线程池，异步提交写 DB 任务</li>
 *   <li>{@code KafkaDbSyncStrategy}       — 向 Kafka Topic 发布消息，消费者异步写 DB</li>
 * </ul>
 */
public interface DbSyncStrategy {

    /**
     * 提交一次异步同步任务，将序列号的最新值同步到 DB。
     * <p>
     * 此方法应立即返回，不阻塞调用线程。同步失败时仅记录日志，不抛出异常。
     *
     * @param seqKey  业务标识（如 "ORDER"）
     * @param dateStr DB 中使用的日期字符串（按日模式为 yyyy-MM-dd，永久模式为 9999-12-31）
     * @param value   当前已生成的最新序列号值
     */
    void asyncSync(String seqKey, String dateStr, long value);
}
