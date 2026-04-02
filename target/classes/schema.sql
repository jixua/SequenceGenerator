-- ============================================================
-- 全局顺序号生成表 DDL (MySQL)
-- 联合主键：(seq_key, curr_date)
-- 乐观锁字段：version
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_sequence` (
    `seq_key`     VARCHAR(50)  NOT NULL COMMENT '业务标识，如：JX, ORDER',
    `curr_date`   DATE         NOT NULL COMMENT '当前日期，如：2026-03-19',
    `curr_value`  INT          NOT NULL DEFAULT 0 COMMENT '当前已使用的最大序列号',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`seq_key`, `curr_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局顺序号生成表';
