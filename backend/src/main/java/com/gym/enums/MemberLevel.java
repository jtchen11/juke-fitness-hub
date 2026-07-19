package com.gym.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 会员等级枚举
 * 定义等级对应的权益：免费私教次数、折扣百分比、优先级
 */
@Getter
public enum MemberLevel {
    NORMAL("普通会员", 0, 0, 0),
    GOLD("黄金会员", 1, 1, 10),      // 每月1次免费私教，10%折扣
    PLATINUM("铂金会员", 2, 2, 20);  // 每月2次免费私教，20%折扣

    private final String displayName;
    private final int priority;              // 优先级，越大越高（用于超额预约等）
    private final int freePersonalTrainingsPerMonth;
    private final int discountPercent;

    MemberLevel(String displayName, int priority, int freePersonalTrainingsPerMonth, int discountPercent) {
        this.displayName = displayName;
        this.priority = priority;
        this.freePersonalTrainingsPerMonth = freePersonalTrainingsPerMonth;
        this.discountPercent = discountPercent;
    }

    /**
     * 根据中文名获取枚举
     */
    public static MemberLevel fromDisplayName(String name) {
        for (MemberLevel level : values()) {
            if (level.displayName.equals(name)) {
                return level;
            }
        }
        return NORMAL;
    }

}