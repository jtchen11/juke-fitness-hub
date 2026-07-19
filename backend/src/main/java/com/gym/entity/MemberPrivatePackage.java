package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("member_private_package")
public class MemberPrivatePackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Integer packageId;
    private String packageName;
    private Long coachId;
    private Integer totalSessions;
    private Integer usedSessions;
    private Integer remainingSessions;
    private BigDecimal price;
    private Integer validDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;  // active/expired/refunded
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate activationDeadline;  // 激活截止日期
    private BigDecimal originalPrice;
    private BigDecimal refundAmount;   // 退款金额
    private String refundReason;       // 退款原因
    private LocalDateTime refundTime;  // 退款时间
}