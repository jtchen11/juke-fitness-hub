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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PointsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PointsService.class);

    @Autowired private MemberMapper memberMapper;
    @Autowired private PointsHistoryMapper historyMapper;
    @Autowired private PointsRedemptionMapper redemptionMapper;
    @Autowired private PointsRewardMapper rewardMapper;
    @Autowired private MemberPrivatePackageMapper packageMapper;

    public int getPoints(Long memberId) {
        Member m = memberMapper.selectById(memberId);
        return m != null && m.getPoints() != null ? m.getPoints() : 0;
    }

    public List<Map<String, Object>> getHistory(Long memberId, int page, int size) {
        LambdaQueryWrapper<PointsHistory> w = new LambdaQueryWrapper<>();
        w.eq(PointsHistory::getMemberId, memberId)
                .orderByDesc(PointsHistory::getCreatedAt);
        IPage<PointsHistory> p = historyMapper.selectPage(new Page<>(page, size), w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PointsHistory h : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(h.getId()));
            m.put("memberId", h.getMemberId());
            m.put("points", h.getPoints());
            m.put("balance", h.getBalance());
            m.put("changeType", h.getChangeType());
            m.put("sourceId", h.getSourceId());
            m.put("description", h.getDescription());
            m.put("createdAt", h.getCreatedAt());
            enrichRedemptionDesc(m, h);
            result.add(m);
        }
        return result;
    }

    /**
     * 管理端：积分流水（会员搜索 / 变动类型 / 日期范围筛选）
     */
    public Map<String, Object> adminHistory(int page, int size, String keyword,
                                            String changeTypes, String category,
                                            String startDate, String endDate) {
        LambdaQueryWrapper<PointsHistory> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            List<Member> members = memberMapper.selectList(new LambdaQueryWrapper<Member>()
                    .like(Member::getName, keyword.trim()).or().like(Member::getPhone, keyword.trim()));
            List<Long> ids = members.stream().map(Member::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", new ArrayList<>());
                empty.put("total", 0);
                return empty;
            }
            w.in(PointsHistory::getMemberId, ids);
        }
        if (changeTypes != null && !changeTypes.trim().isEmpty()) {
            List<String> ts = java.util.Arrays.stream(changeTypes.split(","))
                    .map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
            if (!ts.isEmpty()) {
                w.in(PointsHistory::getChangeType, ts);
            }
        }
        if ("makeup".equals(category)) {
            w.like(PointsHistory::getDescription, "补签");
        }
        if (startDate != null && !startDate.isEmpty()) {
            w.ge(PointsHistory::getCreatedAt, startDate + " 00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            w.le(PointsHistory::getCreatedAt, endDate + " 23:59:59");
        }
        w.orderByDesc(PointsHistory::getCreatedAt);
        IPage<PointsHistory> p = historyMapper.selectPage(new Page<>(page, size), w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PointsHistory h : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", h.getId());
            m.put("memberId", h.getMemberId());
            m.put("points", h.getPoints());
            m.put("balance", h.getBalance());
            m.put("changeType", h.getChangeType());
            m.put("description", h.getDescription());
            m.put("createdAt", h.getCreatedAt());
            if (h.getMemberId() != null) {
                Member member = memberMapper.selectById(h.getMemberId());
                m.put("memberName", member != null ? member.getName() : "未知");
                m.put("memberPhone", member != null ? member.getPhone() : "");
            } else {
                m.put("memberName", "未知");
                m.put("memberPhone", "");
            }
            enrichRedemptionDesc(m, h);
            result.add(m);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("list", result);
        r.put("total", p.getTotal());
        return r;
    }

    /**
     * 兑换类流水补充商品名称描述
     */
    private void enrichRedemptionDesc(Map<String, Object> m, PointsHistory h) {
        if (h.getSourceId() == null
                || (!"redemption".equals(h.getChangeType()) && !"redemption_refund".equals(h.getChangeType()))) {
            return;
        }
        String rewardName = null;
        try {
            PointsReward reward = rewardMapper.selectById(Long.valueOf(h.getSourceId()));
            if (reward != null) rewardName = reward.getName();
        } catch (Exception ignored) {}
        if (rewardName == null) {
            try {
                PointsRedemption red = redemptionMapper.selectById(Long.valueOf(h.getSourceId()));
                if (red != null && red.getRewardId() != null) {
                    PointsReward reward = rewardMapper.selectById(red.getRewardId());
                    if (reward != null) rewardName = reward.getName();
                }
            } catch (Exception ignored) {}
        }
        if (rewardName != null) {
            m.put("description", "redemption".equals(h.getChangeType())
                    ? "兑换：" + rewardName
                    : "兑换驳回退回：" + rewardName);
        }
    }

    /**
     * 增加积分
     */
    @Transactional
    public boolean addPoints(Long memberId, int points, String changeType, Long referenceId, String remark) {
        Member m = memberMapper.selectById(memberId);
        if (m == null) return false;
        int current = m.getPoints() != null ? m.getPoints() : 0;
        int newBalance = current + points;
        m.setPoints(newBalance);
        memberMapper.updateById(m);

        PointsHistory h = new PointsHistory();
        h.setMemberId(memberId);
        h.setPoints(points);
        h.setBalance(newBalance);
        h.setChangeType(changeType);
        h.setSourceId(referenceId != null ? String.valueOf(referenceId) : null);
        h.setDescription(remark);
        h.setCreatedAt(LocalDateTime.now());
        log.info("[积分流水-addPoints] 插入 points_history: memberId={}, points={}, balance={}, changeType={}, sourceId={}, description={}, createdAt={}",
                h.getMemberId(), h.getPoints(), h.getBalance(), h.getChangeType(), h.getSourceId(), h.getDescription(), h.getCreatedAt());
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
        int newBalance = current - points;
        m.setPoints(newBalance);
        memberMapper.updateById(m);

        PointsHistory h = new PointsHistory();
        h.setMemberId(memberId);
        h.setPoints(-points);
        h.setBalance(newBalance);
        h.setChangeType(changeType);
        h.setSourceId(referenceId != null ? String.valueOf(referenceId) : null);
        h.setDescription(remark);
        h.setCreatedAt(LocalDateTime.now());
        log.info("[积分流水-deductPoints] 插入 points_history: memberId={}, points={}, balance={}, changeType={}, sourceId={}, description={}, createdAt={}",
                h.getMemberId(), h.getPoints(), h.getBalance(), h.getChangeType(), h.getSourceId(), h.getDescription(), h.getCreatedAt());
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
        log.info("[兑换] 开始兑换 rewardId={}, memberId={}, remark={}", rewardId, memberId, remark);
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
        log.info("[兑换] 会员当前积分 balance={}, 需扣减={}", balance, reward.getPointsRequired());
        if (balance < reward.getPointsRequired()) {
            result.put("success", false);
            result.put("message", "积分不足，需要" + reward.getPointsRequired() + "分");
            return result;
        }
        deductPoints(memberId, reward.getPointsRequired(), "redemption", rewardId, remark);
        Member after = memberMapper.selectById(memberId);
        log.info("[兑换] 扣减后积分 balance={}", after != null && after.getPoints() != null ? after.getPoints() : 0);

        // 4. 创建兑换记录（R062：实物类人工审批，其余自动完成）
        boolean needApproval = "manual".equals(reward.getApprovalType())
                || (reward.getApprovalType() == null && "physical".equals(reward.getRewardType()));
        PointsRedemption r = new PointsRedemption();
        r.setMemberId(memberId);
        r.setRewardId(rewardId);
        r.setPointsSpent(reward.getPointsRequired());
        r.setRedemptionType(reward.getRewardType());
        r.setStatus(needApproval ? "pending" : "completed");
        r.setCreatedAt(LocalDateTime.now());
        if (!needApproval) {
            r.setProcessedAt(LocalDateTime.now());
        }
        redemptionMapper.insert(r);

        // 5. 根据类型执行兑换动作（实物类等人工审批通过后线下发放）
        if (!needApproval && "pt_session".equals(reward.getRewardType())) {
            int sessions = reward.getSessions() != null && reward.getSessions() > 0 ? reward.getSessions() : 1;
            MemberPrivatePackage pkg = new MemberPrivatePackage();
            pkg.setMemberId(memberId);
            pkg.setPackageId(0);
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
        log.info("[审批] 通过操作 redemptionId={}, adminId={}, currentStatus={}", id, adminId, r == null ? "null" : r.getStatus());
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
        log.info("[审批] 驳回操作 redemptionId={}, adminId={}, currentStatus={}", id, adminId, r == null ? "null" : r.getStatus());
        if (r == null || !"pending".equals(r.getStatus())) return false;
        // 退还积分（source_id 保持为商品ID，与正常兑换一致）
        addPoints(r.getMemberId(), r.getPointsSpent(), "redemption_refund", r.getRewardId(), remark);
        // 归还库存（实物类在申请时已预留库存）
        if (r.getRewardId() != null) {
            PointsReward reward = rewardMapper.selectById(r.getRewardId());
            if (reward != null && reward.getStock() != null && reward.getStock() != -1) {
                reward.setStock(reward.getStock() + 1);
                rewardMapper.updateById(reward);
            }
        }
        r.setStatus("rejected");
        r.setAdminId(adminId);
        r.setAdminRemark(remark);
        r.setProcessedAt(LocalDateTime.now());
        redemptionMapper.updateById(r);
        return true;
    }

    public List<Map<String, Object>> getRedemptions(Long memberId) {
        LambdaQueryWrapper<PointsRedemption> w = new LambdaQueryWrapper<>();
        w.eq(PointsRedemption::getMemberId, memberId)
                .orderByDesc(PointsRedemption::getCreatedAt);
        List<PointsRedemption> list = redemptionMapper.selectList(w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PointsRedemption r : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.getId()));
            m.put("memberId", r.getMemberId());
            m.put("pointsSpent", r.getPointsSpent());
            m.put("rewardId", r.getRewardId() != null ? String.valueOf(r.getRewardId()) : null);
            m.put("redemptionType", r.getRedemptionType());
            m.put("status", r.getStatus());
            m.put("adminRemark", r.getAdminRemark());
            m.put("createdAt", r.getCreatedAt());
            enrichRedemption(m, r);
            m.put("processedAt", r.getProcessedAt());
            PointsReward reward = r.getRewardId() != null ? rewardMapper.selectById(r.getRewardId()) : null;
            m.put("rewardName", reward != null ? reward.getName() : "积分商品");
            result.add(m);
        }
        return result;
    }

    public IPage<Map<String, Object>> getPendingRedemptions(int page, int size) {
        LambdaQueryWrapper<PointsRedemption> w = new LambdaQueryWrapper<>();
        w.eq(PointsRedemption::getStatus, "pending")
                .orderByDesc(PointsRedemption::getCreatedAt);
        IPage<PointsRedemption> p = redemptionMapper.selectPage(new Page<>(page, size), w);
        IPage<Map<String, Object>> result = new Page<>(page, size, p.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (PointsRedemption r : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.getId()));
            m.put("memberId", r.getMemberId());
            m.put("pointsSpent", r.getPointsSpent());
            m.put("rewardId", r.getRewardId() != null ? String.valueOf(r.getRewardId()) : null);
            m.put("redemptionType", r.getRedemptionType());
            m.put("status", r.getStatus());
            m.put("adminRemark", r.getAdminRemark());
            m.put("createdAt", r.getCreatedAt());
            enrichRedemption(m, r);
            m.put("processedAt", r.getProcessedAt());
            records.add(m);
        }
        result.setRecords(records);
        return result;
    }

    public IPage<Map<String, Object>> listRedemptions(int page, int size, String status) {
        LambdaQueryWrapper<PointsRedemption> w = new LambdaQueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            List<String> statuses = new ArrayList<>();
            for (String part : status.split(",")) {
                if (!part.trim().isEmpty()) statuses.add(part.trim());
            }
            if (statuses.size() == 1) {
                w.eq(PointsRedemption::getStatus, statuses.get(0));
            } else if (statuses.size() > 1) {
                w.in(PointsRedemption::getStatus, statuses);
            }
        }
        w.orderByDesc(PointsRedemption::getCreatedAt);
        IPage<PointsRedemption> p = redemptionMapper.selectPage(new Page<>(page, size), w);
        IPage<Map<String, Object>> result = new Page<>(page, size, p.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (PointsRedemption r : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.getId()));
            m.put("memberId", r.getMemberId());
            m.put("pointsSpent", r.getPointsSpent());
            m.put("rewardId", r.getRewardId() != null ? String.valueOf(r.getRewardId()) : null);
            m.put("redemptionType", r.getRedemptionType());
            m.put("status", r.getStatus());
            m.put("adminRemark", r.getAdminRemark());
            m.put("createdAt", r.getCreatedAt());
            m.put("processedAt", r.getProcessedAt());
            records.add(m);
        }
        result.setRecords(records);
        return result;
    }

    /** ????????????????? */
    private void enrichRedemption(Map<String, Object> m, PointsRedemption r) {
        try {
            if (r.getMemberId() != null) {
                Member member = memberMapper.selectById(r.getMemberId());
                if (member != null) {
                    m.put("memberName", member.getName());
                    m.put("memberPhone", member.getPhone());
                }
            }
            if (r.getRewardId() != null) {
                PointsReward reward = rewardMapper.selectById(r.getRewardId());
                m.put("rewardName", reward != null ? reward.getName() : null);
            }
        } catch (Exception ignored) {}
    }
}
