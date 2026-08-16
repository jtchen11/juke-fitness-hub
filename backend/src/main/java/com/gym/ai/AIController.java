package com.gym.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.ai.context.ContextManager;
import com.gym.ai.context.ConversationContext;
import com.gym.ai.context.ConversationState;
import com.gym.ai.memory.MessageRecord;
import com.gym.ai.memory.MongoChatMemoryStore;
import com.gym.ai.rag.KnowledgeBaseService;
import com.gym.ai.service.BookingFacade;
import com.gym.ai.service.ChatOrchestrationService;
import com.gym.ai.service.PaymentService;
import com.gym.ai.tool.GymTools;
import com.gym.entity.*;
import com.gym.mapper.*;
import com.gym.service.GroupClassService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
// import okhttp3.Response; (unused - removed to avoid ambiguity with dev.langchain4j.model.output.Response)
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai/chat")
public class AIController {

    @Autowired
    private MongoChatMemoryStore memoryStore;



    @Autowired
    private MongoTemplate mongoTemplate;




    @Autowired
    private HttpSession session;

    @Autowired
    private ContextManager contextManager;

    @Autowired
    private ChatOrchestrationService chatOrchestrationService;

    @Autowired
    private BookingFacade bookingFacade;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private GroupClassService groupClassService;


    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();



    // ========== 工具：提取图片 URL ==========
    private String extractImageUrl(String text) {
        if (text == null) return null;
        Pattern urlPattern = Pattern.compile("https?://[^\\s]+");
        Matcher matcher = urlPattern.matcher(text);
        if (matcher.find()) {
            String potentialUrl = matcher.group(0);
            if (potentialUrl.matches(".*\\.(png|jpg|jpeg|gif|webp)(\\?.*)?") || potentialUrl.contains("dashscope")) {
                return potentialUrl;
            }
        }
        return null;
    }

    // ========== 获取当前登录会员ID ==========
    private Long getCurrentMemberId() {
        Long memberId = com.gym.auth.LoginContext.getUserId();
        if (memberId == null) {
            try {
                memberId = (Long) session.getAttribute("memberId");
            } catch (Exception e) {}
        }
        if (memberId == null) {
            throw new RuntimeException("用户未登录或会话已过期");
        }
        return memberId;
    }

    // ========== 生成存储键：会员ID + 会话ID，实现数据隔离 ==========
    private String getMemoryId(String sessionId) {
        Long memberId = getCurrentMemberId();
        return memberId + "_" + sessionId;
    }

    // ========== 历史记录（使用组合键） ==========
    @GetMapping("/history")
    public Map<String, Object> getHistory(@RequestParam String sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String memoryId = getMemoryId(sessionId);
            List<MessageRecord> records = memoryStore.getMessageRecords(memoryId);
            List<Map<String, Object>> history = records.stream().map(record -> {
                Map<String, Object> map = new HashMap<>();
                map.put("role", record.getRole());
                map.put("content", record.getText());
                if (record.getImageUrl() != null && !record.getImageUrl().isEmpty()) {
                    map.put("imageUrl", record.getImageUrl());
                }
                return map;
            }).collect(Collectors.toList());
            result.put("success", true);
            result.put("history", history);
        } catch (Exception e) {
            log.error("获取历史失败", e);
            result.put("success", false);
            result.put("message", "获取历史失败：" + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/history")
    public Map<String, Object> deleteHistory(@RequestParam String sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String memoryId = getMemoryId(sessionId);
            memoryStore.deleteMessages(memoryId);
            chatOrchestrationService.clearAssistant(sessionId);
            result.put("success", true);
            result.put("message", "聊天记录已清空！");
        } catch (Exception e) {
            log.error("清空历史失败", e);
            result.put("success", false);
            result.put("message", "清空失败：" + e.getMessage());
        }
        return result;
    }

    @GetMapping("/clean-history")
    public Map<String, Object> cleanHistory() {
        Map<String, Object> result = new HashMap<>();
        try {
            mongoTemplate.getCollection("chat_histories").deleteMany(new Document());
            chatOrchestrationService.clearAllAssistants();
            result.put("success", true);
            result.put("message", "所有聊天历史已清理！");
        } catch (Exception e) {
            log.error("清理历史失败", e);
            result.put("success", false);
            result.put("message", "清理失败：" + e.getMessage());
        }
        return result;
    }

    // ========== 普通聊天 ==========
    @PostMapping
    public Map<String, Object> chat(@RequestBody ChatRequest request, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();
        Long memberId = null;
        try {
            memberId = getCurrentMemberId();
        } catch (Exception e) {
            response.setStatus(401);
            result.put("answer", "请先登录");
            return result;
        }
        String memoryId = getMemoryId(sessionId);

        try {
            if (userMessage == null || userMessage.trim().isEmpty()) {
                result.put("answer", "请输入有效的问题。");
                result.put("sessionId", sessionId);
                return result;
            }

            String answer = handleIntent(userMessage, memberId, sessionId, memoryId);
            // 提取图片 URL
            String imageUrl = extractImageUrl(answer);
            // 保存到记忆（存储完整回答，同时单独存图片URL）
            saveToMemory(memoryId, userMessage, answer, imageUrl);

            result.put("answer", answer);
            result.put("sessionId", sessionId);

        } catch (Exception e) {
            log.error("聊天处理异常", e);
            result.put("answer", "❌ 服务异常：" + e.getMessage());
            result.put("sessionId", sessionId);
        }
        return result;
    }

    // ========== 流式聊天 ==========
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamChat(
            @RequestParam String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) Long memberId,
            HttpServletResponse response) {
        SseEmitter emitter = new SseEmitter(120000L);
        emitter.onTimeout(() -> {
            log.warn("流式请求超时: sessionId={}", sessionId);
            try {
                Map<String, Object> errorEvent = new HashMap<>();
                errorEvent.put("type", "error");
                errorEvent.put("content", "请求超时，请稍后重试");
                emitter.send(SseEmitter.event()
                        .data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorEvent))
                        .name("error"));
            } catch (Exception ex) { }
            emitter.completeWithError(new RuntimeException("请求超时"));
        });
        Long currentMemberId = null;
        try {
            currentMemberId = getCurrentMemberId();
        } catch (Exception e) {
            try { emitter.send(SseEmitter.event().data("{\"type\":\"error\",\"content\":\"请先登录\"}")); } catch (Exception ex) {}
            emitter.complete();
            return emitter;
        }
        final Long finalMemberId = currentMemberId;
        String memoryId = getMemoryId(sessionId);

        long streamStartTime = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                String answer = handleIntent(message, finalMemberId, sessionId, memoryId, emitter);
                // answer != null 表示是工具调用结果（非 LLM 流式输出）
                if (answer != null) {
                    String imageUrl = extractImageUrl(answer);
                    saveToMemory(memoryId, message, answer, imageUrl);

                    // 如果包含 **PAYMENT** 标记，以 complete 事件发送（前端会解析为带按钮的消息）
                    // 否则仍以 tool_result 事件发送
                    if (answer.contains("**PAYMENT**") || answer.contains("**PAYMENT_GROUP**") || answer.contains("__EXIT__")) {
                        Map<String, Object> completeEvent = new HashMap<>();
                        completeEvent.put("type", "complete");
                        completeEvent.put("full", answer);
                        emitter.send(SseEmitter.event()
                                .name("delta")
                                .data(objectMapper.writeValueAsString(completeEvent)));
                        emitter.complete();
                    } else {
                        Map<String, Object> toolEvent = new HashMap<>();
                        toolEvent.put("type", "tool_result");
                        toolEvent.put("content", answer);
                        emitter.send(SseEmitter.event()
                                .name("delta")
                                .data(objectMapper.writeValueAsString(toolEvent)));

                        Map<String, Object> endEvent = new HashMap<>();
                        endEvent.put("type", "end");
                        emitter.send(SseEmitter.event()
                                .name("end")
                                .data(objectMapper.writeValueAsString(endEvent)));
                        emitter.complete();
                    }
                    log.info("流式请求结束(工具结果): sessionId={}, 耗时={}ms",
                            sessionId, System.currentTimeMillis() - streamStartTime);
                }
            } catch (Exception e) {
                if (e instanceof org.apache.catalina.connector.ClientAbortException ||
                        e instanceof IOException && e.getMessage() != null &&
                                e.getMessage().contains("broken pipe")) {
                    log.warn("客户端已断开连接，停止流式输出");
                } else {
                    log.error("流式聊天异常", e);
                    try {
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("type", "error");
                        errorEvent.put("content", "发生错误：" + e.getMessage());
                        emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(errorEvent))
                                .name("error"));
                    } catch (IOException ex) { }
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
    private String handleIntent(String userMessage, Long memberId, String sessionId, String memoryId) {
        return handleIntent(userMessage, memberId, sessionId, memoryId, null);
    }

    private String handleIntent(String userMessage, Long memberId, String sessionId, String memoryId, SseEmitter emitter) {
        // ========== 核心意图处理：6 个快速通道 + AI Function Calling 兜底 ==========
        String lowerMsg = userMessage.toLowerCase();
        if (lowerMsg == null || lowerMsg.trim().isEmpty()) {
            log.warn("意图识别: 收到空消息");
            return "请输入有效的问题，例如【今天有什么团课】【我的预约】【推荐教练】等。";
        }
        log.debug("🔍 [意图识别] 原始消息: '{}'", userMessage);
        log.debug("🔍 [意图识别] memberId={}, sessionId={}, lowerMsg={}", memberId, sessionId, lowerMsg);

        // 快速通道 1：我的私教预约 / 剩余课时（纯查询，不走 AI）
        if (lowerMsg.contains("我的私教") || lowerMsg.contains("私教预约")) {
            if (lowerMsg.contains("还剩") || lowerMsg.contains("剩余") || lowerMsg.contains("几次") || lowerMsg.contains("几节")) {
                log.info("✅ 命中【查询私教剩余课时】快速通道");
                return paymentService.getPackageInfo(memberId);
            }
            log.info("✅ 命中【查询我的私教课】快速通道");
            return bookingFacade.handleBookingQuery(memberId);
        }

        // 快速通道 2：我的团课报名（纯查询，不走 AI）
        if (lowerMsg.contains("我的团课") || lowerMsg.contains("团课报名") ||
                lowerMsg.contains("团课预约记录") || lowerMsg.contains("团课报名记录")) {
            log.info("✅ 命中【查询我的团课记录】快速通道");
            return bookingFacade.handleClassBookingQuery(memberId);
        }

        // 快速通道 3：课程包剩余（纯查询，不走 AI）
        if (lowerMsg.contains("课程包") && (lowerMsg.contains("剩余") || lowerMsg.contains("还剩")
                || lowerMsg.contains("几次") || lowerMsg.contains("几节") || lowerMsg.contains("还有"))) {
            log.info("✅ 命中【查询课程包剩余】快速通道");
            return paymentService.getPackageInfo(memberId);
        }

        // 快速通道 4：我的会员信息（纯查询，不走 AI）
        if (lowerMsg.contains("会员信息") || lowerMsg.contains("我的信息") || lowerMsg.contains("会员资料")) {
            log.info("✅ 命中【查询会员信息】快速通道");
            return bookingFacade.getMemberProfile(memberId);
        }

        // 快速通道 5：体测历史（纯查询，不走 AI）
        if (lowerMsg.contains("体测") && (lowerMsg.contains("历史") || lowerMsg.contains("记录"))) {
            log.info("✅ 命中【查询体测历史】快速通道");
            return bookingFacade.getTestHistory(memberId);
        }

        // 快速通道 6：取消预约（规则说明，不涉及推理，不走 AI）
        if (lowerMsg.contains("取消") && lowerMsg.contains("预约")) {
            log.info("✅ 命中【取消预约规则说明】快速通道");
            return "关于取消预约：\n一、团课预约：可在「我的预约」页面自行取消，需在开课前2小时操作。\n二、私教课预约：需提前2小时联系教练或前台取消。\n三、开课前2小时内不可取消。\n请前往「我的预约」页面查看并操作，如仍有疑问请联系前台。";
        }

        // 快速通道 7：预约私教（包含“约”和“教练”）→ 进入多轮预约流程，创建 booking_ 上下文
        if (lowerMsg.contains("约") && lowerMsg.contains("教练")) {
            log.info("✅ 命中【预约私教】快速通道，进入多轮预约流程");
            String result = bookingFacade.handleBooking(userMessage, memberId, sessionId);
            if (result != null) {
                return result;
            }
            // handleBooking 不应返回 null；若为 null 则继续向下走 AI 转发
        }
        // 快速通道 8A：预约团课（关键词强制拦截，不依赖 AI 判断）→ 确保工具被调用、价格完整显示
        if (lowerMsg.contains("约") && (lowerMsg.contains("团课") || lowerMsg.contains("力量训练")
                || lowerMsg.contains("瑜伽") || lowerMsg.contains("搏击") || lowerMsg.contains("普拉提")
                || lowerMsg.contains("动感单车") || lowerMsg.contains("尊巴") || lowerMsg.contains("杠铃"))
                && !lowerMsg.contains("私教")) {
            log.info("✅ 命中【预约团课】快速通道（关键词强制拦截）");
            String result = bookingFacade.handleBookGroupClass(userMessage, memberId, sessionId);
            if (result != null) {
                return result;
            }
            // handleBookGroupClass 不应返回 null；若为 null 则继续向下走 AI 转发
        }
        // 快速通道 8：预约团课（包含“约团课/团课预约/预约团课”，排除私教）→ 创建 group_ 上下文
        if ((lowerMsg.contains("约团课") || lowerMsg.contains("团课预约") || lowerMsg.contains("预约团课"))
                && !lowerMsg.contains("私教")) {
            log.info("✅ 命中【预约团课】快速通道");
            String result = bookingFacade.handleBookGroupClass(userMessage, memberId, sessionId);
            if (result != null) {
                return result;
            }
            // handleBookGroupClass 不应返回 null；若为 null 则继续向下走 AI 转发
        }
        // ========== 多轮对话上下文拦截：支付方式选择 / 团课序号选择 ==========
        // 私教预约：等待支付选择（booking_ 上下文；handleBooking 等待支付时状态为 PT_BOOKING，此处同时兼容 WAITING_PAYMENT）
        String bookingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        ConversationContext bookingCtx = contextManager.getContext(bookingKey);
        if (bookingCtx != null && bookingCtx.getPayload().get("pendingBooking") instanceof PendingBooking) {
            PendingBooking pending = (PendingBooking) bookingCtx.getPayload().get("pendingBooking");
            ConversationState bookingState = bookingCtx.getCurrentState();
            boolean waitingPayment = bookingState == ConversationState.WAITING_PAYMENT
                    || (bookingState == ConversationState.PT_BOOKING
                        && pending.hasDate && pending.hasTime && pending.paymentMethod == null);
            if (waitingPayment) {
                log.info("🧭 [多轮拦截] 命中私教支付选择: key={}, state={}", bookingKey, bookingState);
                String payResult = paymentService.processPaymentChoice(userMessage, pending, bookingKey, sessionId);
                if (payResult != null) {
                    if (payResult.equals("__EXIT__")) {
                        contextManager.removeContext(bookingKey);
                        log.info("🧭 [多轮拦截] 用户取消私教预约，已清理上下文: key={}", bookingKey);
                        return "好的，已取消预约。请问还有其他问题吗？";
                    }
                    log.info("🧭 [多轮拦截] 返回支付选择/提示信息");
                    return payResult;
                }
                // processPaymentChoice 返回 null 表示支付方式已选定，执行预约
                log.info("🧭 [多轮拦截] 支付方式已选定: {}, 执行私教预约", pending.paymentMethod);
                return bookingFacade.completePendingPTBooking(pending, sessionId);
            }
        }

        // 团课预约：等待序号选择（group_ 上下文）
        String groupKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        ConversationContext groupCtx = contextManager.getContext(groupKey);
        if (groupCtx != null && groupCtx.getCurrentState() == ConversationState.GROUP_BOOKING) {
            log.info("🧭 [多轮拦截] 命中团课序号选择: key={}", groupKey);
            return bookingFacade.handleGroupClassSelection(userMessage, memberId, sessionId, groupCtx);
        }
        // 团课支付确认拦截（payment_ 上下文由 prepareGroupPayment 创建：
        // 注意：ConversationState 无 GROUP_PAYMENT 枚举，GROUP_PAYMENT 语义存于 pending.intentType，state 为 WAITING_PAYMENT）
        String paymentKey = "payment_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        ConversationContext paymentCtx = contextManager.getContext(paymentKey);
        if (paymentCtx != null && paymentCtx.getPayload().get("pendingBooking") instanceof PendingBooking) {
            PendingBooking payPend = (PendingBooking) paymentCtx.getPayload().get("pendingBooking");
            if ("GROUP_PAYMENT".equals(payPend.intentType)
                    && paymentCtx.getCurrentState() == ConversationState.WAITING_PAYMENT) {
                log.info("🧭 [多轮拦截] 命中团课支付确认: key={}, course={}, classId={}",
                        paymentKey, payPend.courseName, payPend.groupClassId);
                String input = userMessage.trim().toLowerCase();
                // 确认支付：前端「确认支付」按钮回传 confirm
                if (input.equals("confirm")) {
                    if (payPend.groupClassId == null) {
                        contextManager.removeContext(paymentKey);
                        log.warn("[团课支付确认] groupClassId 为空，已清理上下文: key={}", paymentKey);
                        return "预约失败：课程信息缺失，请重新发起预约。";
                    }
                    try {
                        String result = groupClassService.bookClass(memberId, payPend.groupClassId);
                        log.info("[团课支付确认] 确认支付，预约结果: {}", result);
                        return result;
                    } catch (Exception e) {
                        log.error("[团课支付确认] 预约执行异常: memberId={}, classId={}, key={}",
                                memberId, payPend.groupClassId, paymentKey, e);
                        return "预约失败：预约执行出现异常（" + e.getMessage() + "），请重试或回复「取消」放弃预约。";
                    } finally {
                        contextManager.removeContext(paymentKey);
                    }
                }
                // 取消支付
                if (input.contains("取消") || input.contains("不")) {
                    contextManager.removeContext(paymentKey);
                    log.info("[团课支付确认] 用户取消团课支付，已清理上下文: key={}", paymentKey);
                    return "好的，已取消预约。请问还有其他问题吗？";
                }
                // 其他输入：继续等待确认
                return "请点击「确认支付」完成预约，或回复「取消」放弃本次预约。";
            }
        }
        // ⭐ 所有其他请求 → 交给 AI Function Calling（AI 通过 @Tool 方法自主调用工具）
        log.info("➡️ 未命中快速通道，进入【AI Function Calling】");
        if (emitter != null) {
            chatOrchestrationService.streamingNormalChat(sessionId, userMessage, memberId, memoryId, emitter);
            return null;
        }
        return chatOrchestrationService.normalChat(sessionId, userMessage, memberId, memoryId);
    }


    private void saveToMemory(String memoryId, String userMessage, String assistantReply, String imageUrl) {
        List<ChatMessage> existing = memoryStore.getMessages(memoryId);
        boolean lastIsSameUser = !existing.isEmpty() &&
                existing.get(existing.size() - 1) instanceof UserMessage && ((UserMessage) existing.get(existing.size() - 1)).singleText().equals(userMessage);
        if (!lastIsSameUser) {
            memoryStore.saveMessageRecord(memoryId, "user", userMessage, null);
            memoryStore.saveMessageRecord(memoryId, "assistant", assistantReply, imageUrl);
        }
    }

    public static class ChatRequest {
        private String sessionId;
        private String message;
        private Long memberId;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
    }

    // ====== 待完成预约上下文（用于多轮对话） ======
    public static class PendingBooking {
        public Long memberId;
        public Long trainerId;
        public String trainerName;
        public String courseName;
        public Long groupClassId;
        public String intentType;  // "PT" = 私教, "GROUP" = 团课
        public String userType;    // "member" / "visitor"
        public boolean hasDate;
        public boolean hasTime;
        public String dateStr;
        public String timeStr;
        public int retryCount;
        public String paymentMethod;
        public Long packageId;               // 用户选择的课程包ID（未激活包点击激活后使用）
        public Map<Integer, Long> paymentPkgMap;  // 支付选项序号 → 课程包ID
        public int singlePayOptionNo;        // 单次付费选项的序号

        public PendingBooking(Long memberId, Long trainerId, String trainerName, boolean hasDate, boolean hasTime, String dateStr) {
            this.memberId = memberId;
            this.trainerId = trainerId;
            this.trainerName = trainerName;
            this.courseName = null;
            this.groupClassId = null;
            this.intentType = "PT";
            this.userType = null;
            this.hasDate = hasDate;
            this.hasTime = hasTime;
            this.dateStr = dateStr;
            this.timeStr = null;
            this.retryCount = 0;
            this.paymentMethod = null;
            this.packageId = null;
            this.paymentPkgMap = new HashMap<>();
            this.singlePayOptionNo = 3;
        }

        public PendingBooking(Long memberId, String courseName, Long groupClassId, boolean hasDate, boolean hasTime, String dateStr, String userType) {
            this.memberId = memberId;
            this.trainerId = null;
            this.trainerName = null;
            this.courseName = courseName;
            this.groupClassId = groupClassId;
            this.intentType = "GROUP";
            this.userType = userType;
            this.hasDate = hasDate;
            this.hasTime = hasTime;
            this.dateStr = dateStr;
            this.timeStr = null;
            this.retryCount = 0;
            this.paymentMethod = null;
            this.packageId = null;
            this.paymentPkgMap = new HashMap<>();
            this.singlePayOptionNo = 3;
        }
    }

}
