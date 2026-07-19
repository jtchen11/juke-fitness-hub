package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("personal_training")
public class PersonalTraining {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long trainerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentTime;

    private Integer durationMinutes;
    private String status;
    private String notes;

    // 关联课程包
    private Long packageId;
    private Integer sessionIndex;
    private String packageName;
    private String cancelReason;

    // 新增：是否使用免费私教次数
    private Boolean isFree;   // 数据库字段，tinyint(1)

    // 前端传参用（非数据库字段）
    @TableField(exist = false)
    private Boolean useFree;

    @TableField(exist = false)
    private String memberName;

    @TableField(exist = false)
    private String trainerName;
}