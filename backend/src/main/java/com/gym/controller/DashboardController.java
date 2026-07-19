package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.entity.PersonalTraining;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.TrainerMapper;
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
    private ClassBookingMapper bookingMapper;

    @Autowired
    private PersonalTrainingMapper ptMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;

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