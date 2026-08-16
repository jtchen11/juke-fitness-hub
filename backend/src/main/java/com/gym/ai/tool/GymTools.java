package com.gym.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.ai.model.PaymentAction;
import com.gym.ai.model.ToolResult;
import com.gym.entity.*;
import com.gym.mapper.*;
import com.gym.service.GroupClassService;
import com.gym.service.MemberLevelService;
import com.gym.service.PersonalTrainingService;
import dev.langchain4j.agent.tool.Tool;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.lang.Boolean;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class GymTools {
    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;      // 查私教预约

    @Autowired
    private ClassBookingMapper classBookingMapper;
    @Autowired
    private CompetitionMapper competitionMapper;   // 查团课预约

    @Autowired
    private MemberPrivatePackageMapper memberPrivatePackageMapper; // 查课程包
    @Autowired private GroupClassMapper groupClassMapper;

    @Autowired
    private GroupClassService groupClassService;

    @Autowired
    private PersonalTrainingService ptService;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private MemberLevelService levelService;

    @Autowired
    private FitnessTestMapper fitnessTestMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("查询指定时间范围内的可预约团课课程列表，参数：开始时间(yyyy-MM-dd HH:mm:ss)，结束时间(yyyy-MM-dd HH:mm:ss)")
    public ToolResult<String> queryAvailableClasses(String startTime, String endTime) {
        return queryAvailableClasses(startTime, endTime, null);
    }

    public ToolResult<String> queryAvailableClasses(String startTime, String endTime, String type) {
        return queryAvailableClasses(startTime, endTime, type, null);
    }

    public ToolResult<String> queryAvailableClasses(String startTime, String endTime, String type, Boolean allowVisitor) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);
            List<GroupClass> classes = groupClassService.getAvailableClasses(start, end, type, allowVisitor);

            if (classes.isEmpty()) {
                return ToolResult.success("在 " + startTime + " 到 " + endTime + " 范围内没有可预约的团课。建议扩大时间范围或选择其他日期。无需继续调用工具。");
            }

            StringBuilder sb = new StringBuilder();
            boolean isFreeFilter = "free".equals(type);
            sb.append("共找到 ").append(classes.size()).append(isFreeFilter ? " 门免费可预约团课：\n" : " 门可预约团课：\n");
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
            for (int i = 0; i < classes.size(); i++) {
                GroupClass gc = classes.get(i);
                String timeStr = gc.getStartTime() != null ? gc.getStartTime().format(dtf) : "";
                String endTimeStr = gc.getEndTime() != null ? "" : "";
                if (gc.getEndTime() != null) { endTimeStr = gc.getEndTime().format(dtf); }
                String priceStr = ("free".equals(gc.getType()) || gc.getPrice() == null || gc.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) ? "免费" : "￥" + gc.getPrice().toString();
                int remaining = gc.getMaxCapacity() - gc.getEnrolled();
                sb.append(i + 1).append(". ").append(gc.getName() != null ? gc.getName() : "")
                        .append(" - ").append(timeStr);
                if (gc.getEndTime() != null) {
                    String[] endParts = endTimeStr.split(" ");
                    if (endParts.length > 1) { sb.append("-").append(endParts[1]); }
                }
                sb.append(" - ").append(priceStr)
                        .append("（剩余 ").append(remaining).append(" 人）\n");
            }
            sb.append("\n请根据以上信息为用户推荐合适的团课。无需继续调用工具。");
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("查询团课失败，时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式。示例：2026-06-28 09:00:00。无需继续调用工具。");
        }
    }

    @Tool("推荐未来一段时间内适合报名的团课列表，参数：开始时间(yyyy-MM-dd HH:mm:ss)，结束时间(yyyy-MM-dd HH:mm:ss)")
    public ToolResult<String> recommendGroupClasses(String startTime, String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);
            java.util.List<GroupClass> classes = groupClassService.getAvailableClasses(start, end, null, null);

            // 过滤已满员课程
            java.util.List<GroupClass> available = new java.util.ArrayList<>();
            for (GroupClass gc : classes) {
                int enrolled = gc.getEnrolled() != null ? gc.getEnrolled() : 0;
                int cap = gc.getMaxCapacity() != null ? gc.getMaxCapacity() : 0;
                if (enrolled < cap) {
                    available.add(gc);
                }
            }
            if (available.isEmpty()) {
                return ToolResult.success("当前时间段没有可推荐的团课（课程均已满员）。无需继续调用工具。");
            }

            // 按预约人数降序排序，取前3门
            available.sort((a, b) -> {
                int ea = a.getEnrolled() != null ? a.getEnrolled() : 0;
                int eb = b.getEnrolled() != null ? b.getEnrolled() : 0;
                return Integer.compare(eb, ea);
            });
            int limit = Math.min(3, available.size());
            java.util.List<GroupClass> top = available.subList(0, limit);

            StringBuilder sb = new StringBuilder();
            sb.append("为您推荐 ").append(top.size()).append(" 门热门团课：\n");
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
            for (int i = 0; i < top.size(); i++) {
                GroupClass gc = top.get(i);
                String timeStr = gc.getStartTime() != null ? gc.getStartTime().format(dtf) : "";
                String endTimeStr = "";
                if (gc.getEndTime() != null) { endTimeStr = gc.getEndTime().format(dtf); }
                String priceStr = ("free".equals(gc.getType()) || gc.getPrice() == null || gc.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) ? "免费" : "￥" + gc.getPrice().toString();
                int remaining = gc.getMaxCapacity() - gc.getEnrolled();
                sb.append(i + 1).append(". ").append(gc.getName() != null ? gc.getName() : "")
                        .append(" - ").append(timeStr);
                if (gc.getEndTime() != null) {
                    String[] endParts = endTimeStr.split(" ");
                    if (endParts.length > 1) { sb.append("-").append(endParts[1]); }
                }
                sb.append(" - ").append(priceStr)
                        .append("（已约 ").append(gc.getEnrolled()).append(" 人，剩余 ").append(remaining).append(" 人）\n");
            }
            sb.append("\n如需预约请告诉我课程名称。无需继续调用工具。");
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("推荐团课失败，时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式。无需继续调用工具。");
        }
    }

    @Tool("为会员预约团课，参数：会员ID，课程ID")
    public ToolResult<String> bookGroupClass(Long memberId, Long classId) {
        try {
            if (memberId == null || memberId <= 0) {
                return ToolResult.fail("预约失败：会员ID无效，请确认后重试。无需继续调用工具。");
            }
            if (classId == null || classId <= 0) {
                return ToolResult.fail("预约失败：课程ID无效，请确认后重试。无需继续调用工具。");
            }
            String result = groupClassService.bookClass(memberId, classId);
            if (result != null && result.contains("成功")) {
                                com.gym.entity.Member m = memberMapper.selectById(memberId);
                if (m != null && m.isVisitor()) {
                    return ToolResult.success("预约成功！您已使用免费体验券预约课程，剩余体验次数：0。无需继续调用工具。");
                }
                return ToolResult.success(result + "无需继续调用工具。");
            } else {
                return ToolResult.fail("预约失败：" + (result != null ? result : "未知错误，请重试") + "。无需继续调用工具。");
            }
        } catch (Exception e) {
            return ToolResult.fail("预约失败，系统异常：" + e.getMessage() + "。无需继续调用工具。");
        }
    }

    @Tool("预约私教课，参数：会员ID，教练ID，预约时间(yyyy-MM-dd HH:mm:ss)，时长(分钟，默认60)")
    public ToolResult<String> bookPersonalTraining(Long memberId, Long trainerId, String appointmentTime, Integer duration) {
        try {
            if (memberId == null || memberId <= 0) {
                return ToolResult.fail("预约失败：会员ID无效，请确认后重试。无需继续调用工具。");
            }
            if (trainerId == null || trainerId <= 0) {
                return ToolResult.fail("预约失败：教练ID无效，请确认后重试。无需继续调用工具。");
            }
            LocalDateTime time = LocalDateTime.parse(appointmentTime, FORMATTER);
            if (duration == null || duration <= 0) {
                duration = 60;
            }
            String result = ptService.bookPersonalTraining(memberId, trainerId, time, duration);

            if (result != null && result.startsWith("私教预约成功")) {
                Trainer trainer = trainerMapper.selectById(trainerId);
                String trainerName = (trainer != null) ? trainer.getName() : "该教练";
                return ToolResult.success(result + "。已为您预约 " + trainerName + " 的课程，时间：" + appointmentTime + "。无需继续调用工具。");
            } else {
                return ToolResult.fail("预约失败：" + (result != null ? result : "未知错误，请重试") + "。\n请检查：1) 教练是否在当天请假 2) 该时段是否已被预约 3) 选择其他时间再试。无需继续调用工具。");
            }
        } catch (Exception e) {
            return ToolResult.fail("预约失败，时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式。示例：2026-06-28 14:00:00。无需继续调用工具。");
        }
    }

    @Tool("查询所有教练列表")
    public ToolResult<String> listAllTrainers() {
        try {
            List<Trainer> trainers = trainerMapper.selectList(null);
            if (trainers == null || trainers.isEmpty()) {
                return ToolResult.success("暂无教练信息。无需继续调用工具。");
            }
            java.util.List<Trainer> activeTrainers = new java.util.ArrayList<>();
            for (Trainer t : trainers) {
                if ("active".equals(t.getStatus())) {
                    activeTrainers.add(t);
                }
            }
            if (activeTrainers.isEmpty()) {
                return ToolResult.success("暂无在职教练信息。无需继续调用工具。");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(activeTrainers.size()).append(" 位在职教练：\n");
            for (int i = 0; i < activeTrainers.size(); i++) {
                Trainer t = activeTrainers.get(i);
                sb.append(i + 1).append(". ").append(t.getName())
                        .append("，专长：").append(t.getSpecialty() != null ? t.getSpecialty() : "未设置")
                        .append("，价格：").append(t.getPricePerHour()).append("元/小时")
                        .append("，状态：在职\n");
            }
            sb.append("无需继续调用工具。");
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("获取教练列表异常：" + e.getMessage() + "。无需继续调用工具。");
        }
    }

    @Tool("根据会员等级和体测数据推荐适合的教练，参数：会员ID")
    public ToolResult<String> recommendTrainerByLevel(Long memberId) {
        try {
            if (memberId == null || memberId <= 0) {
                return ToolResult.fail("推荐失败：会员ID无效，请确认后重试。无需继续调用工具。");
            }
            Member member = memberMapper.selectById(memberId);
            if (member == null) {
                return ToolResult.fail("会员（ID：" + memberId + "）不存在，请确认会员ID是否正确。无需继续调用工具。");
            }
            // P2-8: 访客拦截
            if (member.isVisitor()) {
                return ToolResult.fail("访客暂不支持教练推荐功能，请注册会员后再使用。无需继续调用工具。");
            }
            String level = member.getLevel();
            if (level == null) level = "普通会员";
            // P2-9: 查询最新体测数据
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FitnessTest> fw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            fw.eq(FitnessTest::getMemberId, memberId).orderByDesc(FitnessTest::getTestDate).last("LIMIT 1");
            FitnessTest ft = fitnessTestMapper.selectOne(fw);
            String goal = "塑形";  // 默认
            if (ft != null && ft.getBodyFatPercent() != null) {
                double bf = ft.getBodyFatPercent().doubleValue();
                String gender = member.getGender();
                boolean isHigh = ("男".equals(gender) && bf > 25) || (!"男".equals(gender) && bf > 32);
                if (isHigh) { goal = "减脂"; }
            }
            // 按会员等级确定目标价格区间
            double maxPrice;
            if ("铂金会员".equals(level)) { maxPrice = 9999; }
            else if ("黄金会员".equals(level)) { maxPrice = 350; }
            else { maxPrice = 300; }
            // 查询匹配的教练
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Trainer> tw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            tw.eq(Trainer::getStatus, "active");
            if (maxPrice < 9999) { tw.le(Trainer::getPricePerHour, maxPrice); }
            if ("减脂".equals(goal)) {
                tw.like(Trainer::getSpecialty, "减脂");
            } else {
                // 塑形优先，其次增肌/康复
                tw.and(w -> w.like(Trainer::getSpecialty, "塑形")
                    .or().like(Trainer::getSpecialty, "增肌")
                    .or().like(Trainer::getSpecialty, "康复"));
            }
            java.util.List<Trainer> trainers = trainerMapper.selectList(tw);
            StringBuilder sb = new StringBuilder();
            sb.append("当前会员等级：").append(level);
            if (ft != null && ft.getBodyFatPercent() != null) {
                sb.append("，体脂率：").append(ft.getBodyFatPercent()).append("%");
            }
            sb.append("，建议方向：").append(goal).append("\n");
            if (trainers == null || trainers.isEmpty()) {
                sb.append("暂未找到完全匹配的教练，推荐李教练，专长：减脂塑形，价格：300元/小时。");
            } else {
                for (int i = 0; i < Math.min(trainers.size(), 3); i++) {
                    Trainer t = trainers.get(i);
                    sb.append(i+1).append(". ").append(t.getName()).append("教练");
                    if (t.getSpecialty() != null) sb.append("，专长：").append(t.getSpecialty());
                    if (t.getPricePerHour() != null) sb.append("，价格：").append(t.getPricePerHour()).append("元/小时");
                    if (t.getIntro() != null) sb.append("，简介：").append(t.getIntro());
                    sb.append("\n");
                }
            }
            sb.append("以上推荐仅供参考，具体可根据会员需求调整。无需继续调用工具。");
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("推荐失败，系统异常：" + e.getMessage() + "。无需继续调用工具。");
        }
    }

    @Tool("根据会员最近的体测数据生成锻炼建议和重点锻炼部位，参数：会员ID（从登录信息中获取）")
    public ToolResult<String> generateWorkoutAdvice(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return ToolResult.fail("查询失败：会员ID无效，无法查询体测数据。请确认当前用户已登录。无需继续调用工具。");
        }

        try {
            LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FitnessTest::getMemberId, memberId)
                    .orderByDesc(FitnessTest::getTestDate)
                    .last("LIMIT 3");
            List<FitnessTest> tests = fitnessTestMapper.selectList(wrapper);

            if (tests == null || tests.isEmpty()) {
                return ToolResult.success("会员（ID：" + memberId + "）暂无体测数据。建议联系前台预约体测，数据录入后再查询。无需继续调用工具。");
            }

            FitnessTest latest = tests.get(0);
            StringBuilder advice = new StringBuilder();
            advice.append("根据最新体测数据（").append(latest.getTestDate()).append("）：\n");

            // 处理 BigDecimal 类型转换
            BigDecimal weightKg = latest.getWeightKg();
            advice.append("体重：").append(weightKg != null ? weightKg : "--").append(" kg\n");

            BigDecimal bodyFat = latest.getBodyFatPercent();
            advice.append("体脂率：").append(bodyFat != null ? bodyFat : "--").append("%\n");

            BigDecimal muscleMass = latest.getMuscleMassKg();
            advice.append("肌肉量：").append(muscleMass != null ? muscleMass : "--").append(" kg\n\n");

            advice.append("建议：\n");

            boolean hasAdvice = false;

            if (bodyFat != null) {
                double fat = bodyFat.doubleValue();
                if (fat > 25) {
                    advice.append("- 体脂率偏高，建议：每周至少3次有氧运动 + 饮食控制（减少碳水摄入）\n");
                    hasAdvice = true;
                } else if (fat < 12) {
                    advice.append("- 体脂率偏低，建议：增加营养摄入 + 力量训练（减少有氧）\n");
                    hasAdvice = true;
                } else {
                    advice.append("- 体脂率正常，建议：维持当前训练计划\n");
                    hasAdvice = true;
                }
            }

            if (muscleMass != null) {
                double muscle = muscleMass.doubleValue();
                if (muscle < 30) {
                    advice.append("- 肌肉量偏低，建议：每周至少2次力量训练 + 增加蛋白质摄入（鸡胸肉、蛋白粉）\n");
                    hasAdvice = true;
                } else {
                    advice.append("- 肌肉量良好，建议：保持力量训练，可适当增加强度\n");
                    hasAdvice = true;
                }
            }

            if (!hasAdvice) {
                advice.append("- 数据不足，建议补充完整体测数据后再分析。\n");
            }

            advice.append("\n以上建议仅供参考，具体训练计划请咨询专业教练。无需继续调用工具。");
            return ToolResult.success(advice.toString());
        } catch (Exception e) {
            return ToolResult.fail("体测数据分析异常：" + e.getMessage() + "，请稍后重试。无需继续调用工具。");
        }
    }

    // ====== 新增：生成一周训练计划骨架（JSON格式） ======
        @Tool("根据会员体测数据动态生成一周训练计划骨架（JSON格式），体脂高增加有氧、肌肉量低增加力量")
    public ToolResult<String> generateWorkoutPlanSkeleton(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return ToolResult.fail("{\"error\": \"会员ID无效\"}");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            return ToolResult.fail("{\"error\": \"未找到该会员\"}");
        }

        FitnessTest latest = getLatestFitnessTest(memberId);
        String name = member.getName() != null ? member.getName() : "会员";

        // ====== 根据体测数据动态计算训练比例 ======
        int strengthDays = 3;  // 默认力量训练天数
        int cardioDays = 3;    // 默认有氧训练天数
        String focus = "均衡训练";
        String intensity = "中级";

        if (latest != null) {
            double bodyFat = latest.getBodyFatPercent() != null ? latest.getBodyFatPercent().doubleValue() : 0.0;
            double muscleMass = latest.getMuscleMassKg() != null ? latest.getMuscleMassKg().doubleValue() : 0.0;
            double weight = latest.getWeightKg() != null ? latest.getWeightKg().doubleValue() : 0.0;

            // 获取会员性别，用于体脂/肌肉量判断
            String genderSK = member.getGender() != null ? member.getGender() : "男";
            double fatHighSK = "女".equals(genderSK) ? 32.0 : 25.0;
            double muscleLowSK = "女".equals(genderSK) ? 25.0 : 30.0;
            boolean highFat = bodyFat > fatHighSK;
            boolean lowMuscle = muscleMass > 0.0 && muscleMass < muscleLowSK;

            if (highFat && !lowMuscle) {
                // 体脂高但肌肉量正常 → 减脂为主，多增加有氧
                strengthDays = 2;
                cardioDays = 4;
                focus = "减脂为主";
            } else if (lowMuscle && !highFat) {
                // 肌肉量低但体脂正常 → 增肌为主，多增加力量
                strengthDays = 4;
                cardioDays = 2;
                focus = "增肌为主";
            } else if (highFat && lowMuscle) {
                // 体脂高且肌肉量低 → 减脂增肌并重，适当增加训练量
                strengthDays = 3;
                cardioDays = 3;
                focus = "减脂增肌并重";
            } else {
                // 体脂和肌肉量正常 → 维持均衡训练
                strengthDays = 3;
                cardioDays = 3;
                focus = "维持均衡";
            }

            // 根据会员等级确定训练强度
            String level = member.getLevel() != null ? member.getLevel() : "普通会员";
            if (level.contains("铂金") || level.contains("黄金")) {
                intensity = "高级";
            } else {
                intensity = "中级";
            }
        } else {
            // 无体测数据，使用默认通用计划
        }

        // ====== 构建动态一周训练安排 ======
        String[][] strengthWorkouts = {
            {"胸部训练（杠铃卧推 + 哑铃飞鸟 + 俯卧撑）"},
            {"背部训练（高位下拉 + 俯身划船 + 坐姿划船）"},
            {"腿部训练（深蹲 + 腿举 + 罗马尼亚硬拉）"},
            {"肩部训练（哑铃推举 + 侧平举 + 前平举）"}
        };
        String[][] cardioWorkouts = {
            {"30分钟慢跑"},
            {"30分钟椭圆机"},
            {"30分钟动感单车"},
            {"30分钟划船机"},
            {"30分钟快走"}
        };

        List<Map<String, String>> weeklyPlan = new ArrayList<>();
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        // 交替安排力量和训练日
        int sIdx = 0, cIdx = 0;
        for (int d = 0; d < 7; d++) {
            Map<String, String> day = new LinkedHashMap<>();
            day.put("day", days[d]);
            if (d == 6) {
                // 周日休息
                day.put("strengthTraining", "完全休息");
                day.put("cardio", "无");
                day.put("duration", "0分钟");
            } else if (d % 2 == 0 && sIdx < strengthDays) {
                // 力量训练日
                day.put("strengthTraining", strengthWorkouts[sIdx % strengthWorkouts.length][0]);
                day.put("cardio", "20分钟快走（热身）");
                day.put("duration", "60分钟");
                sIdx++;
            } else if (cIdx < cardioDays) {
                // 有氧训练日
                day.put("strengthTraining", "核心训练（平板支撑 + 卷腹 + 俄罗斯转体）");
                day.put("cardio", cardioWorkouts[cIdx % cardioWorkouts.length][0]);
                day.put("duration", "60分钟");
                cIdx++;
            } else {
                // 日常活动
                day.put("strengthTraining", "低强度活动");
                day.put("cardio", "30分钟快走");
                day.put("duration", "30分钟");
            }
            weeklyPlan.add(day);
        }

        // ====== 构建 JSON ======
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("memberName", name);
        plan.put("memberLevel", member.getLevel() != null ? member.getLevel() : "普通会员");
        plan.put("fitnessGoal", focus);
        plan.put("planType", "training");
        plan.put("trainingIntensity", intensity);
        plan.put("strengthDays", strengthDays);
        plan.put("cardioDays", cardioDays);

        if (latest != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("weight", latest.getWeightKg() != null ? latest.getWeightKg() : "未测量");
            data.put("bodyFat", latest.getBodyFatPercent() != null ? latest.getBodyFatPercent() : "未测量");
            data.put("muscleMass", latest.getMuscleMassKg() != null ? latest.getMuscleMassKg() : "未测量");
            data.put("testDate", latest.getTestDate() != null ? latest.getTestDate().toString() : "未知");
            plan.put("latestTestData", data);
        } else {
            plan.put("latestTestData", "暂无体测数据，以下为通用减脂计划，建议先进行体测评估");
        }

        plan.put("weeklyPlan", weeklyPlan);

        List<String> principles = Arrays.asList(
            "每次训练前热身10分钟，训练后拉伸10分钟",
            "力量训练重量选择：每组8-12次，做到力竭",
            "有氧心率控制在最大心率的60%-70%",
            "训练强度根据自身感受调整，循序渐进",
            "保证充足睡眠（7-8小时），促进恢复",
            "如感到不适，立即停止训练"
        );
        plan.put("trainingPrinciples", principles);

        try {
            return ToolResult.success(objectMapper.writeValueAsString(plan));
        } catch (Exception e) {
            return ToolResult.fail("{\"error\": \"生成训练计划失败：" + e.getMessage() + "\"}");
        }
    }

    @Tool("根据会员体测数据动态生成一周饮食计划骨架（JSON格式）")
public ToolResult<String> generateMealPlanSkeleton(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return ToolResult.fail("{\"error\": \"会员ID无效\"}");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            return ToolResult.fail("{\"error\": \"未找到该会员\"}");
        }

        FitnessTest latest = getLatestFitnessTest(memberId);
        String name = member.getName() != null ? member.getName() : "会员";

        // 处理 BigDecimal 转 double
        double weight = 70;
        if (latest != null && latest.getWeightKg() != null) {
            weight = latest.getWeightKg().doubleValue();
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("memberName", name);
        plan.put("targetCalories", (int)(weight * 25));
        plan.put("dietGoal", "减脂期饮食");
        plan.put("planType", "meal");

        if (latest != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("weight", latest.getWeightKg() != null ? latest.getWeightKg() : "未测量");
            data.put("bodyFat", latest.getBodyFatPercent() != null ? latest.getBodyFatPercent() : "未测量");
            data.put("testDate", latest.getTestDate() != null ? latest.getTestDate().toString() : "未知");
            plan.put("latestTestData", data);
        } else {
            plan.put("latestTestData", "暂无体测数据，以下为通用减脂饮食建议");
        }

        plan.put("principles", Arrays.asList(
                "高蛋白：每公斤体重摄入1.6-2.2g蛋白质",
                "适量碳水：优先选择粗粮，控制总量",
                "低脂肪：避免油炸和加工食品",
                "多蔬菜：每餐保证蔬菜占一半",
                "充足水分：每日至少2L水，避免含糖饮料"
        ));

        // 一周饮食安排
        List<Map<String, String>> weeklyMeals = new ArrayList<>();
        String[][] meals = {
                {"周一", "燕麦粥+水煮蛋", "糙米饭+烤鸡胸+西兰花", "清蒸鱼+蒸蔬菜"},
                {"周二", "全麦三明治+酸奶", "红薯+牛肉片+凉拌黄瓜", "鸡胸肉汤+全麦面包"},
                {"周三", "杂粮粥+水煮蛋+苹果", "藜麦沙拉+三文鱼", "豆腐菌菇汤+少量主食"},
                {"周四", "全麦面包+花生酱+香蕉", "全麦意面+瘦猪肉+番茄酱", "烤鸡腿+烤蔬菜"},
                {"周五", "蔬菜煎蛋+全麦吐司", "杂粮饭+豆腐+青菜", "海鲜沙拉"},
                {"周六", "隔夜燕麦+坚果", "外出可选择轻食沙拉", "清淡蔬菜汤"},
                {"周日", "周末营养早餐", "可安排一次欺骗餐", "与午餐类似，减少主食量"}
        };
        for (String[] m : meals) {
            Map<String, String> day = new LinkedHashMap<>();
            day.put("day", m[0]);
            day.put("breakfast", m[1]);
            day.put("lunch", m[2]);
            day.put("dinner", m[3]);
            weeklyMeals.add(day);
        }
        plan.put("weeklyMeals", weeklyMeals);

        try {
            return ToolResult.success(objectMapper.writeValueAsString(plan));
        } catch (Exception e) {
            return ToolResult.fail("{\"error\": \"生成饮食计划失败：" + e.getMessage() + "\"}");
        }
    }

    // ======================== 高优先级工具方法 ========================

    @Tool("查询当前会员已预约的私教课列表（待上课和已完成）")
    public ToolResult<String> queryMyPTBookings(@Param("memberId") Long memberId) {
        if (memberId == null || memberId <= 0) return ToolResult.fail("请先登录。");

        LambdaQueryWrapper<PersonalTraining> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PersonalTraining::getMemberId, memberId)
                .in(PersonalTraining::getStatus, "scheduled", "completed")
                .orderByDesc(PersonalTraining::getAppointmentTime);
        List<PersonalTraining> list = personalTrainingMapper.selectList(wrapper);

        if (list.isEmpty()) return ToolResult.success("您目前没有私教预约记录。");

        StringBuilder sb = new StringBuilder("📋 您的私教预约记录如下：\n");
        for (int i = 0; i < list.size(); i++) {
            PersonalTraining pt = list.get(i);
            String trainerName = "未知教练";
            if (pt.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(pt.getTrainerId());
                if (trainer != null) trainerName = trainer.getName();
            }
            String statusText = "待上课";
            if ("completed".equals(pt.getStatus())) statusText = "✅ 已完成";
            else if ("cancelled".equals(pt.getStatus())) statusText = "❌ 已取消";

            sb.append(i + 1).append(". ")
                    .append(pt.getAppointmentTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))).append(" ")
                    .append(trainerName).append(" 教练")
                    .append("（").append(statusText).append("）")
                    
                    .append("\n");
        }
        return ToolResult.success(sb.toString());
    }

    @Tool("查询当前会员已报名的团课列表")
    public ToolResult<String> queryMyClassBookings(@Param("memberId") Long memberId) {
        if (memberId == null || memberId <= 0) return ToolResult.fail("请先登录。");

        LambdaQueryWrapper<ClassBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId)
                .in(ClassBooking::getStatus, "booked", "checked_in")
                .orderByDesc(ClassBooking::getBookingTime);
        List<ClassBooking> bookings = classBookingMapper.selectList(wrapper);

        if (bookings.isEmpty()) return ToolResult.success("您目前没有团课报名记录。");

        StringBuilder sb = new StringBuilder("📅 您的团课报名记录如下：\n");
        for (int i = 0; i < bookings.size(); i++) {
            ClassBooking cb = bookings.get(i);
            String className = "未知课程";
            if (cb.getClassId() != null) {
                GroupClass gc = groupClassMapper.selectById(cb.getClassId());
                if (gc != null) className = gc.getName();
            }
            String statusText = "已预约";
            if ("checked_in".equals(cb.getStatus())) statusText = "✅ 已签到";
            else if ("cancelled".equals(cb.getStatus())) statusText = "❌ 已取消";

            sb.append(i + 1).append(". ")
                    .append(className)                    
                    .append("（").append(statusText).append("）")
                    
                    .append("\n");
        }
        return ToolResult.success(sb.toString());
    }

    @Tool("查询当前会员的私教课程包剩余次数和有效期")
    public ToolResult<String> getMyPackageInfo(@Param("memberId") Long memberId) {
        if (memberId == null || memberId <= 0) return ToolResult.fail("请先登录。");

        LambdaQueryWrapper<MemberPrivatePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberPrivatePackage::getMemberId, memberId)
                .ne(MemberPrivatePackage::getStatus, "refunded")
                .isNotNull(MemberPrivatePackage::getStartDate)
                .gt(MemberPrivatePackage::getRemainingSessions, 0)
                .and(w -> w.isNull(MemberPrivatePackage::getEndDate)
                        .or()
                        .ge(MemberPrivatePackage::getEndDate, LocalDate.now())
                );
        List<MemberPrivatePackage> packages = memberPrivatePackageMapper.selectList(wrapper);

        if (packages.isEmpty()) {
            // 诊断：打印所有课程包原始数据
            LambdaQueryWrapper<MemberPrivatePackage> diagAll = new LambdaQueryWrapper<>();
            diagAll.eq(MemberPrivatePackage::getMemberId, memberId);
            List<MemberPrivatePackage> allPkgsForDiag = memberPrivatePackageMapper.selectList(diagAll);
            if (allPkgsForDiag != null && !allPkgsForDiag.isEmpty()) {
                for (MemberPrivatePackage p : allPkgsForDiag) {
                    System.out.println("[课程包诊断] 会员" + memberId + ": id=" + p.getId() + ", name=" + p.getPackageName() + ", total=" + p.getTotalSessions() + ", used=" + p.getUsedSessions() + ", remaining=" + p.getRemainingSessions() + ", status=" + p.getStatus() + ", endDate=" + p.getEndDate());
                }
            }
            // 检查是否有已用完或过期的
            LambdaQueryWrapper<MemberPrivatePackage> allWrapper = new LambdaQueryWrapper<>();
            allWrapper.eq(MemberPrivatePackage::getMemberId, memberId);
            int totalCount = Math.toIntExact(memberPrivatePackageMapper.selectCount(allWrapper));
            if (totalCount > 0) {
                return ToolResult.success("您有 " + totalCount + " 个课程包，但均已用完或已过期。如需购买新套餐，请联系前台或查看商城。");
            }
            return ToolResult.success("您目前没有有效的私教课程包，建议购买套餐更划算。");
        }

        int totalRemaining = 0;
        StringBuilder sb = new StringBuilder("📦 您的私教课程包剩余情况：\n");
        for (MemberPrivatePackage pkg : packages) {
            totalRemaining += pkg.getRemainingSessions();
            String endDateStr = pkg.getEndDate() != null ? pkg.getEndDate().toString() : "长期有效";
            sb.append("- ").append(pkg.getPackageName())
                    .append("：剩余 ").append(pkg.getRemainingSessions()).append(" 节")
                    .append("（有效期至 ").append(endDateStr).append("）")
                    .append("\n");
        }
        sb.append("\n📊 总计剩余课时：").append(totalRemaining).append(" 节");
        return ToolResult.success(sb.toString());
    }

    @Tool("查询当前会员的个人基本信息（姓名、等级、身高、体重、有效期）")
    public ToolResult<String> getMyProfile(@Param("memberId") Long memberId) {
        if (memberId == null || memberId <= 0) return ToolResult.fail("请先登录。");

        Member member = memberMapper.selectById(memberId);
        if (member == null) return ToolResult.fail("会员信息不存在。");

        String expireStatus = "";
        if (member.getExpireDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), member.getExpireDate());
            if (days < 0) expireStatus = "⚠️ 已过期 " + Math.abs(days) + " 天，请及时续费！";
            else if (days < 7) expireStatus = "⏰ 即将到期，剩余 " + days + " 天";
            else expireStatus = "✅ 有效";
        }

        return ToolResult.success(String.format(
                "👤 您的个人信息如下：\n" +
                        "- 姓名：%s\n" +
                        "- 等级：%s\n" +
                        "- 身高：%s cm\n" +
                        "- 体重：%s kg\n" +
                        "- 有效期：%s %s\n" +
                        "- 手机号：%s",
                nullToEmpty(member.getName()),
                nullToEmpty(member.getLevel()),
                member.getHeight() != null ? member.getHeight() : "未设置",
                member.getWeight() != null ? member.getWeight() : "未设置",
                nullToEmpty(member.getExpireDate()),
                expireStatus,
                nullToEmpty(member.getPhone())
        ));
    }

    // ======================== 新增工具方法 ========================

    @Tool("查询指定教练的详细信息（专长、价格、简介等）")
    public ToolResult<String> queryTrainerByName(@Param("trainerName") String trainerName) {
        if (trainerName == null || trainerName.trim().isEmpty()) {
            return ToolResult.fail("请提供教练姓名。");
        }
        LambdaQueryWrapper<Trainer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Trainer::getName, trainerName.trim());
        Trainer trainer = trainerMapper.selectOne(wrapper);
        if (trainer == null) {
            return ToolResult.fail("未找到名为 " + trainerName + " 的教练，请确认姓名是否正确。");
        }
        return ToolResult.success(String.format(
                "🏋️ 教练信息：\n" +
                        "- 姓名：%s\n" +
                        "- 专长：%s\n" +
                        "- 价格：%s 元/小时\n" +
                        "- 简介：%s\n" +
                        "- 状态：%s",
                trainer.getName(),
                nullToEmpty(trainer.getSpecialty()),
                trainer.getPricePerHour(),
                nullToEmpty(trainer.getIntro()),
                trainer.getStatus().equals("active") ? "在职" : "休假/离职"
        ));
    }

    @Tool("查询当前可报名的比赛列表")
    public ToolResult<String> queryAvailableCompetitions() {
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Competition::getStatus, "open")
                .ge(Competition::getDeadline, LocalDateTime.now())
                .eq(Competition::getIsActive, true)
                .orderByAsc(Competition::getDeadline);
        List<Competition> competitions = competitionMapper.selectList(wrapper);
        if (competitions.isEmpty()) {
            return ToolResult.success("目前没有正在报名的比赛。");
        }
        StringBuilder sb = new StringBuilder("🏆 可报名比赛如下：\n");
        for (Competition c : competitions) {
            sb.append("- ").append(c.getName())
                    .append("（截止：").append(c.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .append("，名额：").append(c.getEnrolled() == null ? 0 : c.getEnrolled())
                    .append("/").append(c.getMaxParticipants()).append("）\n");
        }
        return ToolResult.success(sb.toString());
    }

    @Tool("查询当前会员的历史体测记录（按时间倒序）")
    public ToolResult<String> queryMyTestHistory(@Param("memberId") Long memberId) {
        if (memberId == null || memberId <= 0) return ToolResult.fail("请先登录。");
        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FitnessTest::getMemberId, memberId)
                .orderByDesc(FitnessTest::getTestDate)
                .last("LIMIT 5"); // 只取最近5条，避免太长
        List<FitnessTest> tests = fitnessTestMapper.selectList(wrapper);
        if (tests.isEmpty()) {
            return ToolResult.success("您暂无体测记录。");
        }
        StringBuilder sb = new StringBuilder("📊 您的体测历史（最近5条）：\n");
        for (FitnessTest t : tests) {
            sb.append("- ").append(t.getTestDate())
                    .append("：体重 ").append(t.getWeightKg() != null ? t.getWeightKg() : "--")
                    .append(" kg，体脂 ").append(t.getBodyFatPercent() != null ? t.getBodyFatPercent() : "--")
                    .append("%，肌肉量 ").append(t.getMuscleMassKg() != null ? t.getMuscleMassKg() : "--").append(" kg");
            if (t.getRemarks() != null && !t.getRemarks().isEmpty()) {
                sb.append("（备注：").append(t.getRemarks()).append("）");
            }
            sb.append("\n");
        }
        return ToolResult.success(sb.toString());
    }
    // 辅助方法（如果类里没有）
    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }
    // ====== 辅助方法：获取最新体测 ======
    private FitnessTest getLatestFitnessTest(Long memberId) {
        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FitnessTest::getMemberId, memberId)
                .orderByDesc(FitnessTest::getTestDate)
                .last("LIMIT 1");
        return fitnessTestMapper.selectOne(wrapper);
    }
}