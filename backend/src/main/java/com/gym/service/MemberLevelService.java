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
     * 检查会员是否可预约课程（铂金可超额预约）
     * @param memberId 会员ID
     * @param currentEnrolled 当前已预约人数
     * @param maxCapacity 最大容量
     * @return 是否可以预约
     */
    public boolean canBookClass(Long memberId, int currentEnrolled, int maxCapacity) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) return false;
        MemberLevel level = MemberLevel.fromDisplayName(member.getLevel());
        // 铂金会员可额外多预约2个名额（即满员也可预约）
        int effectiveMax = maxCapacity + (level.getPriority() >= 2 ? 2 : 0);
        return currentEnrolled < effectiveMax;
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