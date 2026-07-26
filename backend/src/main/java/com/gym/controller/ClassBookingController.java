package com.gym.controller;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.ClassBooking;
import com.gym.entity.GroupClass;
import com.gym.entity.Member;
import com.gym.enums.MemberLevel;
import com.gym.mapper.ClassBookingMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/class-bookings")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class ClassBookingController {

    @Autowired
    private ClassBookingMapper classBookingMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;
    @Autowired
    private MemberMapper memberMapper;
    /**
     * 查询当前会员的团课预约列表（支持日期范围筛选）
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam Long memberId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<ClassBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId);

        // ====== 安全日期转换 ======
        if (startDate != null && !startDate.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(startDate);
                wrapper.ge(ClassBooking::getBookingTime, date.atStartOfDay());
            } catch (Exception e) {
                System.err.println("Invalid startDate format: " + startDate);
            }
        }
        if (endDate != null && !endDate.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(endDate);
                wrapper.le(ClassBooking::getBookingTime, date.atTime(23, 59, 59));
            } catch (Exception e) {
                System.err.println("Invalid endDate format: " + endDate);
            }
        }

        wrapper.orderByDesc(ClassBooking::getBookingTime);

        IPage<ClassBooking> pageResult = classBookingMapper.selectPage(
                new Page<>(page, size), wrapper
        );

        pageResult.getRecords().forEach(b -> {
            if (b.getClassId() != null) {
                GroupClass gc = groupClassMapper.selectById(b.getClassId());
                if (gc != null) {
                    b.setClassName(gc.getName());
                    b.setEndTime(gc.getEndTime());
                }
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
    }

    /**
     * 预约团课（先支付，再预约）
     */
    @PostMapping
    @Transactional
    public Map<String, Object> book(@RequestBody ClassBooking booking) {
        Map<String, Object> result = new HashMap<>();

        // 1. 检查课程是否存在
        GroupClass gc = groupClassMapper.selectById(booking.getClassId());
        if (gc == null) {
            result.put("success", false);
            result.put("message", "课程不存在");
            return result;
        }

        // 2. 检查会员是否存在并获取等级
        Member member = memberMapper.selectById(booking.getMemberId());
        if (member == null) {
            result.put("success", false);
            result.put("message", "会员不存在");
            return result;
        }

        // 2.5 访客/过期会员权限校验
        if (member.isVisitor() || member.isExpired()) {
            if (member.isExpired()) {
                result.put("success", false);
                result.put("message", "会员卡已过期，请续费");
                return result;
            }
            if (gc.getAllowVisitor() == null || !gc.getAllowVisitor()) {
                result.put("success", false);
                result.put("message", "仅限正式会员预约，请办理会员卡");
                return result;
            }
            if (member.getExperienceUsed() != null && member.getExperienceUsed()) {
                result.put("success", false);
                result.put("message", "您已使用过体验课，请办理会员卡");
                return result;
            }
        }

        // 3. 检查是否已满员（铂金会员可超额2人）
        boolean isPlatinum = "铂金会员".equals(member.getLevel());
        int maxAllowed = isPlatinum ? gc.getMaxCapacity() + 2 : gc.getMaxCapacity();
        if (gc.getEnrolled() >= maxAllowed) {
            result.put("success", false);
            result.put("message", isPlatinum ? "课程已超员（铂金会员可超额2人），当前已满" : "课程已满员，无法预约");
            return result;
        }

        // 4. 检查课程状态
        if (!"scheduled".equals(gc.getStatus())) {
            result.put("success", false);
            result.put("message", "课程已取消或已结束");
            return result;
        }

        // 5. 检查课程是否已过期
        if (gc.getEndTime() != null && gc.getEndTime().isBefore(LocalDateTime.now())) {
            result.put("success", false);
            result.put("message", "课程已过期，无法预约");
            return result;
        }

        // 6. 检查是否已预约过
        LambdaQueryWrapper<ClassBooking> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ClassBooking::getMemberId, booking.getMemberId())
                .eq(ClassBooking::getClassId, booking.getClassId())
                .eq(ClassBooking::getStatus, "booked");
        if (classBookingMapper.selectCount(checkWrapper) > 0) {
            result.put("success", false);
            result.put("message", "您已预约过该课程");
            return result;
        }

        // 7. 检查是否已支付（防止重复支付）
        LambdaQueryWrapper<ClassBooking> payWrapper = new LambdaQueryWrapper<>();
        payWrapper.eq(ClassBooking::getMemberId, booking.getMemberId())
                .eq(ClassBooking::getClassId, booking.getClassId())
                .eq(ClassBooking::getPaymentStatus, "paid");
        if (classBookingMapper.selectCount(payWrapper) > 0) {
            result.put("success", false);
            result.put("message", "您已支付过该课程");
            return result;
        }

        // 8. 计算支付金额
        BigDecimal price = gc.getPrice() != null ? gc.getPrice() : BigDecimal.ZERO;

        // 9. 创建预约记录（状态为待支付）
        booking.setBookingTime(LocalDateTime.now());
        booking.setStatus("booked");
        booking.setPaymentStatus("unpaid");
        booking.setPaidAmount(price);
        classBookingMapper.insert(booking);

        // 9.5 准会员预约体验课后标记
        if (!member.isActiveMember() && gc.getAllowVisitor() != null && gc.getAllowVisitor()) {
            member.setExperienceUsed(true);
            memberMapper.updateById(member);
        }

        // 9.6 免费/体验课直接增加已预约人数（无需支付）
        if ("free".equals(gc.getType()) || price.compareTo(BigDecimal.ZERO) == 0 || (gc.getAllowVisitor() != null && gc.getAllowVisitor())) {
            gc.setEnrolled(gc.getEnrolled() + 1);
            groupClassMapper.updateById(gc);
            log.info("预约成功 courseId={} enrolled={}", gc.getId(), gc.getEnrolled());
            result.put("success", true);
            result.put("message", "预约成功");
            result.put("amount", BigDecimal.ZERO);
            return result;
        }

        // 10. 返回支付信息
        result.put("success", true);
        result.put("message", "预约已创建，请完成支付");
        result.put("bookingId", booking.getId());
        result.put("amount", price);
        result.put("className", gc.getName());
        return result;
    }

    /**
     * 支付确认（支付成功后调用）
     */
    @PostMapping("/{bookingId}/pay")
    @Transactional
    public Map<String, Object> confirmPay(@PathVariable Long bookingId) {
        Map<String, Object> result = new HashMap<>();

        ClassBooking booking = classBookingMapper.selectById(bookingId);
        if (booking == null) {
            result.put("success", false);
            result.put("message", "预约记录不存在");
            return result;
        }

        // 检查是否已支付
        if ("paid".equals(booking.getPaymentStatus())) {
            result.put("success", false);
            result.put("message", "该预约已支付");
            return result;
        }

        // 检查课程是否仍然可预约
        GroupClass gc = groupClassMapper.selectById(booking.getClassId());
        if (gc == null) {
            result.put("success", false);
            result.put("message", "课程不存在");
            return result;
        }

        // 检查会员等级（铂金可超额）
        Member member = memberMapper.selectById(booking.getMemberId());
        if (member == null) {
            result.put("success", false);
            result.put("message", "会员不存在");
            return result;
        }
        boolean isPlatinum = "铂金会员".equals(member.getLevel());
        int maxAllowed = isPlatinum ? gc.getMaxCapacity() + 2 : gc.getMaxCapacity();
        if (gc.getEnrolled() >= maxAllowed) {
            result.put("success", false);
            result.put("message", isPlatinum ? "课程已超员（铂金会员可超额2人），支付失败" : "课程已满员，支付失败");
            return result;
        }

        // 再次检查课程是否过期
        if (gc.getEndTime() != null && gc.getEndTime().isBefore(LocalDateTime.now())) {
            result.put("success", false);
            result.put("message", "课程已过期，无法支付");
            return result;
        }

        // ====== 应用会员折扣 ======
        String levelName = member.getLevel() != null ? member.getLevel() : "普通会员";
        MemberLevel level = MemberLevel.fromDisplayName(levelName);
        int discount = level.getDiscountPercent();

        BigDecimal originalPrice = gc.getPrice() != null ? gc.getPrice() : BigDecimal.ZERO;
        BigDecimal discountedPrice = originalPrice
                .multiply(BigDecimal.valueOf(100 - discount))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // 更新支付状态和实付金额
        booking.setPaymentStatus("paid");
        booking.setPayTime(LocalDateTime.now());
        booking.setPaidAmount(discountedPrice);
        classBookingMapper.updateById(booking);

        // 增加课程已预约人数
        gc.setEnrolled(gc.getEnrolled() + 1);
        groupClassMapper.updateById(gc);
        log.info("支付成功 courseId={} enrolled={}", gc.getId(), gc.getEnrolled());

        result.put("success", true);
        result.put("message", "支付成功，预约完成！");
        result.put("paidAmount", discountedPrice);
        result.put("discountApplied", discount);
        result.put("originalPrice", originalPrice);
        return result;
    }

    /**
     * 取消预约（未支付/已支付均可取消）
     */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancelViaPost(@PathVariable Long id) {
        return cancel(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> cancel(@PathVariable Long id) {
        ClassBooking booking = classBookingMapper.selectById(id);
        if (booking == null) {
            return errorResponse("预约记录不存在");
        }
        if ("cancelled".equals(booking.getStatus())) {
            return errorResponse("该预约已取消");
        }
        if ("checked_in".equals(booking.getStatus())) {
            return errorResponse("该预约已签到，无法取消");
        }

        // ====== 获取课程信息，判断时间限制 ======
        GroupClass gc = groupClassMapper.selectById(booking.getClassId());
        if (gc == null) {
            return errorResponse("课程不存在");
        }

        // ====== 新增：距离开课不足2小时，不允许取消 ======
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime classStart = gc.getStartTime();
        if (classStart != null) {
            if (now.isAfter(classStart.minusHours(2))) {
                return errorResponse("距离开课不足2小时，不可取消，请联系前台处理。");
            }
            if (now.isAfter(classStart)) {
                return errorResponse("课程已开始，不可取消，请联系前台处理。");
            }
        }

        // 原有退款/取消逻辑...
        if ("paid".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("refunded");
            if (gc != null && gc.getEnrolled() > 0) {
                gc.setEnrolled(gc.getEnrolled() - 1);
                groupClassMapper.updateById(gc);
            }
            booking.setStatus("cancelled");
            classBookingMapper.updateById(booking);
            return successResponse("退课退款成功");
        } else {
                                    if (gc != null && gc.getEnrolled() != null && gc.getEnrolled() > 0) {
                gc.setEnrolled(gc.getEnrolled() - 1);
                groupClassMapper.updateById(gc);
            }
            // 访客取消体验课，归还体验机会
            Member cancelMember = memberMapper.selectById(booking.getMemberId());
            if (cancelMember != null && cancelMember.isVisitor()
                && gc.getAllowVisitor() != null && gc.getAllowVisitor()
                && cancelMember.getExperienceUsed() != null && cancelMember.getExperienceUsed()) {
                cancelMember.setExperienceUsed(false);
                memberMapper.updateById(cancelMember);
            }
            booking.setPaymentStatus("cancelled");
            booking.setStatus("cancelled");
            classBookingMapper.updateById(booking);
            return successResponse("取消预约成功");
        }
    }

    /**
     * 获取会员团课预约统计
     */
    @GetMapping("/stats/{memberId}")
    public Map<String, Object> getStats(@PathVariable Long memberId) {
        LambdaQueryWrapper<ClassBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getStatus, "booked")
                .eq(ClassBooking::getPaymentStatus, "paid");
        long booked = classBookingMapper.selectCount(wrapper);

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getStatus, "checked_in");
        long checkedIn = classBookingMapper.selectCount(wrapper);

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getStatus, "cancelled");
        long cancelled = classBookingMapper.selectCount(wrapper);

        // 待支付数量
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBooking::getMemberId, memberId)
                .eq(ClassBooking::getStatus, "booked")
                .eq(ClassBooking::getPaymentStatus, "unpaid");
        long unpaid = classBookingMapper.selectCount(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("booked", booked);
        result.put("checkedIn", checkedIn);
        result.put("cancelled", cancelled);
        result.put("unpaid", unpaid);
        return result;
    }

    private Map<String, Object> successResponse(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", msg);
        return result;
    }

    private Map<String, Object> errorResponse(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", msg);
        return result;
    }
    /**
     * 获取某节团课的所有报名记录（管理员用）
     */
    @GetMapping("/class/{classId}")
    public List<Map<String, Object>> getEnrollments(@PathVariable Long classId) {
        List<ClassBooking> bookings = classBookingMapper.selectList(
                new LambdaQueryWrapper<ClassBooking>()
                        .eq(ClassBooking::getClassId, classId)
                        .eq(ClassBooking::getStatus, "booked")
                        .orderByDesc(ClassBooking::getBookingTime)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassBooking b : bookings) {
            Member member = memberMapper.selectById(b.getMemberId());
            Map<String, Object> item = new HashMap<>();
            item.put("memberName", member != null ? member.getName() : "未知");
            item.put("memberPhone", member != null ? member.getPhone() : "");
            item.put("bookingTime", b.getBookingTime() != null ? b.getBookingTime().toString() : "");
            item.put("paymentStatus", b.getPaymentStatus() != null ? b.getPaymentStatus() : "unknown");
            result.add(item);
        }
        return result;
    }
}