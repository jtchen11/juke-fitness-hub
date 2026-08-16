package com.gym.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.ai.AIController;
import com.gym.ai.context.ConversationContext;
import com.gym.ai.context.ConversationState;
import com.gym.ai.context.ContextManager;
import com.gym.ai.tool.GymTools;
import com.gym.entity.*;
import com.gym.mapper.*;
import com.gym.service.GroupClassService;
import com.gym.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class PaymentService {

    @Autowired
    private ContextManager contextManager;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private MemberPrivatePackageMapper memberPrivatePackageMapper;

    @Autowired
    private GroupClassService groupClassService;

    @Autowired
    private GymTools gymTools;

    @Autowired
    private SystemConfigService systemConfigService;
    private ConversationContext toConversationContext(Long memberId, String sessionId,
            AIController.PendingBooking pending, ConversationState state) {
        ConversationContext ctx = new ConversationContext(memberId, sessionId);
        ctx.setCurrentState(state);
        ctx.getPayload().put("pendingBooking", pending);
        return ctx;
    }

    private boolean isConfigEnabled(String key) {
        Map<String, String> cfg = systemConfigService.getAll();
        String v = cfg.get(key);
        if (v == null || v.isEmpty()) return true;
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on");
    }

    public String processPaymentChoice(String userMessage, AIController.PendingBooking pending, String pendingKey, String sessionId) {
        if (pending.paymentMethod != null) return null;
        String lower = userMessage.toLowerCase().trim();

        // 检测退出意图：用户明确表示不要了，清除支付上下文
        if (lower.contains("不要了") || lower.contains("算了") || lower.contains("不约了") || lower.equals("不")) {
            contextManager.removeContext(pendingKey);
            log.info("[支付退出] 用户取消了支付选择，清除上下文: key={}", pendingKey);
            return "__EXIT__";
        }

        // 数字序号解析优先：先查课程包映射，避免 freeLeft==0 时序号偏移误落免费分支
        if (lower.matches("\\d+")) {
            int optNum = Integer.parseInt(lower);
            Long mappedPkgId = (pending.paymentPkgMap != null) ? pending.paymentPkgMap.get(optNum) : null;
            if (mappedPkgId != null) {
                pending.paymentMethod = "package";
                pending.packageId = mappedPkgId;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                log.info("[支付选择] ✅ 用户选择课程包: 序号={}, pkgId={}, key={}", optNum, mappedPkgId, pendingKey);
                return null;
            }
            if (optNum == pending.singlePayOptionNo) {
                pending.paymentMethod = "pay";
                pending.packageId = null;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                log.info("[支付选择] 私教预约选择单次付费（序号{}），key={}", optNum, pendingKey);
                return null;
            }
            if (optNum == 1) {
                // 序号1未命中课程包映射：仅当本月仍有免费次数时才视为免费（计算逻辑与下方支付选项列表一致）
                int freeLeft = 0;
                if (pending.memberId != null && pending.memberId > 0) {
                    Member member = memberMapper.selectById(pending.memberId);
                    if (member != null) {
                        int used = member.getFreePtUsedMonth() != null ? member.getFreePtUsedMonth() : 0;
                        String levelName = member.getLevel() != null ? member.getLevel() : "普通会员";
                        com.gym.enums.MemberLevel ml = com.gym.enums.MemberLevel.fromDisplayName(levelName);
                        int freeTotal = ml.getFreePersonalTrainingsPerMonth();
                        freeLeft = Math.max(0, freeTotal - used);
                    }
                }
                if (freeLeft > 0) {
                    pending.paymentMethod = "free";
                    pending.packageId = null;
                    contextManager.updateContext(pendingKey, toConversationContext(
                        pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                    log.info("[支付选择] 私教预约选择免费私教课（序号1），key={}", pendingKey);
                    return null;
                }
                log.warn("[支付选择] 序号1未命中课程包映射且本月免费次数已用完，按无效选项处理，freeLeft={}", freeLeft);
            }
            // 序号不在课程包映射中（映射丢失或越界），不再落到免费分支，直接提示重新选择
            log.warn("[支付选择] ⚠️ 无效序号: {}, 当前映射={}, singlePayOptionNo={}", optNum, pending.paymentPkgMap, pending.singlePayOptionNo);
            pending.retryCount++;
            contextManager.updateContext(pendingKey, toConversationContext(
                pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
            return "无效选项，请重新选择支付方式。";
        }
        // 免费文字匹配（不再接受裸数字“1”，避免 freeLeft==0 时误判为免费）
        if (lower.contains("免费")) {
            pending.paymentMethod = "free";
            pending.packageId = null;
            contextManager.updateContext(pendingKey, toConversationContext(
                pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
            log.info("[支付选择] 私教预约选择免费私教课，key={}", pendingKey);
            return null;
        }
        // 未激活课程包：前端点击“点击激活”后回传 pkg=ID，这里解析并交给服务层自动激活使用
        if (lower.startsWith("pkg=")) {
            try {
                Long pkgId = Long.parseLong(lower.substring(4).trim());
                pending.paymentMethod = "package";
                pending.packageId = pkgId;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                log.info("[支付选择] 私教预约选择待激活课程包: pkgId={}, key={}", pkgId, pendingKey);
                return null;
            } catch (NumberFormatException nfe) {
                log.warn("[支付选择] 无效的课程包ID: {}", lower);
            }
        }
        if (lower.contains("课程包")) {
            Long firstPkgId = (pending.paymentPkgMap != null) ? pending.paymentPkgMap.values().stream().findFirst().orElse(null) : null;
            if (firstPkgId != null) {
                pending.paymentMethod = "package";
                pending.packageId = firstPkgId;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                log.info("[支付选择] ✅ 用户选择课程包(文字): pkgId={}, key={}", firstPkgId, pendingKey);
                return null;
            }
            log.warn("[支付选择] 用户选择课程包但无可用课程包，映射={}", pending.paymentPkgMap);
            return "您没有可用的课程包，请选择其他支付方式。";
        }
        if (lower.contains("单次")) {
            pending.paymentMethod = "pay";
            pending.packageId = null;
            contextManager.updateContext(pendingKey, toConversationContext(
                pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
            log.info("[支付选择] 私教预约选择单次付费，key={}", pendingKey);
            return null;
        }
        try {
            // ====== 诊断：打印会员课程包原始数据 ======
            if (pending.memberId != null && pending.memberId > 0) {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.MemberPrivatePackage> diagWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                diagWrapper.eq(com.gym.entity.MemberPrivatePackage::getMemberId, pending.memberId);
                java.util.List<com.gym.entity.MemberPrivatePackage> allPkgs = memberPrivatePackageMapper.selectList(diagWrapper);
                log.info("[支付诊断] 会员{}的课程包原始数据（共{}条）:", pending.memberId, allPkgs != null ? allPkgs.size() : 0);
                if (allPkgs != null) {
                    for (com.gym.entity.MemberPrivatePackage p : allPkgs) {
                        log.info("[支付诊断]   id={}, packageName={}, total={}, used={}, remaining={}, status={}, endDate={}",
                            p.getId(), p.getPackageName(), p.getTotalSessions(), p.getUsedSessions(), p.getRemainingSessions(), p.getStatus(), p.getEndDate());
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("请选择支付方式：\n\n---\n**PAYMENT**\n");
            int optNo = 1;
            if (pending.memberId != null && pending.memberId > 0) {
                Member member = memberMapper.selectById(pending.memberId);
                int freeLeft = 0;
                if (member != null) {
                    // 计算免费私教剩余
                    int used = member.getFreePtUsedMonth() != null ? member.getFreePtUsedMonth() : 0;
                    String levelName = member.getLevel() != null ? member.getLevel() : "普通会员";
                    com.gym.enums.MemberLevel ml = com.gym.enums.MemberLevel.fromDisplayName(levelName);
                    int freeTotal = ml.getFreePersonalTrainingsPerMonth();
                    freeLeft = Math.max(0, freeTotal - used);
                    if (freeLeft > 0) sb.append("1. 免费私教课（剩余").append(freeLeft).append("次）\n");
                }
                // 课程包：已激活且有效（全部列出，含有效期），或未激活但未过激活截止日期（收拢为“待激活”）
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.MemberPrivatePackage> pw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                pw.eq(com.gym.entity.MemberPrivatePackage::getMemberId, pending.memberId)
                   .ne(com.gym.entity.MemberPrivatePackage::getStatus, "refunded")
                   .gt(com.gym.entity.MemberPrivatePackage::getRemainingSessions, 0)
                   .and(w -> w.and(x -> x.isNotNull(com.gym.entity.MemberPrivatePackage::getStartDate)
                            .and(y -> y.isNull(com.gym.entity.MemberPrivatePackage::getEndDate)
                                 .or().ge(com.gym.entity.MemberPrivatePackage::getEndDate, java.time.LocalDate.now())))
                        .or(z -> z.isNull(com.gym.entity.MemberPrivatePackage::getStartDate)
                            .and(y -> y.isNull(com.gym.entity.MemberPrivatePackage::getActivationDeadline)
                                 .or().ge(com.gym.entity.MemberPrivatePackage::getActivationDeadline, java.time.LocalDate.now()))))
                   .orderByDesc(com.gym.entity.MemberPrivatePackage::getStartDate);
                java.util.List<com.gym.entity.MemberPrivatePackage> pkgs = memberPrivatePackageMapper.selectList(pw);
                if (pending.paymentPkgMap == null) pending.paymentPkgMap = new java.util.HashMap<>();
                pending.paymentPkgMap.clear();
                optNo = freeLeft > 0 ? 2 : 1;
                int pendingPkgCount = 0;
                int activePkgCount = 0;
                if (pkgs != null) {
                    log.info("[支付选项] 会员{} 课程包查询: 总数={}", pending.memberId, pkgs.size());
                    for (com.gym.entity.MemberPrivatePackage p : pkgs) {
                        log.info("[支付选项]   id={}, name={}, remaining={}, status={}, startDate={}, endDate={}, activationDeadline={}",
                            p.getId(), p.getPackageName(), p.getRemainingSessions(), p.getStatus(), p.getStartDate(), p.getEndDate(), p.getActivationDeadline());
                    }
                    // 第一轮：已激活且有效的课程包全部列出
                    for (com.gym.entity.MemberPrivatePackage p : pkgs) {
                        if (p.getStartDate() == null) { pendingPkgCount++; continue; }
                        activePkgCount++;
                        String pkgName = p.getPackageName() != null ? p.getPackageName() : "私教包";
                        String endDateText = p.getEndDate() != null ? p.getEndDate().toString() : "长期";
                        sb.append(optNo).append(". 课程包：").append(pkgName).append("（剩余").append(p.getRemainingSessions()).append("节，有效期至").append(endDateText).append("）\n");
                        pending.paymentPkgMap.put(optNo, p.getId());
                        optNo++;
                    }
                    // 第二轮：未激活但未过期的收拢为一条，子项携带包ID
                    if (pendingPkgCount > 0) {
                        sb.append(optNo).append(". ▶ 待激活课程包（").append(pendingPkgCount).append("个）\n");
                        for (com.gym.entity.MemberPrivatePackage p : pkgs) {
                            if (p.getStartDate() != null) continue;
                            String pkgName = p.getPackageName() != null ? p.getPackageName() : "私教包";
                            sb.append("   - 课程包：").append(pkgName).append("（点击激活，剩余").append(p.getRemainingSessions()).append("节）[pkg=").append(p.getId()).append("]\n");
                        }
                        optNo++;
                    }
                    log.info("[支付选项] 课程包分类: 已激活={}, 待激活={}", activePkgCount, pendingPkgCount);
                }
            }
            pending.singlePayOptionNo = optNo;
            sb.append(optNo).append(". 单次付费\n\n请回复数字或点击按钮选择");
            log.info("[支付选择] 返回支付选项给用户，key={}, 选项文本长度={}", pendingKey, sb.length());
            return sb.toString();
        } catch (Exception e) {
            log.warn("查询支付方式失败", e);
            pending.paymentMethod = "pay";
            contextManager.updateContext(pendingKey, toConversationContext(
                pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
            return null;
        }
    }

    public Long resolvePackageId(AIController.PendingBooking pending) {
        if (pending.packageId != null) {
            log.info("[预约执行] 使用用户选择的课程包: pkgId={}", pending.packageId);
            return pending.packageId;
        }
        LambdaQueryWrapper<MemberPrivatePackage> pw = new LambdaQueryWrapper<>();
        pw.eq(MemberPrivatePackage::getMemberId, pending.memberId)
           .ne(MemberPrivatePackage::getStatus, "refunded")
           .gt(MemberPrivatePackage::getRemainingSessions, 0)
           .and(w -> w.and(x -> x.isNotNull(MemberPrivatePackage::getStartDate)
                    .and(y -> y.isNull(MemberPrivatePackage::getEndDate)
                         .or().ge(MemberPrivatePackage::getEndDate, java.time.LocalDate.now())))
                .or(z -> z.isNull(MemberPrivatePackage::getStartDate)
                    .and(y -> y.isNull(MemberPrivatePackage::getActivationDeadline)
                         .or().ge(MemberPrivatePackage::getActivationDeadline, java.time.LocalDate.now()))))
           .last("LIMIT 1");
        try {
            MemberPrivatePackage pkg = memberPrivatePackageMapper.selectOne(pw);
            if (pkg != null) return pkg.getId();
        } catch (Exception e) {
            log.warn("查询课程包失败", e);
        }
        return null;
    }

    /**
     * 访客团课预约判定：
     * - 返回 null：非访客（会员/游客），由调用方继续走"确认支付"流程
     * - 返回字符串：访客处理结果（直接预约成功/开关关闭/付费团课不可约/体验次数已用），不再走支付流程
     */
    public String resolveVisitorGroupBooking(Long memberId, GroupClass gc) {
        if (memberId == null || memberId <= 0) return null;
        Member m = memberMapper.selectById(memberId);
        if (m == null) return null;
        // 访客判定：无会员有效期（expireDate 为空）或等级为"访客"
        boolean isVisitor = m.isVisitor() || "访客".equals(m.getLevel());
        if (!isVisitor) return null;
        boolean experienceClass = gc.getAllowVisitor() != null && gc.getAllowVisitor();
        // 访客 + 非体验课（allow_visitor=0）→ 不可预约
        if (!experienceClass) {
            return "访客无法预约该课程，请先注册会员。";
        }
        // 访客 + 体验课 + 开关关闭 → 不可预约
        if (!isConfigEnabled("VISITOR_EXPERIENCE_ENABLED")) {
            return "体验课功能暂未开放，请联系客服";
        }
        if (Boolean.TRUE.equals(m.getExperienceUsed())) {
            return "您已使用过体验课，请注册会员后再预约。";
        }
        // 访客 + 体验课（公益/付费均可）+ 开关开启 → 直接预约，不创建支付上下文、不弹确认引导语
        String r = groupClassService.bookClass(memberId, gc.getId());
        if (r != null && r.contains("成功")) {
            StringBuilder okSb = new StringBuilder(r);
            if (gc.getStartTime() != null) {
                okSb.append("\n上课时间：").append(gc.getStartTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            return okSb.toString();
        }
        return "预约失败：" + (r != null ? r : "未知错误，请重试") + "。";
    }

    public String prepareGroupPayment(GroupClass gc, Long memberId, String sessionId) {
        // 兜底：访客不允许进入"确认支付"流程（体验课直接预约，付费团课需先注册会员）
        String visitorGuard = resolveVisitorGroupBooking(memberId, gc);
        if (visitorGuard != null) return visitorGuard;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("课程名称：").append(gc.getName() != null ? gc.getName() : "团课").append("\n");
            sb.append("原价：¥").append(gc.getPrice() != null ? gc.getPrice() : java.math.BigDecimal.ZERO).append("\n");

            java.math.BigDecimal finalPrice = gc.getPrice() != null ? gc.getPrice() : java.math.BigDecimal.ZERO;
            if (memberId != null && memberId > 0) {
                Member m = memberMapper.selectById(memberId);
                if (m != null && !m.isVisitor() && m.getLevel() != null) {
                    try {
                        java.math.BigDecimal discounted;
                            String __level = m.getLevel();
                            java.math.BigDecimal __discounted = gc.getPrice();
                            if (__level != null) {
                                if (__level.contains("铂金")) {
                                    __discounted = gc.getPrice().multiply(new java.math.BigDecimal("0.8"));
                                } else if (__level.contains("黄金")) {
                                    __discounted = gc.getPrice().multiply(new java.math.BigDecimal("0.9"));
                                }
                            }
                            discounted = __discounted;
                        java.math.BigDecimal saved = gc.getPrice().subtract(discounted);
                        if (saved.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            sb.append(m.getLevel()).append("折扣：-¥").append(saved.setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
                            finalPrice = discounted;
                        }
                    } catch (Exception e) {
                        log.warn("计算折扣失败", e);
                    }
                }
            }
            sb.append("实付金额：¥").append(finalPrice.setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
            sb.append("\n---\n**PAYMENT_GROUP**\nconfirm\n");

            // 保存到待支付上下文
            String groupKey = "payment_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            String userType = "member";
            if (memberId != null && memberId > 0) {
                Member m = memberMapper.selectById(memberId);
                if (m != null && m.isVisitor()) userType = "visitor";
            }
            AIController.PendingBooking pp = new AIController.PendingBooking(memberId, gc.getName(), gc.getId(), true, true, 
                java.time.LocalDate.now().toString(), userType);
            pp.intentType = "GROUP_PAYMENT";
            pp.paymentMethod = null;
            contextManager.updateContext(groupKey, toConversationContext(
                memberId, sessionId, pp, ConversationState.WAITING_PAYMENT));
            log.info("[团课支付] 保存待支付上下文: key={}, classId={}, intentType={}", groupKey, pp.groupClassId, pp.intentType);
            log.info("[团课支付] 等待用户确认支付: course={}, price={}", gc.getName(), finalPrice);

            sb.append("请点击「确认支付」完成预约");
            return sb.toString();
        } catch (Exception e) {
            log.error("团课支付准备失败", e);
            return gymTools.bookGroupClass(memberId, gc.getId()).getMessage();
        }
    }

    // ====== 快速通道（纯查询，供 AIController 直接调用，不走 AI） ======

    /** 快速通道 1/3：查询课程包剩余课时 */
    public String getPackageInfo(Long memberId) {
        return gymTools.getMyPackageInfo(memberId).getMessage();
    }
}
