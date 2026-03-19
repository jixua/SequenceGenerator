package com.jixu.sequence.core;

/**
 * 序列号生成器状态枚举。
 * <p>
 * 维护在内存中的全局状态，控制请求路由：
 * <ul>
 *   <li>NORMAL   - 正常态，流量全部走 Redis Lua 脚本原子自增</li>
 *   <li>FAILOVER - 降级态，Redis 不可用时切换到 DB 乐观锁</li>
 *   <li>RECOVERING - 恢复态，Redis 恢复后同步 DB 数据到 Redis 的短暂中间态</li>
 * </ul>
 */
public enum SequenceState {

    /** 正常 Redis 模式 */
    NORMAL,

    /** 降级 DB 模式 */
    FAILOVER,

    /** 恢复同步模式 */
    RECOVERING
}
