package com.gym.ai.context;

public enum ConversationState {
    IDLE,               // 空闲，无进行中的流程
    PT_BOOKING,         // 正在预约私教（收集教练、日期、时间）
    GROUP_BOOKING,      // 正在预约团课（选择课程列表）
    WAITING_PAYMENT     // 等待用户确认支付（私教或团课）
}