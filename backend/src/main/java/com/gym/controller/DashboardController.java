package com.gym.controller;

import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.entity.PersonalTraining;
import com.gym.entity.Trainer;
import com.gym.entity.CheckIn;
import com.gym.entity.TrainerLeave;
import com.gym.entity.PointsRedemption;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.CheckInMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.TrainerMapper;
import com.gym.mapper.TrainerLeaveMapper;
import com.gym.mapper.PointsRedemptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private ClassBookingMapper bookingMapper;

    @Autowired
    private PersonalTrainingMapper ptMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;

    @Autowired
    private TrainerLeaveMapper trainerLeaveMapper;

    @Autowired
    private PointsRedemptionMapper pointsRedemptionMapper;


    /** 仪表盘总览：6 张统计卡片 + 教练工作量 */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime d30 = now.minusDays(30);

        // 1. ???????? booked + ?? scheduled?
        long todayGroup = bookingMapper.selectCount(new LambdaQueryWrapper<ClassBooking>()
                .ge(ClassBooking::getBookingTime, dayStart).lt(ClassBooking::getBookingTime, dayEnd)
                .eq(ClassBooking::getStatus, "booked"));
        long todayPt = ptMapper.selectCount(new LambdaQueryWrapper<PersonalTraining>()
                .ge(PersonalTraining::getAppointmentTime, dayStart).lt(PersonalTraining::getAppointmentTime, dayEnd)
                .eq(PersonalTraining::getStatus, "scheduled"));
        result.put("todayBookings", todayGroup + todayPt);

        // 2. 本月营收：团课实收（checked_in/completed）+ 私教按教练单价（completed）
        BigDecimal revenue = BigDecimal.ZERO;
        List<ClassBooking> monthBookings = bookingMapper.selectList(new LambdaQueryWrapper<ClassBooking>()
                .ge(ClassBooking::getPayTime, monthStart).lt(ClassBooking::getPayTime, nextMonthStart)
                .in(ClassBooking::getStatus, "checked_in", "completed"));
        for (ClassBooking b : monthBookings) {
            if (b.getPaidAmount() != null) revenue = revenue.add(b.getPaidAmount());
        }
        result.put("groupRevenue", revenue);
        List<PersonalTraining> monthCompletedPts = ptMapper.selectList(new LambdaQueryWrapper<PersonalTraining>()
                .ge(PersonalTraining::getAppointmentTime, monthStart)
                .lt(PersonalTraining::getAppointmentTime, nextMonthStart)
                .eq(PersonalTraining::getStatus, "completed"));
        BigDecimal ptRevenue = BigDecimal.ZERO;
        int ptRevenueCount = 0;
        for (PersonalTraining pt : monthCompletedPts) {
            if (pt.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(pt.getTrainerId());
                if (trainer != null && trainer.getPricePerHour() != null) {
                    ptRevenue = ptRevenue.add(trainer.getPricePerHour());
                    ptRevenueCount++;
                }
            }
        }
        result.put("ptRevenue", ptRevenue);
        result.put("ptRevenueCount", ptRevenueCount);
        revenue = revenue.add(ptRevenue);
        result.put("monthRevenue", revenue);
        result.put("monthRevenueText", revenue.setScale(2).toPlainString());

        // 3. 活跃会员：近30天有打卡或预约记录（去重）
        Set<Long> active = new HashSet<>();
        checkInMapper.selectList(new LambdaQueryWrapper<CheckIn>()
                .ge(CheckIn::getCheckInTime, d30)).forEach(ci -> { if (ci.getMemberId() != null) active.add(ci.getMemberId()); });
        bookingMapper.selectList(new LambdaQueryWrapper<ClassBooking>()
                .ge(ClassBooking::getBookingTime, d30)).forEach(b -> { if (b.getMemberId() != null) active.add(b.getMemberId()); });
        ptMapper.selectList(new LambdaQueryWrapper<PersonalTraining>()
                .ge(PersonalTraining::getAppointmentTime, d30)).forEach(p -> { if (p.getMemberId() != null) active.add(p.getMemberId()); });
        result.put("activeMembers", active.size());

        // 4. 课程满员率：scheduled 课程 enrolled/max_capacity 平均值
        List<GroupClass> scheduledClasses = groupClassMapper.selectList(
                new LambdaQueryWrapper<GroupClass>().eq(GroupClass::getStatus, "scheduled"));
        double fullRate = 0;
        int validCnt = 0;
        for (GroupClass gc : scheduledClasses) {
            if (gc.getMaxCapacity() != null && gc.getMaxCapacity() > 0) {
                int enrolled = gc.getEnrolled() != null ? gc.getEnrolled() : 0;
                fullRate += enrolled * 1.0 / gc.getMaxCapacity();
                validCnt++;
            }
        }
        result.put("fullRate", validCnt > 0 ? Math.round(fullRate / validCnt * 1000) / 10.0 : 0);

        // 5. 待审批请假数
        long pendingLeaves = trainerLeaveMapper.selectCount(
                new LambdaQueryWrapper<TrainerLeave>().eq(TrainerLeave::getStatus, "pending"));
        result.put("pendingLeaves", pendingLeaves);

        // 6. 积分兑换待处理数
        long pendingRedemptions = pointsRedemptionMapper.selectCount(
                new LambdaQueryWrapper<PointsRedemption>().eq(PointsRedemption::getStatus, "pending"));
        result.put("pendingRedemptions", pendingRedemptions);

        // 教练工作量：按活跃教练统计待上课/进行中的私教数
        List<Map<String, Object>> coachWorkload = new ArrayList<>();
        List<Trainer> activeTrainers = trainerMapper.selectList(
                new LambdaQueryWrapper<Trainer>().eq(Trainer::getStatus, "active"));
        for (Trainer t : activeTrainers) {
            long workload = ptMapper.selectCount(new LambdaQueryWrapper<PersonalTraining>()
                    .eq(PersonalTraining::getTrainerId, t.getId())
                    .in(PersonalTraining::getStatus, "scheduled", "ongoing"));
            Map<String, Object> item = new HashMap<>();
            item.put("name", t.getName());
            item.put("value", workload);
            coachWorkload.add(item);
        }
        result.put("coachWorkload", coachWorkload);

        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(@RequestParam(defaultValue = "7") Integer days) {
        Map<String, Object> result = new HashMap<>();

        long memberCount = memberMapper.selectCount(null);
        long trainerCount = trainerMapper.selectCount(null);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0);

        LambdaQueryWrapper<ClassBooking> bookingWrapper = new LambdaQueryWrapper<>();
        bookingWrapper.ge(ClassBooking::getBookingTime, startOfMonth)
                .eq(ClassBooking::getStatus, "booked");
        long bookingCount = bookingMapper.selectCount(bookingWrapper);

        LambdaQueryWrapper<PersonalTraining> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.ge(PersonalTraining::getAppointmentTime, startOfMonth)
                .eq(PersonalTraining::getStatus, "scheduled");
        long ptCount = ptMapper.selectCount(ptWrapper);

        // 铂金会员统计（新增）
        LambdaQueryWrapper<com.gym.entity.Member> platinumWrapper = new LambdaQueryWrapper<>();
        platinumWrapper.eq(com.gym.entity.Member::getLevel, "铂金会员");
        long platinumCount = memberMapper.selectCount(platinumWrapper);
        result.put("platinumCount", platinumCount);

        LocalDateTime startOfLastMonth = now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0);
        LambdaQueryWrapper<ClassBooking> lastMonthBookingWrapper = new LambdaQueryWrapper<>();
        lastMonthBookingWrapper.ge(ClassBooking::getBookingTime, startOfLastMonth)
                .lt(ClassBooking::getBookingTime, startOfMonth)
                .eq(ClassBooking::getStatus, "booked");
        long lastMonthBookingCount = bookingMapper.selectCount(lastMonthBookingWrapper);

        LambdaQueryWrapper<PersonalTraining> lastMonthPtWrapper = new LambdaQueryWrapper<>();
        lastMonthPtWrapper.ge(PersonalTraining::getAppointmentTime, startOfLastMonth)
                .lt(PersonalTraining::getAppointmentTime, startOfMonth)
                .eq(PersonalTraining::getStatus, "scheduled");
        long lastMonthPtCount = ptMapper.selectCount(lastMonthPtWrapper);

        double bookingTrend = lastMonthBookingCount == 0 ? 0 :
                ((double) (bookingCount - lastMonthBookingCount) / lastMonthBookingCount) * 100;
        double ptTrend = lastMonthPtCount == 0 ? 0 :
                ((double) (ptCount - lastMonthPtCount) / lastMonthPtCount) * 100;

        result.put("memberTrend", 0);
        result.put("trainerTrend", 0);
        result.put("bookingTrend", Math.round(bookingTrend * 10) / 10.0);
        result.put("ptTrend", Math.round(ptTrend * 10) / 10.0);

        result.put("memberCount", memberCount);
        result.put("trainerCount", trainerCount);
        result.put("bookingCount", bookingCount);
        result.put("ptCount", ptCount);

        List<String> trendDates = new ArrayList<>();
        List<Integer> trendData = new ArrayList<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            trendDates.add(d.format(fmt));
            LocalDateTime startOfDay = d.atStartOfDay();
            LocalDateTime endOfDay = d.plusDays(1).atStartOfDay();

            LambdaQueryWrapper<ClassBooking> dayBookingWrapper = new LambdaQueryWrapper<>();
            dayBookingWrapper.ge(ClassBooking::getBookingTime, startOfDay)
                    .lt(ClassBooking::getBookingTime, endOfDay)
                    .eq(ClassBooking::getStatus, "booked");
            long dayBookingCount = bookingMapper.selectCount(dayBookingWrapper);

            LambdaQueryWrapper<PersonalTraining> dayPtWrapper = new LambdaQueryWrapper<>();
            dayPtWrapper.ge(PersonalTraining::getAppointmentTime, startOfDay)
                    .lt(PersonalTraining::getAppointmentTime, endOfDay)
                    .eq(PersonalTraining::getStatus, "scheduled");
            long dayPtCount = ptMapper.selectCount(dayPtWrapper);

            trendData.add((int) (dayBookingCount + dayPtCount));
        }
        result.put("trendDates", trendDates);
        result.put("trendData", trendData);

        long totalGroupBookings = bookingMapper.selectCount(null);
        long totalPersonalTrainings = ptMapper.selectCount(null);
        List<Map<String, Object>> pieData = new ArrayList<>();

        Map<String, Object> groupItem = new HashMap<>();
        groupItem.put("name", "团课");
        groupItem.put("value", totalGroupBookings);
        pieData.add(groupItem);

        Map<String, Object> ptItem = new HashMap<>();
        ptItem.put("name", "私教");
        ptItem.put("value", totalPersonalTrainings);
        pieData.add(ptItem);

        result.put("pieData", pieData);

        return result;
    }

    /** 教练统计：在职教练总数 + 按专长分组数量 */
    @GetMapping("/coach-stats")
    public Map<String, Object> getCoachStats() {
        Map<String, Object> result = new HashMap<>();
        List<Trainer> activeTrainers = trainerMapper.selectList(
                new LambdaQueryWrapper<Trainer>().eq(Trainer::getStatus, "active"));
        result.put("total", activeTrainers.size());

        int fatLoss = 0, muscleGain = 0, rehab = 0, other = 0;
        for (Trainer t : activeTrainers) {
            String specialty = t.getSpecialty() == null ? "" : t.getSpecialty();
            boolean matched = false;
            for (String tag : specialty.split(",")) {
                String tt = tag.trim();
                if (tt.isEmpty()) continue;
                if (tt.contains("减脂")) { fatLoss++; matched = true; }
                else if (tt.contains("增肌")) { muscleGain++; matched = true; }
                else if (tt.contains("康复")) { rehab++; matched = true; }
            }
            if (!matched) other++;
        }
        Map<String, Object> specialties = new LinkedHashMap<>();
        specialties.put("减脂塑形", fatLoss);
        specialties.put("增肌力量", muscleGain);
        specialties.put("康复拉伸", rehab);
        specialties.put("其他", other);
        result.put("specialties", specialties);
        return result;
    }

    @GetMapping("/hot-classes")
    public List<Map<String, Object>> getHotClasses() {
        List<GroupClass> classes = groupClassMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();

        for (GroupClass gc : classes) {
            LambdaQueryWrapper<ClassBooking> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ClassBooking::getClassId, gc.getId())
                    .eq(ClassBooking::getStatus, "booked");
            long count = bookingMapper.selectCount(wrapper);

            Map<String, Object> item = new HashMap<>();
            item.put("name", gc.getName());
            item.put("bookings", count);
            item.put("status", gc.getStatus());
            result.add(item);
        }

        result.sort((a, b) -> ((Long) b.get("bookings")).compareTo((Long) a.get("bookings")));

        if (result.size() > 5) {
            result = result.subList(0, 5);
        }
        return result;
    }
}