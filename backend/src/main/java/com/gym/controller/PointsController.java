package com.gym.controller;

import com.gym.entity.PointsHistory;
import com.gym.entity.PointsRedemption;
import com.gym.service.PointsService;
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

    /** 创建兑换申请 */
    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody Map<String, String> params) {
        Long memberId = LoginContext.getUserId();
        if (memberId == null) { Map<String, Object> e = new HashMap<>(); e.put("success", false); e.put("message", "未登录"); return e; }
        String type = params.get("type");
        String remark = params.get("remark");
        PointsRedemption r = pointsService.createRedemption(memberId, type, remark);
        Map<String, Object> result = new HashMap<>();
        if (r != null) {
            result.put("success", true);
            result.put("message", "兑换申请已提交");
            result.put("data", r);
        } else {
            result.put("success", false);
            result.put("message", "积分不足");
        }
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
