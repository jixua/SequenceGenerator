package com.jixu.sequence.core;

import com.jixu.sequence.config.SequenceProperties;
import com.jixu.sequence.entity.SysSequence;
import com.jixu.sequence.mapper.SysSequenceMapper;
import com.jixu.sequence.sync.DbSyncStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

/**
 * 数据库降级序列号生成器 + 异步同步入口。
 * <p>
 * 承担双重职责：
 * <ol>
 *   <li><b>降级生成</b>：Redis 宕机期间，通过乐观锁（CAS）在 DB 内自增序列号，
 *       保障服务可用性。</li>
 *   <li><b>异步同步</b>：正常态下，每次 Redis INCR 成功后，由此类将最新值
 *       通过 {@link DbSyncStrategy} 异步刷入 DB，使 DB 始终持有接近最新的值。</li>
 * </ol>
 */
@Slf4j
public class DbSequenceGenerator {

    @Getter
    private final SysSequenceMapper sequenceMapper;
    private final SequenceProperties properties;
    private final DbSyncStrategy syncStrategy;

    public DbSequenceGenerator(SysSequenceMapper sequenceMapper,
                                SequenceProperties properties,
                                DbSyncStrategy syncStrategy) {
        this.sequenceMapper = sequenceMapper;
        this.properties = properties;
        this.syncStrategy = syncStrategy;
    }

    // ==================== 正常态：异步同步 ====================

    /**
     * 正常态下调用：将 Redis 已生成的最新值异步同步到 DB。
     * <p>
     * 此方法立即返回，不阻塞调用线程。同步策略由配置决定（线程池 / Kafka）。
     *
     * @param seqKey  业务标识
     * @param dateStr DB 日期字段（按日模式为实际日期，永久模式为哨兵 9999-12-31）
     * @param value   Redis 当前已生成的最新序列号值
     */
    public void asyncSyncToDb(String seqKey, String dateStr, long value) {
        syncStrategy.asyncSync(seqKey, dateStr, value);
    }

    /**
     * 将废号记录请求通过异步策略推送到 DB。
     *
     * @param seqKey   业务标识
     * @param sequence 废号字符串
     */
    public void asyncSyncWasteToDb(String seqKey, String sequence) {
        syncStrategy.asyncSyncWaste(seqKey, sequence);
    }

    // ==================== 降级态：DB 乐观锁自增 ====================

    /**
     * 降级态下调用：通过乐观锁（CAS）在 DB 内自增并返回下一个序列号。
     * <p>
     * 流程：
     * <ol>
     *   <li>查询当前 (seqKey, dateStr) 记录</li>
     *   <li>若不存在，执行初始化插入（处理并发唯一索引冲突）</li>
     *   <li>计算 nextValue = currValue + 1</li>
     *   <li>CAS 更新：{@code UPDATE ... WHERE version = #{version}}</li>
     *   <li>更新成功返回 nextValue；失败则自旋重试</li>
     * </ol>
     *
     * @param seqKey  业务标识
     * @param dateStr 日期字符串（格式 yyyy-MM-dd）
     * @return 下一个序列号值
     */
    public long generateDbSequence(String seqKey, String dateStr) {
        int maxRetries = properties.getMaxRetry();

        for (int i = 0; i < maxRetries; i++) {
            SysSequence record = sequenceMapper.selectCurrent(seqKey, dateStr);

            if (record == null) {
                try {
                    sequenceMapper.insertInitRecord(seqKey, dateStr);
                    log.info("初始化序列号记录: seqKey={}, date={}", seqKey, dateStr);
                } catch (DuplicateKeyException e) {
                    log.debug("序列号记录已被其他线程初始化: seqKey={}, date={}", seqKey, dateStr);
                    continue;
                }
                record = sequenceMapper.selectCurrent(seqKey, dateStr);
            }

            if (record == null) {
                log.warn("查询序列号记录异常为 null，重试中: seqKey={}, date={}", seqKey, dateStr);
                continue;
            }

            long nextValue = record.getCurrValue() + 1L;
            int updatedRows = sequenceMapper.updateSequenceWithOptimisticLock(
                    seqKey, dateStr, (int) nextValue, record.getVersion());

            if (updatedRows > 0) {
                log.debug("DB 降级序列号生成成功: seqKey={}, date={}, value={}", seqKey, dateStr, nextValue);
                return nextValue;
            }

            log.debug("乐观锁冲突 (第{}次重试): seqKey={}, date={}", i + 1, seqKey, dateStr);
        }

        throw new RuntimeException(
                String.format("DB 序列号生成失败，已重试 %d 次: seqKey=%s, date=%s",
                        maxRetries, seqKey, dateStr));
    }

    // ==================== 恢复同步：读取 DB 最大值 ====================

    /**
     * 读取 DB 中当前最大序列号值（用于 Redis 恢复时初始化计数器）。
     *
     * @param seqKey  业务标识
     * @param dateStr 日期字符串
     * @return DB 中已持久化的最大序列号值；若无记录返回 0
     */
    public long readMaxFromDb(String seqKey, String dateStr) {
        SysSequence record = sequenceMapper.selectCurrent(seqKey, dateStr);
        return (record != null) ? record.getCurrValue() : 0L;
    }
}
