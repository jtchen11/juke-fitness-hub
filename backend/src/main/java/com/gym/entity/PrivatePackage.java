package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("private_package")
public class PrivatePackage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String type;
    private String typeLabel;
    private Integer sessions;
    private Integer validDays;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Boolean isActive;
    private Integer sortOrder;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}