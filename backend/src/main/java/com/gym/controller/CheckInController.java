package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.*;
import com.gym.mapper.*;
import com.gym.auth.LoginContext;
import com.gym.service.PointsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/check-in")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class CheckInController {

    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;

    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private MemberPrivatePackageMapper packageMapper;

    @Autowired
    private ClassBookingMapper classBookingMapper;

    @Autowired
    private PointsService pointsService;

    @Value("${gym.location.enabled:true}")
    private boolean locationVerifyEnabled;  // ← 新增注入

    /**
     * 会员签到（每次打卡都新增记录，无每日限制）
     */
    @PostMapping("/member/{memberId}")
    public Map<String, Object> checkIn(@PathVariable Long memberId,
                                       @RequestBody(required = false) Map<String, Double> location) {
        // 位置验证 (gym.location config)
        if (location != null && location.containsKey("latitude")) {
            double dist = haversine(
                location.get("latitude"), location.get("longitude"),
                23.142407507050127, 113.34582694066887);
            if (dist > 100000) {
                return errorResponse("请到达健身房后打卡");
            }
        }

        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            return errorResponse("会员不存在");
        }

        CheckIn checkIn = new CheckIn();
        checkIn.setMemberId(memberId);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckInType("normal");
        checkInMapper.insert(checkIn);

        // 积分在签退时根据时长和次数发放

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "签到成功！");
        result.put("memberName", member.getName());
        result.put("checkInTime", LocalDateTime.now());
        return result;
    }

    /**
     * 自主训练签退：计算时长，符合条件发放积分
     */
    @PostMapping("/member/{memberId}/check-out")
    @Transactional
    public Map<String, Object> checkOut(@PathVariable Long memberId) {
        // 1. 查找最近一条无签退时间的 normal 签到记录
        LambdaQueryWrapper<CheckIn> w = new LambdaQueryWrapper<>();
        w.eq(CheckIn::getMemberId, memberId)
                .eq(CheckIn::getCheckInType, "normal")
                .isNull(CheckIn::getCheckOutTime)
                .orderByDesc(CheckIn::getCheckInTime)
                .last("LIMIT 1");
        CheckIn ci = checkInMapper.selectOne(w);
        if (ci == null) {
            return errorResponse("未找到进行中的训练记录");
        }

        // 2. 记录签退时间
        LocalDateTime now = LocalDateTime.now();
        ci.setCheckOutTime(now);
        checkInMapper.updateById(ci);

        // 3. 计算训练时长（分钟）
        long duration = java.time.Duration.between(ci.getCheckInTime(), now).toMinutes();
        if (duration < 40) {
            return successResponse("训练时长" + duration + "分钟，不足40分钟不计积分");
        }

        // 4. 统计今日已签退次数
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LambdaQueryWrapper<CheckIn> countW = new LambdaQueryWrapper<>();
        countW.eq(CheckIn::getMemberId, memberId)
                .eq(CheckIn::getCheckInType, "normal")
                .isNotNull(CheckIn::getCheckOutTime)
                .ge(CheckIn::getCheckOutTime, todayStart);
        long todayCheckOuts = checkInMapper.selectCount(countW);

        if (todayCheckOuts <= 2) {
            ci.setPointsEarned(1);
            checkInMapper.updateById(ci);
            pointsService.addPoints(memberId, 1, "NORMAL_TRAINING", ci.getId(), "自主训练积分");
            return successResponse("训练时长" + duration + "分钟，积分+1");
        }

        return successResponse("训练时长" + duration + "分钟，当日已获2次积分，不再累计");
    }


    /**
     * 团课签到
     */
    @PostMapping("/class/{classId}")
    public Map<String, Object> checkInClass(@RequestParam Long memberId, @PathVariable Long classId) {
        // 防重复：同一会员同一课程只记一次签到，避免重复发放积分
        LambdaQueryWrapper<CheckIn> dupW = new LambdaQueryWrapper<>();
        dupW.eq(CheckIn::getMemberId, memberId)
                .eq(CheckIn::getClassId, classId)
                .eq(CheckIn::getCheckInType, "class");
        if (checkInMapper.selectCount(dupW) > 0) {
            return errorResponse("您已签到过该课程，请勿重复签到");
        }

        GroupClass gc = groupClassMapper.selectById(classId);
        int points = gc != null && "paid".equals(gc.getType()) ? 10 : 1;
        CheckIn checkIn = new CheckIn();
        checkIn.setMemberId(memberId);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckInType("class");
        checkIn.setClassId(classId);
        checkIn.setPointsEarned(points);
        checkInMapper.insert(checkIn);

        // 团课签到积分（公益+1 / 付费+10）
        try {
            pointsService.addPoints(memberId, points, "CLASS_CHECKIN", classId, "团课签到");
        } catch (Exception ignored) {}
        return successResponse("团课签到成功");
    }

    /**
     * 获取会员签到统计
     */
    @GetMapping("/stats/{memberId}")
    public Map<String, Object> getStats(@PathVariable Long memberId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        int total = checkInMapper.countTotal(memberId);
        int thisMonth = checkInMapper.countThisMonth(memberId, startOfMonth);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("thisMonth", thisMonth);
        return result;
    }

    /**
     * 签到统计摘要（今日/本周/本月/分类明细）
     */
    @GetMapping("/stats/summary")
    public Map<String, Object> getSummaryStats() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);

        Map<String, Object> result = new HashMap<>();

        // 总数
        result.put("total", checkInMapper.selectCount(null));

        // 今日
        long today = checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().ge(CheckIn::getCheckInTime, startOfDay)
        );
        result.put("today", today);
        result.put("todayNormal", checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().ge(CheckIn::getCheckInTime, startOfDay)
                        .eq(CheckIn::getCheckInType, "normal")
        ));
        result.put("todayClass", checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().ge(CheckIn::getCheckInTime, startOfDay)
                        .eq(CheckIn::getCheckInType, "class")
        ));
        result.put("todayPt", checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().ge(CheckIn::getCheckInTime, startOfDay)
                        .eq(CheckIn::getCheckInType, "pt")
        ));

        // 本周
        result.put("thisWeek", checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().ge(CheckIn::getCheckInTime, startOfWeek)
        ));

        // 本月
        result.put("thisMonth", checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().ge(CheckIn::getCheckInTime, startOfMonth)
        ));

        return result;
    }

    /**
     * 查询签到记录（分页，支持按会员、日期范围、签到类型筛选）
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String type) {

        // 1. 构建查询条件
        LambdaQueryWrapper<CheckIn> wrapper = new LambdaQueryWrapper<>();

        if (memberId != null && memberId > 0) {
            wrapper.eq(CheckIn::getMemberId, memberId);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(CheckIn::getCheckInTime, startDate + " 00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(CheckIn::getCheckInTime, endDate + " 23:59:59");
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(CheckIn::getCheckInType, type);
            // 私教签到列表仅展示 remark='end'（下训签退，即发积分的那条）
            if ("pt".equals(type)) {
                wrapper.eq(CheckIn::getRemark, "end");
            }
        }

        wrapper.orderByDesc(CheckIn::getCheckInTime);

        // 2. 执行分页查询
        IPage<CheckIn> pageResult = checkInMapper.selectPage(
                new Page<>(page, size), wrapper
        );

        // 3. 补充关联信息（会员姓名、团课名称、私教信息）
        List<CheckIn> records = pageResult.getRecords();
        for (CheckIn record : records) {
            // 3.1 会员姓名
            if (record.getMemberId() != null) {
                Member member = memberMapper.selectById(record.getMemberId());
                if (member != null) {
                    record.setMemberName(member.getName());
                }
            }

            // 3.2 团课名称
            if ("class".equals(record.getCheckInType()) && record.getClassId() != null) {
                GroupClass gc = groupClassMapper.selectById(record.getClassId());
                if (gc != null) {
                    record.setClassName(gc.getName());
                }
            }

            // 3.3 私教信息
            if ("pt".equals(record.getCheckInType()) && record.getPtId() != null) {
                PersonalTraining pt = personalTrainingMapper.selectById(record.getPtId());
                if (pt != null) {
                    String info = "";
                    if (pt.getTrainerId() != null) {
                        Trainer trainer = trainerMapper.selectById(pt.getTrainerId());
                        if (trainer != null) {
                            info = trainer.getName() + " 私教课";
                        }
                    }
                    if (pt.getPackageName() != null) {
                        info = info + "（" + pt.getPackageName() + "）";
                    }
                    record.setPtInfo(info);
                }
            }
        }

        // 4. 计算训练时长与本次签到获得积分
        for (CheckIn record : records) {
            record.setDurationMinutes(null);
            if ("normal".equals(record.getCheckInType())
                    && record.getCheckInTime() != null && record.getCheckOutTime() != null) {
                record.setDurationMinutes(java.time.Duration.between(record.getCheckInTime(), record.getCheckOutTime()).toMinutes());
            }
            // points_earned 直接取自数据库列（签到/补签时已写入实际积分）
        }

        // 5. 封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("list", records);
        result.put("total", pageResult.getTotal());
        return result;
    }


    /**
     * 私教课签到（开始上课打卡）
     */
    @PostMapping("/pt/{ptId}")
    @Transactional
    public Map<String, Object> checkInPt(@PathVariable Long ptId,
                                         @RequestParam Long memberId,
                                         @RequestParam(required = false, defaultValue = "start") String action) {
        // 1. 检查预约是否存在
        PersonalTraining pt = personalTrainingMapper.selectById(ptId);
        if (pt == null) {
            return errorResponse("预约不存在");
        }
        if (!pt.getMemberId().equals(memberId)) {
            return errorResponse("无权操作该预约");
        }
        if (!"scheduled".equals(pt.getStatus())) {
            return errorResponse("该预约已取消或已完成");
        }

        // 2. 检查该 action 是否已签到（区分 start / end）
        LambdaQueryWrapper<CheckIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckIn::getPtId, ptId)
                .eq(CheckIn::getMemberId, memberId)
                .eq(CheckIn::getCheckInType, "pt")
                .eq(CheckIn::getRemark, action);  // 关键：按 action 区分
        if (checkInMapper.selectCount(wrapper) > 0) {
            String actionName = "start".equals(action) ? "上课" : "下课";
            return errorResponse(actionName + "打卡已签到，无需重复签到");
        }

        // 3. 插入签到记录
        CheckIn checkIn = new CheckIn();
        checkIn.setMemberId(memberId);
        checkIn.setPtId(ptId);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckInType("pt");
        checkIn.setRemark(action);  // 存储 start 或 end
        checkIn.setPointsEarned("end".equals(action) ? 10 : 0);
        checkInMapper.insert(checkIn);

        // 4. 如果 action = end，标记预约完成
        if ("end".equals(action)) {
            pt.setStatus("completed");
            personalTrainingMapper.updateById(pt);
            // 私教完成 +10 积分（规则 R060：私教完成得10分）
            try {
                pointsService.addPoints(memberId, 10, "PT_COMPLETED", ptId, "私教完成");
            } catch (Exception ignored) {}
        } else {
            // 上课打卡：可记录上课时间，但暂不改变状态
            // 可选：设置 pt.setStatus("ongoing")，但需要数据库加字段
        }

        return successResponse(action.equals("end") ? "✅ 下课打卡成功！" : "✅ 上课打卡成功！");
    }

    /**
     * 教练端私教上下课打卡（R030-R032：教练扫会员脸确认身份后记录）
     * 会员端不再有私教打卡入口，均由教练端操作
     * 人脸识别接口暂未集成，此处保留接口但暂不校验人脸
     */
    @PostMapping("/pt/coach/{ptId}")
    @Transactional
    public Map<String, Object> coachCheckInPt(@PathVariable Long ptId,
                                               @RequestParam Long memberId,
                                               @RequestParam(required = false, defaultValue = "start") String action) {
        // 1. 校验调用者角色必须为 trainer
        String role = LoginContext.getRole();
        if (role == null || (!"trainer".equals(role) && !"both".equals(role))) {
            return errorResponse("仅教练可执行此操作");
        }
        // 2. 检查预约是否存在
        PersonalTraining pt = personalTrainingMapper.selectById(ptId);
        if (pt == null) {
            return errorResponse("预约不存在");
        }
        if (!pt.getMemberId().equals(memberId)) {
            return errorResponse("会员ID不匹配");
        }
        if (!"scheduled".equals(pt.getStatus())) {
            return errorResponse("该预约已取消或已完成");
        }
        // 3. 时间校验：开课前1小时内才能打卡
        LocalDateTime now3 = LocalDateTime.now();
        LocalDateTime ptStart = pt.getAppointmentTime();
        if (ptStart == null) {
            return errorResponse("预约时间缺失");
        }
        LocalDateTime ptEnd = ptStart.plusMinutes(pt.getDurationMinutes() != null ? pt.getDurationMinutes() : 60);
        if (now3.isBefore(ptStart.minusHours(1))) {
            return errorResponse("距开课超过1小时，暂不可打卡");
        }
        if (now3.isAfter(ptEnd)) {
            return errorResponse("课程已结束，不可打卡");
        }
        // 4. 检查是否已记录过该动作
        LambdaQueryWrapper<CheckIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckIn::getPtId, ptId)
                .eq(CheckIn::getMemberId, memberId)
                .eq(CheckIn::getCheckInType, "pt")
                .eq(CheckIn::getRemark, action);
        if (checkInMapper.selectCount(wrapper) > 0) {
            String actionName = "start".equals(action) ? "上课" : "下课";
            return errorResponse(actionName + "打卡已记录，无需重复操作");
        }
        // 4. 插入签到记录
        CheckIn checkIn = new CheckIn();
        checkIn.setMemberId(memberId);
        checkIn.setPtId(ptId);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckInType("pt");
        checkIn.setRemark(action);
        checkInMapper.insert(checkIn);
        // 5. 根据action更新预约状态
        if ("start".equals(action)) {
            pt.setStatus("ongoing");
            personalTrainingMapper.updateById(pt);
        } else if ("end".equals(action)) {
            pt.setStatus("completed");
            personalTrainingMapper.updateById(pt);
        }
        return successResponse(action.equals("end") ? "下课打卡成功" : "上课打卡成功");
    }

    /**
     * 管理员补签（会员管理页）：支持 自助训练 / 团课签到 / 私教签到
     * 与正常签到积分规则一致，提交前做防重复校验
     */
    @PostMapping("/makeup")
    @Transactional
    public Map<String, Object> makeup(@RequestBody Map<String, Object> params) {
        Long memberId = params.get("memberId") != null ? Long.valueOf(params.get("memberId").toString()) : null;
        String dateStr = (String) params.get("date");
        String type = (String) params.getOrDefault("type", "normal");
        Long classId = params.get("classId") != null ? Long.valueOf(params.get("classId").toString()) : null;
        Long ptId = params.get("ptId") != null ? Long.valueOf(params.get("ptId").toString()) : null;
        Integer durationMinutes = params.get("durationMinutes") != null ? Integer.valueOf(params.get("durationMinutes").toString()) : null;

        if (memberId == null || dateStr == null || dateStr.isEmpty()) {
            return errorResponse("缺少会员或日期参数");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            return errorResponse("会员不存在");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            return errorResponse("日期格式不正确");
        }
        // 核心原则：补签只能补过去日期，不能补签未来
        if (!date.isBefore(LocalDate.now())) {
            return errorResponse("只能补签过去的日期");
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        LocalDateTime checkTime = date.atTime(LocalTime.now());
        int pointsAwarded = 0;

        if ("normal".equals(type)) {
            // 自助训练：同一天允许多次补签，不限制次数；积分仅每日前2次 ≥40分钟 的有效
            CheckIn ci = new CheckIn();
            ci.setMemberId(memberId);
            ci.setCheckInTime(checkTime);
            ci.setCheckInType("normal");
            checkInMapper.insert(ci);
            // 达标且每日前2次发放积分
            if (durationMinutes != null && durationMinutes >= 40) {
                ci.setCheckOutTime(checkTime.plusMinutes(durationMinutes));
                checkInMapper.updateById(ci);
                long dayCheckouts = checkInMapper.selectCount(new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getMemberId, memberId)
                        .eq(CheckIn::getCheckInType, "normal")
                        .isNotNull(CheckIn::getCheckOutTime)
                        .ge(CheckIn::getCheckInTime, dayStart).lt(CheckIn::getCheckInTime, dayEnd));
                if (dayCheckouts <= 2) {
                    pointsAwarded = 1;
                    pointsService.addPoints(memberId, 1, "NORMAL_TRAINING", ci.getId(), "自助训练积分(补签)");
                }
            }
            ci.setPointsEarned(pointsAwarded);
            checkInMapper.updateById(ci);
        } else if ("class".equals(type)) {
            if (classId == null) {
                return errorResponse("团课补签请选择课程");
            }
            GroupClass gc = groupClassMapper.selectById(classId);
            if (gc == null) {
                return errorResponse("课程不存在");
            }
            // 校验该会员预约了该课程（仅排除已取消，不限制课程开始时间）
            long bookingCnt = classBookingMapper.selectCount(new LambdaQueryWrapper<ClassBooking>()
                    .eq(ClassBooking::getMemberId, memberId)
                    .eq(ClassBooking::getClassId, classId)
                    .in(ClassBooking::getStatus, "booked", "checked_in"));
            if (bookingCnt == 0) {
                return errorResponse("该会员未预约该课程或预约已取消");
            }
            long dup = checkInMapper.selectCount(new LambdaQueryWrapper<CheckIn>()
                    .eq(CheckIn::getMemberId, memberId)
                    .eq(CheckIn::getClassId, classId)
                    .eq(CheckIn::getCheckInType, "class"));
            if (dup > 0) {
                return errorResponse("该会员已签到过该课程，请勿重复补签");
            }
            int pts = "paid".equals(gc.getType()) ? 10 : 1;
            CheckIn ci = new CheckIn();
            ci.setMemberId(memberId);
            ci.setCheckInTime(checkTime);
            ci.setCheckInType("class");
            ci.setClassId(classId);
            ci.setPointsEarned(pts);
            checkInMapper.insert(ci);
            pointsService.addPoints(memberId, pts, "CLASS_CHECKIN", classId, "团课签到(补签)");
            pointsAwarded = pts;
        } else if ("pt".equals(type)) {
            if (ptId == null) {
                return errorResponse("私教补签请选择预约");
            }
            PersonalTraining pt = personalTrainingMapper.selectById(ptId);
            if (pt == null) {
                return errorResponse("私教预约不存在");
            }
            if (!pt.getMemberId().equals(memberId)) {
                return errorResponse("该预约不属于此会员");
            }
            if (!"scheduled".equals(pt.getStatus()) && !"ongoing".equals(pt.getStatus())) {
                return errorResponse("该预约已取消或已完成，无法补签");
            }
            long dup = checkInMapper.selectCount(new LambdaQueryWrapper<CheckIn>()
                    .eq(CheckIn::getPtId, ptId)
                    .eq(CheckIn::getMemberId, memberId)
                    .eq(CheckIn::getCheckInType, "pt")
                    .eq(CheckIn::getRemark, "end"));
            if (dup > 0) {
                return errorResponse("该预约已私教签退，请勿重复补签");
            }
            CheckIn ci = new CheckIn();
            ci.setMemberId(memberId);
            ci.setPtId(ptId);
            ci.setCheckInTime(checkTime);
            ci.setCheckInType("pt");
            ci.setRemark("end");
            ci.setPointsEarned(10);
            checkInMapper.insert(ci);
            if ("scheduled".equals(pt.getStatus())) {
                pt.setStatus("completed");
                personalTrainingMapper.updateById(pt);
            }
            pointsService.addPoints(memberId, 10, "PT_COMPLETED", ptId, "私教完成(补签)");
            pointsAwarded = 10;
        } else {
            return errorResponse("不支持的签到类型");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "补签成功" + (pointsAwarded > 0 ? "，积分+" + pointsAwarded : "（未达积分条件）"));
        result.put("memberName", member.getName());
        result.put("pointsAwarded", pointsAwarded);
        return result;
    }

    // =============================================
    // 工具方法
    // =============================================

    private Map<String, Object> successResponse(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", msg);
        return result;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Map<String, Object> errorResponse(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", msg);
        return result;
    }
    /**
     * 教练生成团课签到码（R105-新）
     */
    @PostMapping("/class/{classId}/generate-code")
    @Transactional
    public Map<String, Object> generateCode(@PathVariable Long classId) {
        GroupClass gc = groupClassMapper.selectById(classId);
        if (gc == null) return errorResponse("课程不存在");
        // 如果已有签到码，直接返回不再重新生成
        if (gc.getCheckinCode() != null && !gc.getCheckinCode().isEmpty()) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("code", gc.getCheckinCode());
            r.put("message", "签到码已存在，无需重新生成");
            r.put("startTime", gc.getStartTime() != null ? gc.getStartTime().toString() : "");
            r.put("endTime", gc.getEndTime() != null ? gc.getEndTime().toString() : "");
            return r;
        }
        // 生成6位随机数字码
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        gc.setCheckinCode(code);
        gc.setCodeGeneratedAt(LocalDateTime.now());
        groupClassMapper.updateById(gc);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("code", code);
        r.put("message", "签到码已生成");
        r.put("startTime", gc.getStartTime() != null ? gc.getStartTime().toString() : "");
        r.put("endTime", gc.getEndTime() != null ? gc.getEndTime().toString() : "");
        return r;
    }

    /**
     * 会员验证签到码并签到（R106-新）
     */
    @PostMapping("/class/{classId}/verify-code")
    @Transactional
    public Map<String, Object> verifyCode(@PathVariable Long classId,
                                          @RequestParam Long memberId,
                                          @RequestParam String code) {
        GroupClass gc = groupClassMapper.selectById(classId);
        if (gc == null) return errorResponse("课程不存在");
        if (gc.getCheckinCode() == null || !gc.getCheckinCode().equals(code))
            return errorResponse("签到码错误");
        if (gc.getCodeGeneratedAt() == null) return errorResponse("签到码已失效");
        // 校验有效期：开课前15分钟~课程结束
        LocalDateTime now = LocalDateTime.now();
        if (gc.getStartTime() != null && now.isBefore(gc.getStartTime().minusMinutes(15)))
            return errorResponse("距开课超过15分钟，签到码暂未生效");
        if (gc.getEndTime() != null && now.isAfter(gc.getEndTime()))
            return errorResponse("课程已结束，签到码已失效");
        // 检查是否已签到
        LambdaQueryWrapper<CheckIn> w = new LambdaQueryWrapper<>();
        w.eq(CheckIn::getClassId, classId).eq(CheckIn::getMemberId, memberId).eq(CheckIn::getCheckInType, "class");
        if (checkInMapper.selectCount(w) > 0) return errorResponse("已签到，无需重复签到");
        // 记录签到
        int pts = gc.getType() != null && "paid".equals(gc.getType()) ? 10 : 1;
        CheckIn ci = new CheckIn();
        ci.setMemberId(memberId);
        ci.setClassId(classId);
        ci.setCheckInTime(now);
        ci.setCheckInType("class");
        ci.setRemark("code");
        ci.setPointsEarned(pts);
        checkInMapper.insert(ci);
        // 团课签到积分
        try {
            pointsService.addPoints(memberId, pts, "CLASS_CHECKIN", classId, "团课签到码签到");
        } catch (Exception ignored) {}
        return successResponse("签到成功");
    }

    @GetMapping("/coach-stats")
    public List<Map<String, Object>> getCoachCheckInStats() {
        List<Trainer> trainers = trainerMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);

        for (Trainer t : trainers) {
            Map<String, Object> item = new HashMap<>();
            item.put("trainerId", t.getId());
            item.put("trainerName", t.getName());

            // 该教练所有 personal_training 的 ptId
            List<PersonalTraining> pts = personalTrainingMapper.selectList(
                new LambdaQueryWrapper<PersonalTraining>().eq(PersonalTraining::getTrainerId, t.getId())
            );
            long ptCheckIns = 0;
            long ptTotal = pts.size();
            for (PersonalTraining pt : pts) {
                LambdaQueryWrapper<CheckIn> ciw = new LambdaQueryWrapper<>();
                ciw.eq(CheckIn::getPtId, pt.getId());
                long cnt = checkInMapper.selectCount(ciw);
                if (cnt > 0) ptCheckIns++;
            }
            item.put("total", ptTotal);
            item.put("checkIns", ptCheckIns);

            // 本月签到数
            long monthTotal = 0;
            for (PersonalTraining pt : pts) {
                if (pt.getAppointmentTime() != null && pt.getAppointmentTime().isAfter(monthStart)) {
                    monthTotal++;
                }
            }
            item.put("monthTotal", monthTotal);

            result.add(item);
        }
        return result;
    }
}
