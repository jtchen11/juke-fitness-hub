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

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查询指定时间范围内的可预约团课（普通会员可预约）
     */
    public List<GroupClass> getAvailableClasses(LocalDateTime start, LocalDateTime end) {
        return getAvailableClasses(start, end, null);
    }

    public List<GroupClass> getAvailableClasses(LocalDateTime start, LocalDateTime end, String type) {
        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(GroupClass::getStartTime, start, end)
                .eq(GroupClass::getStatus, "scheduled")
                .apply("enrolled < max_capacity"); // 未满员
        if ("free".equals(type)) {
            wrapper.eq(GroupClass::getType, "free");
        } else if ("paid".equals(type)) {
            wrapper.eq(GroupClass::getType, "paid");
        }
        return groupClassMapper.selectList(wrapper);
    }

    /**
     * 会员预约团课（含等级超额）
     */
    @Transactional
    public String bookClass(Long memberId, Long classId) {
        // 1. 查询课程
        GroupClass gc = groupClassMapper.selectById(classId);
        if (gc == null) {
            return "课程不存在";
        }
        if (!"scheduled".equals(gc.getStatus())) {
            return "课程已取消或已结束";
        }

        // 2. 查询会员
        Member member = memberMapper.selectById(memberId);

        // 3. 访客校验
        if (member != null && member.isVisitor()) {
            // 体验课限制
            if (gc.getAllowVisitor() == null || !gc.getAllowVisitor()) {
                return "该课程不支持访客预约，请注册会员后再预约。";
            }
            if (Boolean.TRUE.equals(member.getExperienceUsed())) {
                return "您已使用过体验课，请注册会员后再预约。";
            }
        }

        // 4. 检查是否可预约（使用等级服务）
        boolean canBook = levelService.canBookClass(memberId, gc.getEnrolled(), gc.getMaxCapacity());
        if (!canBook) {
            return "课程已满员，您的等级暂不支持超额预约";
        }

        // 5. 检查是否已预约过
        LambdaQueryWrapper<ClassBooking> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getClassId, classId)
                .eq(ClassBooking::getStatus, "booked");
        if (classBookingMapper.selectCount(checkWrapper) > 0) {
            return "您已预约过该课程，请勿重复预约";
        }

        // 6. 创建预约记录
        ClassBooking booking = new ClassBooking();
        booking.setMemberId(memberId);
        booking.setClassId(classId);
        booking.setBookingTime(LocalDateTime.now());
        booking.setStatus("booked");
        classBookingMapper.insert(booking);

        // 7. 增加已预约人数
        gc.setEnrolled(gc.getEnrolled() + 1);
        groupClassMapper.updateById(gc);

        // 8. 访客体验标记
        if (member != null && member.isVisitor()) {
            member.setExperienceUsed(true);
            memberMapper.updateById(member);
        }

        // 9. 构建返回消息（含价格信息）
        StringBuilder sb = new StringBuilder("预约成功！课程名称：" + gc.getName());
        if (gc.getPrice() != null && gc.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append("，价格：¥").append(gc.getPrice());
            if (member != null && !member.isVisitor() && member.getLevel() != null) {
                java.math.BigDecimal discounted = levelService.getDiscountedPrice(gc.getPrice(), member.getLevel());
                sb.append("（").append(member.getLevel()).append("折扣：¥").append(discounted).append("）");
            }
        } else {
            sb.append("（公益课免费）");
        }
        return sb.toString();
    }
}