package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("competition")
public class Competition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String rules;          // 赛制说明
    private String imageUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime deadline;
    private Integer maxParticipants;
    private Integer enrolled;
    private String status;      // open / closed / cancelled
    private Boolean isActive;
    private Boolean rewardGranted;        // 奖励是否已发放（0=未发放 1=已发放）
    private Integer championPoints;       // 冠军积分
    private Integer runnerUpPoints;       // 亚军积分
    private Integer thirdPlacePoints;     // 季军积分
    private Integer participationPoints;  // 参与积分
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}