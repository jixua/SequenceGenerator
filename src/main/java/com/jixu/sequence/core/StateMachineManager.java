package com.jixu.sequence.core;

import com.jixu.sequence.config.SequenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 核心状态机调度器 — 整个中间件的"大脑"。
 * <p>
 * 真实架构：
 * <ul>
 *   <li><b>Redis 是主</b>：正常态下所有序号由 Redis INCR 原子生成（无锁，性能最高）。</li>
 *   <li><b>DB 是异步备份</b>：每次 Redis 自增成功后，通过异步策略（线程池 / Kafka）
 *       将最新值刷入 DB，使 DB 始终持有接近最新的序号。</li>
 *   <li><b>降级（FAILOVER）</b>：Redis 宕机后，流量切换到 DB 乐观锁自增。
 *       由于 DB 有持续同步的最新值，降级后不会出现序号回退。</li>
 *   <li><b>恢复（RECOVERING）</b>：探活发现 Redis 重新连通后，读取 DB 最大值初始化
 *       Redis 计数器，通过分布式锁 + 双重检查保证并发安全。</li>
 * </ul>
 *
 * <pre>
 * ┌─────────┐   Redis 异常    ┌──────────┐   探活成功   ┌────────────┐
 * │ NORMAL  │ ──────────────→ │ FAILOVER │ ───────────→ │ RECOVERING │
 * └─────────┘                 └──────────┘              └────────────┘
 *      ↑  ←———— Redis INCR + asyncSync(DB) ————          恢复完成 |
 *      └───────────── DB.readMax → Redis.SET ────────────────────────┘
 * </pre>
 */
@Slf4j
public class StateMachineManager implements SequenceGenerator {

    private static final DateTimeFormatter DATE_FORMATTER     = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FORMATTER  = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String REDIS_KEY_PREFIX         = "seq:";
    private static final String RECOVERY_LOCK_PREFIX     = "sequence_recovery_lock:";

    /** DB 中永久序号使用的哨兵日期（不按日重置） */
    private static final String PERSISTENT_DB_DATE = "9999-12-31";

    /** 全局系统状态（volatile 保证多线程可见性） */
    private volatile SequenceState systemStatus = SequenceState.NORMAL;

    /** 降级模式请求计数器，用于探活触发判断 */
    private final AtomicInteger failoverCounter = new AtomicInteger(0);

    private final RedisSequenceGenerator redisGenerator;
    private final DbSequenceGenerator    dbGenerator;
    private final RedissonClient         redisson;
    private final SequenceProperties     properties;

    public StateMachineManager(RedisSequenceGenerator redisGenerator,
                               DbSequenceGenerator dbGenerator,
                               RedissonClient redisson,
                               SequenceProperties properties) {
        this.redisGenerator = redisGenerator;
        this.dbGenerator    = dbGenerator;
        this.redisson       = redisson;
        this.properties     = properties;
    }

    // ==================== 对外接口：完整 ID（前缀 + 日期 + 序号，每日重置） ====================

    @Override
    public String nextId() {
        return nextId(properties.getPrefix());
    }

    @Override
    public String nextId(String seqKey) {
        String dateStr    = LocalDate.now().format(DATE_FORMATTER);
        String dbDateStr  = LocalDate.now().format(DB_DATE_FORMATTER);
        String redisKey   = REDIS_KEY_PREFIX + seqKey + ":" + dateStr;
        long value = dispatch(seqKey, redisKey, dbDateStr, false);
        return buildFullId(seqKey, dateStr, value);
    }

    // ==================== 对外接口：纯序号（永久递增，不按日重置） ====================

    @Override
    public String nextValue() {
        return nextValue(properties.getPrefix());
    }

    @Override
    public String nextValue(String seqKey) {
        String redisKey = REDIS_KEY_PREFIX + seqKey;
        long value = dispatch(seqKey, redisKey, PERSISTENT_DB_DATE, true);
        return formatSeqNumber(value);
    }

    @Override
    public long nextRawValue() {
        return nextRawValue(properties.getPrefix());
    }

    @Override
    public long nextRawValue(String seqKey) {
        String redisKey = REDIS_KEY_PREFIX + seqKey;
        return dispatch(seqKey, redisKey, PERSISTENT_DB_DATE, true);
    }

    // ==================== 废号记录与补偿 ====================

    @Override
    public void reportWaste(String sequence) {
        reportWaste(properties.getPrefix(), sequence);
    }

    @Override
    public void reportWaste(String seqKey, String sequence) {
        if (sequence == null || sequence.trim().isEmpty()) {
            return;
        }
        log.info("接受到废号上报，开始异步记录: seqKey={}, sequence={}", seqKey, sequence);
        dbGenerator.asyncSyncWasteToDb(seqKey, sequence);
    }

    // ==================== 核心调度（状态机） ====================

    /**
     * 统一调度入口。
     *
     * @param seqKey     业务标识
     * @param redisKey   Redis Key（日期模式含日期后缀，永久模式不含）
     * @param dbDateStr  DB 查询用日期（日期模式为 yyyy-MM-dd，永久模式为哨兵 9999-12-31）
     * @param persistent 是否为永久模式（true 时 Redis 不设 TTL）
     * @return 原始序号值
     */
    private long dispatch(String seqKey, String redisKey, String dbDateStr, boolean persistent) {

        // ── 1. 正常态：Redis INCR + 异步刷 DB ──
        if (systemStatus == SequenceState.NORMAL) {
            try {
                long value = redisIncr(redisKey, persistent);
                // 异步将最新值同步到 DB（fire-and-forget，失败仅记录日志）
                dbGenerator.asyncSyncToDb(seqKey, dbDateStr, value);
                return value;
            } catch (Exception e) {
                log.error("Redis 执行失败，触发降级 → FAILOVER | seqKey={}, persistent={}", seqKey, persistent, e);
                systemStatus = SequenceState.FAILOVER;
                failoverCounter.set(0);
            }
        }

        // ── 2. 降级 / 恢复状态处理 ──
        if (systemStatus == SequenceState.FAILOVER || systemStatus == SequenceState.RECOVERING) {
            RLock recoveryLock = redisson.getLock(RECOVERY_LOCK_PREFIX + seqKey);

            // 2.1 RECOVERING 态：阻塞等待恢复线程完成，然后 DCL 走 Redis
            if (systemStatus == SequenceState.RECOVERING) {
                return waitForRecovery(recoveryLock, seqKey, redisKey, dbDateStr, persistent);
            }

            // 2.2 FAILOVER 态：每 N 次请求探活一次
            if (systemStatus == SequenceState.FAILOVER
                    && failoverCounter.incrementAndGet() % properties.getRecoveryInterval() == 0) {
                Long recovered = tryRecovery(recoveryLock, seqKey, redisKey, dbDateStr, persistent);
                if (recovered != null) {
                    return recovered;
                }
            }

            // 2.3 降级底线：走 DB 乐观锁自增
            long dbValue = dbGenerator.generateDbSequence(seqKey, dbDateStr);
            log.debug("降级 DB 序列号: seqKey={}, date={}, value={}", seqKey, dbDateStr, dbValue);
            return dbValue;
        }

        throw new IllegalStateException("未知的系统状态: " + systemStatus);
    }

    // ==================== Redis 操作适配 ====================

    private long redisIncr(String redisKey, boolean persistent) {
        return persistent
                ? redisGenerator.incrementPersistent(redisKey)
                : redisGenerator.increment(redisKey);
    }

    // ==================== 恢复流程 ====================

    /**
     * RECOVERING 态时，阻塞等待恢复锁释放后，DCL 走 Redis。
     * 若 Redis 仍不可用，则降级走 DB。
     */
    private long waitForRecovery(RLock recoveryLock, String seqKey,
                                  String redisKey, String dbDateStr, boolean persistent) {
        recoveryLock.lock();
        try {
            // DCL：恢复完成后状态变为 NORMAL → 直接走 Redis
            if (systemStatus == SequenceState.NORMAL) {
                long value = redisIncr(redisKey, persistent);
                dbGenerator.asyncSyncToDb(seqKey, dbDateStr, value);
                return value;
            }
        } catch (Exception e) {
            log.warn("等待恢复后重试 Redis 失败，降级走 DB | seqKey={}", seqKey, e);
        } finally {
            recoveryLock.unlock();
        }
        return dbGenerator.generateDbSequence(seqKey, dbDateStr);
    }

    /**
     * FAILOVER 态时，尝试非阻塞获取恢复锁，执行探活 → 同步 DB→Redis → 切 NORMAL。
     * <p>
     * 使用 tryLock（非阻塞）：
     * <ul>
     *   <li>拿到锁 → 探活 → 从 DB 读最大值 → 初始化 Redis → 切回 NORMAL</li>
     *   <li>未拿到锁 → 其他线程正在恢复 → 当前线程直接走 DB</li>
     * </ul>
     *
     * @return 恢复后的序列号值；null 表示未恢复（当前线程应继续走 DB）
     */
    private Long tryRecovery(RLock recoveryLock, String seqKey,
                              String redisKey, String dbDateStr, boolean persistent) {
        if (!recoveryLock.tryLock()) {
            return null; // 其他线程正在恢复
        }

        try {
            // DCL 2：拿锁后再确认状态，防止重复初始化
            if (systemStatus == SequenceState.NORMAL) {
                long value = redisIncr(redisKey, persistent);
                dbGenerator.asyncSyncToDb(seqKey, dbDateStr, value);
                return value;
            }

            // 探活 Redis
            if (!redisGenerator.isAlive()) {
                return null; // Redis 仍未恢复
            }

            log.info("检测到 Redis 恢复，开始同步数据... | seqKey={}, persistent={}", seqKey, persistent);
            systemStatus = SequenceState.RECOVERING; // 阻塞后续线程（令其等待恢复锁）

            // ─ 关键步骤：从 DB 读取最大值，初始化 Redis ─
            // DB 由于一直在异步同步，此时持有接近最新的序号
            long maxDbValue = dbGenerator.readMaxFromDb(seqKey, dbDateStr);
            if (persistent) {
                redisGenerator.syncFromDbPersistent(redisKey, maxDbValue);
            } else {
                redisGenerator.syncFromDb(redisKey, maxDbValue);
            }

            systemStatus = SequenceState.NORMAL; // 同步完成，切回正常态
            log.info("Redis 恢复完成，计数器已从 DB 最大值 {} 初始化 | seqKey={}", maxDbValue, seqKey);

            // 本线程也从 Redis 取一个号
            long value = redisIncr(redisKey, persistent);
            dbGenerator.asyncSyncToDb(seqKey, dbDateStr, value);
            return value;

        } catch (Exception e) {
            log.error("恢复同步过程发生异常，保持降级状态 | seqKey={}", seqKey, e);
            systemStatus = SequenceState.FAILOVER;
        } finally {
            recoveryLock.unlock();
        }
        return null;
    }

    // ==================== 格式化工具 ====================

    private String buildFullId(String seqKey, String dateStr, long value) {
        return seqKey + dateStr + formatSeqNumber(value);
    }

    private String formatSeqNumber(long value) {
        String format = "%0" + properties.getSeqLength() + "d";
        return String.format(format, value);
    }
}
