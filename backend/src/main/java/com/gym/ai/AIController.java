package com.gym.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.ai.context.ConversationContext;
import com.gym.ai.context.ConversationState;
import com.gym.ai.context.ContextManager;
import com.gym.ai.memory.MessageRecord;
import com.gym.ai.memory.MongoChatMemoryStore;
import com.gym.ai.rag.KnowledgeBaseService;
import com.gym.ai.tool.GymTools;
import com.gym.entity.*;
import com.gym.enums.MemberLevel;
import com.gym.mapper.*;
import com.gym.service.GroupClassService;
import com.gym.service.PersonalTrainingService;
import com.gym.service.SystemConfigService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
// import okhttp3.Response; (unused - removed to avoid ambiguity with dev.langchain4j.model.output.Response)
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
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
    private MemberMapper memberMapper;
    @Autowired
    private GroupClassMapper groupClassMapper;
    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;

    @Autowired
    private MemberPrivatePackageMapper memberPrivatePackageMapper;

    @Autowired
    private ChatLanguageModel chatLanguageModel;
    @Autowired
    private StreamingChatLanguageModel streamingChatLanguageModel;

    @Autowired
    private MongoChatMemoryStore memoryStore;

    @Autowired
    private KnowledgeBaseService knowledgeBase;

    @Autowired
    private GymTools gymTools;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PersonalTrainingService ptService;

    @Autowired
    private GroupClassService groupClassService;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private HttpSession session;

    @Autowired
    private ContextManager contextManager;

    private final Map<String, Assistant> sessionAssistants = new ConcurrentHashMap<>();

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ====== 对话上下文辅助：PendingBooking 存放进 payload，由 ContextManager 统一管理 ======
    private ConversationContext toConversationContext(Long memberId, String sessionId,
            PendingBooking pending, ConversationState state) {
        ConversationContext ctx = new ConversationContext(memberId, sessionId);
        ctx.setCurrentState(state);
        ctx.getPayload().put("pendingBooking", pending);
        return ctx;
    }

    private PendingBooking getPendingBooking(ConversationContext ctx) {
        return ctx == null ? null : (PendingBooking) ctx.getPayload().get("pendingBooking");
    }

    // ====== 多轮预约上下文（由 ContextManager 统一管理）======

    // ====== ????????????"?/?"??? ======
    private final ConcurrentHashMap<String, String> lastMentionedCoaches = new ConcurrentHashMap<>();
    // ====== 团课查询列表缓存（用于代词解析）======
    private final ConcurrentHashMap<String, java.util.List<com.gym.entity.GroupClass>> lastGroupClassListCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastGroupClassListTime = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SYSTEM_PROMPT = 
            "桔刻健身智能助手规范：\n" +
            "1. 角色：你是桔刻健身的智能助手，专门解答健身相关问题。\n" +
            "2. 风格：专业、简洁、友好，使用中文回复。\n" +
            "3. 回复格式：纯文本，不使用 Markdown（如 # ** - 等符号）、代码块或 JSON。\n" +
            "4. 分点清晰，用数字或中文序号（如 一、二、三 或 1. 2. 3.）。\n" +
            "5. 如果用户问的问题超出桔刻健身的知识范围（如通用健身常识、运动营养、训练动作等），你可以用自己的常识进行友好解答，但需注明「以下内容来自通用知识，非本馆内部数据」。如果完全不确定，则引导用户联系前台。\n" +
            "6. 用户的身份信息（会员ID、姓名、等级、剩余课时等）已在每次对话的上下文中提供，请直接使用，不得反问用户「你的会员ID是什么」「你叫什么名字」等身份问题。\n" +
            "7. 工具调用结果已包含完整信息，直接以自然语言回复用户，无需重复呈现原始数据格式。\n" +
            "8. 用户可以在「我的预约」页面自行取消预约。取消规则：团课需在开课前2小时取消，私教课需提前2小时联系教练或前台。2小时内不可取消。如果用户询问「取消预约」，请引导用户去「我的预约」页面操作，并告知取消时间限制。\n" +
            "9. 对于「你好」「嗨」「在吗」「hello」等日常寒暄，请自然友好地回应，并主动介绍自己能提供的帮助（如查询课程、体测建议、预约私教等）。";
    private static final Pattern DATE_PATTERN_CN = Pattern.compile("(\\d{1,2})月(\\d{1,2})(日|号)");
    private static final Pattern DAY_ONLY_PATTERN = Pattern.compile("(\\d{1,2})号");
    private static final Pattern TIME_PATTERN_CN = Pattern.compile("(上午|下午|晚上)?(\\d{1,2})点(\\d{0,2})分?");
    private static final Pattern STD_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern STD_TIME = Pattern.compile("(\\d{1,2})[:：](\\d{2})(?:[:：](\\d{2}))?");

    private volatile Pattern trainerNamePattern;
    private volatile long trainerCacheTime = 0;
    private static final long CACHE_TTL = 60_000;

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
            sessionAssistants.remove(sessionId);
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
            sessionAssistants.clear();
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
        // ========== 核心意图处理 ==========
        String lowerMsg = userMessage.toLowerCase();
        if (lowerMsg == null || lowerMsg.trim().isEmpty()) {
            log.warn("意图识别: 收到空消息");
            return "请输入有效的问题，例如【今天有什么团课】【我的预约】【推荐教练】等。";
        }
       log.debug("🔍 [意图识别] 原始消息: '{}'", userMessage);

        log.debug("🔍 [意图识别] memberId={}, sessionId={}, lowerMsg={}", memberId, sessionId, lowerMsg);
        // ---- 0. 检查待完成预约上下文（优先拦截，避免降级到normalChat）----
        String flowPendKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        String flowPendKey2 = flowPendKey;
        PendingBooking flowPend = getPendingBooking(contextManager.getContext(flowPendKey2));
        if (flowPend != null) {
            log.info("⬆️ [流程拦截] 检测到待完成预约上下文: trainer={}, hasDate={}, hasTime={}",
                     flowPend.trainerName, flowPend.hasDate, flowPend.hasTime);
            // 暂存到局部变量，让后续的Branch 14.5处理
        }

        // ---- 1. 查询我的私教课 ----
        // ---- 1. 查询我的私教课 / 剩余课时 ----
        if (lowerMsg.contains("我的私教") || lowerMsg.contains("我的私教预约") ||
                (lowerMsg.contains("私教") && (lowerMsg.contains("查询") || lowerMsg.contains("看看") || lowerMsg.contains("还剩") || lowerMsg.contains("剩余") || lowerMsg.contains("几次") || lowerMsg.contains("几节") || lowerMsg.contains("预约了什么") || (lowerMsg.contains("约了什么") && !lowerMsg.contains("要")) || lowerMsg.contains("预约记录")))) {
            if (lowerMsg.contains("还剩") || lowerMsg.contains("剩余") || lowerMsg.contains("几次") || lowerMsg.contains("几节")) {
                log.info("✅ 命中【查询私教剩余课时】分支");
                return gymTools.getMyPackageInfo(memberId).getMessage();
            }
            log.info("✅ 命中【查询我的私教课】分支");
            return gymTools.queryMyPTBookings(memberId).getMessage();
        }
        // ---- 1.3 查询课程包剩余 ----        
        if (lowerMsg.contains("课程包") && (lowerMsg.contains("还剩") || lowerMsg.contains("剩余") || lowerMsg.contains("几次") || lowerMsg.contains("几节") || lowerMsg.contains("还有"))) {
            log.info("✅ 命中【查询课程包剩余】分支");
            return gymTools.getMyPackageInfo(memberId).getMessage();
        }

        // ---- 1.5 推荐团课（优先于查询我的团课） ----
        if (lowerMsg.contains("推荐") && lowerMsg.contains("团课")) {
            log.info("命中【推荐团课】分支");
            LocalDateTime now = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0);
            String recStart = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String recEnd = now.plusDays(7).withHour(22).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return gymTools.recommendGroupClasses(recStart, recEnd).getMessage();
        }

        // ---- 2. 查询我的团课记录（精确匹配） ----
        if ((lowerMsg.contains("我的团课") || lowerMsg.contains("我报名的团课") || lowerMsg.contains("我预约的团课")
                || lowerMsg.contains("我约的团课") || lowerMsg.contains("团课预约记录") || lowerMsg.contains("团课报名记录"))
                || (lowerMsg.contains("团课") && (lowerMsg.contains("我的") || lowerMsg.contains("我看")
                        || lowerMsg.contains("我报") || lowerMsg.contains("我约的")
                        || lowerMsg.contains("我预约了什么") || lowerMsg.contains("我预约了哪些")
                        || (lowerMsg.contains("约了什么") && !lowerMsg.contains("要"))
                        || (lowerMsg.contains("约了哪些") && !lowerMsg.contains("要"))))) {
            log.info("命中【查询我的团课记录】分支");
            return gymTools.queryMyClassBookings(memberId).getMessage();
        }

        // ---- 2.5 查询报名/预约记录（通用匹配：我报了/我报了什么课） ----
        if (lowerMsg.contains("我报") && (lowerMsg.contains("课") || lowerMsg.contains("课程") || lowerMsg.contains("什么"))) {
            log.info("✅ 命中【查询报名记录】分支");
            return gymTools.queryMyClassBookings(memberId).getMessage();
        }

        // ---- 2.7 查询我的课程安排（我+今天/明天/我的+课，排除预约意图） ----
        if ((lowerMsg.contains("我的") && lowerMsg.contains("课") && !lowerMsg.contains("约") && !lowerMsg.contains("课程包")) ||
                (lowerMsg.contains("我") && (lowerMsg.contains("今天") || lowerMsg.contains("明天") || lowerMsg.contains("后天")) && lowerMsg.contains("课") && !lowerMsg.contains("预约") && !lowerMsg.contains("约"))) {
            log.info("命中【查询我的课程安排】分支");
            return gymTools.queryMyClassBookings(memberId).getMessage();
        }

        // ---- 2.8 查询我的所有预约（团课+私教） ----
        if ((lowerMsg.contains("我") && (lowerMsg.contains("约了什么课") || lowerMsg.contains("约了哪些课")
                || lowerMsg.contains("的预约") || lowerMsg.contains("预约了什么课") || lowerMsg.contains("预约了哪些课")
                || (lowerMsg.contains("预约了什么") && !lowerMsg.contains("要"))
                || (lowerMsg.contains("约了什么") && !lowerMsg.contains("课") && !lowerMsg.contains("团课")
                        && !lowerMsg.contains("私教") && !lowerMsg.contains("要"))))
                || (lowerMsg.contains("我的") && (lowerMsg.contains("所有预约") || lowerMsg.contains("全部预约")))) {
            log.info("命中【查询所有预约】分支（团课+私教）");
            String classBookings = gymTools.queryMyClassBookings(memberId).getMessage();
            String ptBookings = gymTools.queryMyPTBookings(memberId).getMessage();
            StringBuilder sb = new StringBuilder();
            sb.append("【您的全部预约】\n\n");
            sb.append("--- 团课预约 ---\n");
            if (classBookings != null && !classBookings.contains("暂无") && !classBookings.contains("请先登录")) {
                sb.append(classBookings);
            } else {
                sb.append("暂无团课预约\n");
            }
            sb.append("\n--- 私教课预约 ---\n");
            if (ptBookings != null && !ptBookings.contains("暂无") && !ptBookings.contains("请先登录")) {
                sb.append(ptBookings);
            } else {
                sb.append("暂无私教课预约\n");
            }
            return sb.toString();
        }
        // ---- 3. 查询体测历史 ----
        if (lowerMsg.contains("体测") && (lowerMsg.contains("历史") || lowerMsg.contains("记录") ||
                lowerMsg.contains("以前") || lowerMsg.contains("之前"))) {
            log.info("✅ 命中【查询体测历史】分支");
            return gymTools.queryMyTestHistory(memberId).getMessage();
        }

        // ---- 4. 查询教练详情 ----
        if (lowerMsg.contains("教练") && (lowerMsg.contains("怎么样") || lowerMsg.contains("评价") ||
                lowerMsg.contains("介绍") || lowerMsg.contains("信息"))) {
            log.info("✅ 命中【查询教练】分支");
            String trainerName = extractTrainerName(userMessage);
            if (trainerName == null) {
                return "请告诉我是哪位教练，例如「王教练怎么样」";
            }
            lastMentionedCoaches.put(sessionId, trainerName);
            log.info("[教练详情] 记录上次提及教练: {}", trainerName);
            return gymTools.queryTrainerByName(trainerName).getMessage();
        }

        // ---- 4.5 取消预约 ----
        if (lowerMsg.contains("取消") && (lowerMsg.contains("预约") || lowerMsg.contains("约"))) {
            log.info("✅ 命中【取消预约】分支");
            return "关于取消预约：\n一、团课预约：可在「我的预约」页面自行取消，需在开课前2小时操作。\n二、私教课预约：需提前2小时联系教练或前台取消。\n三、开课前2小时内不可取消。\n请前往「我的预约」页面查看并操作，如仍有疑问请联系前台。";
        }

        // ---- 4.6 预约他/她（代词处理，优先生效）----
        if ((lowerMsg.contains("约") || lowerMsg.contains("预约")) &&
            (lowerMsg.contains("他") || lowerMsg.contains("她") || lowerMsg.contains("ta") || lowerMsg.contains("TA"))) {
            log.info("✅ 命中【预约代词】分支");
            String lastCoach = lastMentionedCoaches.get(sessionId);
            if (lastCoach != null) {
                log.info("[预约代词] 找到上次提及教练: {}", lastCoach);
                java.util.regex.Pattern pronounP = java.util.regex.Pattern.compile("[他她]");
                java.util.regex.Matcher pronounM = pronounP.matcher(userMessage);
                String modifiedMsg = pronounM.replaceAll(lastCoach);
                return handleBooking(modifiedMsg, memberId, sessionId);
            }
            return "请问您想预约哪位教练？请提供教练姓名，例如「我要预约李教练」。";
        }

        // ---- 5. 预约团课（含多轮对话上下文）----
        if ((lowerMsg.contains("约") || lowerMsg.contains("预约") || lowerMsg.contains("报名") || lowerMsg.contains("订"))
                && !lowerMsg.contains("私教") && !lowerMsg.contains("教练")
                && !lowerMsg.contains("体测") && !lowerMsg.contains("比赛") && !(lowerMsg.contains("体验课") && (lowerMsg.contains("什么") || lowerMsg.contains("哪些") || lowerMsg.contains("有没有")))) {
            log.info("✅ 命中【预约团课】分支");
            String result = handleBookGroupClass(userMessage, memberId, sessionId);
            if (result != null && !result.isEmpty()
                    && !result.equals("请告诉我您想预约哪门团课，例如「帮我预约动感单车」")) {
                return result;
            }
            log.warn("预约团课解析失败，继续执行后续分支");
        }

        // ---- 6. 体测建议 ----
        if (lowerMsg.contains("体测") || (lowerMsg.contains("建议") && lowerMsg.contains("锻炼"))) {
            log.info("✅ 命中【体测建议】分支");
            return gymTools.generateWorkoutAdvice(memberId).getMessage();
        }

        // ---- 7. 训练计划 ----
        if (isWorkoutPlanRequest(lowerMsg)) {
            log.info("✅ 命中【训练计划】分支");
            String skeleton = gymTools.generateWorkoutPlanSkeleton(memberId).getMessage();
            if (skeleton != null && skeleton.contains("\"error\"")) {
                return skeleton;
            }
            return generatePersonalizedPlan(skeleton, userMessage, memberId, "workout", sessionId, memoryId);
        }

        // ---- 8. 饮食计划 ----
        if (isMealPlanRequest(lowerMsg)) {
            log.info("✅ 命中【饮食计划】分支");
            String skeleton = gymTools.generateMealPlanSkeleton(memberId).getMessage();
            if (skeleton != null && skeleton.contains("\"error\"")) {
                return skeleton;
            }
            return generatePersonalizedPlan(skeleton, userMessage, memberId, "meal", sessionId, memoryId);
        }

        // ---- 9. 团课查询（查询所有可预约团课） ----
        if (lowerMsg.contains("团课") || lowerMsg.contains("课表") || lowerMsg.contains("什么课") ||
                lowerMsg.contains("能报名") || lowerMsg.contains("可预约") || lowerMsg.contains("可以预约") ||
                lowerMsg.contains("体验课") || lowerMsg.contains("公益课") ||
                (lowerMsg.contains("团") && lowerMsg.contains("课"))) {
            log.info("✅ 命中【团课查询】分支");
            return handleQueryClasses(userMessage, memberId);
        }

        // ---- 9.5 查询会员信息 / 过期时间 ----
        if (lowerMsg.contains("会员信息") || lowerMsg.contains("我的信息") || lowerMsg.contains("会员资料") ||
                (lowerMsg.contains("过期") && (lowerMsg.contains("我") || lowerMsg.contains("到期")))) {
            log.info("✅ 命中【查询会员信息】分支");
            return gymTools.getMyProfile(memberId).getMessage();
        }

        // ---- 10. 推荐教练 ----
        if (lowerMsg.contains("推荐") && lowerMsg.contains("教练")) {
            log.info("✅ 命中【推荐教练】分支");
            if (memberId == null || memberId <= 0) {
                return "请先登录，以便根据您的会员等级推荐合适的教练。";
            }
            try {
                String recResult = gymTools.recommendTrainerByLevel(memberId).getMessage();
                log.info("[推荐教练] 推荐结果: {}", recResult);
                return recResult;
            } catch (Exception e) {
                log.error("推荐教练异常 memberId={}", memberId, e);
                return "推荐教练时出现异常，请稍后再试。";
            }
        }

        // ---- 11. 预约私教（置于教练列表之前，防止"约李教练"被误判） ----
        if ((lowerMsg.contains("约") || lowerMsg.contains("预约") || lowerMsg.contains("订"))
                && (lowerMsg.contains("教练") || lowerMsg.contains("私教"))) {
            log.info("✅ 命中【预约私教】分支");
            String extractedTr = extractTrainerName(userMessage);
            if (extractedTr != null) {
                lastMentionedCoaches.put(sessionId, extractedTr);
                log.info("[预约私教] 记录上次提及教练: {}", extractedTr);
            }
            String bookingResult = handleBooking(userMessage, memberId, sessionId);
            if (bookingResult != null && !bookingResult.isEmpty()) {
                return bookingResult;
            }
        }


// ---- 14.5 检查是否有待完成的预约上下文 ----
        String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        PendingBooking pending = getPendingBooking(contextManager.getContext(pendingKey));
        // P0-4: 如果输入是纯数字且存在团课预约上下文，跳过私教预约上下文，让团课序号选择处理
        String groupPendKey14 = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        PendingBooking groupPend14 = getPendingBooking(contextManager.getContext(groupPendKey14));
        if (pending != null && groupPend14 != null && userMessage.trim().matches("\\d+")) {
            // 私教流程正在等待支付选择（选项1/2/3），优先处理私教支付，避免被团课序号抢占
            boolean ptWaitingPayment = pending.hasDate && pending.hasTime && pending.paymentMethod == null;
            if (ptWaitingPayment && userMessage.trim().matches("[123]")) {
                log.info("[私教上下文] 私教流程等待支付选择（输入{}），优先处理私教支付，跳过团课序号处理", userMessage.trim());
            } else {
                boolean validGroupChoice = false;
                if (groupPend14.dateStr != null) {
                    try {
                        int num = Integer.parseInt(userMessage.trim());
                        String[] groupOpts = groupPend14.dateStr.split(",");
                        validGroupChoice = (num >= 1 && num <= groupOpts.length);
                    } catch (NumberFormatException ignored) {}
                }
                if (validGroupChoice) {
                    log.info("[私教上下文] 输入为纯数字，且是有效的团课序号，跳过私教上下文处理");
                    pending = null;
                }
            }
        }
        if (pending != null) {
            log.info("命中【待完成预约上下文】pendingKey={}, hasDate={}, dateStr={}, hasTime={}", 
                     pendingKey, pending.hasDate, pending.dateStr, pending.hasTime);
            String dateStr = parseDate(userMessage);
            String timeStr = parseTime(userMessage);
            if (!pending.hasDate && dateStr != null && !pending.hasTime && timeStr != null) {
                // 日期和时间同时解析成功，直接执行预约
                LocalDateTime apptTime;
                try {
                    apptTime = LocalDateTime.parse(dateStr + " " + timeStr, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    try {
                        apptTime = LocalDateTime.parse(dateStr + " " + timeStr + ":00", DATE_FORMATTER2);
                    } catch (DateTimeParseException ex) {
                        return "日期时间格式有误，请使用类似【明天下午2点】的格式。";
                    }
                }
                // 校验预约时间是否已过
                if (apptTime.isBefore(LocalDateTime.now())) {
                    contextManager.removeContext(pendingKey);
                    return "预约时间已过，请选择未来的时间。";
                }
                pending.hasDate = true;
                pending.dateStr = dateStr;
                pending.hasTime = true;
                pending.timeStr = timeStr;
                pending.retryCount = 0;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                log.info("[预约上下文] 已获取日期和时间，更新上下文，检查支付方式");
                // 校验时间范围
                int hour = apptTime.getHour();
                int min = apptTime.getMinute();
                if (hour < 9 || hour > 21 || (hour == 21 && min > 0)) {
                    contextManager.removeContext(pendingKey);
                    return "预约时间必须在上午9点到晚上9点之间，请重新选择。";
                }
                // 检查支付方式
                String payResult1 = processPaymentChoice(userMessage, pending, pendingKey, sessionId);
                if (payResult1 != null) {
                    if (payResult1.equals("__EXIT__")) {
                        contextManager.removeContext(pendingKey);
                        return "好的，已取消预约。请问还有其他问题吗？";
                    }
                    return payResult1;
                }
                // 有支付方式，执行预约
                boolean useFree1 = "free".equals(pending.paymentMethod);
                Long pkgId1 = null;
                if ("package".equals(pending.paymentMethod)) {
                    pkgId1 = resolvePackageId(pending);
                }
                log.info("[预约执行] 打算执行私教预约：memberId={}, trainerId={}, time={}, paymentMethod={}, useFree={}, pkgId={}", pending.memberId, pending.trainerId, apptTime, pending.paymentMethod, useFree1, pkgId1);
                String result1 = ptService.bookPersonalTraining(pending.memberId, pending.trainerId, apptTime, 60, pkgId1, useFree1);
                contextManager.removeContext(pendingKey);
            if (result1.startsWith("私教预约成功")) {
                String label1 = "单次付费";
                if (useFree1) label1 = "免费私教课";
                else if (pkgId1 != null) label1 = "课程包扣费";
                // Issue 2: 使用实际返回结果
                if (result1.contains("原价")) {
                    return result1 + "\n支付方式：" + label1;
                } else {
                    return "预约成功！已为您预约 " + pending.trainerName + " 的课程，时间：" +
                            apptTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n支付方式：" + label1;
                }
            } else {
                return result1;
            }
            } else if (!pending.hasDate && dateStr != null) {
                // 校验日期是否过期
                String pastMsg = validateDateNotPast(dateStr);
                if (pastMsg != null) {
                    return pastMsg;
                }
                // 只解析出日期，更新上下文继续等待时间
                pending.hasDate = true;
                pending.dateStr = dateStr;
                pending.retryCount = 0;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                log.info("[预约上下文] 已获取日期={}, 继续等待时间", dateStr);
                return "好的，" + dateStr + "。请问您想约什么时间？（例如：下午2点、14:00）";
            } else if (pending.hasDate && !pending.hasTime) {
                if (timeStr == null) {
                    // 检查是否提供了新的日期（用于"换个日期"场景）
                    String newDate = parseDate(userMessage);
                    if (newDate != null && !newDate.equals(pending.dateStr)) {
                        pending.dateStr = newDate;
                        pending.retryCount = 0;
                        contextManager.updateContext(pendingKey, toConversationContext(
                            pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                        log.info("[预约上下文] 已更新日期={}, 继续等待时间", newDate);
                        return "好的，已更新日期为 " + newDate + "。请问您想约什么时间？（例如：下午2点、14:00）";
                    }
                    // 检测是否输入了非整点时间
                    boolean hasTimePattern = userMessage.matches(".*[0-9一两三四五六七八九十].*[:\uff1a点].*") ||
                        userMessage.matches(".*(上午|下午|晚上).*[0-9一两三四五六七八九十].*");
                    if (hasTimePattern) {
                        pending.retryCount++;
                        contextManager.updateContext(pendingKey, toConversationContext(
                            pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                        return "预约时间只支持整点（如 14:00、15:00），请重新输入。";
                    }
                    pending.retryCount++;
                    if (pending.retryCount >= 2) {
                        contextManager.removeContext(pendingKey);
                        return "未能识别您的时间，请重新发起预约。例如：「帮我约李教练明天下午2点」";
                    }
                    contextManager.updateContext(pendingKey, toConversationContext(
                        pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                    return "未能识别您的时间，请用【下午2点】或【14:00】格式重新输入。";
                }
                // 已有日期，本次解析出时间，用上下文中的日期+本次的时间执行预约
                String effectiveDate = pending.dateStr;
                if (effectiveDate == null) {
                    effectiveDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
                LocalDateTime apptTime;
                try {
                    apptTime = LocalDateTime.parse(effectiveDate + " " + timeStr, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    try {
                        apptTime = LocalDateTime.parse(effectiveDate + " " + timeStr + ":00", DATE_FORMATTER2);
                    } catch (DateTimeParseException ex) {
                        pending.retryCount++;
                        if (pending.retryCount >= 2) {
                            contextManager.removeContext(pendingKey);
                            log.info("[预约上下文] 连续{}次解析失败，清除上下文", pending.retryCount);
                            return "未能识别您的时间，请重新发起预约。";
                        }
                        contextManager.updateContext(pendingKey, toConversationContext(
                            pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                        return "未能识别您的时间，请用【下午2点】或【14:00】格式重新输入。";
                    }
                }
                // 校验预约时间是否已过
                if (apptTime.isBefore(LocalDateTime.now())) {
                    contextManager.removeContext(pendingKey);
                    return "预约时间已过，请选择未来的时间。";
                }
                // ??????????????????????????
                log.info("[预约上下文] 已获取时间，执行预约");
                // 校验冲突
                String conflictMsg2 = checkBookingConflict(pending.memberId, apptTime);
                if (conflictMsg2 != null) {
                    pending.retryCount = 0;
                    contextManager.updateContext(pendingKey, toConversationContext(
                        pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                    log.info("[预约上下文] 冲突后保留上下文，等待新时间");
                    return conflictMsg2 + "\n\n请选择其他时间，例如【下午2点】或【14:00】";
                }
                // 校验时间范围
                int hour2 = apptTime.getHour();
                int min2 = apptTime.getMinute();
                if (hour2 < 9 || hour2 > 21 || (hour2 == 21 && min2 > 0)) {
                    contextManager.removeContext(pendingKey);
                    return "预约时间必须在上午9点到晚上9点之间，请重新选择。";
                }
                // 更新上下文：已收集时间，等待支付方式
                pending.hasTime = true;
                pending.timeStr = timeStr;
                pending.retryCount = 0;
                contextManager.updateContext(pendingKey, toConversationContext(
                    pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                // 检查支付方式
                String payResult2 = processPaymentChoice(userMessage, pending, pendingKey, sessionId);
                if (payResult2 != null) {
                    if (payResult2.equals("__EXIT__")) {
                        contextManager.removeContext(pendingKey);
                        return "好的，已取消预约。请问还有其他问题吗？";
                    }
                    return payResult2;
                }
                boolean useFree2 = "free".equals(pending.paymentMethod);
                Long pkgId2 = null;
                if ("package".equals(pending.paymentMethod)) {
                    pkgId2 = resolvePackageId(pending);
                }
                    log.info("[预约执行] 打算执行私教预约：memberId={}, trainerId={}, time={}, paymentMethod={}, useFree={}, pkgId={}", pending.memberId, pending.trainerId, apptTime, pending.paymentMethod, useFree2, pkgId2);
                String result2 = ptService.bookPersonalTraining(pending.memberId, pending.trainerId, apptTime, 60, pkgId2, useFree2);
                contextManager.removeContext(pendingKey);
                if (result2.startsWith("私教预约成功")) {
                    String label2 = "单次付费";
                    if (useFree2) label2 = "免费私教课";
                    else if (pkgId2 != null) label2 = "课程包扣费";
                    if (result2.contains("原价") || result2.contains("时间")) {
                        return result2 + "\n支付方式：" + label2;
                    }
                    return "预约成功！已为您预约 " + pending.trainerName + " 的课程，时间：" +
                            apptTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n支付方式：" + label2;
                } else {
                    return result2;
                }
            } else if (pending.hasDate && pending.hasTime) {
                // 已收集日期和时间，等待选择支付方式
                String payResult = processPaymentChoice(userMessage, pending, pendingKey, sessionId);
                if (payResult != null) {
                    if (payResult.equals("__EXIT__")) {
                        contextManager.removeContext(pendingKey);
                        return "好的，已取消预约。请问还有其他问题吗？";
                    }
                    return payResult;
                }
                // 支付方式已选择，执行预约
                contextManager.removeContext(pendingKey);
                String effectiveDate = pending.dateStr;
                if (effectiveDate == null) {
                    effectiveDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
                LocalDateTime apptTime;
                try {
                    apptTime = LocalDateTime.parse(effectiveDate + " " + pending.timeStr, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    try {
                        apptTime = LocalDateTime.parse(effectiveDate + " " + pending.timeStr + ":00", DATE_FORMATTER2);
                    } catch (DateTimeParseException ex) {
                        contextManager.updateContext(pendingKey, toConversationContext(
                            pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                        return "日期时间格式有误，请重新发起预约。";
                    }
                }
                // 校验预约时间是否已过
                if (apptTime.isBefore(LocalDateTime.now())) {
                    contextManager.removeContext(pendingKey);
                    return "预约时间已过，请选择未来的时间。";
                }
                // 校验时间范围
                int hour = apptTime.getHour();
                int min = apptTime.getMinute();
                if (hour < 9 || hour > 21 || (hour == 21 && min > 0)) {
                    contextManager.removeContext(pendingKey);
                    return "预约时间必须在上午9点到晚上9点之间，请重新选择。";
                }
                // 校验冲突
                String conflictMsg = checkBookingConflict(pending.memberId, apptTime);
                if (conflictMsg != null) {
                    pending.retryCount = 0;
                    pending.hasTime = false;
                    pending.timeStr = null;
                    contextManager.updateContext(pendingKey, toConversationContext(
                        pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
                    log.info("[预约上下文] 冲突后保留上下文，等待新时间");
                    return conflictMsg + "\n\n请选择其他时间，例如【下午2点】或【14:00】";
                }
                // 执行预约
                boolean useFree = "free".equals(pending.paymentMethod);
                Long pkgId = null;
                if ("package".equals(pending.paymentMethod)) {
                    pkgId = resolvePackageId(pending);
                }
                log.info("[预约执行] 打算执行私教预约：memberId={}, trainerId={}, time={}, paymentMethod={}, useFree={}, pkgId={}", pending.memberId, pending.trainerId, apptTime, pending.paymentMethod, useFree, pkgId);
                String result = ptService.bookPersonalTraining(pending.memberId, pending.trainerId, apptTime, 60, pkgId, useFree);
                contextManager.removeContext(pendingKey);
        if (result.startsWith("私教预约成功")) {
            String label = "单次付费";
            if (useFree) label = "免费私教课";
            else if (pkgId != null) label = "课程包扣费";
            // Issue 2: 使用实际返回结果（含折扣明细）
            if (result.contains("原价")) {
                return result + "\n支付方式：" + label;
            } else {
                return "预约成功！已为您预约 " + pending.trainerName + " 的课程，时间：" +
                        apptTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n支付方式：" + label;
            }
        } else {
                return result;
            }
            }
        }

                // ---- 11.5 具体教练名检测（解决"李教练的"进入预约）----
        String detectedTrainer = extractTrainerName(userMessage);
        if (detectedTrainer != null && !lowerMsg.contains("查询") && !lowerMsg.contains("看看") && !lowerMsg.contains("介绍") && !lowerMsg.contains("怎么样") && !lowerMsg.contains("评价") && !lowerMsg.contains("有什么") && !lowerMsg.contains("有哪些") && !lowerMsg.contains("列表") && !lowerMsg.contains("谁")) {
            log.info("✔️ 命中【具体教练名预约】分支: detected={}", detectedTrainer);
            lastMentionedCoaches.put(sessionId, detectedTrainer);
            return handleBooking(userMessage, memberId, sessionId);
        }

        // ---- 12. 教练列表（宽匹配，排除已处理预约的） ----
        if (lowerMsg.contains("教练") && !lowerMsg.contains("预约") && !lowerMsg.contains("推荐")) {
            log.info("✅ 命中【教练列表】分支（宽匹配）");
            return gymTools.listAllTrainers().getMessage();
        }
        // ---- 13. 查询可报名比赛 ----
        if (lowerMsg.contains("比赛") && (lowerMsg.contains("查询") || lowerMsg.contains("报名") ||
                lowerMsg.contains("参加") || lowerMsg.contains("有什么"))) {
            log.info("✅ 命中【查询比赛】分支");
            return gymTools.queryAvailableCompetitions().getMessage();
        }

        // ---- 13.5 查询教练可预约时段 ----
        if (lowerMsg.contains("什么时候有空") || lowerMsg.contains("可预约时段") || lowerMsg.contains("有没有空") ||
                lowerMsg.contains("何时有空") || (lowerMsg.contains("时间") && lowerMsg.contains("上课"))) {
            log.info("✅ 命中【查询教练可预约时段】分支");
            String coachName = extractTrainerName(userMessage);
            String dateStr = parseDate(userMessage);
            if (dateStr == null) {
                dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            return "该教练" + (coachName != null ? coachName : "") + "的可预约时段，请您拨打前台电话或到店咨询。";
        }
        // ---- 13.7 模糊预约意图引导 ----
        if ((lowerMsg.contains("约课") || lowerMsg.contains("约一下") || lowerMsg.contains("想约") ||
                lowerMsg.contains("要约") || lowerMsg.equals("约") || lowerMsg.equals("预约"))
                && !lowerMsg.contains("团课") && !lowerMsg.contains("私教") && !lowerMsg.contains("教练")) {
            log.info("✅ 命中【模糊预约意图】分支");
            return "请问您想预约团课还是私教课？团课请回复课程名称（如动感单车），私教课请提供教练姓名。";
        }
        // ---- 13.8 模糊引导后选择私教 ----
        if (lowerMsg.equals("私教") || lowerMsg.equals("私教课") || lowerMsg.contains("私教吧")) {
            log.info("✅ 命中【模糊引导-选择私教】分支");
            return "好的，为您预约私教课。请提供教练姓名，例如「王教练」或「我要预约李教练」。";
        }
        // ---- 14. 图片生成 ----
        if (lowerMsg.contains("画") || lowerMsg.contains("图片") || lowerMsg.contains("图示") ||
                lowerMsg.contains("姿势") || lowerMsg.contains("图解")) {
            log.info("✅ 命中【图片生成】分支");
            String imageUrl = generateImage(userMessage);
            if (imageUrl != null) {
                return "根据您的要求，生成了以下健身动作示意图：\n" + imageUrl;
            }
            log.warn("图片生成失败，降级到普通对话");
        }

        // ---- 14.6 团课预约选择（用户从列表中选择）----
        String groupPendKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        PendingBooking groupPend = getPendingBooking(contextManager.getContext(groupPendKey));
        if (groupPend != null && "GROUP".equals(groupPend.intentType)) {
            log.info("命中【团课预约上下文】course={}, 待选择课程列表", groupPend.courseName);

            // 解析用户选择的序号
            String input = userMessage.trim();
            int selectedIdx = -1;
            try {
                selectedIdx = Integer.parseInt(input) - 1;  // 用户从1开始数
            } catch (NumberFormatException e) {
                // 不是纯数字，尝试从日期/时间匹配课程
                String inputDate = parseDate(userMessage);
                String inputTime = parseTime(userMessage);
                if (inputDate != null && groupPend.dateStr != null) {
                    String[] options = groupPend.dateStr.split(",");
                    for (int i = 0; i < options.length; i++) {
                        String[] parts = options[i].split("\\|");
                        if (parts.length >= 3 && parts[2].contains(inputDate)) {
                            selectedIdx = i;
                            break;
                        }
                    }
                }
            }

            if (selectedIdx >= 0 && groupPend.dateStr != null) {
                String[] options = groupPend.dateStr.split(",");
                if (selectedIdx < options.length) {
                    String[] parts = options[selectedIdx].split("\\|");
                    if (parts.length >= 1) {
                        try {
                            Long classId = Long.parseLong(parts[0]);
                            contextManager.removeContext(groupPendKey);
                            // 查询课程类型，决定是否弹支付
                            GroupClass gcSel = groupClassMapper.selectById(classId);
                            if (gcSel != null) {
                                // 先处理访客：体验课（公益/付费均可）直接预约；非体验课拒绝
                                String visitorResult = resolveVisitorGroupBooking(memberId, gcSel);
                                if (visitorResult != null) return visitorResult;
                                // 会员 + 付费课 → 确认支付流程
                                if ("paid".equals(gcSel.getType()) && gcSel.getPrice() != null && gcSel.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                                    return prepareGroupPayment(gcSel, memberId, sessionId);
                                }
                                // 会员 + 公益课 → 直接预约，不创建支付上下文、不生成确认引导语
                                String result = groupClassService.bookClass(memberId, classId);
                                log.info("[团课预约] 选择序号{}（公益课），预约结果: {}", selectedIdx + 1, result);
                                return result;
                            }
                        } catch (NumberFormatException ex) {
                            log.warn("课程ID解析失败: {}", parts[0]);
                        }
                    }
                }
            }

            // 无法识别选择
            if (groupPend.retryCount >= 2) {
                contextManager.removeContext(groupPendKey);
                return "未能识别您的选择，请重新发起预约。";
                }
            groupPend.retryCount++;
            contextManager.updateContext(groupPendKey, toConversationContext(
                memberId, sessionId, groupPend, ConversationState.GROUP_BOOKING));
            return "请回复课程对应的序号（如回复 1、2）来选择您想预约的课程。";
        }

        // ---- 14.7 团课支付确认 ----
        String payKey = "payment_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        PendingBooking payPend = getPendingBooking(contextManager.getContext(payKey));
        if (payPend != null && "GROUP_PAYMENT".equals(payPend.intentType)) {
            log.info("[团课支付确认] 命中 key={}, course={}, classId={}, intentType={}",
                    payKey, payPend.courseName, payPend.groupClassId, payPend.intentType);
            String input = userMessage.trim().toLowerCase();
            if (input.equals("confirm") || input.contains("确认") || input.equals("1") || input.contains("支付")) {
                if (payPend.groupClassId != null) {
                    // 确认环节兜底：访客体验课开关校验（VISITOR_EXPERIENCE_ENABLED）
                    GroupClass confirmGc = groupClassMapper.selectById(payPend.groupClassId);
                    Member confirmMember = (memberId != null && memberId > 0) ? memberMapper.selectById(memberId) : null;
                    if (confirmGc != null && confirmMember != null && confirmMember.isVisitor()
                            && confirmGc.getAllowVisitor() != null && confirmGc.getAllowVisitor()
                            && !isConfigEnabled("VISITOR_EXPERIENCE_ENABLED")) {
                        contextManager.removeContext(payKey);
                        return "体验课功能暂未开放，请联系客服";
                    }
                    // 真正执行预约：先执行后清理上下文；执行异常时保留上下文，避免重试被误判为"没有待确认"
                    String result;
                    try {
                        result = groupClassService.bookClass(memberId, payPend.groupClassId);
                    } catch (Exception e) {
                        log.error("[团课支付] 预约执行异常: memberId={}, classId={}, payKey={}",
                                memberId, payPend.groupClassId, payKey, e);
                        return "预约失败：预约执行出现异常（" + e.getMessage() + "），请重试或回复「取消」放弃预约。";
                    }
                    log.info("[团课支付] 确认支付，预约结果: {}", result);
                    contextManager.removeContext(payKey);
                    if (result != null && result.contains("成功")) {
                        StringBuilder okSb = new StringBuilder(result);
                        if (confirmGc != null && confirmGc.getStartTime() != null) {
                            okSb.append("\n上课时间：").append(confirmGc.getStartTime()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                        }
                        okSb.append("\n您可在「我的预约」页面查看预约记录。");
                        return okSb.toString();
                    }
                    return "预约失败：" + (result != null ? result : "未知错误，请重试") + "。";
                } else {
                    contextManager.removeContext(payKey);
                    return "预约失败：未找到课程信息，请重新发起预约。";
                }
            } else if (input.contains("不") || input.contains("取消") || input.contains("算了") || input.contains("不要")) {
                contextManager.removeContext(payKey);
                return "已取消支付，预约未完成。如有需要请重新预约。";
            } else {
                return "请点击「确认支付」完成预约，或回复「取消」放弃预约。";
            }
        }

        // ---- 14.8 用户确认支付但无团课支付上下文：直接提示，防止 AI 无限循环追问 ----
        String confirmOnlyMsg = userMessage.trim().toLowerCase();
        if (confirmOnlyMsg.equals("confirm") || confirmOnlyMsg.equals("确认")
                || confirmOnlyMsg.equals("确认支付") || confirmOnlyMsg.equals("确认付款")
                || confirmOnlyMsg.equals("支付确认") || confirmOnlyMsg.contains("确认支付")
                || confirmOnlyMsg.contains("确认付款")) {
            log.warn("命中【无上下文支付确认】memberId={}, sessionId={}, 现有上下文keys={}, userMessage={}",
                    memberId, sessionId, contextManager.keys(), userMessage);
            return "当前没有待确认的团课预约，请先发起预约（例如回复「我要约热力搏击」）。\n如需预约私教课，请提供教练姓名发起预约。";
        }

        // ---- 15. 默认：普通AI对话 ----
        log.info("➡️ 未命中任何专用分支，进入【普通 AI 对话】");
        if (emitter != null) { streamingNormalChat(sessionId, userMessage, memberId, memoryId, emitter); return null; }
        return normalChat(sessionId, userMessage, memberId, memoryId);
    }


    // ========== 普通对话（直接使用AI + 工具 + 记忆） ==========
    private String normalChat(String sessionId, String userMessage, Long memberId, String memoryId) {
        try {
            Assistant assistant = getOrCreateAssistant(sessionId, memoryId);
            String userContext = buildUserContext(memberId);

            // RAG 检索：仅当与健身相关时才检索
            String ragContext = null;
            if (isFitnessRelated(userMessage)) {
                ragContext = knowledgeBase.searchRelevant(userMessage);
            } else {
                log.info("normalChat: 非健身问题，跳过 RAG 检索");
            }

            // 注入动态数据（教练信息、课程介绍）
            String dynContext = buildDynamicContext(userMessage);

            StringBuilder promptBuilder = new StringBuilder(userContext);
            if (ragContext != null && ragContext.startsWith("【健身知识库检索结果】")) {
                promptBuilder.append("\n\n参考知识库信息：\n").append(ragContext);
            }
            if (!dynContext.isEmpty()) {
                promptBuilder.append("\n\n").append(dynContext);
            }
            promptBuilder.append("\n\n用户提问：").append(userMessage);
            String finalPrompt = promptBuilder.toString();
            log.debug("normalChat finalPrompt(前500字): {}",
                    finalPrompt.length() > 500 ? finalPrompt.substring(0, 500) + "..." : finalPrompt);
            log.info("normalChat: memberId={}, promptLen={}", memberId, finalPrompt.length());
            String answer = CompletableFuture.supplyAsync(() ->
                    assistant.chat(finalPrompt),
                    CompletableFuture.delayedExecutor(0, TimeUnit.SECONDS)
            ).get(60, TimeUnit.SECONDS);
            if (answer == null || answer.trim().isEmpty()) {
                log.warn("normalChat: AI 返回内容为空, answer={}", answer);
                return getFallbackResponse(userMessage);
            }
            log.info("normalChat response(前300字): {}",
                    answer.length() > 300 ? answer.substring(0, 300) + "..." : answer);
            return answer;
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("AI 模型调用超时(60s)", e);
            return "抱歉，AI 响应超时，请简化问题后重试。";
        } catch (Exception e) {
            log.error("AI 模型调用失败", e);
            return getFallbackResponse(userMessage);
        }
    }

    // 降级回复方法
    private String getFallbackResponse(String userMessage) {
        String lowerMsg = userMessage.toLowerCase();
        if (lowerMsg.contains("你好") || lowerMsg.contains("hi") || lowerMsg.contains("hello")) {
            return "🤖 您好！我是智能健身助手，很高兴为您服务。由于当前服务繁忙，暂时无法提供完整的AI回答。您可以尝试：\n" +
                    "1. 询问「我的课程包还剩几次」\n" +
                    "2. 询问「帮我推荐教练」\n" +
                    "3. 询问「今天有什么团课」\n" +
                    "如有紧急需求，请直接联系前台。";
        }
        return "🤖 您好！我是智能健身助手，很高兴为您服务。您可以直接问我关于课程预约、体测建议、训练计划、团课查询等问题。如有紧急需求，也可以联系前台人工服务。";
    }

    // ========== 流式普通对话（逐 Token 推送） ==========
    private void streamingNormalChat(String sessionId, String userMessage, Long memberId, String memoryId, SseEmitter emitter) {
        try {
            String userContext = buildUserContext(memberId);

            // RAG 检索：仅当与健身相关时才检索
            String ragContext = null;
            if (isFitnessRelated(userMessage)) {
                ragContext = knowledgeBase.searchRelevant(userMessage);
            } else {
                log.info("streamingNormalChat: 非健身问题，跳过 RAG 检索");
            }

            String dynContext = buildDynamicContext(userMessage);

            StringBuilder promptBuilder = new StringBuilder(userContext);
            if (ragContext != null && ragContext.startsWith("【健身知识库检索结果】")) {
                promptBuilder.append("\n\n参考知识库信息：\n").append(ragContext);
            }
            if (!dynContext.isEmpty()) {
                promptBuilder.append("\n\n").append(dynContext);
            }
            promptBuilder.append("\n\n用户提问：").append(userMessage);
            String finalPrompt = promptBuilder.toString();

            log.debug("streamingNormalChat finalPrompt(前500字): {}",
                    finalPrompt.length() > 500 ? finalPrompt.substring(0, 500) + "..." : finalPrompt);
            log.info("streamingNormalChat: memberId={}, promptLen={}", memberId, finalPrompt.length());

            // 从记忆加载历史消息
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT));
            List<ChatMessage> history = memoryStore.getMessages(memoryId);
            if (history != null) {
                messages.addAll(history);
            }
            messages.add(new UserMessage(finalPrompt));

            StringBuilder fullAnswer = new StringBuilder();

            streamingChatLanguageModel.generate(messages, new StreamingResponseHandler<dev.langchain4j.data.message.AiMessage>() {
                @Override
                public void onNext(String token) {
                    try {
                        fullAnswer.append(token);
                        emitter.send(SseEmitter.event()
                                .name("delta")
                                .data(Map.of("type", "delta", "content", token)));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE send failed", e);
                    }
                }

                @Override
                public void onComplete(Response<dev.langchain4j.data.message.AiMessage> response) {
                    try {
                        String answer = fullAnswer.toString();

                        // 空响应兜底：推一条降级消息
                        if (answer == null || answer.trim().isEmpty()) {
                            dev.langchain4j.data.message.AiMessage aiMsg = response != null ? response.content() : null;
                            log.warn("streamingNormalChat onComplete: fullAnswer 为空, AiMessage={}",
                                    aiMsg != null ? aiMsg.text() : "null");
                            answer = "抱歉，暂时无法回答，请稍后重试或联系前台。";
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(Map.of("type", "delta", "content", answer)));
                        } else {
                            log.debug("streamingNormalChat onComplete fullAnswer(前300字): {}",
                                    answer.length() > 300 ? answer.substring(0, 300) + "..." : answer);
                        }

                        String imageUrl = extractImageUrl(answer);
                        saveToMemory(memoryId, userMessage, answer, imageUrl);

                        emitter.send(SseEmitter.event()
                                .name("end")
                                .data(Map.of("type", "end")));
                        emitter.complete();
                        log.info("streamingNormalChat 完成: sessionId={}", sessionId);
                    } catch (IOException e) {
                        log.error("streamingNormalChat onComplete send failed", e);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    try {
                        log.error("streamingNormalChat 模型异常", error);
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("type", "error", "content", "模型响应异常：" + error.getMessage())));
                    } catch (IOException e) {
                        log.error("streamingNormalChat onError send failed", e);
                    }
                    emitter.completeWithError(error);
                }
            });
        } catch (Exception e) {
            log.error("streamingNormalChat 初始化失败", e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("type", "error", "content", "流式对话初始化失败：" + e.getMessage())));
            } catch (IOException ex) { }
            emitter.completeWithError(e);
        }
    }

    // ========== 获取或创建 Assistant（注入工具和记忆） ==========
    private Assistant getOrCreateAssistant(String sessionId, String memoryId) {
        return sessionAssistants.computeIfAbsent(sessionId, id -> {
            ChatLanguageModel model = chatLanguageModel;

            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .maxMessages(10)
                    .chatMemoryStore(memoryStore)
                    .id(memoryId)
                    .build();

            return AiServices.builder(Assistant.class)
                    .chatLanguageModel(model)
                    .tools(gymTools)
                    .chatMemory(chatMemory)
                    .systemMessageProvider(m -> SYSTEM_PROMPT)
                    .build();
        });
    }

    // ========== 保存消息到记忆（支持图片URL） ==========
    private void saveToMemory(String memoryId, String userMessage, String assistantReply, String imageUrl) {
        List<ChatMessage> existing = memoryStore.getMessages(memoryId);
        boolean lastIsSameUser = !existing.isEmpty() &&
                existing.get(existing.size() - 1) instanceof UserMessage && ((UserMessage) existing.get(existing.size() - 1)).singleText().equals(userMessage);
        if (!lastIsSameUser) {
            memoryStore.saveMessageRecord(memoryId, "user", userMessage, null);
            memoryStore.saveMessageRecord(memoryId, "assistant", assistantReply, imageUrl);
        }
    }

    // ========== 以下为所有原有辅助方法（完全保留） ==========

    private String generateImage(String prompt) {
        log.warn("图片生成已禁用：当前使用 Ollama 文本模型，不支持图片生成");
        return null;
    }

    private String generatePersonalizedPlan(String skeleton, String userMessage, Long memberId,
                                            String planType, String sessionId, String memoryId) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个专业的健身教练和营养师。\n");
            prompt.append("当前会员ID：").append(memberId).append("\n");
            prompt.append("用户提问：").append(userMessage).append("\n\n");
            prompt.append("以下是工具根据用户体测数据生成的计划骨架（JSON格式）：\n");
            prompt.append(skeleton).append("\n\n");
            if ("workout".equals(planType)) {
                prompt.append("请根据以上骨架，生成一份个性化的周训练计划。要求：\n");
                prompt.append("1. 用自然、亲切的语气，称呼用户为\"您\"\n");
                prompt.append("2. 保持骨架中的数据不变，但用更完整的句子表达\n");
                prompt.append("3. 添加一些实用建议（如热身注意事项、动作选择建议等）\n");
                prompt.append("4. 如果骨架中缺少某些数据，可以补充通用建议\n");
                prompt.append("5. 最终输出要清晰、有条理，适合用户直接阅读和执行\n");
            } else if ("meal".equals(planType)) {
                prompt.append("请根据以上骨架，生成一份个性化的周饮食计划。要求：\n");
                prompt.append("1. 用自然、亲切的语气，称呼用户为\"您\"\n");
                prompt.append("2. 保持骨架中的数据不变，但用更完整的句子表达\n");
                prompt.append("3. 解释每条建议背后的原理（如为什么需要高蛋白）\n");
                prompt.append("4. 如果需要热量计算，可以说明计算方法\n");
                prompt.append("5. 最终输出要清晰、有条理，适合用户直接参考和执行\n");
            }
            String ragContext = knowledgeBase.searchRelevant(userMessage);
            if (ragContext != null && !ragContext.isEmpty() &&
                    !ragContext.contains("未找到") && !ragContext.contains("失败") && !ragContext.contains("暂未加载")) {
                prompt.append("\n参考知识库信息：\n").append(ragContext).append("\n");
            }
            prompt.append("\n请直接输出润色后的计划内容，不要包含JSON格式，不要有开场白或结束语。");
            Assistant assistant = getOrCreateAssistant("planner_" + sessionId, memoryId + "_planner");
            String result = assistant.chat(prompt.toString());
            return (result != null && !result.isEmpty()) ? result : "生成计划失败，请稍后重试。";
        } catch (Exception e) {
            log.error("生成计划出错", e);
            return "计划生成出错：" + e.getMessage();
        }
    }


    // ====== 处理支付方式选择（返回带 **PAYMENT** 标记的引导语，或 null 表示继续） ======
    private String processPaymentChoice(String userMessage, PendingBooking pending, String pendingKey, String sessionId) {
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

    // 解析用户选中的课程包ID：优先使用用户选择的包（含未激活包，点击“点击激活”后服务层自动激活并扣减），否则取第一个可用包
    private Long resolvePackageId(PendingBooking pending) {
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

    private String handleBooking(String userMessage, Long memberId, String sessionId) {
        long t0 = System.currentTimeMillis();
        if (memberId == null || memberId <= 0) {
            return "预约失败：请先登录。";
        }
        Pattern trainerPattern = getTrainerNamePattern();
        Matcher trainerMatcher = trainerPattern.matcher(userMessage);
        String trainerName = null;
        if (trainerMatcher.find()) {
            trainerName = trainerMatcher.group(0);
        } else {
            return "预约失败：未识别到教练姓名，请明确指定教练（如：王教练、李教练等）。";
        }
        long t1 = System.currentTimeMillis();
        log.info("[预约耗时] 解析教练姓名: {}ms", t1 - t0);

        Trainer trainer = trainerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Trainer>()
                        .eq("name", trainerName)
        );
        long t2 = System.currentTimeMillis();
        log.info("[预约耗时] 查询教练信息: {}ms", t2 - t1);
        if (trainer == null) {
            return "预约失败：未找到名为 " + trainerName + " 的教练，请确认教练姓名是否正确。";
        }
        Long trainerId = trainer.getId();
        String dateStr = parseDate(userMessage);
        if (dateStr != null) {
            String pastMsg = validateDateNotPast(dateStr);
            if (pastMsg != null) {
                // 过期日期，但保存上下文（教练已知）以便用户重新选择
                String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
                contextManager.updateContext(pendingKey, toConversationContext(
                        memberId, sessionId,
                        new PendingBooking(memberId, trainerId, trainerName, false, false, null),
                        ConversationState.PT_BOOKING));
                log.info("[预约上下文] 日期过期，已保存教练上下文");
                return pastMsg;
            }
        }
        if (dateStr == null) {
            // 保存预约上下文，等待用户补充日期
            String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            contextManager.updateContext(pendingKey, toConversationContext(
                memberId, sessionId, new PendingBooking(memberId, trainerId, trainerName, false, false, null), ConversationState.PT_BOOKING));
            log.info("[预约上下文] 已保存待完成预约: 教练={}, 等待提供日期", trainerName);
            return "好的，已为您选择 " + trainerName + "。请问您想约哪一天？（例如：明天、6月30日）";
        }
        String timeStr = parseTime(userMessage);
        if (timeStr == null) {
            // 检查是否包含时间格式但解析失败（如非整点）
            boolean hasTimePattern = userMessage.matches(".*[0-9一两三四五六七八九十].*[:：点].*") ||
                    userMessage.matches(".*(上午|下午|晚上).*[0-9一两三四五六七八九十].*");
            if (hasTimePattern) {
                log.info("[预约耗时] 识别到时间格式但解析失败（可能是非整点），保存上下文");
                String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
                contextManager.updateContext(pendingKey, toConversationContext(
                        memberId, sessionId,
                        new PendingBooking(memberId, trainerId, trainerName, true, false, dateStr),
                        ConversationState.PT_BOOKING));
                return "预约时间只支持整点（如 13:00、14:00），请重新输入。";
            }
            // 保存预约上下文，等待用户补充时间
            String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            contextManager.updateContext(pendingKey, toConversationContext(
                memberId, sessionId, new PendingBooking(memberId, trainerId, trainerName, true, false, dateStr), ConversationState.PT_BOOKING));
            log.info("[预约上下文] 已保存待完成预约: 教练={}, 日期={}, 等待提供时间", trainerName, dateStr);
            return "好的，已为您选择 " + trainerName + "，请问您想约什么时间？（例如：下午2点、14:00）";
        }
        // 校验时间范围 09:00-21:00
        int hour = Integer.parseInt(timeStr.split(":")[0]);
        int min = Integer.parseInt(timeStr.split(":")[1]);
        if (hour < 9 || hour > 21 || (hour == 21 && min > 0)) {
            return "预约时间必须在上午9点到晚上9点之间，请重新选择。";
        }
        LocalDateTime appointmentTime;
        try {
            appointmentTime = LocalDateTime.parse(dateStr + " " + timeStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                appointmentTime = LocalDateTime.parse(dateStr + " " + timeStr + ":00", DATE_FORMATTER2);
            } catch (DateTimeParseException ex) {
                return "预约失败：日期或时间格式不正确，请使用类似【6月30日下午2点】或【2026-06-30 14:00】的格式。";
            }
        }
        // 校验预约时间是否已过
        if (appointmentTime.isBefore(LocalDateTime.now())) {
            return "预约时间已过，请选择未来的时间。";
        }
        long t3 = System.currentTimeMillis();
        log.info("[预约耗时] 解析日期时间: {}ms", t3 - t2);

        String conflictMsg = checkBookingConflict(memberId, appointmentTime);
        if (conflictMsg != null) {
            // 保留上下文，允许用户重新选择时间
            String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
            contextManager.updateContext(pendingKey, toConversationContext(
                memberId, sessionId, new PendingBooking(memberId, trainerId, trainerName, true, false, dateStr), ConversationState.PT_BOOKING));
            log.info("[预约上下文] 冲突提示后保留上下文，等待新时间");
            return conflictMsg + "\n\n请选择其他时间，例如【下午2点】或【14:00】";
        }
        // 保存预约上下文，进入支付选择
        String pendingKey = "booking_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        PendingBooking pending = new PendingBooking(memberId, trainerId, trainerName, true, true, dateStr);
        pending.timeStr = timeStr;
        contextManager.updateContext(pendingKey, toConversationContext(
            pending.memberId, sessionId, pending, ConversationState.PT_BOOKING));
        log.info("[预约流程] 预约信息完整，进入支付选择: 教练={}, 日期={}, 时间={}", trainerName, dateStr, timeStr);

        // 检查支付方式
        String payResult = processPaymentChoice(userMessage, pending, pendingKey, sessionId);
        if (payResult != null) {
            if (payResult.equals("__EXIT__")) {
                contextManager.removeContext(pendingKey);
                log.info("[预约流程] 用户取消预约");
                return "好的，已取消预约。请问还有其他问题吗？";
            }
            log.info("[预约流程] 需要用户选择支付方式");
            return payResult;
        }

        // 用户已选择支付方式，执行预约
        log.info("[预约流程] 支付方式已选择: {}, 执行预约", pending.paymentMethod);
        contextManager.removeContext(pendingKey);

        boolean useFree = "free".equals(pending.paymentMethod);
        Long pkgId = null;
        if ("package".equals(pending.paymentMethod)) {
            pkgId = resolvePackageId(pending);
        }

        log.info("[预约执行] 打算执行私教预约：memberId={}, trainerId={}, time={}, paymentMethod={}, useFree={}, pkgId={}", memberId, trainerId, appointmentTime, null, useFree, pkgId);
        String result = ptService.bookPersonalTraining(memberId, trainerId, appointmentTime, 60, pkgId, useFree);
        long t4 = System.currentTimeMillis();
        log.info("[预约耗时] 执行预约(DB): {}ms", t4 - t3);
        log.info("[预约耗时] 总耗时: {}ms", t4 - t0);

        if (result.startsWith("私教预约成功")) {
            String label = "单次付费";
            if (useFree) label = "免费私教课";
            else if (pkgId != null) label = "课程包扣费";
            // 优先返回实际结果（含课程/时间/原价/实付明细），不足时再补支付方式
            if (result.contains("原价") || result.contains("时间")) {
                return result + "\n支付方式：" + label;
            }
            return "预约成功！已为您预约 " + trainerName + " 的课程，时间：" +
                    appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n支付方式：" + label;
        } else {
            return "❌ " + result;
        }
        }

    private String parseDate(String userMessage) {
        if (userMessage.contains("明天")) {
            return LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (userMessage.contains("后天")) {
            return LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (userMessage.contains("今天")) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        // 支持"X年X月X日"格式（如"2026年7月12日"）
        java.util.regex.Matcher yearMatcher = java.util.regex.Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})(日|号)").matcher(userMessage);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            int month = Integer.parseInt(yearMatcher.group(2));
            int day = Integer.parseInt(yearMatcher.group(3));
            return String.format("%d-%02d-%02d", year, month, day);
        }
        // 先尝试完整的"X月X号/日"格式
        Matcher dateMatcher = DATE_PATTERN_CN.matcher(userMessage);
        if (dateMatcher.find()) {
            int month = Integer.parseInt(dateMatcher.group(1));
            int day = Integer.parseInt(dateMatcher.group(2));
            int currentYear = LocalDateTime.now().getYear();
            int currentMonth = LocalDateTime.now().getMonthValue();
            int year = currentYear;
            return String.format("%d-%02d-%02d", year, month, day);
        }
        // 支持"号"格式（如"28号"，默认当前年月）
        java.util.regex.Matcher dayOnlyMatcher = DAY_ONLY_PATTERN.matcher(userMessage);
        if (dayOnlyMatcher.find()) {
            int day = Integer.parseInt(dayOnlyMatcher.group(1));
            java.time.LocalDate now = java.time.LocalDate.now();
            int year = now.getYear();
            int month = now.getMonthValue();
            // 如果传入的日期小于今天，推至下月
            if (day < now.getDayOfMonth()) {
                month++;
                if (month > 12) { month = 1; year++; }
            }
            return String.format("%d-%02d-%02d", year, month, day);
        }
        Matcher stdMatcher = STD_DATE.matcher(userMessage);
        if (stdMatcher.find()) {
            return stdMatcher.group(0);
        }
        return null;
    }

    private String parseTime(String userMessage) {
        // 先移除日期信息，避免干扰时间解析
        String timeInput = userMessage.replaceAll("明天|后天|今天", "").trim();
        timeInput = timeInput.replaceAll("\\d{1,2}月\\d{1,2}(日|号)", "").trim();
        timeInput = timeInput.replaceAll("\\d{4}年\\d{1,2}月\\d{1,2}日", "").trim();
        timeInput = timeInput.replaceAll("\\d{4}-\\d{2}-\\d{2}", "").trim();
        
        // 尝试标准格式: "14:00", "14:00:00" （优先于中文格式，避免数字冲突）
        Matcher stdMatcher = STD_TIME.matcher(timeInput);
        if (stdMatcher.find()) {
            int hour = Integer.parseInt(stdMatcher.group(1));
            int minute = Integer.parseInt(stdMatcher.group(2));
            if (minute != 0) {
                log.info("[parseTime] 拒绝非整点时间: hour={}, minute={}", hour, minute);
                return null;
            }
            // 校验小时范围
            if (hour >= 0 && hour <= 23) {
                return String.format("%02d:%02d", hour, minute);
            }
        }
        
        // 尝试阿拉伯数字格式: "2点", "下午2点", "2点30分"
        Matcher timeMatcher = TIME_PATTERN_CN.matcher(timeInput);
        if (timeMatcher.find()) {
            String period = timeMatcher.group(1);
            int hour = Integer.parseInt(timeMatcher.group(2));
            int minute = 0;
            if (timeMatcher.group(3) != null && !timeMatcher.group(3).isEmpty()) {
                minute = Integer.parseInt(timeMatcher.group(3));
            }
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            }
            return String.format("%02d:%02d", hour, minute);
        }
        // 尝试中文数字格式: "三点", "三点半", "下午三点", "下午三点半"
        // 匹配: (上午|下午|晚上)?(一|二|两|三|四|五|六|七|八|九|十|十一|十二)点(半|(一|二|三|四|五|六|七|八|九|十)(分)?)?
        String chineseOrdinal = "(一|二|两|三|四|五|六|七|八|九|十|十一|十二)";
        String chineseMinute = "(半|(一|二|三|四|五|六|七|八|九|十|十一|十二|十三|十四|十五|十六|十七|十八|十九|二十|二十一|二十二|二十三|二十四|二十五|二十六|二十七|二十八|二十九|三十|三十一|三十二|三十三|三十四|三十五|三十六|三十七|三十八|三十九|四十|四十一|四十二|四十三|四十四|四十五|四十六|四十七|四十八|四十九|五十|五十一|五十二|五十三|五十四|五十五|五十六|五十七|五十八|五十九))";
        Pattern chineseTimePattern = Pattern.compile("(上午|下午|晚上)?" + chineseOrdinal + "点(" + chineseMinute + "分?)?");
        Matcher chineseMatcher = chineseTimePattern.matcher(userMessage);
        if (chineseMatcher.find()) {
            String period = chineseMatcher.group(1);
            String hourCn = chineseMatcher.group(2);
            String minuteCn = chineseMatcher.group(3); // could be "半" or a number
            int hour = chineseToDigit(hourCn);
            int minute = 0;
            if (minuteCn != null && !minuteCn.isEmpty()) {
                if ("半".equals(minuteCn)) {
                    minute = 30;
                } else {
                    minute = chineseToDigit(minuteCn);
                }
            }
            // 拒绝非整点时间（如三点半、15:30）
            if (minute != 0) {
                log.info("[parseTime] 拒绝非整点时间: hour={}, minute={}", hour, minute);
                return null;
            }
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            }
            return String.format("%02d:%02d", hour, minute);
        }
        return null;
    }

    // ====== 中文数字 → 阿拉伯数字 ======
    private int chineseToDigit(String cn) {
        switch (cn) {
            case "一": return 1;
            case "二":
            case "两": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
            case "十一": return 11;
            case "十二": return 12;
            case "十三": return 13;
            case "十四": return 14;
            case "十五": return 15;
            case "十六": return 16;
            case "十七": return 17;
            case "十八": return 18;
            case "十九": return 19;
            case "二十": return 20;
            case "二十一": return 21;
            case "二十二": return 22;
            case "二十三": return 23;
            case "二十四": return 24;
            case "二十五": return 25;
            case "二十六": return 26;
            case "二十七": return 27;
            case "二十八": return 28;
            case "二十九": return 29;
            case "三十": return 30;
            case "三十一": return 31;
            case "三十二": return 32;
            case "三十三": return 33;
            case "三十四": return 34;
            case "三十五": return 35;
            case "三十六": return 36;
            case "三十七": return 37;
            case "三十八": return 38;
            case "三十九": return 39;
            case "四十": return 40;
            case "四十一": return 41;
            case "四十二": return 42;
            case "四十三": return 43;
            case "四十四": return 44;
            case "四十五": return 45;
            case "四十六": return 46;
            case "四十七": return 47;
            case "四十八": return 48;
            case "四十九": return 49;
            case "五十": return 50;
            case "五十一": return 51;
            case "五十二": return 52;
            case "五十三": return 53;
            case "五十四": return 54;
            case "五十五": return 55;
            case "五十六": return 56;
            case "五十七": return 57;
            case "五十八": return 58;
            case "五十九": return 59;
            default: return 0;
        }
    }

    private Pattern getTrainerNamePattern() {
        long now = System.currentTimeMillis();
        if (trainerNamePattern == null || (now - trainerCacheTime) > CACHE_TTL) {
            List<Trainer> trainers = trainerMapper.selectList(null);
            String names = trainers.stream().map(Trainer::getName).collect(Collectors.joining("|"));
            if (names.isEmpty()) {
                names = "王教练|李教练|张教练|刘教练|小垚教练";
            }
            trainerNamePattern = Pattern.compile("(" + names + ")");
            trainerCacheTime = now;
            log.debug("更新教练名称正则: {}", names);
        }
        return trainerNamePattern;
    }

    private boolean isWorkoutPlanRequest(String lowerMsg) {
        return lowerMsg.contains("排课") || lowerMsg.contains("训练计划") ||
                (lowerMsg.contains("一周") && (lowerMsg.contains("训练") || lowerMsg.contains("减脂"))) ||
                (lowerMsg.contains("排课表") && lowerMsg.contains("减脂"));
    }

    private boolean isMealPlanRequest(String lowerMsg) {
        return lowerMsg.contains("食谱") || lowerMsg.contains("饮食") || lowerMsg.contains("吃的") ||
                (lowerMsg.contains("一周") && (lowerMsg.contains("吃") || lowerMsg.contains("食谱")));
    }

    /** 系统功能开关：'1'/'true'/'on' 视为开启；未配置时默认开启 */
    private boolean isConfigEnabled(String key) {
        Map<String, String> cfg = systemConfigService.getAll();
        String v = cfg.get(key);
        if (v == null || v.isEmpty()) return true;
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on");
    }

    private String handleQueryClasses(String userMessage, Long memberId) {
        String targetDateStr = parseDate(userMessage);
        LocalDateTime start, end;
        String typeFilter = null;
        Boolean allowVisitorFilter = null;

        // 检测是否查询体验课（访客可见的课程）
        if (userMessage.contains("体验课") || userMessage.contains("体验")) {
            allowVisitorFilter = true;
            log.info("[团课查询] 筛选体验课 allow_visitor=1");
        }

        // 检测是否查询免费/公益团课
        if (userMessage.contains("免费") || userMessage.contains("公益")) {
            typeFilter = "free";
            log.info("[团课查询] 筛选类型: free");
        }

        if (targetDateStr != null) {
            LocalDate targetDate = LocalDate.parse(targetDateStr);
            start = targetDate.atStartOfDay();
            end = targetDate.atTime(23, 59, 59);
        } else {
            start = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0);
            end = start.plusDays(7).withHour(22).withMinute(0).withSecond(0);
        }

        String startStr = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String result = gymTools.queryAvailableClasses(startStr, endStr, typeFilter, allowVisitorFilter).getMessage();
        // 存储本次查询结果到缓存（用于代词解析）
        String cacheKey = "lastGC_" + (memberId != null ? memberId : "guest");
        try {
            // 根据同样的条件从数据库查询课程列表
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.GroupClass> qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            qw.ge(com.gym.entity.GroupClass::getStartTime, start)
               .le(com.gym.entity.GroupClass::getStartTime, end)
               .eq(com.gym.entity.GroupClass::getStatus, "scheduled")
               .orderByAsc(com.gym.entity.GroupClass::getStartTime);
            if (typeFilter != null) {
                qw.eq(com.gym.entity.GroupClass::getType, typeFilter);
            }
            if (allowVisitorFilter != null && allowVisitorFilter) {
                qw.eq(com.gym.entity.GroupClass::getAllowVisitor, 1);
            }
            java.util.List<com.gym.entity.GroupClass> cachedList = groupClassMapper.selectList(qw);
            setCachedGroupClassList(cacheKey, cachedList);
            // 如果是体验课查询，结果中标注"体验课"
            if (allowVisitorFilter != null && allowVisitorFilter && !result.contains("体验课")) {
                result = result.replace("可预约团课", "可预约体验课（访客可约）");
                result = result.replace("没有可预约的团课", "没有可预约的体验课（访客可约）");
            }
        } catch (Exception e) {
            log.warn("存储团课列表缓存失败", e);
        }




        Pattern countPattern = Pattern.compile("推荐\\s*(\\d+)\\s*个");
        Matcher countMatcher = countPattern.matcher(userMessage);
        if (countMatcher.find()) {
            int limit = Integer.parseInt(countMatcher.group(1));
            String[] lines = result.split("\n");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                sb.append(line).append("\n");
                if (line.trim().startsWith("课程：")) {
                    count++;
                    if (count >= limit) break;
                }
            }
            return sb.toString();
        }

        return result;
    }

    public interface Assistant {
        String chat(String userMessage);
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

    // ========== 构建用户上下文 ==========
    private String buildUserContext(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return "用户未登录。";
        }

        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            return "用户信息不存在。";
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(23, 59, 59);
        LambdaQueryWrapper<PersonalTraining> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.eq(PersonalTraining::getMemberId, memberId)
                .eq(PersonalTraining::getStatus, "scheduled")
                .between(PersonalTraining::getAppointmentTime, todayStart, todayEnd);
        long todayPT = personalTrainingMapper.selectCount(ptWrapper);

        LambdaQueryWrapper<MemberPrivatePackage> pkgWrapper = new LambdaQueryWrapper<>();
        pkgWrapper.eq(MemberPrivatePackage::getMemberId, memberId)
                .ne(MemberPrivatePackage::getStatus, "refunded")
                .isNotNull(MemberPrivatePackage::getStartDate)
                .gt(MemberPrivatePackage::getRemainingSessions, 0)
                .and(w -> w.isNull(MemberPrivatePackage::getEndDate)
                        .or()
                        .ge(MemberPrivatePackage::getEndDate, LocalDate.now())
                );
        List<MemberPrivatePackage> pkgs = memberPrivatePackageMapper.selectList(pkgWrapper);
        int totalRemaining = pkgs.stream().mapToInt(MemberPrivatePackage::getRemainingSessions).sum();

        int used = member.getFreePtUsedMonth() != null ? member.getFreePtUsedMonth() : 0;
        MemberLevel level = MemberLevel.fromDisplayName(member.getLevel() != null ? member.getLevel() : "普通会员");
        int freeTotal = level.getFreePersonalTrainingsPerMonth();
        int freeRemaining = Math.max(0, freeTotal - used);

        String expireInfo = "";
        if (member.getExpireDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), member.getExpireDate());
            if (days < 0) expireInfo = "⚠️ 已过期 " + Math.abs(days) + " 天";
            else if (days < 7) expireInfo = "⏰ 即将到期，剩余 " + days + " 天";
            else expireInfo = "✅ 有效（剩余 " + days + " 天）";
        }

        return String.format(
                "【当前用户实时上下文】\n" +
                        "- 会员ID：%d\n" +
                        "- 姓名：%s\n" +
                        "- 等级：%s\n" +
                        "- 会员状态：%s\n" +
                        "- 今日待上课：%d 节\n" +
                        "- 剩余课程包课时：%d 节\n" +
                        "- 本月免费私教剩余：%d 次\n" +
                        "- 身高：%s cm\n" +
                        "- 体重：%s kg\n" +
                        "\n请基于以上信息，为用户提供个性化的服务。当用户询问日程、体测、剩余课时等时，优先使用以上数据回答。" +
                        "如果用户只是打招呼（如你好、嗨），请热情回应并简要介绍您能提供的帮助。" +
                        "如果用户问的问题超出以上数据范围，请礼貌地引导用户提供更多信息或联系前台。",
                memberId,
                nullToEmpty(member.getName()),
                nullToEmpty(member.getLevel()),
                expireInfo,
                todayPT,
                totalRemaining,
                freeRemaining,
                member.getHeight() != null ? member.getHeight() : "未设置",
                member.getWeight() != null ? member.getWeight() : "未设置"
        );
    }

    
    // ========== 构建动态上下文（教练信息、课程介绍等） ==========
    private String buildDynamicContext(String userMessage) {
        String lower = userMessage.toLowerCase();
        StringBuilder sb = new StringBuilder();
        String lowerMsg = userMessage.toLowerCase();

        // 用户问到教练信息时，注入教练列表
        if (lowerMsg.contains("教练")) {
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Trainer> tw = 
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                tw.eq(Trainer::getStatus, "active");
                tw.last("LIMIT 10");
                java.util.List<Trainer> trainers = trainerMapper.selectList(tw);
                if (trainers != null && !trainers.isEmpty()) {
                    sb.append("【当前在职教练】\n");
                    for (Trainer t : trainers) {
                        sb.append("- ").append(nullToEmpty(t.getName()))
                          .append("，专长：").append(nullToEmpty(t.getSpecialty()))
                          .append("，价格：").append(t.getPricePerHour() != null ? t.getPricePerHour() : "待定").append(" 元/小时\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("查询教练动态数据失败", e);
            }
        }

        // 用户问到课程时，注入团课信息
        if (lowerMsg.contains("课程") || lowerMsg.contains("团课") || lowerMsg.contains("课表")) {
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GroupClass> gw = 
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                gw.eq(GroupClass::getStatus, "scheduled")
                   .gt(GroupClass::getStartTime, java.time.LocalDateTime.now())
                   .orderByAsc(GroupClass::getStartTime)
                   .last("LIMIT 5");
                java.util.List<GroupClass> classes = groupClassMapper.selectList(gw);
                if (classes != null && !classes.isEmpty()) {
                    sb.append("【近期可预约团课】\n");
                    for (GroupClass gc : classes) {
                        sb.append("- ").append(nullToEmpty(gc.getName()))
                          .append(" | ").append(gc.getStartTime() != null ? gc.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "")
                          .append(" | 余位 ").append(gc.getMaxCapacity() - gc.getEnrolled()).append(" 人\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("查询团课动态数据失败", e);
            }
        }

        return sb.toString();
    }

private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }
    // ====== 判断是否与健身相关，跳过 RAG ======
    private boolean isFitnessRelated(String message) {
        if (message == null || message.trim().isEmpty()) return true;
        String lower = message.toLowerCase();
        String[] keywords = {
            "健身", "运动", "课程", "团课", "私教", "教练", "体测", "预约",
            "深蹲", "卧推", "硬拉", "跑步", "有氧", "力量", "减脂", "增肌",
            "瑜伽", "普拉提", "搏击", "动感单车", "杠铃", "饮食", "营养",
            "蛋白", "卡路里", "热量", "训练", "拉伸", "热身", "肌肉",
            "会员", "课时", "套餐", "积分", "签到", "打卡", "器械",
            "你好", "嗨", "hello", "hi", "在吗", "help"
        };
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }


    private String extractTrainerName(String userMessage) {
        Pattern p = Pattern.compile("([\\u4e00-\\u9fa5]{2,})教练");
        Matcher m = p.matcher(userMessage);
        if (m.find()) {
            return m.group(1) + "教练";
        }
        Pattern trainerP = getTrainerNamePattern();
        Matcher tm = trainerP.matcher(userMessage);
        if (tm.find()) {
            return tm.group(1);
        }
        return null;
    }

    // ====== 团课支付准备（返回支付确认信息，不直接预约） ======
    /**
     * 访客团课预约判定：
     * - 返回 null：非访客（会员/游客），由调用方继续走"确认支付"流程
     * - 返回字符串：访客处理结果（直接预约成功/开关关闭/付费团课不可约/体验次数已用），不再走支付流程
     */
    private String resolveVisitorGroupBooking(Long memberId, GroupClass gc) {
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

    private String prepareGroupPayment(GroupClass gc, Long memberId, String sessionId) {
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
            PendingBooking pp = new PendingBooking(memberId, gc.getName(), gc.getId(), true, true, 
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


    /** 清洗预约类输入，提取课程名关键词（去除"我要约/帮我约/想约"等动词与语气词） */
    private String cleanGroupCourseInput(String userMessage) {
        if (userMessage == null) return null;
        String s = userMessage.trim().replaceAll("[，。！？!?、,.\\s]+", "");
        String[] prefixes = {"我要预约", "帮我预约", "麻烦预约", "请帮我预约", "我想要预约", "我想预约",
                "我要约", "帮我约", "麻烦约", "请帮我约", "我想约", "想预约", "想约",
                "要约", "约一下", "预约一下", "帮我订", "我要订", "想订", "请约",
                "帮我报名", "我要报名", "报名", "预约", "约"};
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length());
                break;
            }
        }
        s = s.replaceAll("^(我要|帮我|我想|麻烦|请|给我|我想要|预约|约|一下)", "");
        s = s.replaceAll("(团课|一节课|一节|课程|课|一下|吧|呢|哦|谢谢|多谢|吗|的)$", "");
        return s.trim();
    }

    private String handleBookGroupClass(String userMessage, Long memberId, String sessionId) {
        // 清除该会员之前的团课上下文，避免残留影响
        String _ctxKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        contextManager.removeContext(_ctxKey);
        String _cacheKey = "lastGC_" + (memberId != null ? memberId : "guest");
        clearCachedGroupClassList(_cacheKey);
        String[] keywords = {"动感单车", "瑜伽", "搏击", "杠铃", "普拉提", "有氧", "力量", "单车", "操课", "舞蹈", "尊巴", "HIIT"};
        String lowerMsgForKW = userMessage.toLowerCase();
        String courseKeyword = null;
        for (String kw : keywords) {
            if (lowerMsgForKW.contains(kw.toLowerCase())) {
                courseKeyword = kw;
                break;
            }
        }
        log.info("[handleBookGroupClass] courseKeyword={}, userMessage={}", courseKeyword, userMessage);
        if (courseKeyword == null) {
            // DB查询：从消息中提取课程名（匹配数据库中的课程名称）
            String lowerMsg = userMessage.toLowerCase();
            java.util.List<com.gym.entity.GroupClass> allScheduled = groupClassMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.GroupClass>()
                    .eq(com.gym.entity.GroupClass::getStatus, "scheduled")
                    .gt(com.gym.entity.GroupClass::getStartTime, java.time.LocalDateTime.now())
            );
            if (allScheduled != null) {
                for (com.gym.entity.GroupClass gc : allScheduled) {
                    if (gc.getName() != null && lowerMsg.contains(gc.getName().toLowerCase())) {
                        courseKeyword = gc.getName();
                        break;
                    }
                }
                // 如果还没匹配到，尝试匹配课程名的前2-4个字
                if (courseKeyword == null) {
                    for (com.gym.entity.GroupClass gc : allScheduled) {
                        if (gc.getName() != null && gc.getName().length() >= 2) {
                            String shortName = gc.getName().substring(0, Math.min(gc.getName().length(), 4));
                            if (lowerMsg.contains(shortName.toLowerCase())) {
                                courseKeyword = shortName;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (courseKeyword == null) {
            // 第二步：关键词匹配失败时，直接用用户输入查库（LIKE '%输入%'），支持任意课程名
            String cleanedInput = cleanGroupCourseInput(userMessage);
            log.info("[handleBookGroupClass] 关键词未匹配，尝试直接按用户输入查库: {}", cleanedInput);
            if (cleanedInput != null && !cleanedInput.isEmpty()
                    && !cleanedInput.contains("什么") && !cleanedInput.contains("哪些")
                    && !cleanedInput.contains("有没有") && !cleanedInput.contains("怎么")
                    && !cleanedInput.contains("吗") && !cleanedInput.contains("哪")
                    && !cleanedInput.contains("今天") && !cleanedInput.contains("明天")
                    && !cleanedInput.contains("周") && !cleanedInput.contains("星期")) {
                java.util.List<com.gym.entity.GroupClass> directHits = groupClassMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.GroupClass>()
                        .eq(com.gym.entity.GroupClass::getStatus, "scheduled")
                        .gt(com.gym.entity.GroupClass::getStartTime, java.time.LocalDateTime.now())
                        .like(com.gym.entity.GroupClass::getName, cleanedInput)
                        .last("LIMIT 1"));
                if (directHits != null && !directHits.isEmpty()) {
                    courseKeyword = directHits.get(0).getName();
                }
            }
            if (courseKeyword == null) {
                if (cleanedInput == null || cleanedInput.isEmpty()) {
                    return "请告诉我您想预约哪门团课，例如「帮我预约动感单车」";
                }
                return "未找到与「" + cleanedInput + "」相关的可预约课程，请确认课程名称或稍后再试。";
            }
        }

        // 尝试从用户输入中提取日期/时间，用于过滤课程
        String targetDateStr = parseDate(userMessage);
        String targetTimeStr = parseTime(userMessage);

        // 查询所有匹配该课程名的未来排期
        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(GroupClass::getName, courseKeyword)
                .eq(GroupClass::getStatus, "scheduled")
                .gt(GroupClass::getStartTime, LocalDateTime.now())
                .orderByAsc(GroupClass::getStartTime);

        // 如果用户指定了日期，按日期过滤
        if (targetDateStr != null) {
            try {
                java.time.LocalDate td = java.time.LocalDate.parse(targetDateStr);
                wrapper.between(GroupClass::getStartTime, td.atStartOfDay(), td.atTime(23, 59, 59));
            } catch (Exception e) {
                log.warn("日期解析失败: {}", targetDateStr);
            }
        }
        // 如果用户指定了时间，也按时间过滤（可选增强）

        List<GroupClass> classes = groupClassMapper.selectList(wrapper);
        log.info("[handleBookGroupClass] {} 查询到 {} 节课程", courseKeyword, classes != null ? classes.size() : 0);

        if (classes == null || classes.isEmpty()) {
            // 尝试去掉"课"字再查一次
            if (courseKeyword.endsWith("课") && courseKeyword.length() > 1) {
                String withoutKe = courseKeyword.substring(0, courseKeyword.length() - 1);
                wrapper = new LambdaQueryWrapper<>();
                wrapper.like(GroupClass::getName, withoutKe)
                        .eq(GroupClass::getStatus, "scheduled")
                        .gt(GroupClass::getStartTime, LocalDateTime.now())
                        .orderByAsc(GroupClass::getStartTime);
                if (targetDateStr != null) {
                    try {
                        java.time.LocalDate td = java.time.LocalDate.parse(targetDateStr);
                        wrapper.between(GroupClass::getStartTime, td.atStartOfDay(), td.atTime(23, 59, 59));
                    } catch (Exception e) {}
                }
                classes = groupClassMapper.selectList(wrapper);
            }
            if (classes == null || classes.isEmpty()) {
                return "未找到与「" + courseKeyword + "」相关的可预约课程，请确认课程名称或稍后再试。";
            }
        }

        // 只有一节 → 检查类型，决定是否弹支付
        // 存储到代词解析缓存
        String cacheKeyGC2 = "lastGC_" + (memberId != null ? memberId : "guest");
        setCachedGroupClassList(cacheKeyGC2, classes);
        

        if (classes.size() == 1) {
            GroupClass gc = classes.get(0);
            // 先处理访客：体验课（公益/付费均可）直接预约；非体验课拒绝
            String visitorResult = resolveVisitorGroupBooking(memberId, gc);
            if (visitorResult != null) return visitorResult;
            // 会员 + 付费课 → 确认支付流程
            if ("paid".equals(gc.getType()) && gc.getPrice() != null && gc.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                return prepareGroupPayment(gc, memberId, sessionId);
            }
            // 会员 + 公益课 → 直接预约，不创建支付上下文、不生成确认引导语
            return groupClassService.bookClass(memberId, gc.getId());
        }


        // 有多节 → 列出所有排期，让用户选择
        String userType = "member";
        if (memberId != null && memberId > 0) {
            Member m = memberMapper.selectById(memberId);
            if (m != null && m.isVisitor()) userType = "visitor";
        }

        // 保存课程列表到上下文
        String groupKey = "group_" + (memberId != null ? memberId : "guest") + "_" + sessionId;
        PendingBooking gp = new PendingBooking(memberId, courseKeyword, null, false, false, null, userType);
        gp.courseName = courseKeyword;
        gp.groupClassId = null;  // 用户还未选择具体课程
        gp.retryCount = 0;
        // 将课程列表序列化为简单的字符串（id|name|startTime 格式）
        StringBuilder sb = new StringBuilder();
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
        sb.append("找到 ").append(classes.size()).append(" 节").append(courseKeyword).append("课程：\n");
        // 批量查询教练姓名
        java.util.Map<Long, String> trainerNameMap = new java.util.HashMap<>();
        try {
            for (GroupClass gc2 : classes) {
                if (gc2.getTrainerId() != null) {
                    com.gym.entity.Trainer t = trainerMapper.selectById(gc2.getTrainerId());
                    if (t != null) {
                        trainerNameMap.put(gc2.getTrainerId(), t.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询教练信息失败", e);
        }
        java.util.List<String> classOptions = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter dtf2 = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
        for (int i = 0; i < classes.size(); i++) {
            GroupClass gc = classes.get(i);
            String timeStr2 = gc.getStartTime() != null ? gc.getStartTime().format(dtf2) : "待定";
            int remaining = (gc.getMaxCapacity() != null ? gc.getMaxCapacity() : 0) - (gc.getEnrolled() != null ? gc.getEnrolled() : 0);
            String trainerName = (gc.getTrainerId() != null && trainerNameMap.containsKey(gc.getTrainerId()))
                    ? trainerNameMap.get(gc.getTrainerId()) : "";
            // 计算价格显示
            String priceStr2 = (gc.getPrice() != null && gc.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0)
                ? ("￥" + gc.getPrice().toString()) : "免费";
            // 计算剩余名额显示
            int maxCap2 = gc.getMaxCapacity() != null ? gc.getMaxCapacity() : 0;
            int enrolled2 = gc.getEnrolled() != null ? gc.getEnrolled() : 0;
            int remaining2 = maxCap2 - enrolled2;
            String capStr;
            if (remaining2 > 0) {
                capStr = "剩余 " + remaining2 + " 人";
            } else if (remaining2 == 0) {
                capStr = "已满";
            } else {
                capStr = "已满（超额 " + (-remaining2) + " 人）";
            }
            sb.append(i + 1).append(". ").append(gc.getName() != null ? gc.getName() : courseKeyword)
              .append(" - ").append(trainerName)
              .append(" - ").append(timeStr2)
              .append(" - ").append(priceStr2)
              .append("（").append(capStr).append("）\n");
            classOptions.add(gc.getId() + "|" + gc.getName() + "|" + (gc.getStartTime() != null ? gc.getStartTime().toString() : "") + "|" + trainerName);
        }
        gp.dateStr = String.join(",", classOptions);  // 偷个懒：用 dateStr 存课程列表字符串
        contextManager.updateContext(groupKey, toConversationContext(
            memberId, sessionId, gp, ConversationState.GROUP_BOOKING));
        log.info("[团课预约] 保存课程列表: {} 节可供选择", classes.size());
        // 存储到代词解析缓存
        String cacheKeyGC = "lastGC_" + (memberId != null ? memberId : "guest");
        setCachedGroupClassList(cacheKeyGC, classes);
        return sb.toString() + "\n请回复序号选择（如回复 1、2）。";
    }


    // ====== 校验日期是否过期 ======
    // ====== 团课列表缓存清理（过期5分钟）======
    private java.util.List<com.gym.entity.GroupClass> getCachedGroupClassList(String sessionKey) {
        Long time = lastGroupClassListTime.get(sessionKey);
        if (time == null || (System.currentTimeMillis() - time) > 300000L) {
            lastGroupClassListCache.remove(sessionKey);
            lastGroupClassListTime.remove(sessionKey);
            return null;
        }
        return lastGroupClassListCache.get(sessionKey);
    }
    
    private void setCachedGroupClassList(String sessionKey, java.util.List<com.gym.entity.GroupClass> list) {
        if (list == null || list.isEmpty()) {
            lastGroupClassListCache.remove(sessionKey);
            lastGroupClassListTime.remove(sessionKey);
            return;
        }
        lastGroupClassListCache.put(sessionKey, list);
        lastGroupClassListTime.put(sessionKey, System.currentTimeMillis());
    }
    
    private void clearCachedGroupClassList(String sessionKey) {
        lastGroupClassListCache.remove(sessionKey);
        lastGroupClassListTime.remove(sessionKey);
    }

    private String validateDateNotPast(String dateStr) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            java.time.LocalDate today = java.time.LocalDate.now();
            if (date.isBefore(today)) {
                return "该日期已过期（" + dateStr + "），请选择未来的日期。";
            }
        } catch (Exception e) {
            log.warn("校验日期失败: {}", dateStr);
        }
        return null;
    }

    // ====== 检查私教预约冲突 ======
    private String checkBookingConflict(Long memberId, LocalDateTime appointmentTime) {
        try {
            LocalDateTime endTime = appointmentTime.plusMinutes(60);
            Long count = personalTrainingMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gym.entity.PersonalTraining>()
                    .eq(com.gym.entity.PersonalTraining::getMemberId, memberId)
                    .eq(com.gym.entity.PersonalTraining::getStatus, "scheduled")
                    .between(com.gym.entity.PersonalTraining::getAppointmentTime, appointmentTime, endTime)
            );
            if (count != null && count > 0) {
                return "您在该时段（" + appointmentTime.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) + "）已有私教预约，请选择其他时间。";
            }
        } catch (Exception e) {
            log.warn("检查预约冲突异常", e);
        }
        return null;
    }

    // ====== 待完成预约上下文（用于多轮对话） ======
    private static class PendingBooking {
        Long memberId;
        Long trainerId;
        String trainerName;
        String courseName;
        Long groupClassId;
        String intentType;  // "PT" = 私教, "GROUP" = 团课
        String userType;    // "member" / "visitor"
        boolean hasDate;
        boolean hasTime;
        String dateStr;
        String timeStr;
        int retryCount;
        String paymentMethod;
        Long packageId;               // 用户选择的课程包ID（未激活包点击激活后使用）
        Map<Integer, Long> paymentPkgMap;  // 支付选项序号 → 课程包ID
        int singlePayOptionNo;        // 单次付费选项的序号

        PendingBooking(Long memberId, Long trainerId, String trainerName, boolean hasDate, boolean hasTime, String dateStr) {
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

        PendingBooking(Long memberId, String courseName, Long groupClassId, boolean hasDate, boolean hasTime, String dateStr, String userType) {
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
