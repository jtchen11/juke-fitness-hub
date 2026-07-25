package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("group_class")
public class GroupClass {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long trainerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer maxCapacity;
    private Integer enrolled;
    private String status;
    private String type;  // paid / free

    // ====== 新增：课程价格 ======
    private BigDecimal price;

    @TableField(exist = false)
    private String trainerName;
    private String description;
    private String difficulty;
    private String classroom;
    private String coverImage;  // 课程介绍
    private Boolean allowVisitor;
    private String checkinCode;  // 6位随机签到码
    private LocalDateTime codeGeneratedAt;  // 签到码生成时间 // 是否允许准会员预约
}