package com.gym.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

@Slf4j
@Component
public class PointsScheduledTask {

    @Autowired private GroupClassMapper groupClassMapper;
    @Autowired private ClassBookingMapper classBookingMapper;
    @Autowired private PointsService pointsService;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processPaidClassPoints() {
        log.info("[定时任务] processPaidClassPoints 开始执行");
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getType, "paid")
                .eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now);
        List<GroupClass> classes = groupClassMapper.selectList(gw);
        if (classes.isEmpty()) {
            log.info("[定时任务] processPaidClassPoints: 无待处理的付费团课");
            return;
        }
        log.info("[定时任务] processPaidClassPoints: 发现 {} 个待处理付费团课", classes.size());
        for (GroupClass gc : classes) {
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
            groupClassMapper.update(null, new LambdaUpdateWrapper<GroupClass>()
                    .eq(GroupClass::getId, gc.getId())
                    .set(GroupClass::getStatus, "completed"));
            log.info("  [定时任务] 付费团课 {} 已处理: {} 人签到", gc.getId(), bookings.size());
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processFreeClassPoints() {
        log.info("[定时任务] processFreeClassPoints 开始执行");
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getType, "free")
                .eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now);
        List<GroupClass> classes = groupClassMapper.selectList(gw);
        if (classes.isEmpty()) {
            log.info("[定时任务] processFreeClassPoints: 无待处理的免费团课");
            return;
        }
        log.info("[定时任务] processFreeClassPoints: 发现 {} 个待处理免费团课", classes.size());
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
            groupClassMapper.update(null, new LambdaUpdateWrapper<GroupClass>()
                    .eq(GroupClass::getId, gc.getId())
                    .set(GroupClass::getStatus, "completed"));
            log.info("  [定时任务] 免费团课 {} 已处理: {} 人签到", gc.getId(), bookings.size());
        }
    }

    /**
     * 兜底清理：将所有已过结束时间但状态仍为 scheduled 的团课设为 completed
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupPastGroupClasses() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now);
        List<GroupClass> pastClasses = groupClassMapper.selectList(gw);
        if (pastClasses.isEmpty()) {
            return;
        }
        log.warn("[定时任务] cleanupPastGroupClasses: 发现 {} 个已过时间但状态仍为 scheduled 的团课", pastClasses.size());
        for (GroupClass gc : pastClasses) {
            log.warn("  - 团课ID={}, 名称={}, 结束时间={}", gc.getId(), gc.getName(), gc.getEndTime());
        }
        int updated = groupClassMapper.update(null, new LambdaUpdateWrapper<GroupClass>()
                .eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now)
                .set(GroupClass::getStatus, "completed"));
        log.warn("[定时任务] cleanupPastGroupClasses: 已更新 {} 条记录为 completed", updated);
    }
}