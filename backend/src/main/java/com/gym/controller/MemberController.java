package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.*;
import com.gym.enums.MemberLevel;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.UserMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.gym.mapper.FitnessTestMapper;
import com.gym.mapper.CheckInMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.CompetitionRegistrationMapper;
import com.gym.mapper.TrainerMapper;
import com.gym.mapper.MemberPrivatePackageMapper;
import com.gym.auth.LoginContext;
import com.gym.service.PointsService;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    @Autowired
    private MemberPrivatePackageMapper memberPrivatePackageMapper;
    @Autowired
    private UserMessageMapper userMessageMapper;

    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private FitnessTestMapper fitnessTestMapper;
    @Autowired
    private CheckInMapper checkInMapper;
    @Autowired
    private GroupClassMapper groupClassMapper;
    @Autowired
    private ClassBookingMapper classBookingMapper;
    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;
    @Autowired
    private CompetitionRegistrationMapper competitionRegistrationMapper;
    @Autowired
    private TrainerMapper trainerMapper;
    @Autowired
    private PointsService pointsService;


    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Member::getName, keyword).or().like(Member::getPhone, keyword));
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(Member::getLevel, level);
        }
        if (status != null && !status.isEmpty()) {
            LocalDate today = LocalDate.now();
            switch (status) {
                case "active":
                    wrapper.ge(Member::getExpireDate, today.plusDays(7));
                    break;
                case "expiring":
                    wrapper.between(Member::getExpireDate, today, today.plusDays(7));
                    break;
                case "expired":
                    wrapper.lt(Member::getExpireDate, today);
                    break;
                default:
                    break;
            }
        }
        wrapper.orderByDesc(Member::getCreatedAt);

        IPage<Member> pageResult = memberMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Member m : pageResult.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("name", m.getName());
            item.put("phone", m.getPhone());
            item.put("gender", m.getGender());
            item.put("birthday", m.getBirthday());
            item.put("level", m.getLevel());
            item.put("expireDate", m.getExpireDate());
            item.put("height", m.getHeight());
            item.put("weight", m.getWeight());
            item.put("createdAt", m.getCreatedAt());
            item.put("points", m.getPoints() != null ? m.getPoints() : 0);
            // 免费私教剩余 = 等级额度 - 已用次数
            MemberLevel lv = MemberLevel.fromDisplayName(m.getLevel());
            int quota = lv.getFreePersonalTrainingsPerMonth();
            int used = m.getFreePtUsedMonth() != null ? m.getFreePtUsedMonth() : 0;
            item.put("freePtQuota", quota);
            item.put("freePtRemaining", Math.max(0, quota - used));
            // 课程包剩余课时（排除已退款）
            List<MemberPrivatePackage> pkgs = memberPrivatePackageMapper.selectList(
                    new LambdaQueryWrapper<MemberPrivatePackage>()
                            .eq(MemberPrivatePackage::getMemberId, m.getId())
                            .ne(MemberPrivatePackage::getStatus, "refunded"));
            int pkgRemaining = 0;
            for (MemberPrivatePackage p : pkgs) {
                if (p.getRemainingSessions() != null) pkgRemaining += p.getRemainingSessions();
            }
            item.put("packageRemaining", pkgRemaining);
            // 状态：有效 / 即将到期(7天内) / 已过期
            String expireStatus;
            if (m.getExpireDate() == null) {
                expireStatus = "active";
            } else if (m.getExpireDate().isBefore(today)) {
                expireStatus = "expired";
            } else if (!m.getExpireDate().isAfter(today.plusDays(7))) {
                expireStatus = "expiring";
            } else {
                expireStatus = "active";
            }
            item.put("expireStatus", expireStatus);
            list.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
        return result;
    }

    @GetMapping("/all")
    public List<Member> getAll() {
        return memberMapper.selectList(null);
    }

    @GetMapping("/{id}")
    public Member getById(@PathVariable Long id) {
        return memberMapper.selectById(id);
    }

    /**
     * 手机号校验工具方法
     */
    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1\\d{10}$");
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Member member) {
        // ====== 手机号校验 ======
        if (!isValidPhone(member.getPhone())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "手机号必须以1开头，共11位数字");
            return error;
        }

        // 处理空日期字段
        if (member.getBirthday() != null && member.getBirthday().toString().isEmpty()) {
            member.setBirthday(null);
        }
        if (member.getExpireDate() != null && member.getExpireDate().toString().isEmpty()) {
            member.setExpireDate(null);
        }
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "添加成功");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Member member) {
        // ====== 手机号校验 ======
        if (!isValidPhone(member.getPhone())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "手机号必须以1开头，共11位数字");
            return error;
        }

        // 处理空日期字段
        if (member.getBirthday() != null && member.getBirthday().toString().isEmpty()) {
            member.setBirthday(null);
        }
        if (member.getExpireDate() != null && member.getExpireDate().toString().isEmpty()) {
            member.setExpireDate(null);
        }
        member.setId(id);

        // 读取旧 expire_date，仅变更时才发站内信
        Member oldMember = memberMapper.selectById(id);
        LocalDate oldExpire = oldMember != null ? oldMember.getExpireDate() : null;

        memberMapper.updateById(member);

        boolean expireChanged = member.getExpireDate() != null
                && (oldExpire == null || !oldExpire.equals(member.getExpireDate()));

        if (expireChanged) {
            String msg;
            UserMessage um = new UserMessage();
            um.setMemberId(id);
            if (member.getExpireDate().isBefore(java.time.LocalDate.now())) {
                msg = "您的会员卡已过期，请及时续费";
            } else {
                msg = "恭喜成为正式会员，有效期至" + member.getExpireDate().toString();
            }
            um.setContent(msg);
            um.setIsRead(false);
            um.setCreatedAt(java.time.LocalDateTime.now());
            userMessageMapper.insert(um);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        memberMapper.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();

        long total = memberMapper.selectCount(null);
        result.put("total", total);

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        LambdaQueryWrapper<Member> monthlyWrapper = new LambdaQueryWrapper<>();
        monthlyWrapper.ge(Member::getCreatedAt, startOfMonth);
        long monthlyNew = memberMapper.selectCount(monthlyWrapper);
        result.put("monthlyNew", monthlyNew);

        LambdaQueryWrapper<Member> goldWrapper = new LambdaQueryWrapper<>();
        goldWrapper.eq(Member::getLevel, "黄金会员");
        long goldCount = memberMapper.selectCount(goldWrapper);
        result.put("goldCount", goldCount);

        LambdaQueryWrapper<Member> expiringWrapper = new LambdaQueryWrapper<>();
        expiringWrapper.between(Member::getExpireDate, today, today.plusDays(7));
        long expiringCount = memberMapper.selectCount(expiringWrapper);
        result.put("expiringCount", expiringCount);

        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);
        LambdaQueryWrapper<Member> lastMonthWrapper = new LambdaQueryWrapper<>();
        lastMonthWrapper.ge(Member::getCreatedAt, startOfLastMonth)
                .lt(Member::getCreatedAt, startOfMonth);
        long lastMonthNew = memberMapper.selectCount(lastMonthWrapper);
        long totalChange = lastMonthNew == 0 ? 0 :
                (long) (((double) (monthlyNew - lastMonthNew) / lastMonthNew) * 100);
        result.put("totalChange", totalChange);
        result.put("monthlyChange", 0);

        return result;
    }

    @GetMapping("/export")
    public void exportMembers(HttpServletResponse response) throws IOException {
        List<Member> list = memberMapper.selectList(
                new LambdaQueryWrapper<Member>().orderByDesc(Member::getCreatedAt)
        );

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=会员列表_" + LocalDate.now().toString() + ".csv");

        try (PrintWriter writer = response.getWriter()) {
            writer.write("\uFEFF");
            writer.println("ID,姓名,手机号,性别,生日,等级,有效期,身高(cm),体重(kg),注册时间");
            for (Member m : list) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        m.getId(),
                        nullToEmpty(m.getName()),
                        nullToEmpty(m.getPhone()),
                        nullToEmpty(m.getGender()),
                        nullToEmpty(m.getBirthday()),
                        nullToEmpty(m.getLevel()),
                        nullToEmpty(m.getExpireDate()),
                        m.getHeight() != null ? m.getHeight().toString() : "",
                        m.getWeight() != null ? m.getWeight().toString() : "",
                        nullToEmpty(m.getCreatedAt())
                );
            }
            writer.flush();
        }
    }


    /**
     * 获取当前登录会员的统计信息（首页资产卡片）
     */
    @GetMapping("/self/stats")
    public Map<String, Object> selfStats() {
        Long memberId = LoginContext.getUserId();
        Map<String, Object> result = new HashMap<>();
        if (memberId == null) {
            result.put("bookingCount", 0); result.put("ptRemaining", 0);
            result.put("competitions", 0); result.put("checkInMonth", 0); result.put("points", 0);
            return result;
        }
        Member member = memberMapper.selectById(memberId);
        // 预约数（待上课）：团课 booked + 私教 scheduled
        int classBookings = classBookingMapper.selectCount(new LambdaQueryWrapper<ClassBooking>().eq(ClassBooking::getMemberId, memberId).eq(ClassBooking::getStatus, "booked")).intValue();
        int ptBookings = personalTrainingMapper.selectCount(new LambdaQueryWrapper<PersonalTraining>().eq(PersonalTraining::getMemberId, memberId).eq(PersonalTraining::getStatus, "scheduled")).intValue();
        // 剩余课时：可用课程包剩余课时 + 本月免费私教剩余次数
        int ptRemaining = 0;
        if (member != null) {
            LambdaQueryWrapper<MemberPrivatePackage> pw = new LambdaQueryWrapper<>();
            pw.eq(MemberPrivatePackage::getMemberId, memberId)
              .ne(MemberPrivatePackage::getStatus, "refunded")
              .gt(MemberPrivatePackage::getRemainingSessions, 0)
              .and(w -> w.and(x -> x.isNotNull(MemberPrivatePackage::getStartDate)
                      .and(y -> y.isNull(MemberPrivatePackage::getEndDate).or().ge(MemberPrivatePackage::getEndDate, LocalDate.now())))
                      .or(z -> z.isNull(MemberPrivatePackage::getStartDate)
                              .and(y -> y.isNull(MemberPrivatePackage::getActivationDeadline).or().ge(MemberPrivatePackage::getActivationDeadline, LocalDate.now()))));
            List<MemberPrivatePackage> pkgList = memberPrivatePackageMapper.selectList(pw);
            for (MemberPrivatePackage pkg : pkgList) {
                ptRemaining += pkg.getRemainingSessions() != null ? pkg.getRemainingSessions() : 0;
            }
            String levelName = member.getLevel();
            if (levelName == null || levelName.trim().isEmpty()) levelName = "普通会员";
            MemberLevel level = MemberLevel.fromDisplayName(levelName.trim());
            int freeUsed = member.getFreePtUsedMonth() != null ? member.getFreePtUsedMonth() : 0;
            ptRemaining += Math.max(0, level.getFreePersonalTrainingsPerMonth() - freeUsed);
        }
        // 比赛报名数：仅已报名（registered）
        int competitions = competitionRegistrationMapper.selectCount(new LambdaQueryWrapper<CompetitionRegistration>().eq(CompetitionRegistration::getMemberId, memberId).eq(CompetitionRegistration::getStatus, "registered")).intValue();
        // 本月打卡：团课/自主训练各1次，私教按下训（remark='end'）计1次
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        int checkInMonth = checkInMapper.countThisMonth(memberId, startOfMonth);
        // 积分
        int points = member != null ? (member.getPoints() != null ? member.getPoints() : 0) : 0;

        result.put("bookingCount", classBookings + ptBookings);
        result.put("ptRemaining", ptRemaining);
        result.put("competitions", competitions);
        result.put("checkInMonth", checkInMonth);
        result.put("points", points);
        return result;
    }

    /**
     * 获取当前登录会员最新体测数据
     */
    @GetMapping("/self/fitness-latest")
    public Map<String, Object> selfFitnessLatest() {
        Long memberId = LoginContext.getUserId();
        Map<String, Object> result = new HashMap<>();
        if (memberId == null) return result;
        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FitnessTest::getMemberId, memberId).orderByDesc(FitnessTest::getTestDate).last("LIMIT 1");
        FitnessTest latest = fitnessTestMapper.selectOne(wrapper);
        if (latest == null) {
            result.put("weight", 0); result.put("bodyFat", 0);
            result.put("bmi", 0); result.put("muscle", 0);
            return result;
        }
        double weight = latest.getWeightKg() != null ? latest.getWeightKg().doubleValue() : 0;
        double bodyFat = latest.getBodyFatPercent() != null ? latest.getBodyFatPercent().doubleValue() : 0;
        double muscle = latest.getMuscleMassKg() != null ? latest.getMuscleMassKg().doubleValue() : 0;
        double bmi = 0;
        Member member = memberMapper.selectById(memberId);
        if (member != null && member.getHeight() != null && member.getHeight().doubleValue() > 0 && weight > 0) {
            double h = member.getHeight().doubleValue() / 100;
            bmi = Math.round(weight / (h * h) * 10.0) / 10.0;
        }
        result.put("weight", weight);
        result.put("bodyFat", bodyFat);
        result.put("bmi", bmi);
        result.put("muscle", muscle);
        return result;
    }

    /**
     * 获取当前会员今日课程列表
     */
    @GetMapping("/today-courses")
    public List<Map<String, Object>> todayCourses() {
        Long memberId = LoginContext.getUserId();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (memberId == null) return result;
        LocalDate today = LocalDate.now();
        // 团课预约
        LambdaQueryWrapper<ClassBooking> cw = new LambdaQueryWrapper<>();
        cw.eq(ClassBooking::getMemberId, memberId).eq(ClassBooking::getStatus, "booked");
        for (ClassBooking cb : classBookingMapper.selectList(cw)) {
            if (cb.getClassId() == null) continue;
            GroupClass gc = groupClassMapper.selectById(cb.getClassId());
            if (gc == null || gc.getStartTime() == null) continue;
            // 只取今天的
            if (!gc.getStartTime().toLocalDate().equals(today)) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", cb.getId());
            item.put("name", gc.getName());
            item.put("time", gc.getStartTime().toLocalTime().toString().substring(0, 5));
            item.put("type", "group");
            item.put("trainerName", "");
            if (gc.getTrainerId() != null) {
                Trainer t = trainerMapper.selectById(gc.getTrainerId());
                if (t != null) item.put("trainerName", t.getName());
            }
            item.put("status", "scheduled");
            result.add(item);
        }
        // 私教预约
        LambdaQueryWrapper<PersonalTraining> pw = new LambdaQueryWrapper<>();
        pw.eq(PersonalTraining::getMemberId, memberId).eq(PersonalTraining::getStatus, "scheduled");
        for (PersonalTraining pt : personalTrainingMapper.selectList(pw)) {
            if (pt.getAppointmentTime() == null) continue;
            if (!pt.getAppointmentTime().toLocalDate().equals(today)) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", pt.getId());
            item.put("name", pt.getPackageName() != null ? pt.getPackageName() : "私教课");
            item.put("time", pt.getAppointmentTime().toLocalTime().toString().substring(0, 5));
            item.put("type", "pt");
            item.put("trainerName", "");
            if (pt.getTrainerId() != null) {
                Trainer t = trainerMapper.selectById(pt.getTrainerId());
                if (t != null) item.put("trainerName", t.getName());
            }
            item.put("status", "scheduled");
            result.add(item);
        }
        return result;
    }
    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }


    @GetMapping("/{id}/packages")
    public List<MemberPrivatePackage> getMemberPackages(@PathVariable Long id) {
        LambdaQueryWrapper<MemberPrivatePackage> w = new LambdaQueryWrapper<>();
        w.eq(MemberPrivatePackage::getMemberId, id).orderByDesc(MemberPrivatePackage::getCreatedAt);
        return memberPrivatePackageMapper.selectList(w);
    }

    @GetMapping("/{id}/benefits")
    public Map<String, Object> getBenefits(@PathVariable Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "会员不存在");
            return error;
        }

        // 跨月重置免费次数（兜底逻辑）
        LocalDate now = LocalDate.now();
        if (member.getFreePtMonthReset() == null ||
                member.getFreePtMonthReset().getMonthValue() != now.getMonthValue()) {
            member.setFreePtUsedMonth(0);
            member.setFreePtMonthReset(now);
            memberMapper.updateById(member);
        }

        // 获取会员等级（字符串）
        String levelName = member.getLevel();
        if (levelName == null || levelName.trim().isEmpty()) {
            levelName = "普通会员";
        }
        // 调用枚举解析
        MemberLevel level = MemberLevel.fromDisplayName(levelName.trim());

        // 构建返回数据（确保字段名与前端匹配）
        Map<String, Object> benefits = new HashMap<>();
        benefits.put("levelName", level.getDisplayName());
        benefits.put("discount", level.getDiscountPercent());           // 前端用的字段名是 discount
        benefits.put("freeSessions", level.getFreePersonalTrainingsPerMonth()); // 前端用的字段名 freeSessions
        benefits.put("canOverbook", level.getPriority() >= 2);         // 铂金 true
        // 额外返回免费次数使用情况（前端可能用，但权益弹窗目前不用，保留）
        benefits.put("freePtTotal", level.getFreePersonalTrainingsPerMonth());
        benefits.put("freePtUsed", member.getFreePtUsedMonth());
        benefits.put("freePtRemaining", Math.max(0, level.getFreePersonalTrainingsPerMonth() - member.getFreePtUsedMonth()));

        return benefits;
    }
}