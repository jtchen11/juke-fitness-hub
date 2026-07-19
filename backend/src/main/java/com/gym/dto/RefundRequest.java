package com.gym.dto;

import lombok.Data;

@Data
public class RefundRequest {
    private Long packageId;      // 课程包ID
    private Long memberId;       // 会员ID
    private String reason;       // 退款原因
}