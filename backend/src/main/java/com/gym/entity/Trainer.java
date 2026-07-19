package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("trainer")
public class Trainer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String phone;
    private String specialty;
    private String intro;
    private BigDecimal pricePerHour;
    private String status;  // active, vacation, resigned

    @TableField(exist = false)
    private Integer bookingCount;  // 暂时默认0
}