package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.Member;
import com.gym.entity.CheckIn;
import com.gym.entity.MemberPrivatePackage;
import com.gym.entity.PersonalTraining;
import com.gym.entity.Trainer;
import com.gym.entity.TrainerLeave;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.MemberPrivatePackageMapper;
import com.gym.mapper.CheckInMapper;
import com.gym.mapper.TrainerMapper;
import com.gym.mapper.TrainerLeaveMapper;
import com.gym.service.PersonalTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personal-trainings")
public class PersonalTrainingController {

    @Autowired
    private PersonalTrainingMapper ptMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private TrainerLeaveMapper trainerLeaveMapper;

    @Autowired
    private PersonalTrainingService personalTrainingService;

    @Autowired
    private CheckInMapper ptCheckInMapper;  // ← 注入 Service

    @Autowired
    private MemberPrivatePackageMapper packageMapper;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String trainerId,  // ← 改名
            HttpSession session) {

        LambdaQueryWrapper<PersonalTraining> wrapper = new LambdaQueryWrapper<>();

        // 优先级：参数 memberId > session > 全量
        if (memberId != null && memberId > 0) {
            wrapper.eq(PersonalTraining::getMemberId, memberId);
        } else {
            Long sessionMemberId = (Long) session.getAttribute("memberId");
            if (sessionMemberId != null) {
                wrapper.eq(PersonalTraining::getMemberId, sessionMemberId);
            }
        }

        // 教练筛选（逻辑不变，只是变量名变了）
        if (trainerId != null && !trainerId.isEmpty()) {
            try {
                wrapper.eq(PersonalTraining::getTrainerId, Long.parseLong(trainerId));
            } catch (NumberFormatException ignored) {}
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PersonalTraining::getStatus, status);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(PersonalTraining::getAppointmentTime, startDate + " 00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(PersonalTraining::getAppointmentTime, endDate + " 23:59:59");
        }
        wrapper.orderByDesc(PersonalTraining::getAppointmentTime);

        IPage<PersonalTraining> pageResult = ptMapper.selectPage(new Page<>(page, size), wrapper);

        // 补充名称信息
        for (PersonalTraining pt : pageResult.getRecords()) {
            if (pt.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(pt.getTrainerId());
                if (trainer != null) pt.setTrainerName(trainer.getName());
            }
            if (pt.getMemberId() != null) {
                Member member = memberMapper.selectById(pt.getMemberId());
                if (member != null) pt.setMemberName(member.getName());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
    }
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = ptMapper.selectCount(null);
        long scheduled = ptMapper.selectCount(
                new LambdaQueryWrapper<PersonalTraining>().eq(PersonalTraining::getStatus, "scheduled")
        );
        long completed = ptMapper.selectCount(
                new LambdaQueryWrapper<PersonalTraining>().eq(PersonalTraining::getStatus, "completed")
        );
        long cancelled = ptMapper.selectCount(
                new LambdaQueryWrapper<PersonalTraining>().eq(PersonalTraining::getStatus, "cancelled")
        );

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("scheduled", scheduled);
        result.put("completed", completed);
        result.put("cancelled", cancelled);
        return result;
    }

    @GetMapping("/{id}")
    public PersonalTraining getById(@PathVariable Long id) {
        PersonalTraining pt = ptMapper.selectById(id);
        if (pt != null) {
            if (pt.getMemberId() != null) {
                Member member = memberMapper.selectById(pt.getMemberId());
                if (member != null) {
                    pt.setMemberName(member.getName());
                }
            }
            if (pt.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(pt.getTrainerId());
                if (trainer != null) {
                    pt.setTrainerName(trainer.getName());
                }
            }
        }
        return pt;
    }

    // ====== 修改：调用 Service 处理课程包扣减 ======
    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, Object> params) {
        try {
            Long memberId = params.get("memberId") != null ? Long.valueOf(params.get("memberId").toString()) : null;
            Long trainerId = params.get("trainerId") != null ? Long.valueOf(params.get("trainerId").toString()) : null;
            String appointmentTimeStr = (String) params.get("appointmentTime");
            Integer durationMinutes = params.get("durationMinutes") != null ? Integer.valueOf(params.get("durationMinutes").toString()) : 60;
            Long packageId = params.get("packageId") != null ? Long.valueOf(params.get("packageId").toString()) : null;
            boolean useFree = params.get("useFree") != null && Boolean.TRUE.equals(params.get("useFree"));

            LocalDateTime appointmentTime = LocalDateTime.parse(appointmentTimeStr.replace(" ", "T"));

            String result = personalTrainingService.bookPersonalTraining(
                    memberId, trainerId, appointmentTime, durationMinutes, packageId, useFree
            );

            Map<String, Object> response = new HashMap<>();
            if (result.startsWith("私教预约成功")) {
                response.put("success", true);
                response.put("message", result);
            } else {
                response.put("success", false);
                response.put("message", result);
            }
            return response;
        } catch (Exception e) {
            System.err.println("Personal training booking error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return error;
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @RequestBody PersonalTraining pt) {
        PersonalTraining old = ptMapper.selectById(id);
        pt.setId(id);
        ptMapper.updateById(pt);

        // ====== 取消时归还免费次数 / 课程包课时（R009）======
        if (old != null && "cancelled".equals(pt.getStatus()) && !"cancelled".equals(old.getStatus())) {
            if (Boolean.TRUE.equals(old.getIsFree())) {
                Member member = memberMapper.selectById(old.getMemberId());
                if (member != null) {
                    int used = member.getFreePtUsedMonth() != null ? member.getFreePtUsedMonth() : 0;
                    if (used > 0) {
                        member.setFreePtUsedMonth(used - 1);
                        memberMapper.updateById(member);
                    }
                }
            }
            if (old.getPackageId() != null) {
                MemberPrivatePackage pkg = packageMapper.selectById(old.getPackageId());
                if (pkg != null) {
                    pkg.setUsedSessions(pkg.getUsedSessions() - 1);
                    pkg.setRemainingSessions(pkg.getRemainingSessions() + 1);
                    packageMapper.updateById(pkg);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public Map<String, Object> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        if (status == null || status.isEmpty()) {
            return errorResponse("请指定状态");
        }

        PersonalTraining pt = ptMapper.selectById(id);
        if (pt == null) {
            return errorResponse("预约不存在");
        }

        // 只有 scheduled 状态才能变更
        if (!"scheduled".equals(pt.getStatus())) {
            return errorResponse("该预约已取消或已完成，无法变更状态");
        }

        // ====== 取消操作的时间限制 ======
        if ("cancelled".equals(status)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime classStart = pt.getAppointmentTime();
            if (classStart != null) {
                // 距离开课不足2小时，不允许取消
                if (now.isAfter(classStart.minusHours(2))) {
                    return errorResponse("距离开课不足2小时，不可取消，请联系前台处理。");
                }
                // 如果已经过了上课时间，也不允许取消
                if (now.isAfter(classStart)) {
                    return errorResponse("课程已开始，不可取消，请联系前台处理。");
                }
            }
        }

        // 更新状态
        pt.setStatus(status);
        int rows = ptMapper.updateById(pt);   // 关键：获取更新行数

        // ====== 检查更新是否成功 ======
        if (rows == 0) {
            return errorResponse("更新失败，请稍后重试");
        }

        // ====== 如果取消的是免费预约，归还免费次数 ======
        if ("cancelled".equals(status) && Boolean.TRUE.equals(pt.getIsFree())) {
            Member member = memberMapper.selectById(pt.getMemberId());
            if (member != null) {
                int used = member.getFreePtUsedMonth();
                if (used > 0) {
                    member.setFreePtUsedMonth(used - 1);
                    memberMapper.updateById(member);
                }
            }
        }

        // ====== 如果取消的是课程包预约，归还课时 ======
        if ("cancelled".equals(status) && pt.getPackageId() != null) {
            MemberPrivatePackage pkg = packageMapper.selectById(pt.getPackageId());
            if (pkg != null) {
                pkg.setUsedSessions(pkg.getUsedSessions() - 1);
                pkg.setRemainingSessions(pkg.getRemainingSessions() + 1);
                packageMapper.updateById(pkg);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "状态更新成功");
        return result;
    }

    // 工具方法
    private Map<String, Object> errorResponse(String msg) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", msg);
        return error;
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public Map<String, Object> cancelByTrainer(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        PersonalTraining pt = ptMapper.selectById(id);
        if (pt == null) {
            result.put("success", false);
            result.put("message", "预约记录不存在");
            return result;
        }
        if ("cancelled".equals(pt.getStatus())) {
            result.put("success", false);
            result.put("message", "该预约已取消");
            return result;
        }
        pt.setStatus("cancelled_by_trainer");
        pt.setCancelReason("教练端取消");
        ptMapper.updateById(pt);
        result.put("success", true);
        result.put("message", "取消成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        ptMapper.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }

    @GetMapping("/export")
    public void exportBookings(HttpServletResponse response) throws IOException {
        List<PersonalTraining> list = ptMapper.selectList(
                new LambdaQueryWrapper<PersonalTraining>().orderByDesc(PersonalTraining::getAppointmentTime)
        );
        list.forEach(pt -> {
            if (pt.getMemberId() != null) {
                Member member = memberMapper.selectById(pt.getMemberId());
                if (member != null) {
                    pt.setMemberName(member.getName());
                }
            }
            if (pt.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(pt.getTrainerId());
                if (trainer != null) {
                    pt.setTrainerName(trainer.getName());
                }
            }
        });

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=私教预约_" + LocalDate.now().toString() + ".csv");

        try (PrintWriter writer = response.getWriter()) {
            writer.write("\uFEFF");
            writer.println("ID,会员,教练,预约时间,时长(分钟),状态,备注,取消原因");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (PersonalTraining pt : list) {
                writer.printf("%d,%s,%s,%s,%d,%s,%s,%s%n",
                        pt.getId(),
                        nullToEmpty(pt.getMemberName()),
                        nullToEmpty(pt.getTrainerName()),
                        pt.getAppointmentTime() != null ? pt.getAppointmentTime().format(fmt) : "",
                        pt.getDurationMinutes() != null ? pt.getDurationMinutes() : 60,
                        getStatusText(pt.getStatus()),
                        nullToEmpty(pt.getNotes()),
                        nullToEmpty(pt.getCancelReason())
                );
            }
            writer.flush();
        }
    }

    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private String getStatusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "scheduled": return "待上课";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            case "cancelled_by_trainer": return "已取消";
            default: return status;
        }
    }
    @PostMapping("/{id}/check-in")
    @Transactional
    public Map<String, Object> manualCheckIn(@PathVariable Long id) {
        PersonalTraining pt = ptMapper.selectById(id);
        Map<String, Object> result = new HashMap<>();
        if (pt == null) {
            result.put("success", false);
            result.put("message", "预约记录不存在");
            return result;
        }
        if (!"scheduled".equals(pt.getStatus())) {
            result.put("success", false);
            result.put("message", "当前状态不可核销");
            return result;
        }

        // 记录签到
        CheckIn ci = new CheckIn();
        ci.setMemberId(pt.getMemberId());
        ci.setPtId(pt.getId());
        ci.setCheckInType("pt");
        ci.setCheckInTime(LocalDateTime.now());
        ci.setRemark("manual_checkin");
        ptCheckInMapper.insert(ci);

        // 更新预约状态
        pt.setStatus("completed");
        ptMapper.updateById(pt);

        result.put("success", true);
        result.put("message", "核销成功");
        return result;
    }
}
