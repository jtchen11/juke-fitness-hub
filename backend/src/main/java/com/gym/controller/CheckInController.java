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

import java.time.LocalDateTime;
import java.util.HashMap;
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
            if (dist > 100) {
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

        // 训练签到 +1 积分
        pointsService.addPoints(memberId, 1, "check_in", checkIn.getId(), "训练打卡签到");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "签到成功！");
        result.put("memberName", member.getName());
        result.put("checkInTime", LocalDateTime.now());
        return result;
    }

    /**
     * 团课签到
     */
    @PostMapping("/class/{classId}")
    public Map<String, Object> checkInClass(@RequestParam Long memberId, @PathVariable Long classId) {
        CheckIn checkIn = new CheckIn();
        checkIn.setMemberId(memberId);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckInType("class");
        checkIn.setClassId(classId);
        checkInMapper.insert(checkIn);

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

        // 4. 封装返回结果
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
        checkInMapper.insert(checkIn);

        // 4. 如果 action = end，标记预约完成
        if ("end".equals(action)) {
            pt.setStatus("completed");
            personalTrainingMapper.updateById(pt);
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
        if (role == null || !"trainer".equals(role)) {
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
        // 3. 检查是否已记录过该动作
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
        // 5. end动作标记完成
        if ("end".equals(action)) {
            pt.setStatus("completed");
            personalTrainingMapper.updateById(pt);
        }
        return successResponse(action.equals("end") ? "下课打卡成功" : "上课打卡成功");
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
}