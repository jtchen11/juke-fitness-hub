package com.gym.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.*;
import com.gym.enums.MemberLevel;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.MemberPrivatePackageMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.TrainerLeaveMapper;
import com.gym.mapper.TrainerMapper;
import com.gym.service.PersonalTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class PersonalTrainingServiceImpl implements PersonalTrainingService {

    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private TrainerMapper trainerMapper;
    @Autowired
    private TrainerLeaveMapper trainerLeaveMapper;
    @Autowired
    private PersonalTrainingMapper ptMapper;
    @Autowired
    private MemberPrivatePackageMapper packageMapper;

    @Override
    @Transactional
    public String bookPersonalTraining(Long memberId, Long trainerId, LocalDateTime appointmentTime,
                                       Integer durationMinutes, Long packageId, boolean useFree) {
        // 1. 校验会员
        Member member = memberMapper.selectById(memberId);
        if (member == null) return "会员不存在";
        // P0-1: 访客无法预约私教
        if (member.isVisitor()) {
            return "访客无法预约私教课，请注册会员后再预约";
        }
        // 过期会员校验
        if (member.isExpired()) {
            return "您的会员已过期，请续费后再预约";
        }

        // 2. 校验教练
        Trainer trainer = trainerMapper.selectById(trainerId);
        if (trainer == null) return "教练不存在";
        if (!"active".equals(trainer.getStatus())) return "教练当前不可预约";

        // 3. 检查教练是否请假
        LocalDate appointmentDate = appointmentTime.toLocalDate();
        LambdaQueryWrapper<TrainerLeave> leaveWrapper = new LambdaQueryWrapper<>();
        leaveWrapper.eq(TrainerLeave::getTrainerId, trainerId)
                .eq(TrainerLeave::getLeaveDate, appointmentDate);
        if (trainerLeaveMapper.selectCount(leaveWrapper) > 0) {
            return "教练当日请假，不可预约";
        }

        // 4. 检查该时段是否已被预约
        LambdaQueryWrapper<PersonalTraining> conflictWrapper = new LambdaQueryWrapper<>();
        conflictWrapper.eq(PersonalTraining::getTrainerId, trainerId)
                .eq(PersonalTraining::getAppointmentTime, appointmentTime)
                .eq(PersonalTraining::getStatus, "scheduled");
        if (ptMapper.selectCount(conflictWrapper) > 0) {
            return "该时段已被其他会员预约";
        }

        // 4.5 检查会员该时段是否已有私教预约
        LambdaQueryWrapper<PersonalTraining> memberConflictWrapper = new LambdaQueryWrapper<>();
        memberConflictWrapper.eq(PersonalTraining::getMemberId, memberId)
                .eq(PersonalTraining::getAppointmentTime, appointmentTime)
                .eq(PersonalTraining::getStatus, "scheduled");
        if (ptMapper.selectCount(memberConflictWrapper) > 0) {
            return "您在该时段已有私教预约，请选择其他时间";
        }

        // 5. 创建预约记录
        PersonalTraining pt = new PersonalTraining();
        pt.setMemberId(memberId);
        pt.setTrainerId(trainerId);
        pt.setAppointmentTime(appointmentTime);
        pt.setDurationMinutes(durationMinutes != null ? durationMinutes : 60);
        pt.setStatus("scheduled");
        pt.setIsFree(false);
        pt.setPackageId(null);

        // 6. 处理付费方式
        if (packageId != null && packageId > 0) {
            // 使用课程包
            MemberPrivatePackage pkg = packageMapper.selectById(packageId);
            if (pkg == null) return "课程包不存在";
            if (!pkg.getMemberId().equals(memberId)) return "无权使用该课程包";
            if (pkg.getRemainingSessions() <= 0) return "课程包已用完";
            if (pkg.getEndDate() != null && pkg.getEndDate().isBefore(LocalDate.now())) {
                // Issue 3: 尝试自动查找另一个有效课程包
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberPrivatePackage> fbw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                fbw.eq(MemberPrivatePackage::getMemberId, memberId)
                   .eq(MemberPrivatePackage::getStatus, "active")
                   .gt(MemberPrivatePackage::getRemainingSessions, 0)
                   .and(w -> w.isNull(MemberPrivatePackage::getEndDate)
                        .or()
                        .ge(MemberPrivatePackage::getEndDate, LocalDate.now()))
                   .last("LIMIT 1");
                MemberPrivatePackage fbPkg = packageMapper.selectOne(fbw);
                if (fbPkg != null) {
                    pkg = fbPkg;
                    packageId = pkg.getId();
                    // log removed (no @Slf4j in this class)
                } else {
                    return "课程包已过期，且没有其他可用的课程包";
                }
            }
            pkg.setUsedSessions(pkg.getUsedSessions() + 1);
            pkg.setRemainingSessions(pkg.getRemainingSessions() - 1);
            packageMapper.updateById(pkg);
            pt.setPackageId(pkg.getId());
            pt.setSessionIndex(pkg.getUsedSessions());
            pt.setPackageName(pkg.getPackageName());
            pt.setIsFree(false);
        } else if (useFree) {
            MemberLevel level = MemberLevel.fromDisplayName(member.getLevel());
            int maxFree = level.getFreePersonalTrainingsPerMonth();
            if (maxFree <= 0) return "当前等级无免费私教权益";

            LocalDate now = LocalDate.now();
            if (member.getFreePtMonthReset() == null ||
                    member.getFreePtMonthReset().getMonthValue() != now.getMonthValue()) {
                member.setFreePtUsedMonth(0);
                member.setFreePtMonthReset(now);
                memberMapper.updateById(member);
            }

            if (member.getFreePtUsedMonth() >= maxFree) {
                return "本月免费次数已用完";
            }

            member.setFreePtUsedMonth(member.getFreePtUsedMonth() + 1);
            memberMapper.updateById(member);
            pt.setIsFree(true);
            pt.setPackageId(null);
            pt.setPackageName(null);
        } else {
            pt.setIsFree(false);
            pt.setPackageId(null);
            pt.setPackageName(null);
            pt.setNotes("单次付费预约");
        }

        // 7. 保存预约记录
        ptMapper.insert(pt);
        // P1-6: 单次付费返回折扣明细
        if (packageId == null && !useFree) {
            java.math.BigDecimal price = java.math.BigDecimal.valueOf(300);
            if (trainer.getPricePerHour() != null) {
                price = trainer.getPricePerHour();
            }
            String levelName = member.getLevel();
            java.math.BigDecimal discounted = price;
            String discountInfo = "";
            if (levelName != null && !"访客".equals(levelName)) {
                com.gym.enums.MemberLevel ml = com.gym.enums.MemberLevel.fromDisplayName(levelName);
                int discountPct = ml.getDiscountPercent();
                if (discountPct > 0) {
                    discounted = price.multiply(java.math.BigDecimal.valueOf(100 - discountPct))
                        .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    discountInfo = "\n" + levelName + "折扣：-￥" + price.subtract(discounted) + "（" + discountPct + "%）";
                }
            }
            String timeRange = appointmentTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + "-" + appointmentTime.plusMinutes(durationMinutes != null ? durationMinutes : 60).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            return "私教预约成功！\n课程：私教课（" + trainer.getName() + "）\n时间：" + timeRange + "\n原价：￥" + price + discountInfo + "\n实付金额：￥" + discounted;
        }
        return "私教预约成功！";
    }
}
