package com.gym.ai.model;

public enum PaymentAction {
    NONE,               // 无支付相关操作，普通文本
    CONFIRM_PAYMENT,    // 等待用户点击“确认支付”
    SHOW_OPTIONS        // 展示支付方式选择（免费/课程包/单次）
}