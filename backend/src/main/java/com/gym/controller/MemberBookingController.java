package com.gym.controller;

import com.gym.auth.LoginContext;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.entity.PersonalTraining;
import com.gym.entity.Trainer;
import com.gym.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class MemberBookingController {

    @Autowired private ClassBookingMapper classBookingMapper;
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
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            cw.eq(ClassBooking::getStatus, status);
        }
        cw.orderByDesc(ClassBooking::getBookingTime);
        for (ClassBooking cb : classBookingMapper.selectList(cw)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", cb.getId());
            item.put("type", "group");
            item.put("status", cb.getStatus());
            item.put("bookingTime", cb.getBookingTime());
            String name = "", trainerName = "";
            if (cb.getClassId() != null) {
                GroupClass gc = groupClassMapper.selectById(cb.getClassId());
                if (gc != null) {
                    name = gc.getName();
                    if (gc.getTrainerId() != null) {
                        Trainer t = trainerMapper.selectById(gc.getTrainerId());
                        if (t != null) trainerName = t.getName();
                    }
                }
            }
            item.put("name", name);
            item.put("trainerName", trainerName);
            item.put("typeLabel", "\u56e2\u8bfe");
            result.add(item);
        }

        // ????
        LambdaQueryWrapper<PersonalTraining> pw = new LambdaQueryWrapper<>();
        pw.eq(PersonalTraining::getMemberId, memberId);
        String ptStatus = status;
        if ("booked".equals(status) || "checked_in".equals(status)) ptStatus = "scheduled";
        if (ptStatus != null && !ptStatus.isEmpty() && !"all".equals(ptStatus)) {
            pw.eq(PersonalTraining::getStatus, ptStatus);
        }
        pw.orderByDesc(PersonalTraining::getAppointmentTime);
        for (PersonalTraining pt : ptMapper.selectList(pw)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", pt.getId());
            item.put("type", "pt");
            String s = pt.getStatus();
            if ("scheduled".equals(s)) s = "booked";
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
