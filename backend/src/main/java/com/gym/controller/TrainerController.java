package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.*;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.TrainerLeaveMapper;
import com.gym.mapper.TrainerMapper;
import com.gym.entity.TrainerLeave;
import com.gym.mapper.UserMessageMapper;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.ClassBookingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trainers")
public class TrainerController {

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private TrainerLeaveMapper trainerLeaveMapper;

    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;
    @Autowired
    private UserMessageMapper userMessageMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;

    @Autowired
    private ClassBookingMapper classBookingMapper;

    /**
     * 手机号校验工具方法
     */
    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1\\d{10}$");
    }

    /**
     * 获取教练列表（支持关键词、专长、状态筛选）
     */
    // 替换原有的 @GetMapping 方法
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String specialties,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<Trainer> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Trainer::getName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Trainer::getStatus, status);
        }
        if (specialties != null && !specialties.isEmpty()) {
            String[] specialtyArray = specialties.split(",");
            wrapper.and(w -> {
                for (String s : specialtyArray) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) {
                        w.or().like(Trainer::getSpecialty, trimmed);
                    }
                }
            });
        }

        // ====== 关键改动：使用 MyBatis-Plus 分页 ======
        IPage<Trainer> pageResult = trainerMapper.selectPage(new Page<>(page, size), wrapper);

        // 填充 bookingCount（保持原逻辑）
        pageResult.getRecords().forEach(t -> t.setBookingCount(0));

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
    }

    /**
     * ====== 新增：获取所有教练（下拉框用） ======
     */
    @GetMapping("/all")
    public List<Trainer> getAll() {
        return trainerMapper.selectList(null);
    }

    @GetMapping("/{id}")
    public Trainer getById(@PathVariable Long id) {
        Trainer trainer = trainerMapper.selectById(id);
        if (trainer != null) {
            long count = personalTrainingMapper.selectCount(
                new LambdaQueryWrapper<com.gym.entity.PersonalTraining>()
                    .eq(com.gym.entity.PersonalTraining::getTrainerId, id)
                    .eq(com.gym.entity.PersonalTraining::getStatus, "completed")
            );
            trainer.setBookingCount((int) count);
        }
        return trainer;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> add(@RequestBody Trainer trainer) {
        // 手机号校验
        if (!isValidPhone(trainer.getPhone())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "手机号必须以1开头，共11位数字");
            return error;
        }
        if (trainer.getStatus() == null || trainer.getStatus().isEmpty()) {
            trainer.setStatus("active");
        }
        trainerMapper.insert(trainer);

        // 同步创建会员账号（教练同时也是普通会员，无有效期限制）
        QueryWrapper<Member> qw = new QueryWrapper<>();
        qw.eq("phone", trainer.getPhone());
        Member member = memberMapper.selectOne(qw);
        if (member == null) {
            member = new Member();
            member.setPhone(trainer.getPhone());
            member.setName(trainer.getName());
            member.setLevel("普通会员");
            member.setExpireDate(java.time.LocalDate.of(2099, 12, 31));
            member.setCreatedAt(java.time.LocalDateTime.now());
            member.setExperienceUsed(false);
            memberMapper.insert(member);
        } else {
            member.setLevel("普通会员");
            member.setExpireDate(java.time.LocalDate.of(2099, 12, 31));
            member.setName(trainer.getName());
            memberMapper.updateById(member);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "添加成功，已同步创建会员账号");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Trainer trainer) {
        // 手机号校验
        if (!isValidPhone(trainer.getPhone())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "手机号必须以1开头，共11位数字");
            return error;
        }
        trainer.setId(id);
        trainerMapper.updateById(trainer);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        trainerMapper.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }

    @GetMapping("/export")
    public void exportTrainers(HttpServletResponse response) throws IOException {
        List<Trainer> list = trainerMapper.selectList(null);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=教练列表_" + LocalDate.now().toString() + ".csv");

        try (PrintWriter writer = response.getWriter()) {
            writer.write("\uFEFF");
            writer.println("ID,姓名,手机号,专长,价格(元/小时),状态");
            for (Trainer t : list) {
                writer.printf("%d,%s,%s,%s,%s,%s%n",
                        t.getId(),
                        nullToEmpty(t.getName()),
                        nullToEmpty(t.getPhone()),
                        nullToEmpty(t.getSpecialty()),
                        t.getPricePerHour(),
                        getStatusText(t.getStatus())
                );
            }
            writer.flush();
        }
    }

    // ========== 新增：教练请假接口 ==========
    @PostMapping("/{trainerId}/leave")
    @Transactional
    public Map<String, Object> setLeave(@PathVariable Long trainerId,
                                        @RequestBody Map<String, Object> params) {
        String leaveDate = (String) params.get("leaveDate");
        String reason = (String) params.get("reason");
        Map<String, Object> result = new HashMap<>();

        // 1. 校验教练是否存在
        Trainer trainer = trainerMapper.selectById(trainerId);
        if (trainer == null) {
            result.put("success", false);
            result.put("message", "教练不存在");
            return result;
        }

        LocalDate date = LocalDate.parse(leaveDate);

        // 2. 检查是否已存在请假记录（避免重复）
        LambdaQueryWrapper<TrainerLeave> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(TrainerLeave::getTrainerId, trainerId)
                .eq(TrainerLeave::getLeaveDate, date);
        if (trainerLeaveMapper.selectCount(existWrapper) > 0) {
            result.put("success", false);
            result.put("message", "该教练当日已请假，请勿重复设置");
            return result;
        }

        // 3. 保存请假记录
        TrainerLeave leave = new TrainerLeave();
        leave.setTrainerId(trainerId);
        leave.setLeaveDate(date);
        leave.setReason(reason);
        trainerLeaveMapper.insert(leave);

        // 4. 自动取消该教练当天的所有待上课预约
        LambdaQueryWrapper<PersonalTraining> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.eq(PersonalTraining::getTrainerId, trainerId)
                .eq(PersonalTraining::getStatus, "scheduled")
                .apply("DATE(appointment_time) = {0}", date);
        List<PersonalTraining> affected = personalTrainingMapper.selectList(ptWrapper);

        int cancelCount = 0;
        if (!affected.isEmpty()) {
            for (PersonalTraining pt : affected) {
                pt.setStatus("cancelled_by_trainer");
                pt.setCancelReason("教练请假：" + (reason != null ? reason : "临时请假"));
                personalTrainingMapper.updateById(pt);
            }
            cancelCount = affected.size();
        }

        // ====== 5. 新增：发送站内信给受影响的会员 ======
        if (!affected.isEmpty()) {
            // 获取所有受影响的会员ID（去重）
            List<Long> memberIds = affected.stream()
                    .map(PersonalTraining::getMemberId)
                    .distinct()
                    .collect(Collectors.toList());

            String messageContent = "教练 " + trainer.getName() + " 于 " + leaveDate + " 请假，您的私教课已自动取消，请重新预约。";
            for (Long memberId : memberIds) {
                UserMessage msg = new UserMessage();
                msg.setMemberId(memberId);
                msg.setContent(messageContent);
                msg.setIsRead(false);
                userMessageMapper.insert(msg);
            }
        }

        // 6. 返回结果
        result.put("success", true);
        result.put("message", "请假设置成功");
        result.put("cancelCount", cancelCount);
        result.put("affectedMembers", affected.stream().map(pt -> pt.getMemberId()).distinct().count());
        return result;
    }

    // ========== 新增：取消请假接口（可选，用于恢复） ==========
    @DeleteMapping("/{trainerId}/leave")
    public Map<String, Object> cancelLeave(@PathVariable Long trainerId,
                                           @RequestParam String leaveDate) {
        Map<String, Object> result = new HashMap<>();
        LocalDate date = LocalDate.parse(leaveDate);

        LambdaQueryWrapper<TrainerLeave> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainerLeave::getTrainerId, trainerId)
                .eq(TrainerLeave::getLeaveDate, date);
        int deleted = trainerLeaveMapper.delete(wrapper);

        if (deleted > 0) {
            result.put("success", true);
            result.put("message", "取消请假成功");
        } else {
            result.put("success", false);
            result.put("message", "该日期无请假记录");
        }
        return result;
    }



    // =============================================
    // 教练端小程序 API（2026-07-19）
    // =============================================

    /**
     * 获取教练今日课表（团课 + 私教）
     */
        @GetMapping("/{trainerId}/stats")
    public Map<String, Object> getTrainerStats(@PathVariable Long trainerId) {
        Map<String, Object> result = new HashMap<>();
        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        // 本月私教上课数（completed）
        LambdaQueryWrapper<PersonalTraining> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.eq(PersonalTraining::getTrainerId, trainerId)
                .eq(PersonalTraining::getStatus, "completed")
                .ge(PersonalTraining::getAppointmentTime, monthStart)
                .le(PersonalTraining::getAppointmentTime, monthEnd);
        long completedSessions = personalTrainingMapper.selectCount(ptWrapper);

        // 本月私教预约数（scheduled + completed）
        LambdaQueryWrapper<PersonalTraining> bookingWrapper = new LambdaQueryWrapper<>();
        bookingWrapper.eq(PersonalTraining::getTrainerId, trainerId)
                .ge(PersonalTraining::getAppointmentTime, monthStart)
                .le(PersonalTraining::getAppointmentTime, monthEnd)
                .in(PersonalTraining::getStatus, "scheduled", "completed");
        long totalBookings = personalTrainingMapper.selectCount(bookingWrapper);

        // 本月团课核销数
        LambdaQueryWrapper<GroupClass> gcWrapper = new LambdaQueryWrapper<>();
        gcWrapper.eq(GroupClass::getTrainerId, trainerId)
                .eq(GroupClass::getStatus, "completed")
                .ge(GroupClass::getStartTime, monthStart)
                .le(GroupClass::getStartTime, monthEnd);
        long classCheckins = groupClassMapper.selectCount(gcWrapper);

        result.put("thisMonthSessions", completedSessions);
        result.put("thisMonthBookings", totalBookings);
        result.put("thisMonthCheckins", classCheckins);
        return result;
    }

    @GetMapping("/{trainerId}/today-schedule")
    public List<Map<String, Object>> getTodaySchedule(@PathVariable Long trainerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 查询今日团课
        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getTrainerId, trainerId)
                .apply("DATE(start_time) = {0}", today)
                .orderByAsc(GroupClass::getStartTime);
        List<GroupClass> classes = groupClassMapper.selectList(gw);
        for (GroupClass gc : classes) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", gc.getId());
            item.put("name", gc.getName());
            item.put("time", gc.getStartTime() != null ? gc.getStartTime().toLocalTime().toString().substring(0, 5) : "");
            item.put("memberName", "团课 - " + gc.getName());
            item.put("status", gc.getStatus());
            item.put("statusText", getScheduleStatusText(gc.getStatus()));
            item.put("type", "group");
            result.add(item);
        }

        // 查询今日私教课
        LambdaQueryWrapper<PersonalTraining> pw = new LambdaQueryWrapper<>();
        pw.eq(PersonalTraining::getTrainerId, trainerId)
                .apply("DATE(appointment_time) = {0}", today)
                .orderByAsc(PersonalTraining::getAppointmentTime);
        List<PersonalTraining> pts = personalTrainingMapper.selectList(pw);
        for (PersonalTraining pt : pts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", pt.getId());
            item.put("name", "私教课");
            item.put("time", pt.getAppointmentTime() != null ? pt.getAppointmentTime().toLocalTime().toString().substring(0, 5) : "");
            Member ptMember = memberMapper.selectById(pt.getMemberId());
            item.put("memberName", ptMember != null ? ptMember.getName() : "会员#" + pt.getMemberId());
            item.put("status", pt.getStatus());
            item.put("statusText", getScheduleStatusText(pt.getStatus()));
            item.put("type", "pt");
            result.add(item);
        }

        // 按时间排序
        result.sort((a, b) -> ((String) a.get("time")).compareTo((String) b.get("time")));
        return result;
    }

    private String getScheduleStatusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "scheduled": return "待上课";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            case "checked_in": return "已签到";
            default: return status;
        }
    }

    /**
     * 获取教练的预约管理列表（私教预约）
     */
    @GetMapping("/{trainerId}/appointments")
    public List<Map<String, Object>> getAppointments(
            @PathVariable Long trainerId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PersonalTraining> pw = new LambdaQueryWrapper<>();
        pw.eq(PersonalTraining::getTrainerId, trainerId);
        if (status != null && !status.isEmpty()) {
            pw.eq(PersonalTraining::getStatus, status);
        }
        pw.orderByDesc(PersonalTraining::getAppointmentTime);

        List<PersonalTraining> pts = personalTrainingMapper.selectList(pw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PersonalTraining pt : pts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", pt.getId());
            Member ptMember = memberMapper.selectById(pt.getMemberId());
            item.put("memberName", ptMember != null ? ptMember.getName() : "会员#" + pt.getMemberId());
            item.put("courseName", pt.getPackageName() != null ? pt.getPackageName() : "私教课");
            item.put("time", pt.getAppointmentTime() != null ? pt.getAppointmentTime().toString().replace("T", " ") : "");
            item.put("status", pt.getStatus());
            item.put("memberId", pt.getMemberId());
            item.put("type", "pt");
            result.add(item);
        }
        return result;
    }

    /**
     * 获取教练的学员列表
     */
    @GetMapping("/{trainerId}/students")
    public List<Map<String, Object>> getStudents(
            @PathVariable Long trainerId,
            @RequestParam(required = false) String keyword) {
        // 查询该教练带过的所有私教记录
        LambdaQueryWrapper<PersonalTraining> pw = new LambdaQueryWrapper<>();
        pw.eq(PersonalTraining::getTrainerId, trainerId)
                .isNotNull(PersonalTraining::getMemberId)
                .orderByDesc(PersonalTraining::getAppointmentTime);
        List<PersonalTraining> pts = personalTrainingMapper.selectList(pw);

        // 按 memberId 去重
        LinkedHashSet<Long> memberIds = new java.util.LinkedHashSet<>();
        LinkedHashMap<Long, String> lastClassMap = new java.util.LinkedHashMap<>();
        for (PersonalTraining pt : pts) {
            if (!memberIds.contains(pt.getMemberId())) {
                memberIds.add(pt.getMemberId());
            }
            // 记录最后一次上课时间
            if (!lastClassMap.containsKey(pt.getMemberId())) {
                lastClassMap.put(pt.getMemberId(),
                        pt.getAppointmentTime() != null ? pt.getAppointmentTime().toLocalDate().toString() : null);
            }
        }

        // Count class sessions per member
        java.util.Map<Long, Long> classCountMap = new java.util.HashMap<>();
        for (PersonalTraining pt : pts) {
            classCountMap.merge(pt.getMemberId(), 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long memberId : memberIds) {
            Member m = memberMapper.selectById(memberId);
            if (m == null) continue;

            // 关键词筛选
            if (keyword != null && !keyword.isEmpty()) {
                String name = m.getName() != null ? m.getName() : "";
                String phone = m.getPhone() != null ? m.getPhone() : "";
                if (!name.contains(keyword) && !phone.contains(keyword)) {
                    continue;
                }
            }

            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("name", m.getName());
            item.put("phone", m.getPhone());
            item.put("avatarUrl", null);
            item.put("lastClass", lastClassMap.get(memberId));
            item.put("classCount", classCountMap.getOrDefault(memberId, 0L));
            result.add(item);
        }
        return result;
    }

    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private String getStatusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "active": return "在职";
            case "vacation": return "休假";
            case "resigned": return "离职";
            default: return status;
        }
    }
    // =============================================
    // 新增：预约教练相关接口
    // =============================================

    /**
     * 获取某天可预约的教练列表（过滤已请假的教练）
     */
    @GetMapping("/available")
    public List<Trainer> getAvailableTrainers(@RequestParam String date) {
        LocalDate targetDate = LocalDate.parse(date);

        // 1. 查询所有在职教练
        List<Trainer> allTrainers = trainerMapper.selectList(
                new LambdaQueryWrapper<Trainer>().eq(Trainer::getStatus, "active")
        );

        // 2. 查询当天请假的教练ID
        LambdaQueryWrapper<TrainerLeave> leaveWrapper = new LambdaQueryWrapper<>();
        leaveWrapper.eq(TrainerLeave::getLeaveDate, targetDate);
        List<Long> leaveTrainerIds = trainerLeaveMapper.selectList(leaveWrapper)
                .stream().map(TrainerLeave::getTrainerId)
                .collect(Collectors.toList());

        // 3. 过滤掉请假的教练
        return allTrainers.stream()
                .filter(t -> !leaveTrainerIds.contains(t.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取某天某个教练的可用时段（9:00-21:00，排除已预约时段）
     */
    @GetMapping("/{trainerId}/slots")
    public List<String> getAvailableSlots(@PathVariable Long trainerId, @RequestParam String date) {
        LocalDate targetDate = LocalDate.parse(date);
        LocalDateTime now = LocalDateTime.now();

        // 1. 生成 9:00-21:00 的整点时段
        List<String> allSlots = new ArrayList<>();
        for (int h = 9; h <= 21; h++) {
            String slot = String.format("%02d:00", h);
            allSlots.add(slot);
        }

        // 2. 查询该教练当天已预约的时段
        LambdaQueryWrapper<PersonalTraining> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PersonalTraining::getTrainerId, trainerId)
                .eq(PersonalTraining::getStatus, "scheduled")
                .apply("DATE(appointment_time) = {0}", targetDate);
        List<LocalDateTime> bookedTimes = personalTrainingMapper.selectList(wrapper)
                .stream().map(PersonalTraining::getAppointmentTime)
                .collect(Collectors.toList());

        // 3. 过滤已预约时段 + 已过去的时段
        return allSlots.stream()
                .filter(slot -> {
                    LocalDateTime slotTime = LocalDateTime.of(targetDate, LocalTime.parse(slot));
                    // 如果该时段已过当前时间，且是今天，则不可选
                    if (slotTime.isBefore(now) && targetDate.equals(LocalDate.now())) {
                        return false;
                    }
                    return !bookedTimes.contains(slotTime);
                })
                .collect(Collectors.toList());
    }
    @GetMapping("/leaves/pending")
    public List<Map<String, Object>> getPendingLeaves() {
        List<TrainerLeave> leaves = trainerLeaveMapper.selectList(
            new LambdaQueryWrapper<TrainerLeave>().orderByDesc(TrainerLeave::getCreatedAt)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (TrainerLeave l : leaves) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", l.getId());
            item.put("trainerId", l.getTrainerId());
            item.put("leaveDate", l.getLeaveDate() != null ? l.getLeaveDate().toString() : "");
            item.put("reason", l.getReason());
            item.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : "");
            item.put("status", l.getStatus() != null ? l.getStatus() : "pending");

            Trainer trainer = trainerMapper.selectById(l.getTrainerId());
            item.put("trainerName", trainer != null ? trainer.getName() : "未知");
            result.add(item);
        }
        return result;
    }

    @PutMapping("/leaves/{id}/approve")
    public Map<String, Object> approveLeave(@PathVariable Long id, @RequestParam String status) {
        TrainerLeave leave = trainerLeaveMapper.selectById(id);
        Map<String, Object> result = new HashMap<>();
        if (leave == null) {
            result.put("success", false);
            result.put("message", "请假记录不存在");
            return result;
        }
        leave.setStatus(status);
        trainerLeaveMapper.updateById(leave);
        result.put("success", true);
        return result;
    }
}
