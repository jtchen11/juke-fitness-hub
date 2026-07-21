package com.gym.controller;

import com.gym.entity.PointsHistory;
import com.gym.entity.PointsRedemption;
import com.gym.service.PointsService;
import com.gym.mapper.PointsRewardMapper;
import com.gym.entity.PointsReward;
import com.gym.auth.LoginContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
public class PointsController {

    @Autowired private PointsService pointsService;
    @Autowired private PointsRewardMapper rewardMapper;

    /** 查询当前积分 */
    @GetMapping
    public Map<String, Object> getPoints() {
        Long memberId = LoginContext.getUserId();
        Map<String, Object> r = new HashMap<>();
        if (memberId == null) { r.put("points", 0); return r; }
        r.put("points", pointsService.getPoints(memberId));
        return r;
    }

    /** 积分明细 */
    @GetMapping("/history")
    public Map<String, Object> getHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long memberId = LoginContext.getUserId();
        Map<String, Object> r = new HashMap<>();
        if (memberId == null) { r.put("list", java.util.Collections.emptyList()); r.put("total", 0); return r; }
        List<PointsHistory> list = pointsService.getHistory(memberId, page, size);
        r.put("list", list);
        r.put("total", list.size());
        return r;
    }

    /** 积分商品列表（会员端） */
    @GetMapping("/rewards")
    public Map<String, Object> getRewards(@RequestParam(defaultValue = "true") Boolean active) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PointsReward> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (active) w.eq(PointsReward::getIsActive, true);
        w.orderByAsc(PointsReward::getSortOrder);
        List<PointsReward> list = rewardMapper.selectList(w);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        return r;
    }

    /** 创建兑换申请 */
    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody Map<String, Object> params) {
        Long memberId = LoginContext.getUserId();
        if (memberId == null) { Map<String, Object> e = new HashMap<>(); e.put("success", false); e.put("message", "未登录"); return e; }
        Long rewardId = params.get("rewardId") != null ? Long.valueOf(params.get("rewardId").toString()) : null;
        if (rewardId == null) { Map<String, Object> e = new HashMap<>(); e.put("success", false); e.put("message", "缺少rewardId"); return e; }
        String remark = (String) params.getOrDefault("remark", "");
        Map<String, Object> result = pointsService.redeemReward(memberId, rewardId, remark);
        return result;
    }

    /** 兑换记录 */
    @GetMapping("/redemptions")
    public Map<String, Object> getRedemptions() {
        Long memberId = LoginContext.getUserId();
        List<PointsRedemption> list = pointsService.getRedemptions(memberId);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        return r;
    }

    /** 管理员：待审批列表 */
    @GetMapping("/admin/pending")
    public Map<String, Object> getPending(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        var p = pointsService.getPendingRedemptions(page, size);
        Map<String, Object> r = new HashMap<>();
        r.put("list", p.getRecords());
        r.put("total", p.getTotal());
        return r;
    }

    /** 管理员：审批通过 */
    @PostMapping("/admin/approve/{id}")
    public Map<String, Object> approve(@PathVariable Long id, @RequestBody Map<String, String> params) {
        Long adminId = LoginContext.getUserId();
        String remark = params.getOrDefault("remark", "");
        boolean ok = pointsService.approveRedemption(id, adminId, remark);
        Map<String, Object> r = new HashMap<>();
        r.put("success", ok);
        r.put("message", ok ? "已通过" : "操作失败");
        return r;
    }

    /** 管理员：驳回 */
    @PostMapping("/admin/reject/{id}")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody Map<String, String> params) {
        Long adminId = LoginContext.getUserId();
        String remark = params.getOrDefault("remark", "");
        boolean ok = pointsService.rejectRedemption(id, adminId, remark);
        Map<String, Object> r = new HashMap<>();
        r.put("success", ok);
        r.put("message", ok ? "已驳回" : "操作失败");
        return r;
    }
}
