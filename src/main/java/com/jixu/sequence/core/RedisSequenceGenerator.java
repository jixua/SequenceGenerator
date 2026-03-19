package com.jixu.sequence.core;

import com.jixu.sequence.config.SequenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import javax.annotation.PostConstruct;
import java.util.Collections;

/**
 * Redis 序列号生成器。
 * <p>
 * 通过 Lua 脚本在 Redis 中执行原子自增操作（INCR + EXPIRE），
 * 保证高并发下的性能与一致性。
 */
@Slf4j
public class RedisSequenceGenerator {

    private final StringRedisTemplate stringRedisTemplate;
    private final SequenceProperties properties;

    private DefaultRedisScript<Long> incrScript;

    public RedisSequenceGenerator(StringRedisTemplate stringRedisTemplate,
                                  SequenceProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        incrScript = new DefaultRedisScript<>();
        incrScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/sequence_incr.lua")));
        incrScript.setResultType(Long.class);
        log.info("Redis Lua 脚本 [sequence_incr.lua] 加载完成");
    }

    /**
     * 执行 Redis Lua 脚本进行原子自增（带 TTL，用于按日重置的场景）。
     *
     * @param redisKey Redis Key，格式：seq:{seqKey}:{yyyyMMdd}
     * @return 自增后的序列号值
     */
    public Long increment(String redisKey) {
        return stringRedisTemplate.execute(
                incrScript,
                Collections.singletonList(redisKey),
                String.valueOf(properties.getExpireSeconds()));
    }

    /**
     * 永久自增（无 TTL，不按日重置）。
     * <p>
     * 用于纯序号模式，Key 永不过期，序号持续递增。
     *
     * @param redisKey Redis Key，格式：seq:{seqKey}
     * @return 自增后的序列号值
     */
    public Long incrementPersistent(String redisKey) {
        Long value = stringRedisTemplate.opsForValue().increment(redisKey);
        log.debug("Redis 永久自增 | key={}, value={}", redisKey, value);
        return value;
    }

    /**
     * 探测 Redis 是否存活。
     *
     * @return true 如果 Redis 正常响应 PONG
     */
    public boolean isAlive() {
        try {
            String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            log.debug("Redis 探活失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将 DB 最大值同步到 Redis（带 TTL，用于按日重置的场景）。
     *
     * @param redisKey   Redis Key
     * @param maxDbValue DB 当前最大序列值
     */
    public void syncFromDb(String redisKey, long maxDbValue) {
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(maxDbValue),
                properties.getExpireSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        log.info("Redis Key [{}] 已同步至 DB 最大值 (带TTL): {}", redisKey, maxDbValue);
    }

    /**
     * 将 DB 最大值同步到 Redis（无 TTL，用于永久递增的场景）。
     *
     * @param redisKey   Redis Key
     * @param maxDbValue DB 当前最大序列值
     */
    public void syncFromDbPersistent(String redisKey, long maxDbValue) {
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(maxDbValue));
        log.info("Redis Key [{}] 已同步至 DB 最大值 (永久): {}", redisKey, maxDbValue);
    }
}
