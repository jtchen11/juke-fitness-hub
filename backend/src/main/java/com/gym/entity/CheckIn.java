package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("check_in")
public class CheckIn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String checkInType;
    private Long classId;
    private String remark;
    @TableField(exist = false)
    private String memberName;   // 会员姓名

    @TableField(exist = false)
    private String className;    // 团课名称
    private Long ptId;  // 关联私教预约ID
    @TableField(exist = false)
    private String ptInfo;       // 私教信息（如“王教练 私教课（塑形包周）”）

    @TableField(exist = false)
    private Long durationMinutes;    // 自助训练时长（分钟）
    private Integer pointsEarned;    // 本次签到实际获得的积分（points_earned 列）
}