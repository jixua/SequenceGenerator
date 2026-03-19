package com.jixu.sequence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全局顺序号生成表实体类。
 * <p>
 * 对应数据库表 {@code sys_sequence}，采用 (seq_key, curr_date) 作为联合主键。
 */
@Data
@TableName("sys_sequence")
public class SysSequence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务标识，如：JX, ORDER */
    private String seqKey;

    /** 当前日期 */
    private LocalDate currDate;

    /** 当前已使用的最大序列号 */
    private Integer currValue;

    /** 乐观锁版本号 */
    private Integer version;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
