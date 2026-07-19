package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.Member;
import com.gym.entity.PointsHistory;
import com.gym.entity.PointsRedemption;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.PointsHistoryMapper;
import com.gym.mapper.PointsRedemptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PointsService {

    @Autowired private MemberMapper memberMapper;
    @Autowired private PointsHistoryMapper historyMapper;
    @Autowired private PointsRedemptionMapper redemptionMapper;

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
