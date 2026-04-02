package com.jixu.sequence.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 废弃序列号记录实体。
 * <p>
 * 用于记录由于业务异常或者其他原因未能成功使用的序列号，
 * 保证数据流的完整性追踪。
 */
@Data
public class SysSequenceWaste {

    /** 业务标识（如 JX, ORDER） */
    private String seqKey;

    /** 废弃的具体序列号（包含前缀和日期的完整 ID 或 纯序号） */
    private String wasteSequence;

    /** 废号记录的时间 */
    private LocalDateTime createTime;

}
