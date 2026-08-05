package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("trainer_leave")
public class TrainerLeave {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long trainerId;
    private LocalDate leaveDate;
    private String reason;
    private String status;  // pending, approved, rejected
    private String period;    // full_day, morning, afternoon
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String trainerName;
}