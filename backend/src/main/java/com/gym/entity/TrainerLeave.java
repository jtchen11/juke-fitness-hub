package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
    private LocalDateTime createdAt;
}