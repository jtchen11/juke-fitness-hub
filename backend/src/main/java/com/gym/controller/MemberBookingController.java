package com.gym.controller;

import com.gym.auth.LoginContext;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.CheckIn;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.entity.PersonalTraining;
import com.gym.entity.Trainer;
import com.gym.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class MemberBookingController {

    @Autowired private ClassBookingMapper classBookingMapper;
    @Autowired private CheckInMapper checkInMapper;
    @Autowired private PersonalTrainingMapper ptMapper;
    @Autowired private GroupClassMapper groupClassMapper;
    @Autowired private TrainerMapper trainerMapper;

    private Long resolveMemberId(HttpSession session, HttpServletRequest request) {
        Long fromJwt = LoginContext.getUserId();
        if (fromJwt != null) return fromJwt;
        Object fromSession = session.getAttribute("memberId");
        if (fromSession instanceof Number) {
            return ((Number) fromSession).longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/bookings")
    public List<Map<String, Object>> listBookings(
            @RequestParam(required = false) String status,
            HttpSession session,
            HttpServletRequest request) {
        Long memberId = resolveMemberId(session, request);
        if (memberId == null) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();

        // ????
        LambdaQueryWrapper<ClassBooking> cw = new LambdaQueryWrapper<>();
        cw.eq(ClassBooking::getMemberId, memberId);
        // 已取消单独按状态查；其余状态在内存中结合课程时间动态判定
        if ("cancelled".equals(status)) {
            cw.eq(ClassBooking::getStatus, "cancelled");
        } else {
            cw.in(ClassBooking::getStatus, "booked", "checked_in", "completed");
        }
        cw.orderByDesc(ClassBooking::getBookingTime);
        LocalDateTime now = LocalDateTime.now();
        for (ClassBooking cb : classBookingMapper.selectList(cw)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", cb.getId());
            item.put("type", "group");
            item.put("bookingTime", cb.getBookingTime());
            String name = "", trainerName = "";
            LocalDateTime classEndTime = null;
            if (cb.getClassId() != null) {
                GroupClass gc = groupClassMapper.selectById(cb.getClassId());
                if (gc != null) {
                    name = gc.getName();
                    classEndTime = gc.getEndTime();
                    item.put("classStartTime", gc.getStartTime());
                    item.put("classEndTime", gc.getEndTime());
                    item.put("classId", cb.getClassId());
                    if (gc.getTrainerId() != null) {
                        Trainer t = trainerMapper.selectById(gc.getTrainerId());
                        if (t != null) trainerName = t.getName();
                    }
                }
            }
            // 状态判定：已完成 = 已签到且课程已结束（或系统标记为 completed）
            String cs = cb.getStatus();
            if ("cancelled".equals(cs)) {
                cs = "cancelled";
            } else if ("completed".equals(cs)
                    || ("checked_in".equals(cs) && classEndTime != null && classEndTime.isBefore(now))) {
                cs = "completed";
            } else if ("checked_in".equals(cs)) {
                cs = "checked_in";
            } else {
                cs = "booked";
            }
            if (status != null && !status.isEmpty() && !"all".equals(status) && !status.equals(cs)) {
                continue;
            }
            item.put("status", cs);
            item.put("name", name);
            item.put("trainerName", trainerName);
            item.put("typeLabel", "\u56e2\u8bfe");
            result.add(item);
        }

        // ????
        // 私教状态判定（check_in 表，check_in_type='pt'）：
        // 已签到 = 有 start 打卡且无 end 打卡；已完成 = 有 end 打卡
        List<Long> checkedInPtIds = new ArrayList<>();
        List<Long> completedPtIds = new ArrayList<>();
        if (status == null || "all".equals(status) || "booked".equals(status)
                || "checked_in".equals(status) || "completed".equals(status)) {
            LambdaQueryWrapper<CheckIn> ckW = new LambdaQueryWrapper<>();
            ckW.eq(CheckIn::getMemberId, memberId).eq(CheckIn::getCheckInType, "pt");
            for (CheckIn ci : checkInMapper.selectList(ckW)) {
                if (ci.getPtId() == null) continue;
                if ("start".equals(ci.getRemark())) checkedInPtIds.add(ci.getPtId());
                else if ("end".equals(ci.getRemark())) completedPtIds.add(ci.getPtId());
            }
        }

        LambdaQueryWrapper<PersonalTraining> pw = new LambdaQueryWrapper<>();
        pw.eq(PersonalTraining::getMemberId, memberId);
        if ("checked_in".equals(status)) {
            // 已签到：有 start 打卡且无 end 打卡
            List<Long> checkedOnly = new ArrayList<>(checkedInPtIds);
            checkedOnly.removeAll(completedPtIds);
            if (checkedOnly.isEmpty()) {
                pw.in(PersonalTraining::getId, -1L);
            } else {
                pw.in(PersonalTraining::getId, checkedOnly);
            }
        } else if ("completed".equals(status)) {
            // 已完成：有 end 打卡记录
            if (completedPtIds.isEmpty()) {
                pw.in(PersonalTraining::getId, -1L);
            } else {
                pw.in(PersonalTraining::getId, completedPtIds);
            }
        } else {
            String ptStatus = status;
            if ("booked".equals(status)) ptStatus = "scheduled";
            if (ptStatus != null && !ptStatus.isEmpty() && !"all".equals(ptStatus)) {
                pw.eq(PersonalTraining::getStatus, ptStatus);
            }
            // 已预约分类排除已签到记录
            if ("booked".equals(status) && !checkedInPtIds.isEmpty()) {
                pw.notIn(PersonalTraining::getId, checkedInPtIds);
            }
        }
        pw.orderByDesc(PersonalTraining::getAppointmentTime);
        for (PersonalTraining pt : ptMapper.selectList(pw)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", pt.getId());
            item.put("type", "pt");
            String s = pt.getStatus();
            if ("scheduled".equals(s)) {
                if (completedPtIds.contains(pt.getId())) s = "completed";
                else if (checkedInPtIds.contains(pt.getId())) s = "checked_in";
                else s = "booked";
            }
            item.put("status", s);
            item.put("bookingTime", pt.getAppointmentTime());
            item.put("name", pt.getPackageName() != null ? pt.getPackageName() : "\u79c1\u6559\u8bfe");
            String tn = "";
            if (pt.getTrainerId() != null) {
                Trainer t = trainerMapper.selectById(pt.getTrainerId());
                if (t != null) tn = t.getName();
            }
            item.put("trainerName", tn);
            item.put("typeLabel", "\u79c1\u6559");
            item.put("isFree", pt.getIsFree());
            item.put("trainerId", pt.getTrainerId());
            result.add(item);
        }

        // ? bookingTime ??
        result.sort((a, b) -> {
            Object ta = a.get("bookingTime");
            Object tb = b.get("bookingTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return -((Comparable) ta).compareTo(tb);
        });
        return result;
    }
}