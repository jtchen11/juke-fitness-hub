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
                return "课程包已过期";
            }
            // 扣减课程包
            pkg.setUsedSessions(pkg.getUsedSessions() + 1);
            pkg.setRemainingSessions(pkg.getRemainingSessions() - 1);
            packageMapper.updateById(pkg);
            pt.setPackageId(pkg.getId());
            pt.setSessionIndex(pkg.getUsedSessions());
            pt.setPackageName(pkg.getPackageName());
            pt.setIsFree(false);
        } else if (useFree) {
            // 使用免费次数
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
            // ====== 单次付费（新增支持） ======
            pt.setIsFree(false);
            pt.setPackageId(null);
            pt.setPackageName(null);
            pt.setNotes("单次付费预约");
            // 这里可以扩展为生成支付记录，演示版本直接通过
        }

        // 7. 保存预约记录
        ptMapper.insert(pt);
        return "私教预约成功！";
    }
}