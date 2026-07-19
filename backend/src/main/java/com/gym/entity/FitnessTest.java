package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("fitness_test")
public class FitnessTest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private LocalDate testDate;
    private BigDecimal weightKg;
    private BigDecimal bodyFatPercent;
    private BigDecimal muscleMassKg;
    private String remarks;

    // ========== 新增：临时字段，不映射数据库 ==========
    @TableField(exist = false)
    private String memberName;
}