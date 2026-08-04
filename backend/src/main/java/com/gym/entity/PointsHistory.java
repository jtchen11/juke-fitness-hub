package com.gym.entity;

import java.time.LocalDateTime;

public class PointsHistory {
    private Long id;
    private Long memberId;
    private Integer points;      // 变动积分（正=增加，负=扣减）
    private Integer balance;     // 变动后余额
    private String changeType;   // 变动类型（NORMAL_TRAINING/CLASS_CHECKIN/PT_COMPLETED/competition_reward/redemption 等）
    private String sourceId;     // 来源ID（签到记录/课程ID/比赛ID/商品ID）
    private String description;  // 变动说明
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
