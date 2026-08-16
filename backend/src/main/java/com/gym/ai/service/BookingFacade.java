package com.gym.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.ai.AIController;
import com.gym.ai.context.ConversationContext;
import com.gym.ai.context.ConversationState;
import com.gym.ai.context.ContextManager;
import com.gym.ai.tool.GymTools;
import com.gym.entity.*;
import com.gym.mapper.*;
import com.gym.service.GroupClassService;
import com.gym.service.PersonalTrainingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BookingFacade {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;

    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private GroupClassService groupClassService;

    @Autowired
    private PersonalTrainingService ptService;

    @Autowired
    private ContextManager contextManager;

    @Autowired
    private GymTools gymTools;

    @Autowired
    private PaymentService paymentService;

    private final ConcurrentHashMap<String, java.util.List<com.gym.entity.GroupClass>> lastGroupClassListCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastGroupClassListTime = new ConcurrentHashMap<>();

    private volatile Pattern trainerNamePattern;
    private volatile long trainerCacheTime = 0;
    private static final long CACHE_TTL = 60_000;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern DATE_PATTERN_CN = Pattern.compile("(\\d{1,2})月(\\d{1,2})(日|号)");
    private static final Pattern DAY_ONLY_PATTERN = Pattern.compile("(\\d{1,2})号");
    private static final Pattern TIME_PATTERN_CN = Pattern.compile("(上午|下午|晚上)?(\\d{1,2})点(\\d{0,2})分?");
    private static final Pattern STD_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern STD_TIME = Pattern.compile("(\\d{1,2})[:：](\\d{2})(?:[:：](\\d{2}))?");
    private ConversationContext toConversationContext(Long memberId, String sessionId,
            AIController.PendingBooking pending, ConversationState state) {
        ConversationContext ctx = new ConversationContext(memberId, sessionId);
        ctx.setCurrentState(state);
        ctx.getPayload().put("pendingBooking", pending);
        return ctx;
    }

    public String handleBooking(String userMessage, Long memberId, String sessionId) {
        long t0 = System.currentTimeMillis();
        if (memberId == null || memberId <= 0) {
            return "预约失败：请先登录。";
        }
        Pattern trainerPattern = getTrainerNamePattern();
        Matcher trainerMatcher = trainerPattern.matcher(userMessage);
        String trainerName = null;
        if (trainerMatcher.find()) {
            trainerName = trainerMatcher.group(0);
        } else {
            return "预约失败：未识别到教练姓名，请明确指定教练（如：王教练、李教练等）。";
        }
        long t1 = System.currentTimeMillis();
        log.info("[预约耗时] 解析教练姓名: {}ms", t1 - t0);

        Trainer trainer = trainerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Trainer>()
                        .eq("name", trainerName)
        );
        long t2 = System.currentTimeMillis();
        log.info("[预约耗时] 查询教练信息: {}ms", t2 - t1);
        if (trainer == null) {
            return "预约失败：未找到名为 " + trainerName + " 的教练，请确认教练姓名是否正确。";
        }
        Long trainerId = trainer.getId();
        String dateStr = parseDate(userMessage);
        if (dateStr != null) {
            String pastMsg = validateDateNotPast(dateStr);
            if (pastMsg != null) {
                // 过期日期，但保存上下文（教练已知）以便用户重新选择
                String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
                contextManager.updateContext(pendingKey, toConversationContext(
                        memberId, sessionId,
                        new AIController.PendingBooking(memberId, trainerId, trainerName, false, false, null),
                        ConversationState.PT_BOOKING));
                log.info("[预约上下文] 日期过期，已保存教练上下文");
                return pastMsg;
            }
        }
        if (dateStr == null) {
            // 保存预约上下文，等待用户补充日期
            String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            contextManager.updateContext(pendingKey, toConversationContext(
                memberId, sessionId, new AIController.PendingBooking(memberId, trainerId, trainerName, false, false, null), ConversationState.PT_BOOKING));
            log.info("[预约上下文] 已保存待完成预约: 教练={}, 等待提供日期", trainerName);
            return "好的，已为您选择 " + trainerName + "。请问您想约哪一天？（例如：明天、6月30日）";
        }
        String timeStr = parseTime(userMessage);
        if (timeStr == null) {
            // 检查是否包含时间格式但解析失败（如非整点）
            boolean hasTimePattern = userMessage.matches(".*[0-9一两三四五六七八九十].*[:：点].*") ||
                    userMessage.matches(".*(上午|下午|晚上).*[0-9一两三四五六七八九十].*");
            if (hasTimePattern) {
                log.info("[预约耗时] 识别到时间格式但解析失败（可能是非整点），保存上下文");
                String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
                contextManager.updateContext(pendingKey, toConversationContext(
                        memberId, sessionId,
                        new AIController.PendingBooking(memberId, trainerId, trainerName, true, false, dateStr),
                        ConversationState.PT_BOOKING));
                return "预约时间只支持整点（如 13:00、14:00），请重新输入。";
            }
            // 保存预约上下文，等待用户补充时间
            String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            contextManager.updateContext(pendingKey, toConversationContext(
                memberId, sessionId, new AIController.PendingBooking(memberId, trainerId, trainerName, true, false, dateStr), ConversationState.PT_BOOKING));
            log.info("[预约上下文] 已保存待完成预约: 教练={}, 日期={}, 等待提供时间", trainerName, dateStr);
            return "好的，已为您选择 " + trainerName + "，请问您想约什么时间？（例如：下午2点、14:00）";
        }
        // 校验时间范围 09:00-21:00
        int hour = Integer.parseInt(timeStr.split(":")[0]);
        int min = Integer.parseInt(timeStr.split(":")[1]);
        if (hour < 9 || hour > 21 || (hour == 21 && min > 0)) {
            return "预约时间必须在上午9点到晚上9点之间，请重新选择。";
        }
        LocalDateTime appointmentTime;
        try {
            appointmentTime = LocalDateTime.parse(dateStr + " " + timeStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                appointmentTime = LocalDateTime.parse(dateStr + " " + timeStr + ":00", DATE_FORMATTER2);
            } catch (DateTimeParseException ex) {
                return "预约失败：日期或时间格式不正确，请使用类似【6月30日下午2点】或【2026-06-30 14:00】的格式。";
            }
        }
        // 校验预约时间是否已过
        if (appointmentTime.isBefore(LocalDateTime.now())) {
            return "预约时间已过，请选择未来的时间。";
        }
        long t3 = System.currentTimeMillis();
        log.info("[预约耗时] 解析日期时间: {}ms", t3 - t2);

        String conflictMsg = checkBookingConflict(memberId, appointmentTime);
        if (conflictMsg != null) {
            // 保留上下文，允许用户重新选择时间
            String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            contextManager.updateContext(pendingKey, toConversationContext(
                memberId, sessionId, new AIController.PendingBooking(memberId, trainerId, trainerName, true, false, dateStr), ConversationState.PT_BOOKING));
            log.info("[预约上下文] 冲突提示后保留上下文，等待新时间");
            return conflictMsg + "\n\n请选择其他时间，例如【下午2点】或【14:00】";
        }
        // 保存预约上下文，进入支付选择
        String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        AIController.PendingBooking pending = new AIController.PendingBooking(memberId, trainerId, trainerName, true, true, dateStr);
        pending.timeStr = timeStr;
        contextManager.updateContext(pendingKey, toConversationContext(
            pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
        log.info("[预约流程] 预约信息完整，进入支付选择: 教练={}, 日期={}, 时间={}", trainerName, dateStr, timeStr);

        // 检查支付方式
        String payResult = paymentService.processPaymentChoice(userMessage, pending, pendingKey, sessionId);
        if (payResult != null) {
            if (payResult.equals("__EXIT__")) {
                contextManager.removeContext(pendingKey);
                log.info("[预约流程] 用户取消预约");
                return "好的，已取消预约。请问还有其他问题吗？";
            }
            log.info("[预约流程] 需要用户选择支付方式");
            return payResult;
        }

        // 用户已选择支付方式，执行预约
        log.info("[预约流程] 支付方式已选择: {}, 执行预约", pending.paymentMethod);
        contextManager.removeContext(pendingKey);

        boolean useFree = "free".equals(pending.paymentMethod);
        Long pkgId = null;
        if ("package".equals(pending.paymentMethod)) {
            pkgId = paymentService.resolvePackageId(pending);
        }

        log.info("[预约执行] 打算执行私教预约：memberId={}, trainerId={}, time={}, paymentMethod={}, useFree={}, pkgId={}", memberId, trainerId, appointmentTime, null, useFree, pkgId);
        String result = ptService.bookPersonalTraining(memberId, trainerId, appointmentTime, 60, pkgId, useFree);
        long t4 = System.currentTimeMillis();
        log.info("[预约耗时] 执行预约(DB): {}ms", t4 - t3);
        log.info("[预约耗时] 总耗时: {}ms", t4 - t0);

        if (result.startsWith("私教预约成功")) {
            String label = "单次付费";
            if (useFree) label = "免费私教课";
            else if (pkgId != null) label = "课程包扣费";
            // 优先返回实际结果（含课程/时间/原价/实付明细），不足时再补支付方式
            if (result.contains("原价") || result.contains("时间")) {
                return result + "\n支付方式：" + label;
            }
            return "预约成功！已为您预约 " + trainerName + " 的课程，时间：" +
                    appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n支付方式：" + label;
        } else {
            return "❌ " + result;
        }
        }

    /**
     * 执行已完成支付选择的私教预约（多轮对话拦截使用；逻辑与 handleBooking 后半段一致）。
     */
    public String completePendingPTBooking(AIController.PendingBooking pending, String sessionId) {
        LocalDateTime appointmentTime;
        try {
            appointmentTime = LocalDateTime.parse(pending.dateStr + " " + pending.timeStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                appointmentTime = LocalDateTime.parse(pending.dateStr + " " + pending.timeStr + ":00", DATE_FORMATTER2);
            } catch (DateTimeParseException ex) {
                return "日期时间格式有误，请使用类似【明天下午2点】的格式。";
            }
        }
        String pendingKey = "booking_" + (pending.memberId != null ? pending.memberId : "guest") + "_" + sessionId;
        contextManager.removeContext(pendingKey);

        boolean useFree = "free".equals(pending.paymentMethod);
        Long pkgId = null;
        if ("package".equals(pending.paymentMethod)) {
            pkgId = paymentService.resolvePackageId(pending);
        }
        log.info("[预约执行] 打算执行私教预约：memberId={}, trainerId={}, time={}, paymentMethod={}, useFree={}, pkgId={}",
                pending.memberId, pending.trainerId, appointmentTime, pending.paymentMethod, useFree, pkgId);
        String result = ptService.bookPersonalTraining(pending.memberId, pending.trainerId, appointmentTime, 60, pkgId, useFree);

        if (result.startsWith("私教预约成功")) {
            String label = "单次付费";
            if (useFree) label = "免费私教课";
            else if (pkgId != null) label = "课程包扣费";
            // 优先返回实际结果（含课程/时间/原价/实付明细），不足时再补支付方式
            if (result.contains("原价") || result.contains("时间")) {
                return result + "\n支付方式：" + label;
            }
            return "预约成功！已为您预约 " + pending.trainerName + " 的课程，时间：" +
                    appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n支付方式：" + label;
        } else {
            return "❌ " + result;
        }
    }
    public String parseDate(String userMessage) {
        if (userMessage.contains("明天")) {
            return LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (userMessage.contains("后天")) {
            return LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (userMessage.contains("今天")) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        // 支持"X年X月X日"格式（如"2026年7月12日"）
        java.util.regex.Matcher yearMatcher = java.util.regex.Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})(日|号)").matcher(userMessage);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            int month = Integer.parseInt(yearMatcher.group(2));
            int day = Integer.parseInt(yearMatcher.group(3));
            return String.format("%d-%02d-%02d", year, month, day);
        }
        // 先尝试完整的"X月X号/日"格式
        Matcher dateMatcher = DATE_PATTERN_CN.matcher(userMessage);
        if (dateMatcher.find()) {
            int month = Integer.parseInt(dateMatcher.group(1));
            int day = Integer.parseInt(dateMatcher.group(2));
            int currentYear = LocalDateTime.now().getYear();
            int currentMonth = LocalDateTime.now().getMonthValue();
            int year = currentYear;
            return String.format("%d-%02d-%02d", year, month, day);
        }
        // 支持"号"格式（如"28号"，默认当前年月）
        java.util.regex.Matcher dayOnlyMatcher = DAY_ONLY_PATTERN.matcher(userMessage);
        if (dayOnlyMatcher.find()) {
            int day = Integer.parseInt(dayOnlyMatcher.group(1));
            java.time.LocalDate now = java.time.LocalDate.now();
            int year = now.getYear();
            int month = now.getMonthValue();
            // 如果传入的日期小于今天，推至下月
            if (day < now.getDayOfMonth()) {
                month++;
                if (month > 12) { month = 1; year++; }
            }
            return String.format("%d-%02d-%02d", year, month, day);
        }
        Matcher stdMatcher = STD_DATE.matcher(userMessage);
        if (stdMatcher.find()) {
            return stdMatcher.group(0);
        }
        return null;
    }

    public String parseTime(String userMessage) {
        // 先移除日期信息，避免干扰时间解析
        String timeInput = userMessage.replaceAll("明天|后天|今天", "").trim();
        timeInput = timeInput.replaceAll("\\d{1,2}月\\d{1,2}(日|号)", "").trim();
        timeInput = timeInput.replaceAll("\\d{4}年\\d{1,2}月\\d{1,2}日", "").trim();
        timeInput = timeInput.replaceAll("\\d{4}-\\d{2}-\\d{2}", "").trim();
        
        // 尝试标准格式: "14:00", "14:00:00" （优先于中文格式，避免数字冲突）
        Matcher stdMatcher = STD_TIME.matcher(timeInput);
        if (stdMatcher.find()) {
            int hour = Integer.parseInt(stdMatcher.group(1));
            int minute = Integer.parseInt(stdMatcher.group(2));
            if (minute != 0) {
                log.info("[parseTime] 拒绝非整点时间: hour={}, minute={}", hour, minute);
                return null;
            }
            // 校验小时范围
            if (hour >= 0 && hour <= 23) {
                return String.format("%02d:%02d", hour, minute);
            }
        }
        
        // 尝试阿拉伯数字格式: "2点", "下午2点", "2点30分"
        Matcher timeMatcher = TIME_PATTERN_CN.matcher(timeInput);
        if (timeMatcher.find()) {
            String period = timeMatcher.group(1);
            int hour = Integer.parseInt(timeMatcher.group(2));
            int minute = 0;
            if (timeMatcher.group(3) != null && !timeMatcher.group(3).isEmpty()) {
                minute = Integer.parseInt(timeMatcher.group(3));
            }
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            }
            return String.format("%02d:%02d", hour, minute);
        }
        // 尝试中文数字格式: "三点", "三点半", "下午三点", "下午三点半"
        // 匹配: (上午|下午|晚上)?(一|二|两|三|四|五|六|七|八|九|十|十一|十二)点(半|(一|二|三|四|五|六|七|八|九|十)(分)?)?
        String chineseOrdinal = "(一|二|两|三|四|五|六|七|八|九|十|十一|十二)";
        String chineseMinute = "(半|(一|二|三|四|五|六|七|八|九|十|十一|十二|十三|十四|十五|十六|十七|十八|十九|二十|二十一|二十二|二十三|二十四|二十五|二十六|二十七|二十八|二十九|三十|三十一|三十二|三十三|三十四|三十五|三十六|三十七|三十八|三十九|四十|四十一|四十二|四十三|四十四|四十五|四十六|四十七|四十八|四十九|五十|五十一|五十二|五十三|五十四|五十五|五十六|五十七|五十八|五十九))";
        Pattern chineseTimePattern = Pattern.compile("(上午|下午|晚上)?" + chineseOrdinal + "点(" + chineseMinute + "分?)?");
        Matcher chineseMatcher = chineseTimePattern.matcher(userMessage);
        if (chineseMatcher.find()) {
            String period = chineseMatcher.group(1);
            String hourCn = chineseMatcher.group(2);
            String minuteCn = chineseMatcher.group(3); // could be "半" or a number
            int hour = chineseToDigit(hourCn);
            int minute = 0;
            if (minuteCn != null && !minuteCn.isEmpty()) {
                if ("半".equals(minuteCn)) {
                    minute = 30;
                } else {
                    minute = chineseToDigit(minuteCn);
                }
            }
            // 拒绝非整点时间（如三点半、15:30）
            if (minute != 0) {
                log.info("[parseTime] 拒绝非整点时间: hour={}, minute={}", hour, minute);
                return null;
            }
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            }
            return String.format("%02d:%02d", hour, minute);
        }
        return null;
    }

    private int chineseToDigit(String cn) {
        switch (cn) {
            case "一": return 1;
            case "二":
            case "两": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
            case "十一": return 11;
            case "十二": return 12;
            case "十三": return 13;
            case "十四": return 14;
            case "十五": return 15;
            case "十六": return 16;
            case "十七": return 17;
            case "十八": return 18;
            case "十九": return 19;
            case "二十": return 20;
            case "二十一": return 21;
            case "二十二": return 22;
            case "二十三": return 23;
            case "二十四": return 24;
            case "二十五": return 25;
            case "二十六": return 26;
            case "二十七": return 27;
            case "二十八": return 28;
            case "二十九": return 29;
            case "三十": return 30;
            case "三十一": return 31;
            case "三十二": return 32;
            case "三十三": return 33;
            case "三十四": return 34;
            case "三十五": return 35;
            case "三十六": return 36;
            case "三十七": return 37;
            case "三十八": return 38;
            case "三十九": return 39;
            case "四十": return 40;
            case "四十一": return 41;
            case "四十二": return 42;
            case "四十三": return 43;
            case "四十四": return 44;
            case "四十五": return 45;
            case "四十六": return 46;
            case "四十七": return 47;
            case "四十八": return 48;
            case "四十九": return 49;
            case "五十": return 50;
            case "五十一": return 51;
            case "五十二": return 52;
            case "五十三": return 53;
            case "五十四": return 54;
            case "五十五": return 55;
            case "五十六": return 56;
            case "五十七": return 57;
            case "五十八": return 58;
            case "五十九": return 59;
            default: return 0;
        }
    }

    private Pattern getTrainerNamePattern() {
        long now = System.currentTimeMillis();
        if (trainerNamePattern == null || (now - trainerCacheTime) > CACHE_TTL) {
            List<Trainer> trainers = trainerMapper.selectList(null);
            String names = trainers.stream().map(Trainer::getName).collect(Collectors.joining("|"));
            if (names.isEmpty()) {
                names = "王教练|李教练|张教练|刘教练|小垚教练";
            }
            trainerNamePattern = Pattern.compile("(" + names + ")");
            trainerCacheTime = now;
            log.debug("更新教练名称正则: {}", names);
        }
        return trainerNamePattern;
    }

    public String handleQueryClasses(String userMessage, Long memberId) {
        String targetDateStr = parseDate(userMessage);
        LocalDateTime start, end;
        String typeFilter = null;
        Boolean allowVisitorFilter = null;

        // 检测是否查询体验课（访客可见的课程）
        if (userMessage.contains("体验课") || userMessage.contains("体验")) {
            allowVisitorFilter = true;
            log.info("[团课查询] 筛选体验课 allow_visitor=1");
        }

        // 检测是否查询免费/公益团课
        if (userMessage.contains("免费") || userMessage.contains("公益")) {
            typeFilter = "free";
            log.info("[团课查询] 筛选类型: free");
        }

        if (targetDateStr != null) {
            LocalDate targetDate = LocalDate.parse(targetDateStr);
            start = targetDate.atStartOfDay();
            end = targetDate.atTime(23, 59, 59);
        } else {
            start = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0);
            end = start.plusDays(7).withHour(22).withMinute(0).withSecond(0);
        }

        String startStr = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String result = gymTools.queryAvailableClasses(startStr, endStr, typeFilter, allowVisitorFilter).getMessage();
        // 存储本次查询结果到缓存（用于代词解析）
        String cacheKey = "lastGC_" + (memberId != null ? memberId : "guest");
        try {
            // 根据同样的条件从数据库查询课程列表
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.GroupClass> qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            qw.ge(com.gym.entity.GroupClass::getStartTime, start)
               .le(com.gym.entity.GroupClass::getStartTime, end)
               .eq(com.gym.entity.GroupClass::getStatus, "scheduled")
               .orderByAsc(com.gym.entity.GroupClass::getStartTime);
            if (typeFilter != null) {
                qw.eq(com.gym.entity.GroupClass::getType, typeFilter);
            }
            if (allowVisitorFilter != null && allowVisitorFilter) {
                qw.eq(com.gym.entity.GroupClass::getAllowVisitor, 1);
            }
            java.util.List<com.gym.entity.GroupClass> cachedList = groupClassMapper.selectList(qw);
            setCachedGroupClassList(cacheKey, cachedList);
            // 如果是体验课查询，结果中标注"体验课"
            if (allowVisitorFilter != null && allowVisitorFilter && !result.contains("体验课")) {
                result = result.replace("可预约团课", "可预约体验课（访客可约）");
                result = result.replace("没有可预约的团课", "没有可预约的体验课（访客可约）");
            }
        } catch (Exception e) {
            log.warn("存储团课列表缓存失败", e);
        }




        Pattern countPattern = Pattern.compile("推荐\\s*(\\d+)\\s*个");
        Matcher countMatcher = countPattern.matcher(userMessage);
        if (countMatcher.find()) {
            int limit = Integer.parseInt(countMatcher.group(1));
            String[] lines = result.split("\n");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                sb.append(line).append("\n");
                if (line.trim().startsWith("课程：")) {
                    count++;
                    if (count >= limit) break;
                }
            }
            return sb.toString();
        }

        return result;
    }

    public String extractTrainerName(String userMessage) {
        Pattern p = Pattern.compile("([\\u4e00-\\u9fa5]{2,})教练");
        Matcher m = p.matcher(userMessage);
        if (m.find()) {
            return m.group(1) + "教练";
        }
        Pattern trainerP = getTrainerNamePattern();
        Matcher tm = trainerP.matcher(userMessage);
        if (tm.find()) {
            return tm.group(1);
        }
        return null;
    }

    /** 清洗预约类输入，提取课程名关键词（去除"我要约/帮我约/想约"等动词与语气词） */
    private String cleanGroupCourseInput(String userMessage) {
        if (userMessage == null) return null;
        String s = userMessage.trim().replaceAll("[，。！？!?、,.\\s]+", "");
        String[] prefixes = {"我要预约", "帮我预约", "麻烦预约", "请帮我预约", "我想要预约", "我想预约",
                "我要约", "帮我约", "麻烦约", "请帮我约", "我想约", "想预约", "想约",
                "要约", "约一下", "预约一下", "帮我订", "我要订", "想订", "请约",
                "帮我报名", "我要报名", "报名", "预约", "约"};
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length());
                break;
            }
        }
        s = s.replaceAll("^(我要|帮我|我想|麻烦|请|给我|我想要|预约|约|一下)", "");
        s = s.replaceAll("(团课|一节课|一节|课程|课|一下|吧|呢|哦|谢谢|多谢|吗|的)$", "");
        return s.trim();
    }

    public String handleBookGroupClass(String userMessage, Long memberId, String sessionId) {
        // 清除该会员之前的团课上下文，避免残留影响
        String _ctxKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        contextManager.removeContext(_ctxKey);
        String _cacheKey = "lastGC_" + (memberId != null ? memberId : "guest");
        clearCachedGroupClassList(_cacheKey);
        String[] keywords = {"动感单车", "瑜伽", "搏击", "杠铃", "普拉提", "有氧", "力量", "单车", "操课", "舞蹈", "尊巴", "HIIT"};
        String lowerMsgForKW = userMessage.toLowerCase();
        String courseKeyword = null;
        for (String kw : keywords) {
            if (lowerMsgForKW.contains(kw.toLowerCase())) {
                courseKeyword = kw;
                break;
            }
        }
        log.info("[handleBookGroupClass] courseKeyword={}, userMessage={}", courseKeyword, userMessage);
        if (courseKeyword == null) {
            // DB查询：从消息中提取课程名（匹配数据库中的课程名称）
            String lowerMsg = userMessage.toLowerCase();
            java.util.List<com.gym.entity.GroupClass> allScheduled = groupClassMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.GroupClass>()
                    .eq(com.gym.entity.GroupClass::getStatus, "scheduled")
                    .gt(com.gym.entity.GroupClass::getStartTime, java.time.LocalDateTime.now())
            );
            if (allScheduled != null) {
                for (com.gym.entity.GroupClass gc : allScheduled) {
                    if (gc.getName() != null && lowerMsg.contains(gc.getName().toLowerCase())) {
                        courseKeyword = gc.getName();
                        break;
                    }
                }
                // 如果还没匹配到，尝试匹配课程名的前2-4个字
                if (courseKeyword == null) {
                    for (com.gym.entity.GroupClass gc : allScheduled) {
                        if (gc.getName() != null && gc.getName().length() >= 2) {
                            String shortName = gc.getName().substring(0, Math.min(gc.getName().length(), 4));
                            if (lowerMsg.contains(shortName.toLowerCase())) {
                                courseKeyword = shortName;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (courseKeyword == null) {
            // 第二步：关键词匹配失败时，直接用用户输入查库（LIKE '%输入%'），支持任意课程名
            String cleanedInput = cleanGroupCourseInput(userMessage);
            log.info("[handleBookGroupClass] 关键词未匹配，尝试直接按用户输入查库: {}", cleanedInput);
            if (cleanedInput != null && !cleanedInput.isEmpty()
                    && !cleanedInput.contains("什么") && !cleanedInput.contains("哪些")
                    && !cleanedInput.contains("有没有") && !cleanedInput.contains("怎么")
                    && !cleanedInput.contains("吗") && !cleanedInput.contains("哪")
                    && !cleanedInput.contains("今天") && !cleanedInput.contains("明天")
                    && !cleanedInput.contains("周") && !cleanedInput.contains("星期")) {
                java.util.List<com.gym.entity.GroupClass> directHits = groupClassMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.GroupClass>()
                        .eq(com.gym.entity.GroupClass::getStatus, "scheduled")
                        .gt(com.gym.entity.GroupClass::getStartTime, java.time.LocalDateTime.now())
                        .like(com.gym.entity.GroupClass::getName, cleanedInput)
                        .last("LIMIT 1"));
                if (directHits != null && !directHits.isEmpty()) {
                    courseKeyword = directHits.get(0).getName();
                }
            }
            if (courseKeyword == null) {
                if (cleanedInput == null || cleanedInput.isEmpty()) {
                    return "请告诉我您想预约哪门团课，例如「帮我预约动感单车」";
                }
                return "未找到与「" + cleanedInput + "」相关的可预约课程，请确认课程名称或稍后再试。";
            }
        }

        // 尝试从用户输入中提取日期/时间，用于过滤课程
        String targetDateStr = parseDate(userMessage);
        String targetTimeStr = parseTime(userMessage);

        // 查询所有匹配该课程名的未来排期
        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(GroupClass::getName, courseKeyword)
                .eq(GroupClass::getStatus, "scheduled")
                .gt(GroupClass::getStartTime, LocalDateTime.now())
                .orderByAsc(GroupClass::getStartTime);

        // 如果用户指定了日期，按日期过滤
        if (targetDateStr != null) {
            try {
                java.time.LocalDate td = java.time.LocalDate.parse(targetDateStr);
                wrapper.between(GroupClass::getStartTime, td.atStartOfDay(), td.atTime(23, 59, 59));
            } catch (Exception e) {
                log.warn("日期解析失败: {}", targetDateStr);
            }
        }
        // 如果用户指定了时间，也按时间过滤（可选增强）

        List<GroupClass> classes = groupClassMapper.selectList(wrapper);
        log.info("[handleBookGroupClass] {} 查询到 {} 节课程", courseKeyword, classes != null ? classes.size() : 0);

        if (classes == null || classes.isEmpty()) {
            // 尝试去掉"课"字再查一次
            if (courseKeyword.endsWith("课") && courseKeyword.length() > 1) {
                String withoutKe = courseKeyword.substring(0, courseKeyword.length() - 1);
                wrapper = new LambdaQueryWrapper<>();
                wrapper.like(GroupClass::getName, withoutKe)
                        .eq(GroupClass::getStatus, "scheduled")
                        .gt(GroupClass::getStartTime, LocalDateTime.now())
                        .orderByAsc(GroupClass::getStartTime);
                if (targetDateStr != null) {
                    try {
                        java.time.LocalDate td = java.time.LocalDate.parse(targetDateStr);
                        wrapper.between(GroupClass::getStartTime, td.atStartOfDay(), td.atTime(23, 59, 59));
                    } catch (Exception e) {}
                }
                classes = groupClassMapper.selectList(wrapper);
            }
            if (classes == null || classes.isEmpty()) {
                return "未找到与「" + courseKeyword + "」相关的可预约课程，请确认课程名称或稍后再试。";
            }
        }

        // 只有一节 → 检查类型，决定是否弹支付
        // 存储到代词解析缓存
        String cacheKeyGC2 = "lastGC_" + (memberId != null ? memberId : "guest");
        setCachedGroupClassList(cacheKeyGC2, classes);
        

        if (classes.size() == 1) {
            GroupClass gc = classes.get(0);
            // 先处理访客：体验课（公益/付费均可）直接预约；非体验课拒绝
            String visitorResult = paymentService.resolveVisitorGroupBooking(memberId, gc);
            if (visitorResult != null) return visitorResult;
            // 会员 + 付费课 → 确认支付流程
            if ("paid".equals(gc.getType()) && gc.getPrice() != null && gc.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                return paymentService.prepareGroupPayment(gc, memberId, sessionId);
            }
            // 会员 + 公益课 → 直接预约，不创建支付上下文、不生成确认引导语
            return groupClassService.bookClass(memberId, gc.getId());
        }


        // 有多节 → 列出所有排期，让用户选择
        String userType = "member";
        if (memberId != null && memberId > 0) {
            Member m = memberMapper.selectById(memberId);
            if (m != null && m.isVisitor()) userType = "visitor";
        }

        // 保存课程列表到上下文
        String groupKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        AIController.PendingBooking gp = new AIController.PendingBooking(memberId, courseKeyword, null, false, false, null, userType);
        gp.courseName = courseKeyword;
        gp.groupClassId = null;  // 用户还未选择具体课程
        gp.retryCount = 0;
        // 将课程列表序列化为简单的字符串（id|name|startTime 格式）
        StringBuilder sb = new StringBuilder();
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
        sb.append("找到 ").append(classes.size()).append(" 节").append(courseKeyword).append("课程：\n");
        // 批量查询教练姓名
        java.util.Map<Long, String> trainerNameMap = new java.util.HashMap<>();
        try {
            for (GroupClass gc2 : classes) {
                if (gc2.getTrainerId() != null) {
                    com.gym.entity.Trainer t = trainerMapper.selectById(gc2.getTrainerId());
                    if (t != null) {
                        trainerNameMap.put(gc2.getTrainerId(), t.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询教练信息失败", e);
        }
        java.util.List<String> classOptions = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter dtf2 = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
        for (int i = 0; i < classes.size(); i++) {
            GroupClass gc = classes.get(i);
            String timeStr2 = gc.getStartTime() != null ? gc.getStartTime().format(dtf2) : "待定";
            int remaining = (gc.getMaxCapacity() != null ? gc.getMaxCapacity() : 0) - (gc.getEnrolled() != null ? gc.getEnrolled() : 0);
            String trainerName = (gc.getTrainerId() != null && trainerNameMap.containsKey(gc.getTrainerId()))
                    ? trainerNameMap.get(gc.getTrainerId()) : "";
            // 计算价格显示
            String priceStr2 = (gc.getPrice() != null && gc.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0)
                ? ("￥" + gc.getPrice().toString()) : "免费";
            // 计算剩余名额显示
            int maxCap2 = gc.getMaxCapacity() != null ? gc.getMaxCapacity() : 0;
            int enrolled2 = gc.getEnrolled() != null ? gc.getEnrolled() : 0;
            int remaining2 = maxCap2 - enrolled2;
            String capStr;
            if (remaining2 > 0) {
                capStr = "剩余 " + remaining2 + " 人";
            } else if (remaining2 == 0) {
                capStr = "已满";
            } else {
                capStr = "已满（超额 " + (-remaining2) + " 人）";
            }
            sb.append(i + 1).append(". ").append(gc.getName() != null ? gc.getName() : courseKeyword)
              .append(" - ").append(trainerName)
              .append(" - ").append(timeStr2)
              .append(" - ").append(priceStr2)
              .append("（").append(capStr).append("）\n");
            classOptions.add(gc.getId() + "|" + gc.getName() + "|" + (gc.getStartTime() != null ? gc.getStartTime().toString() : "") + "|" + trainerName);
        }
        gp.dateStr = String.join(",", classOptions);  // 偷个懒：用 dateStr 存课程列表字符串
        contextManager.updateContext(groupKey, toConversationContext(
            memberId, sessionId, gp, ConversationState.GROUP_BOOKING));
        log.info("[团课预约] 保存课程列表: {} 节可供选择", classes.size());
        // 存储到代词解析缓存
        String cacheKeyGC = "lastGC_" + (memberId != null ? memberId : "guest");
        setCachedGroupClassList(cacheKeyGC, classes);
        return sb.toString() + "\n请回复序号选择（如回复 1、2）。";
    }

    /**
     * 团课预约多轮拦截：用户从课程列表中选择序号（或按日期匹配）。
     * 复用 handleBookGroupClass 的课程列表格式（dateStr 存 id|name|startTime|trainer 逗号分隔）。
     */
    public String handleGroupClassSelection(String userMessage, Long memberId, String sessionId, ConversationContext groupCtx) {
        String groupKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        Object pendingObj = groupCtx.getPayload().get("pendingBooking");
        if (!(pendingObj instanceof AIController.PendingBooking) || !"GROUP".equals(((AIController.PendingBooking) pendingObj).intentType)) {
            contextManager.removeContext(groupKey);
            log.warn("[团课预约选择] 上下文异常，已清理: key={}", groupKey);
            return "预约状态异常，请重新发起团课预约。";
        }
        AIController.PendingBooking groupPend = (AIController.PendingBooking) pendingObj;
        log.info("[团课预约选择] 命中团课预约上下文: course={}, 待选择课程列表", groupPend.courseName);

        // 退出意图：用户明确表示不要了，清除团课预约上下文
        String lowerInput = userMessage.trim().toLowerCase();
        if (lowerInput.contains("不要了") || lowerInput.contains("算了") || lowerInput.contains("不约了") || lowerInput.equals("不")) {
            contextManager.removeContext(groupKey);
            log.info("[团课预约选择] 用户退出团课预约，已清理上下文: key={}", groupKey);
            return "好的，已取消团课预约。请问还有其他问题吗？";
        }

        // 解析用户选择的序号（纯数字优先，用户从 1 开始数）
        String input = userMessage.trim();
        int selectedIdx = -1;
        try {
            selectedIdx = Integer.parseInt(input) - 1;
        } catch (NumberFormatException e) {
            // 不是纯数字，尝试从日期匹配课程
            String inputDate = parseDate(userMessage);
            if (inputDate != null && groupPend.dateStr != null) {
                String[] options = groupPend.dateStr.split(",");
                for (int i = 0; i < options.length; i++) {
                    String[] parts = options[i].split("\\|");
                    if (parts.length >= 3 && parts[2].contains(inputDate)) {
                        selectedIdx = i;
                        break;
                    }
                }
            }
        }

        if (selectedIdx >= 0 && groupPend.dateStr != null) {
            String[] options = groupPend.dateStr.split(",");
            if (selectedIdx < options.length) {
                String[] parts = options[selectedIdx].split("\\|");
                if (parts.length >= 1) {
                    try {
                        Long classId = Long.parseLong(parts[0]);
                        contextManager.removeContext(groupKey);
                        log.info("[团课预约选择] 用户选择序号{}，classId={}", selectedIdx + 1, classId);
                        // 查询课程类型，决定是否弹支付
                        GroupClass gcSel = groupClassMapper.selectById(classId);
                        if (gcSel != null) {
                            // 先处理访客：体验课（公益/付费均可）直接预约；非体验课拒绝
                            String visitorResult = paymentService.resolveVisitorGroupBooking(memberId, gcSel);
                            if (visitorResult != null) return visitorResult;
                            // 会员 + 付费课 → 确认支付流程
                            if ("paid".equals(gcSel.getType()) && gcSel.getPrice() != null
                                    && gcSel.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                                return paymentService.prepareGroupPayment(gcSel, memberId, sessionId);
                            }
                            // 会员 + 公益课 → 直接预约，不创建支付上下文、不生成确认引导语
                            String result = groupClassService.bookClass(memberId, classId);
                            log.info("[团课预约选择] 选择序号{}（公益课），预约结果: {}", selectedIdx + 1, result);
                            return result;
                        }
                    } catch (NumberFormatException ex) {
                        log.warn("[团课预约选择] 课程ID解析失败: {}", parts[0]);
                    }
                }
            }
        }

        // 无法识别选择
        if (groupPend.retryCount >= 2) {
            contextManager.removeContext(groupKey);
            log.warn("[团课预约选择] 多次无法识别，已清理上下文: key={}", groupKey);
            return "未能识别您的选择，请重新发起预约。";
        }
        groupPend.retryCount++;
        contextManager.updateContext(groupKey, toConversationContext(
            memberId, sessionId, groupPend, ConversationState.GROUP_BOOKING));
        log.info("[团课预约选择] 无法识别选择，重试次数={}", groupPend.retryCount);
        return "请回复课程对应的序号（如回复 1、2）来选择您想预约的课程。";
    }
    private java.util.List<com.gym.entity.GroupClass> getCachedGroupClassList(String sessionKey) {
        Long time = lastGroupClassListTime.get(sessionKey);
        if (time == null || (System.currentTimeMillis() - time) > 300000L) {
            lastGroupClassListCache.remove(sessionKey);
            lastGroupClassListTime.remove(sessionKey);
            return null;
        }
        return lastGroupClassListCache.get(sessionKey);
    }
    

    private void setCachedGroupClassList(String sessionKey, java.util.List<com.gym.entity.GroupClass> list) {
        if (list == null || list.isEmpty()) {
            lastGroupClassListCache.remove(sessionKey);
            lastGroupClassListTime.remove(sessionKey);
            return;
        }
        lastGroupClassListCache.put(sessionKey, list);
        lastGroupClassListTime.put(sessionKey, System.currentTimeMillis());
    }
    

    private void clearCachedGroupClassList(String sessionKey) {
        lastGroupClassListCache.remove(sessionKey);
        lastGroupClassListTime.remove(sessionKey);
    }

    public String validateDateNotPast(String dateStr) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            java.time.LocalDate today = java.time.LocalDate.now();
            if (date.isBefore(today)) {
                return "该日期已过期（" + dateStr + "），请选择未来的日期。";
            }
        } catch (Exception e) {
            log.warn("校验日期失败: {}", dateStr);
        }
        return null;
    }

    public String checkBookingConflict(Long memberId, LocalDateTime appointmentTime) {
        try {
            LocalDateTime endTime = appointmentTime.plusMinutes(60);
            Long count = personalTrainingMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.PersonalTraining>()
                    .eq(com.gym.entity.PersonalTraining::getMemberId, memberId)
                    .eq(com.gym.entity.PersonalTraining::getStatus, "scheduled")
                    .ge(com.gym.entity.PersonalTraining::getAppointmentTime, appointmentTime)
                    .lt(com.gym.entity.PersonalTraining::getAppointmentTime, endTime)
            );
            if (count != null && count > 0) {
                return "您在该时段（" + appointmentTime.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) + "）已有私教预约，请选择其他时间。";
            }
        } catch (Exception e) {
            log.warn("检查预约冲突异常", e);
        }
        return null;
    }

    // ====== 快速通道（纯查询，供 AIController 直接调用，不走 AI） ======

    /** 快速通道 1：查询我的私教预约列表 */
    public String handleBookingQuery(Long memberId) {
        return gymTools.queryMyPTBookings(memberId).getMessage();
    }

    /** 快速通道 2：查询我的团课报名记录 */
    public String handleClassBookingQuery(Long memberId) {
        return gymTools.queryMyClassBookings(memberId).getMessage();
    }

    /** 快速通道 4：查询会员信息 */
    public String getMemberProfile(Long memberId) {
        return gymTools.getMyProfile(memberId).getMessage();
    }

    /** 快速通道 5：查询体测历史 */
    public String getTestHistory(Long memberId) {
        return gymTools.queryMyTestHistory(memberId).getMessage();
    }
}
