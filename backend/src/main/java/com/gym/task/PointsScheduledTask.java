package com.gym.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.service.PointsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分相关定时任务
 * R020: 付费团课结束后自动为已签到会员 +10 积分
 */
@Slf4j
@Component
public class PointsScheduledTask {

    @Autowired private GroupClassMapper groupClassMapper;
    @Autowired private ClassBookingMapper classBookingMapper;
    @Autowired private PointsService pointsService;

    /**
     * 每小时执行一次，检查已结束的付费团课
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processPaidClassPoints() {
        LocalDateTime now = LocalDateTime.now();

        // 查找已结束且状态仍为 scheduled 的付费团课
        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getType, "paid")
                .eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now);
        List<GroupClass> classes = groupClassMapper.selectList(gw);

        for (GroupClass gc : classes) {
            // 查找该课程所有已签到的预约
            LambdaQueryWrapper<ClassBooking> bw = new LambdaQueryWrapper<>();
            bw.eq(ClassBooking::getClassId, gc.getId())
                    .eq(ClassBooking::getStatus, "checked_in");
            List<ClassBooking> bookings = classBookingMapper.selectList(bw);

            for (ClassBooking cb : bookings) {
                try {
                    pointsService.addPoints(cb.getMemberId(), 10, "paid_class", cb.getId(),
                            "付费团课完成: " + (gc.getName() != null ? gc.getName() : "课程#" + gc.getId()));
                } catch (Exception e) {
                    log.error("addPoints error memberId={} classId={}", cb.getMemberId(), gc.getId(), e);
                }
            }

            // 标记团课为已完成
            gc.setStatus("completed");
            groupClassMapper.updateById(gc);
            log.info("Processed paid class {}: {} bookings, {} points awarded", gc.getId(), bookings.size(), bookings.size() * 10);
        }
    }

    /**
     * 免费团课签到积分处理（运行频率同付费课）
     * 仅处理已结束的免费团课中已签到的预约
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processFreeClassPoints() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getType, "free")
                .eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now);
        List<GroupClass> classes = groupClassMapper.selectList(gw);

        for (GroupClass gc : classes) {
            LambdaQueryWrapper<ClassBooking> bw = new LambdaQueryWrapper<>();
            bw.eq(ClassBooking::getClassId, gc.getId())
                    .eq(ClassBooking::getStatus, "checked_in");
            List<ClassBooking> bookings = classBookingMapper.selectList(bw);

            for (ClassBooking cb : bookings) {
                try {
                    pointsService.addPoints(cb.getMemberId(), 1, "free_class", cb.getId(),
                            "公益团课签到: " + (gc.getName() != null ? gc.getName() : "课程#" + gc.getId()));
                } catch (Exception e) {
                    log.error("addPoints error memberId={} classId={}", cb.getMemberId(), gc.getId(), e);
                }
            }

            gc.setStatus("completed");
            groupClassMapper.updateById(gc);
            log.info("Processed free class {}: {} bookings, {} points awarded", gc.getId(), bookings.size(), bookings.size());
        }
    }
}
