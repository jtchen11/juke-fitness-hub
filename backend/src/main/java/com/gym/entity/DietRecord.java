package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("diet_record")
public class DietRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate recordDate;

    private String mealType;
    private String foodName;
    private String quantity;
    private Integer calories;
    private Double protein;
    private Double fat;
    private Double carbs;
    private String notes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String memberName;
}
