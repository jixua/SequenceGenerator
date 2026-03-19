package com.jixu.sequence.mapper;

import com.jixu.sequence.entity.SysSequence;
import org.apache.ibatis.annotations.*;

/**
 * 全局顺序号生成表 Mapper 接口。
 * <p>
 * 使用 MyBatis 注解方式定义 SQL，核心操作包括：
 * <ul>
 *   <li>查询当前序列号记录（用于降级读取最大值）</li>
 *   <li>Upsert 写入最新值（用于异步同步 + 恢复同步）</li>
 *   <li>乐观锁自增（用于 Redis 宕机降级期间生成序号）</li>
 *   <li>初始化插入新记录</li>
 * </ul>
 */
@Mapper
public interface SysSequenceMapper {

    /**
     * 查询指定业务标识和日期的当前序列号记录。
     *
     * @param seqKey  业务标识
     * @param dateStr 日期字符串，格式 yyyy-MM-dd
     * @return 序列号记录，不存在时返回 null
     */
    @Select("SELECT seq_key, curr_date, curr_value, version, update_time " +
            "FROM sys_sequence " +
            "WHERE seq_key = #{seqKey} AND curr_date = #{dateStr}")
    @Results({
            @Result(column = "seq_key",     property = "seqKey"),
            @Result(column = "curr_date",   property = "currDate"),
            @Result(column = "curr_value",  property = "currValue"),
            @Result(column = "version",     property = "version"),
            @Result(column = "update_time", property = "updateTime")
    })
    SysSequence selectCurrent(@Param("seqKey") String seqKey, @Param("dateStr") String dateStr);

    /**
     * 初始化插入一条新的序列号记录（首次访问时使用）。
     * <p>
     * 并发插入时若出现唯一索引冲突（DuplicateKeyException），调用方应捕获后重试。
     */
    @Insert("INSERT INTO sys_sequence (seq_key, curr_date, curr_value, version) " +
            "VALUES (#{seqKey}, #{dateStr}, 0, 0)")
    void insertInitRecord(@Param("seqKey") String seqKey, @Param("dateStr") String dateStr);

    /**
     * 异步同步：将 Redis 已生成的最新序列号值写入 DB （UPSERT + GREATEST）。
     * <p>
     * 使用 {@code GREATEST} 保证只写大值，适配并发下消息/任务乱序到达的情况。
     * 若记录不存在则插入；若记录存在且新值更大则更新，否则静默跳过。
     *
     * @param seqKey  业务标识
     * @param dateStr 日期字符串，格式 yyyy-MM-dd
     * @param value   当前已生成的最新序列号值
     */
    @Insert("INSERT INTO sys_sequence (seq_key, curr_date, curr_value, version) " +
            "VALUES (#{seqKey}, #{dateStr}, #{value}, #{value}) " +
            "ON DUPLICATE KEY UPDATE " +
            "  curr_value = GREATEST(curr_value, #{value}), " +
            "  version    = GREATEST(curr_value, #{value}), " +
            "  update_time = NOW()")
    void upsertMaxValue(@Param("seqKey") String seqKey,
                        @Param("dateStr") String dateStr,
                        @Param("value") long value);

    /**
     * 乐观锁自增（Redis 降级期间使用）。
     * <p>
     * CAS 策略：仅当 version 与预期一致时才更新，同时将 version 自增 1。
     * 若返回值为 0，则表示乐观锁冲突，调用方应自旋重试。
     */
    @Update("UPDATE sys_sequence " +
            "SET curr_value = #{newValue}, version = version + 1 " +
            "WHERE seq_key = #{seqKey} AND curr_date = #{dateStr} AND version = #{version}")
    int updateSequenceWithOptimisticLock(@Param("seqKey") String seqKey,
                                         @Param("dateStr") String dateStr,
                                         @Param("newValue") int newValue,
                                         @Param("version") int version);
}
