package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.entity.Member;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gym.enums.MemberLevel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class GroupClassService {

    @Autowired
    private GroupClassMapper groupClassMapper;

    @Autowired
    private ClassBookingMapper classBookingMapper;

    @Autowired
    private MemberLevelService levelService;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SystemConfigService systemConfigService;

    public List<GroupClass> getAvailableClasses(LocalDateTime start, LocalDateTime end) {
        return getAvailableClasses(start, end, null, null);
    }

    public List<GroupClass> getAvailableClasses(LocalDateTime start, LocalDateTime end, String type) {
        return getAvailableClasses(start, end, type, null);
    }

    public List<GroupClass> getAvailableClasses(LocalDateTime start, LocalDateTime end, String type, Boolean allowVisitor) {
        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(GroupClass::getStartTime, start, end)
                .eq(GroupClass::getStatus, "scheduled");
        if ("free".equals(type)) {
            wrapper.eq(GroupClass::getType, "free");
        } else if ("paid".equals(type)) {
            wrapper.eq(GroupClass::getType, "paid");
        }
        if (allowVisitor != null && allowVisitor) {
            wrapper.eq(GroupClass::getAllowVisitor, 1);
        }
        return groupClassMapper.selectList(wrapper);
    }

    private int countPlatinumOverflow(Long memberId) {
        LambdaQueryWrapper<ClassBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId)
               .eq(ClassBooking::getStatus, "booked");
        List<ClassBooking> bookings = classBookingMapper.selectList(wrapper);
        int overflowCount = 0;
        for (ClassBooking cb : bookings) {
            GroupClass gc = groupClassMapper.selectById(cb.getClassId());
            if (gc != null && gc.getEnrolled() >= gc.getMaxCapacity()) {
                overflowCount++;
            }
        }
        return overflowCount;
    }

    @Transactional
    public String bookClass(Long memberId, Long classId) {
        GroupClass gc = groupClassMapper.selectById(classId);
        if (gc == null) {
            return "课程不存在";
        }
        if (!"scheduled".equals(gc.getStatus())) {
            return "课程已取消或已结束";
        }
        Member member = memberMapper.selectById(memberId);
        if (member != null && member.isVisitor()) {
            if (gc.getAllowVisitor() == null || !gc.getAllowVisitor()) {
                return "该课程不支持访客预约，请注册会员后再预约。";
            }
            // 体验课功能开关校验（VISITOR_EXPERIENCE_ENABLED）
            if (!isExperienceEnabled()) {
                return "体验课功能暂未开放，请联系客服";
            }
            if (Boolean.TRUE.equals(member.getExperienceUsed())) {
                return "您已使用过体验课，请注册会员后再预约。";
            }
        }
        int enrolled = gc.getEnrolled();
        int maxCapacity = gc.getMaxCapacity();
        if (enrolled >= maxCapacity) {
            MemberLevel memberLevel = MemberLevel.fromDisplayName(member != null ? member.getLevel() : "");
            if (memberLevel.getPriority() < 2) {
                return "课程已满，无法预约";
            }
            int overflowCount = countPlatinumOverflow(memberId);
            if (overflowCount >= 2) {
            int remainingOverflow = 2 - overflowCount;
                return "您已用完2次铂金会员超额预约机会，无法预约已满课程";
            }
        }
        LambdaQueryWrapper<ClassBooking> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getClassId, classId)
                .eq(ClassBooking::getStatus, "booked");
        if (classBookingMapper.selectCount(checkWrapper) > 0) {
            return "您已预约过该课程，请勿重复预约";
        }
        ClassBooking booking = new ClassBooking();
        booking.setMemberId(memberId);
        booking.setClassId(classId);
        booking.setBookingTime(LocalDateTime.now());
        booking.setStatus("booked");
        booking.setPaymentStatus("paid");
        booking.setPayTime(LocalDateTime.now());
        classBookingMapper.insert(booking);
        gc.setEnrolled(gc.getEnrolled() + 1);
        groupClassMapper.updateById(gc);
        if (member != null && member.isVisitor()) {
            member.setExperienceUsed(true);
            memberMapper.updateById(member);
        }
        // Issue 5: 铂金超额提示
        String overflowNote = "";
        if (member != null && !member.isVisitor()) {
            MemberLevel ml = MemberLevel.fromDisplayName(member.getLevel());
            if (ml.getPriority() >= 2) {
                int rem = 2 - countPlatinumOverflow(memberId);
                if (rem > 0 && rem < 2) {
                    overflowNote = "\n已使用铂金会员超额预约特权（本月剩余 " + rem + " 次）";
                }
            }
        }
        StringBuilder sb = new StringBuilder("预约成功！课程名称：" + gc.getName());
        if (gc.getPrice() != null && gc.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append("，价格：￥").append(gc.getPrice());
            if (member != null && !member.isVisitor() && member.getLevel() != null) {
                java.math.BigDecimal discounted = levelService.getDiscountedPrice(gc.getPrice(), member.getLevel());
                sb.append("（").append(member.getLevel()).append("折扣：￥").append(discounted).append("）");
            }
        } else {
            sb.append("（公益课免费）");
        }
        return sb.toString();
    }

    /** 体验课功能开关：未配置时默认开启 */
    private boolean isExperienceEnabled() {
        Map<String, String> cfg = systemConfigService.getAll();
        String v = cfg.get("VISITOR_EXPERIENCE_ENABLED");
        if (v == null || v.isEmpty()) return true;
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on");
    }
}