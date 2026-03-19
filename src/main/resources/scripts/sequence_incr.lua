-- Redis Lua 脚本：原子自增 + 自动过期
-- 保证 INCR 和 EXPIRE 在同一个原子操作中执行，避免 Key 永不过期的风险
--
-- KEYS[1]: 序列号 Key (例如 seq:JX:20260319)
-- ARGV[1]: 过期时间 (秒，建议设置为 172800，即 48 小时)
--
-- 返回值: 自增后的序列号值

local current = redis.call('INCR', KEYS[1])
if current == 1 then
    -- 仅在 Key 首次创建时设置过期时间
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
