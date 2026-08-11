package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("class_booking")
public class ClassBooking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long classId;
    private LocalDateTime bookingTime;
    private String status;  // booked / cancelled / checked_in

    // ====== 支付相关字段 ======
    private String paymentStatus;  // unpaid / paid / refunded / cancelled
    private BigDecimal paidAmount;
    private LocalDateTime payTime;

    // ====== 新增：课程结束时间（不映射数据库） ======
    @TableField(exist = false)
    private LocalDateTime endTime;

    // ====== 新增：课程名称（不映射数据库） ======
    @TableField(exist = false)
    private String className;
}