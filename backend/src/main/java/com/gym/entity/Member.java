package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("member")
public class Member {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String phone;
    private String gender;
    private LocalDate birthday;
    private String level;
    private LocalDate expireDate;
    private BigDecimal height;
    private BigDecimal weight;
    private LocalDateTime createdAt;
    private Integer freePtUsedMonth;    // 本月已用免费私教次数
    private LocalDate freePtMonthReset; // 免费次数重置月份（每月1日更新）
    private Boolean experienceUsed; // 是否已使用体验课
    private Integer points; // 当前积分

    public boolean isActiveMember() {
        return expireDate != null && !expireDate.isBefore(java.time.LocalDate.now());
    }

    public boolean isVisitor() {
        return expireDate == null;
    }

    public boolean isExpired() {
        return expireDate != null && expireDate.isBefore(java.time.LocalDate.now());
    }
}