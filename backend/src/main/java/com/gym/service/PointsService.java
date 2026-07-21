package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.Member;
import com.gym.entity.PointsHistory;
import com.gym.entity.PointsRedemption;
import com.gym.entity.PointsReward;
import com.gym.entity.MemberPrivatePackage;
import com.gym.mapper.PointsRewardMapper;
import com.gym.mapper.MemberPrivatePackageMapper;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.PointsHistoryMapper;
import com.gym.mapper.PointsRedemptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PointsService {

    @Autowired private MemberMapper memberMapper;
    @Autowired private PointsHistoryMapper historyMapper;
    @Autowired private PointsRedemptionMapper redemptionMapper;
    @Autowired private PointsRewardMapper rewardMapper;
    @Autowired private MemberPrivatePackageMapper packageMapper;

    public int getPoints(Long memberId) {
        Member m = memberMapper.selectById(memberId);
        return m != null && m.getPoints() != null ? m.getPoints() : 0;
    }

    public List<PointsHistory> getHistory(Long memberId, int page, int size) {
        LambdaQueryWrapper<PointsHistory> w = new LambdaQueryWrapper<>();
        w.eq(PointsHistory::getMemberId, memberId)
                .orderByDesc(PointsHistory::getCreatedAt);
        IPage<PointsHistory> p = historyMapper.selectPage(new Page<>(page, size), w);
        return p.getRecords();
    }

    /**
     * 增加积分
     */
    @Transactional
    public boolean addPoints(Long memberId, int points, String changeType, Long referenceId, String remark) {
        Member m = memberMapper.selectById(memberId);
        if (m == null) return false;
        int current = m.getPoints() != null ? m.getPoints() : 0;
        m.setPoints(current + points);
        memberMapper.updateById(m);

        PointsHistory h = new PointsHistory();
        h.setMemberId(memberId);
        h.setPointsChange(points);
        h.setChangeType(changeType);
        h.setReferenceId(referenceId);
        h.setRemark(remark);
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);
        return true;
    }

    /**
     * 扣除积分（points参数为正数，内部转为负数）
     */
    @Transactional
    public boolean deductPoints(Long memberId, int points, String changeType, Long referenceId, String remark) {
        Member m = memberMapper.selectById(memberId);
        if (m == null) return false;
        int current = m.getPoints() != null ? m.getPoints() : 0;
        if (current < points) return false;
        m.setPoints(current - points);
        memberMapper.updateById(m);

        PointsHistory h = new PointsHistory();
        h.setMemberId(memberId);
        h.setPointsChange(-points);
        h.setChangeType(changeType);
        h.setReferenceId(referenceId);
        h.setRemark(remark);
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);
        return true;
    }

    /**
     * 创建兑换申请
     */
    @Transactional
    public PointsRedemption createRedemption(Long memberId, String type, String remark) {
        int cost = "pt_session".equals(type) ? 100 : 100;
        if (!deductPoints(memberId, cost, "redemption", null, remark)) return null;
        PointsRedemption r = new PointsRedemption();
        r.setMemberId(memberId);
        r.setPointsSpent(cost);
        r.setRedemptionType(type);
        r.setStatus("physical_goods".equals(type) ? "pending" : "approved");
        r.setCreatedAt(LocalDateTime.now());
        if (!"physical_goods".equals(type)) {
            r.setProcessedAt(LocalDateTime.now());
        }
        redemptionMapper.insert(r);
        return r;
    }

    /**
     * 兑换积分商品（新版，支持自动生成私教课包）
     */
    @Transactional
    public Map<String, Object> redeemReward(Long memberId, Long rewardId, String remark) {
        Map<String, Object> result = new java.util.HashMap<>();
        // 1. 查商品
        PointsReward reward = rewardMapper.selectById(rewardId);
        if (reward == null || !Boolean.TRUE.equals(reward.getIsActive())) {
            result.put("success", false);
            result.put("message", "商品不存在或已下架");
            return result;
        }
        // 2. 校验库存
        if (reward.getStock() != null && reward.getStock() != -1 && reward.getStock() <= 0) {
            result.put("success", false);
            result.put("message", "库存不足");
            return result;
        }
        // 3. 校验并扣减积分
        Member m = memberMapper.selectById(memberId);
        int balance = m != null && m.getPoints() != null ? m.getPoints() : 0;
        if (balance < reward.getPointsRequired()) {
            result.put("success", false);
            result.put("message", "积分不足，需要" + reward.getPointsRequired() + "分");
            return result;
        }
        deductPoints(memberId, reward.getPointsRequired(), "redemption", rewardId, remark);

        // 4. 创建兑换记录（状态直接完成，不需要审批）
        PointsRedemption r = new PointsRedemption();
        r.setMemberId(memberId);
        r.setRewardId(rewardId);
        r.setPointsSpent(reward.getPointsRequired());
        r.setRedemptionType(reward.getRewardType());
        r.setStatus("completed");
        r.setCreatedAt(LocalDateTime.now());
        r.setProcessedAt(LocalDateTime.now());
        redemptionMapper.insert(r);

        // 5. 根据类型执行兑换动作
        if ("pt_session".equals(reward.getRewardType())) {
            int sessions = 1;
            try { sessions = Integer.parseInt(reward.getRewardValue()); } catch (Exception ignored) {}
            MemberPrivatePackage pkg = new MemberPrivatePackage();
            pkg.setMemberId(memberId);
            pkg.setPackageId(null);
            pkg.setPackageName(reward.getName());
            pkg.setCoachId(null);
            pkg.setTotalSessions(sessions);
            pkg.setUsedSessions(0);
            pkg.setRemainingSessions(sessions);
            pkg.setStatus("active");
            pkg.setCreatedAt(LocalDateTime.now());
            packageMapper.insert(pkg);
        }

        // 6. 扣库存
        if (reward.getStock() != null && reward.getStock() > 0) {
            reward.setStock(reward.getStock() - 1);
            rewardMapper.updateById(reward);
        }

        result.put("success", true);
        result.put("message", "兑换成功");
        result.put("data", r);
        return result;
    }

    /**
     * 审批兑换（管理员）
     */
    @Transactional
    public boolean approveRedemption(Long id, Long adminId, String remark) {
        PointsRedemption r = redemptionMapper.selectById(id);
        if (r == null || !"pending".equals(r.getStatus())) return false;
        r.setStatus("approved");
        r.setAdminId(adminId);
        r.setAdminRemark(remark);
        r.setProcessedAt(LocalDateTime.now());
        redemptionMapper.updateById(r);
        return true;
    }

    @Transactional
    public boolean rejectRedemption(Long id, Long adminId, String remark) {
        PointsRedemption r = redemptionMapper.selectById(id);
        if (r == null || !"pending".equals(r.getStatus())) return false;
        // 退还积分
        addPoints(r.getMemberId(), r.getPointsSpent(), "redemption_refund", id, remark);
        r.setStatus("rejected");
        r.setAdminId(adminId);
        r.setAdminRemark(remark);
        r.setProcessedAt(LocalDateTime.now());
        redemptionMapper.updateById(r);
        return true;
    }

    public List<PointsRedemption> getRedemptions(Long memberId) {
        LambdaQueryWrapper<PointsRedemption> w = new LambdaQueryWrapper<>();
        w.eq(PointsRedemption::getMemberId, memberId)
                .orderByDesc(PointsRedemption::getCreatedAt);
        return redemptionMapper.selectList(w);
    }

    public IPage<PointsRedemption> getPendingRedemptions(int page, int size) {
        LambdaQueryWrapper<PointsRedemption> w = new LambdaQueryWrapper<>();
        w.eq(PointsRedemption::getStatus, "pending")
                .orderByDesc(PointsRedemption::getCreatedAt);
        return redemptionMapper.selectPage(new Page<>(page, size), w);
    }
}
