package com.jixu.sequence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 废号记录表 Mapper 接口。
 * <p>
 * 使用 MyBatis 注解方式定义 SQL。
 */
@Mapper
public interface SysSequenceWasteMapper {

    /**
     * 写入一条废号记录。
     * 
     * @param seqKey        业务标识
     * @param wasteSequence 废弃的序列号
     */
    @Insert("INSERT INTO sys_sequence_waste (seq_key, waste_sequence, create_time) " +
            "VALUES (#{seqKey}, #{wasteSequence}, NOW())")
    void insertWasteRecord(@Param("seqKey") String seqKey,
                           @Param("wasteSequence") String wasteSequence);

}
