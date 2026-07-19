package com.gym.service;

import java.time.LocalDateTime;

public interface PersonalTrainingService {

    /**
     * 便捷方法：预约私教（默认不使用免费，无课程包）
     * 用于AI调用（4个参数）
     */
    default String bookPersonalTraining(Long memberId, Long trainerId, LocalDateTime appointmentTime,
                                        Integer durationMinutes) {
        return bookPersonalTraining(memberId, trainerId, appointmentTime, durationMinutes, null, false);
    }

    /**
     * 核心方法：预约私教课（支持免费次数或课程包）
     * @param memberId 会员ID
     * @param trainerId 教练ID
     * @param appointmentTime 预约时间
     * @param durationMinutes 时长（分钟）
     * @param packageId 课程包ID（可为null）
     * @param useFree 是否使用免费次数（当packageId为null时有效）
     * @return 成功消息或错误信息
     */
    String bookPersonalTraining(Long memberId, Long trainerId, LocalDateTime appointmentTime,
                                Integer durationMinutes, Long packageId, boolean useFree);
}