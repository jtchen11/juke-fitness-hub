package com.gym.entity;

import java.time.LocalDateTime;

public class PointsRedemption {
    private Long id;
    private Long memberId;
    private Integer pointsSpent;
    private Long rewardId;
    private String redemptionType;
    private String status;
    private Long adminId;
    private String adminRemark;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Integer getPointsSpent() { return pointsSpent; }
    public void setPointsSpent(Integer pointsSpent) { this.pointsSpent = pointsSpent; }
    public Long getRewardId() { return rewardId; }
    public void setRewardId(Long rewardId) { this.rewardId = rewardId; }
    public String getRedemptionType() { return redemptionType; }
    public void setRedemptionType(String redemptionType) { this.redemptionType = redemptionType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public String getAdminRemark() { return adminRemark; }
    public void setAdminRemark(String adminRemark) { this.adminRemark = adminRemark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
