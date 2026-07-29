package com.gym.service;

import com.gym.entity.Member;
import com.gym.enums.MemberLevel;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MemberLevelService {

    @Autowired
    private MemberMapper memberMapper;

    /**
     * 获取会员等级权益对象
     */
    public MemberLevel getLevel(String levelName) {
        return MemberLevel.fromDisplayName(levelName);
    }

    /**
     * 检查会员是否可预约课程（纯容量检查，铂金超额在 bookClass 内单独处理）
     */
    public boolean canBookClass(Long memberId, int currentEnrolled, int maxCapacity) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) return false;
        // 纯容量检查：仅当 enrolled < maxCapacity 才返回 true
        // 铂金会员超额预约资格在 GroupClassService.bookClass 中单独判断
        return currentEnrolled < maxCapacity;
    }

    /**
     * 计算折扣后的价格
     */
    public BigDecimal getDiscountedPrice(BigDecimal originalPrice, String levelName) {
        MemberLevel level = MemberLevel.fromDisplayName(levelName);
        BigDecimal discount = BigDecimal.valueOf(100 - level.getDiscountPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return originalPrice.multiply(discount).setScale(2, RoundingMode.HALF_UP);
    }
}
