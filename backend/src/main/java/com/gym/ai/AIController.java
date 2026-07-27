package com.gym.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.ai.memory.MessageRecord;
import com.gym.ai.memory.MongoChatMemoryStore;
import com.gym.ai.rag.KnowledgeBaseService;
import com.gym.ai.tool.GymTools;
import com.gym.entity.*;
import com.gym.enums.MemberLevel;
import com.gym.mapper.*;
import com.gym.service.PersonalTrainingService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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
    private MongoChatMemoryStore memoryStore;

    @Autowired
    private KnowledgeBaseService knowledgeBase;

    @Autowired
    private GymTools gymTools;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PersonalTrainingService ptService;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private HttpSession session;

    private final Map<String, Assistant> sessionAssistants = new ConcurrentHashMap<>();

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_PATTERN_CN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern TIME_PATTERN_CN = Pattern.compile("(上午|下午|晚上)?(\\d{1,2})点(\\d{0,2})分?");
    private static final Pattern STD_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern STD_TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

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
        log.info("流式请求开始: sessionId={}, message={}", sessionId, message);

        executor.execute(() -> {
            try {
                String answer = handleIntent(message, finalMemberId, sessionId, memoryId);
                // 提取图片 URL
                String imageUrl = extractImageUrl(answer);
                saveToMemory(memoryId, message, answer, imageUrl);

                for (int i = 0; i < answer.length(); i++) {
                    String token = answer.substring(i, i + 1);
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "token");
                    event.put("content", token);
                    event.put("full", answer.substring(0, i + 1));
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(event))
                            .name("message"));
                    Thread.sleep(20);
                }

                Map<String, Object> completeEvent = new HashMap<>();
                completeEvent.put("type", "complete");
                completeEvent.put("full", answer);
                emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(completeEvent))
                        .name("complete"));
                emitter.complete();
                log.info("流式请求结束: sessionId={}, 耗时={}ms",
                        sessionId, System.currentTimeMillis() - streamStartTime);

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

    // ========== 核心意图处理 ==========
    private String handleIntent(String userMessage, Long memberId, String sessionId, String memoryId) {
        String lowerMsg = userMessage.toLowerCase();
        log.info("🔍 [意图识别] 原始消息: '{}'", userMessage);

        log.info("🔍 [意图识别] memberId={}, sessionId={}, lowerMsg={}", memberId, sessionId, lowerMsg);
        // ---- 1. 查询我的私教课 ----
        if (lowerMsg.contains("我的私教") || lowerMsg.contains("我的预约") ||
                (lowerMsg.contains("私教") && (lowerMsg.contains("查询") || lowerMsg.contains("看看")))) {
            log.info("✅ 命中【查询我的私教课】分支");
            return gymTools.queryMyPTBookings(memberId);
        }

        // ---- 2. 查询我的团课 ----
        if (lowerMsg.contains("团课") && (lowerMsg.contains("我") || lowerMsg.contains("报名") ||
                lowerMsg.contains("约") || lowerMsg.contains("查询") || lowerMsg.contains("看看"))) {
            log.info("✅ 命中【查询我的团课】分支");
            return gymTools.queryMyClassBookings(memberId);
        }

        // ---- 3. 查询体测历史 ----
        if (lowerMsg.contains("体测") && (lowerMsg.contains("历史") || lowerMsg.contains("记录") ||
                lowerMsg.contains("以前") || lowerMsg.contains("之前"))) {
            log.info("✅ 命中【查询体测历史】分支");
            return gymTools.queryMyTestHistory(memberId);
        }

        // ---- 4. 查询教练详情 ----
        if (lowerMsg.contains("教练") && (lowerMsg.contains("怎么样") || lowerMsg.contains("评价") ||
                lowerMsg.contains("介绍") || lowerMsg.contains("信息"))) {
            log.info("✅ 命中【查询教练】分支");
            String trainerName = extractTrainerName(userMessage);
            if (trainerName == null) {
                return "请告诉我是哪位教练，例如「王教练怎么样」";
            }
            return gymTools.queryTrainerByName(trainerName);
        }

        // ---- 5. 预约团课 ----
        if ((lowerMsg.contains("约") || lowerMsg.contains("预约") || lowerMsg.contains("报名"))
                && !lowerMsg.contains("私教") && !lowerMsg.contains("教练")
                && !lowerMsg.contains("体测") && !lowerMsg.contains("比赛")) {
            log.info("✅ 命中【预约团课】分支");
            String result = handleBookGroupClass(userMessage, memberId);
            if (!result.equals("请告诉我您想预约哪门团课，例如「帮我预约动感单车」")) {
                return result;
            }
            log.warn("预约团课解析失败，继续执行后续分支");
        }

        // ---- 6. 体测建议 ----
        if (lowerMsg.contains("体测") || (lowerMsg.contains("建议") && lowerMsg.contains("锻炼"))) {
            log.info("✅ 命中【体测建议】分支");
            return gymTools.generateWorkoutAdvice(memberId);
        }

        // ---- 7. 训练计划 ----
        if (isWorkoutPlanRequest(lowerMsg)) {
            log.info("✅ 命中【训练计划】分支");
            String skeleton = gymTools.generateWorkoutPlanSkeleton(memberId);
            if (skeleton != null && skeleton.contains("\"error\"")) {
                return skeleton;
            }
            return generatePersonalizedPlan(skeleton, userMessage, memberId, "workout", sessionId, memoryId);
        }

        // ---- 8. 饮食计划 ----
        if (isMealPlanRequest(lowerMsg)) {
            log.info("✅ 命中【饮食计划】分支");
            String skeleton = gymTools.generateMealPlanSkeleton(memberId);
            if (skeleton != null && skeleton.contains("\"error\"")) {
                return skeleton;
            }
            return generatePersonalizedPlan(skeleton, userMessage, memberId, "meal", sessionId, memoryId);
        }

        // ---- 9. 团课查询（查询所有可预约团课） ----
        if (lowerMsg.contains("团课") || lowerMsg.contains("课表") ||
                lowerMsg.contains("能报名") || lowerMsg.contains("可预约") ||
                (lowerMsg.contains("团") && lowerMsg.contains("课"))) {
            log.info("✅ 命中【团课查询】分支");
            return handleQueryClasses(userMessage);
        }

        // ---- 10. 推荐教练 ----
        if (lowerMsg.contains("推荐") && lowerMsg.contains("教练")) {
            log.info("✅ 命中【推荐教练】分支");
            if (memberId == null || memberId <= 0) {
                return "请先登录，以便根据您的会员等级推荐合适的教练。";
            }
            try {
                return gymTools.recommendTrainerByLevel(memberId);
            } catch (Exception e) {
                log.error("推荐教练异常 memberId={}", memberId, e);
                return "推荐教练时出现异常，请稍后再试。";
            }
        }

        // ---- 11. 教练列表（宽匹配） ----
        if (lowerMsg.contains("教练") && !lowerMsg.contains("预约") && !lowerMsg.contains("推荐")) {
            log.info("✅ 命中【教练列表】分支（宽匹配）");
            return gymTools.listAllTrainers();
        }
        // ---- 12. 预约私教 ----
        if ((lowerMsg.contains("约") || lowerMsg.contains("预约") || lowerMsg.contains("订"))
                && (lowerMsg.contains("教练") || lowerMsg.contains("私教"))) {
            log.info("✅ 命中【预约私教】分支");
            String bookingResult = handleBooking(userMessage, memberId);
            if (bookingResult != null && !bookingResult.isEmpty()) {
                return bookingResult;
            }
        }

        // ---- 13. 查询可报名比赛 ----
        if (lowerMsg.contains("比赛") && (lowerMsg.contains("查询") || lowerMsg.contains("报名") ||
                lowerMsg.contains("参加") || lowerMsg.contains("有什么"))) {
            log.info("✅ 命中【查询比赛】分支");
            return gymTools.queryAvailableCompetitions();
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

        // ---- 15. 默认：普通AI对话 ----
        log.info("➡️ 未命中任何专用分支，进入【普通 AI 对话】");
        return normalChat(sessionId, userMessage, memberId, memoryId);
    }

    // ========== 普通对话（直接使用AI + 工具 + 记忆） ==========
    private String normalChat(String sessionId, String userMessage, Long memberId, String memoryId) {
        try {
            Assistant assistant = getOrCreateAssistant(sessionId, memoryId);
            String userContext = buildUserContext(memberId);
            // 注入 RAG 知识库上下文
            String ragContext = knowledgeBase.searchRelevant(userMessage);

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
            String answer = CompletableFuture.supplyAsync(() ->
                    assistant.chat(finalPrompt),
                    CompletableFuture.delayedExecutor(0, TimeUnit.SECONDS)
            ).get(60, TimeUnit.SECONDS);
            if (answer == null || answer.trim().isEmpty()) {
                return getFallbackResponse(userMessage);
            }
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

    private String handleBooking(String userMessage, Long memberId) {
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
        Trainer trainer = trainerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Trainer>()
                        .eq("name", trainerName)
        );
        if (trainer == null) {
            return "预约失败：未找到名为 " + trainerName + " 的教练，请确认教练姓名是否正确。";
        }
        Long trainerId = trainer.getId();
        String dateStr = parseDate(userMessage);
        if (dateStr == null) {
            return "预约失败：未识别到日期，请提供具体日期（如：6月30日 或 明天）。";
        }
        String timeStr = parseTime(userMessage);
        if (timeStr == null) {
            return "预约失败：未识别到时间，请提供具体时间（如：下午2点 或 14:00）。";
        }
        LocalDateTime appointmentTime;
        try {
            appointmentTime = LocalDateTime.parse(dateStr + " " + timeStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                appointmentTime = LocalDateTime.parse(dateStr + " " + timeStr + ":00", DATE_FORMATTER2);
            } catch (DateTimeParseException ex) {
                return "预约失败：日期或时间格式不正确，请使用类似“6月30日下午2点”或“2026-06-30 14:00”的格式。";
            }
        }
        String result = ptService.bookPersonalTraining(memberId, trainerId, appointmentTime, 60);
        if (result.startsWith("私教预约成功")) {
            return "✅ " + result + "。已为您预约 " + trainerName + " 的课程，时间：" +
                    appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
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
        Matcher dateMatcher = DATE_PATTERN_CN.matcher(userMessage);
        if (dateMatcher.find()) {
            int month = Integer.parseInt(dateMatcher.group(1));
            int day = Integer.parseInt(dateMatcher.group(2));
            int currentYear = LocalDateTime.now().getYear();
            int currentMonth = LocalDateTime.now().getMonthValue();
            int year = currentYear;
            if (month < currentMonth || (month == currentMonth && day < LocalDateTime.now().getDayOfMonth())) {
                year++;
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
        Matcher timeMatcher = TIME_PATTERN_CN.matcher(userMessage);
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
        Matcher stdMatcher = STD_TIME.matcher(userMessage);
        if (stdMatcher.find()) {
            int hour = Integer.parseInt(stdMatcher.group(1));
            int minute = Integer.parseInt(stdMatcher.group(2));
            return String.format("%02d:%02d", hour, minute);
        }
        return null;
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

    private String handleQueryClasses(String userMessage) {
        String targetDateStr = parseDate(userMessage);
        LocalDateTime start, end;

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

        String result = gymTools.queryAvailableClasses(startStr, endStr);

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
                .eq(MemberPrivatePackage::getStatus, "active")
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

    private String extractTrainerName(String userMessage) {
        Pattern p = Pattern.compile("([\\u4e00-\\u9fa5]{2,})教练");
        Matcher m = p.matcher(userMessage);
        if (m.find()) {
            return m.group(1) + "教练";
        }
        Pattern trainerP = getTrainerNamePattern();
        Matcher tm = trainerP.matcher(userMessage);
        if (tm.find()) {
            return tm.group(0);
        }
        return null;
    }

    private String handleBookGroupClass(String userMessage, Long memberId) {
        String[] keywords = {"动感单车", "瑜伽", "搏击", "杠铃", "普拉提", "有氧", "力量", "单车", "操课", "舞蹈", "尊巴"};
        String courseKeyword = null;
        for (String kw : keywords) {
            if (userMessage.contains(kw)) {
                courseKeyword = kw;
                break;
            }
        }
        if (courseKeyword == null) {
            Pattern p = Pattern.compile("([\\u4e00-\\u9fa5]{2,}课)");
            Matcher m = p.matcher(userMessage);
            if (m.find()) {
                courseKeyword = m.group(0);
            }
        }
        if (courseKeyword == null) {
            return "请告诉我您想预约哪门团课，例如「帮我预约动感单车」";
        }

        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(GroupClass::getName, courseKeyword)
                .eq(GroupClass::getStatus, "scheduled")
                .gt(GroupClass::getStartTime, LocalDateTime.now())
                .orderByAsc(GroupClass::getStartTime)
                .last("LIMIT 1");
        GroupClass gc = groupClassMapper.selectOne(wrapper);

        if (gc == null && courseKeyword.endsWith("课") && courseKeyword.length() > 1) {
            String withoutKe = courseKeyword.substring(0, courseKeyword.length() - 1);
            wrapper.like(GroupClass::getName, withoutKe);
            gc = groupClassMapper.selectOne(wrapper);
        }

        if (gc == null) {
            return "未找到与「" + courseKeyword + "」相关的可预约课程，请确认课程名称或稍后再试。";
        }

        return gymTools.bookGroupClass(memberId, gc.getId());
    }
}