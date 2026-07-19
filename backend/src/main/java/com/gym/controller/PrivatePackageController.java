package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.dto.RefundRequest;
import com.gym.entity.Member;
import com.gym.entity.MemberPrivatePackage;
import com.gym.entity.PersonalTraining;
import com.gym.entity.PrivatePackage;
import com.gym.enums.MemberLevel;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.MemberPrivatePackageMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.service.MemberLevelService;
import com.gym.service.PrivatePackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/private-packages")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class PrivatePackageController {

    @Autowired
    private MemberPrivatePackageMapper packageMapper;

    // 注入新的 Service
    @Autowired
    private PrivatePackageService privatePackageService;
    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private MemberLevelService memberLevelService;
    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;

    @PostMapping("/buy")
    public Map<String, Object> buyPackage(@RequestBody Map<String, Object> req) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long memberId = Long.valueOf(req.get("memberId").toString());
            Integer packageId = Integer.valueOf(req.get("packageId").toString());

            // 从商品表读取真实数据
            PrivatePackage pkg = privatePackageService.getById(packageId);
            if (pkg == null) {
                result.put("success", false);
                result.put("message", "套餐不存在或已下架");
                return result;
            }
            if (!pkg.getIsActive()) {
                result.put("success", false);
                result.put("message", "该套餐已下架，暂不可购买");
                return result;
            }

            // ====== 获取会员等级，计算折扣价 ======
            Member member = memberMapper.selectById(memberId);
            String levelName = member != null ? member.getLevel() : "普通会员";
            BigDecimal originalPrice = pkg.getPrice();
            BigDecimal discountedPrice = memberLevelService.getDiscountedPrice(originalPrice, levelName);

            // 获取折扣百分比（用于前端显示）
            MemberLevel level = MemberLevel.fromDisplayName(levelName);
            int discountPercent = level.getDiscountPercent();

            // 使用商品表的数据 + 折扣价
            MemberPrivatePackage memberPkg = new MemberPrivatePackage();
            memberPkg.setMemberId(memberId);
            memberPkg.setPackageId(packageId);
            memberPkg.setPackageName(pkg.getName());
            memberPkg.setTotalSessions(pkg.getSessions());
            memberPkg.setUsedSessions(0);
            memberPkg.setRemainingSessions(pkg.getSessions());
            memberPkg.setValidDays(pkg.getValidDays());
            memberPkg.setPrice(discountedPrice);
            memberPkg.setOriginalPrice(originalPrice);
            memberPkg.setStartDate(null);
            memberPkg.setEndDate(null);
            memberPkg.setActivationDeadline(LocalDate.now().plusDays(30));
            memberPkg.setStatus("active");

            packageMapper.insert(memberPkg);

            // ====== 返回折扣信息，前端显示 ======
            result.put("success", true);
            result.put("message", "购买成功");
            result.put("packageId", memberPkg.getId());
            result.put("originalPrice", originalPrice);
            result.put("discountedPrice", discountedPrice);
            result.put("discountPercent", discountPercent);
            result.put("levelName", levelName);
            result.put("savedAmount", originalPrice.subtract(discountedPrice));

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "购买失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取会员所有有效课程包（包含未设置有效期的）
     */
    @GetMapping("/mine")
    public List<MemberPrivatePackage> getMyPackages(@RequestParam Long memberId) {
        LambdaQueryWrapper<MemberPrivatePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberPrivatePackage::getMemberId, memberId)
                .eq(MemberPrivatePackage::getStatus, "active")
                .gt(MemberPrivatePackage::getRemainingSessions, 0)   // 新增
                .and(w -> w.isNull(MemberPrivatePackage::getEndDate)
                        .or()
                        .ge(MemberPrivatePackage::getEndDate, LocalDate.now())
                );
        return packageMapper.selectList(wrapper);
    }
    /**
     * 私教包退款（按已用比例计算）
     */
    @PostMapping("/refund")
    @Transactional
    public Map<String, Object> refund(@RequestBody RefundRequest request) {
        Map<String, Object> result = new HashMap<>();

        MemberPrivatePackage pkg = packageMapper.selectById(request.getPackageId());
        if (pkg == null) {
            result.put("success", false);
            result.put("message", "课程包不存在");
            return result;
        }

        // 校验是否属于该会员
        if (!pkg.getMemberId().equals(request.getMemberId())) {
            result.put("success", false);
            result.put("message", "无权操作该课程包");
            return result;
        }

        // 检查是否已退款
        if ("refunded".equals(pkg.getStatus())) {
            result.put("success", false);
            result.put("message", "该课程包已退款");
            return result;
        }

        // 检查是否已过期（已过期的不退款，或按规则处理）
        if (pkg.getEndDate() != null && pkg.getEndDate().isBefore(LocalDate.now())) {
            result.put("success", false);
            result.put("message", "课程包已过期，无法退款");
            return result;
        }

        // ====== 计算退款金额 ======
        int totalSessions = pkg.getTotalSessions();
        int usedSessions = pkg.getUsedSessions();
        int remainingSessions = pkg.getRemainingSessions();

        if (remainingSessions <= 0) {
            result.put("success", false);
            result.put("message", "课程包已用完，无法退款");
            return result;
        }

        // 原价（或实付价）按比例退款
        BigDecimal paidPrice = pkg.getPrice();           // 实付金额
        BigDecimal originalPrice = pkg.getOriginalPrice() != null ? pkg.getOriginalPrice() : paidPrice;

        // 单节课价值 = 实付 / 总课时
        BigDecimal perSessionValue = paidPrice.divide(BigDecimal.valueOf(totalSessions), 2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = perSessionValue.multiply(BigDecimal.valueOf(remainingSessions));

        // ====== 执行退款（标记状态 + 记录退款金额） ======
        pkg.setStatus("refunded");
        pkg.setRefundAmount(refundAmount);
        pkg.setRefundReason(request.getReason());
        pkg.setRefundTime(LocalDateTime.now());
        packageMapper.updateById(pkg);

        // 同时取消该课程包下所有待上课的预约
        LambdaQueryWrapper<PersonalTraining> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.eq(PersonalTraining::getPackageId, pkg.getId())
                .eq(PersonalTraining::getStatus, "scheduled");
        List<PersonalTraining> ptList = personalTrainingMapper.selectList(ptWrapper);
        for (PersonalTraining pt : ptList) {
            pt.setStatus("cancelled");
            pt.setCancelReason("课程包退款：" + request.getReason());
            personalTrainingMapper.updateById(pt);
        }

        result.put("success", true);
        result.put("message", "退款成功");
        result.put("refundAmount", refundAmount);
        result.put("usedSessions", usedSessions);
        result.put("remainingSessions", remainingSessions);
        result.put("cancelledBookings", ptList.size());
        return result;
    }
}