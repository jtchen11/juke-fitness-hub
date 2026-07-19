package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.GroupClassMapper;
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
    private MemberLevelService levelService;  // 新增注入

    /**
     * 查询指定时间范围内的可预约团课（普通会员可预约）
     */
    public List<GroupClass> getAvailableClasses(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(GroupClass::getStartTime, start, end)
                .eq(GroupClass::getStatus, "scheduled")
                .apply("enrolled < max_capacity"); // 未满员
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

        // 2. 检查是否可预约（使用等级服务）
        boolean canBook = levelService.canBookClass(memberId, gc.getEnrolled(), gc.getMaxCapacity());
        if (!canBook) {
            return "课程已满员，您的等级暂不支持超额预约";
        }

        // 3. 检查是否已预约过
        LambdaQueryWrapper<ClassBooking> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getClassId, classId)
                .eq(ClassBooking::getStatus, "booked");
        if (classBookingMapper.selectCount(checkWrapper) > 0) {
            return "您已预约过该课程，请勿重复预约";
        }

        // 4. 创建预约记录
        ClassBooking booking = new ClassBooking();
        booking.setMemberId(memberId);
        booking.setClassId(classId);
        booking.setBookingTime(LocalDateTime.now());
        booking.setStatus("booked");
        classBookingMapper.insert(booking);

        // 5. 增加已预约人数
        gc.setEnrolled(gc.getEnrolled() + 1);
        groupClassMapper.updateById(gc);

        return "预约成功！课程名称：" + gc.getName();
    }
}