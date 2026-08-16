package com.gym.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.ai.memory.MongoChatMemoryStore;
import com.gym.ai.rag.KnowledgeBaseService;
import com.gym.ai.tool.GymTools;
import com.gym.entity.*;
import com.gym.enums.MemberLevel;
import com.gym.mapper.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatOrchestrationService {

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
    private MemberMapper memberMapper;

    @Autowired
    private PersonalTrainingMapper personalTrainingMapper;

    @Autowired
    private MemberPrivatePackageMapper memberPrivatePackageMapper;

    @Autowired
    private TrainerMapper trainerMapper;

    @Autowired
    private GroupClassMapper groupClassMapper;

    private final Map<String, Assistant> sessionAssistants = new ConcurrentHashMap<>();

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
                    "9. 对于「你好」「嗨」「在吗」「hello」等日常寒暄，请自然友好地回应，并主动介绍自己能提供的帮助（如查询课程、体测建议、预约私教等）。\n" +
                    "10. 重要规则：\n" +
                    "  1. 当用户提出健身相关需求（预约课程、查询课表、推荐教练、制定计划等）时，你必须调用对应的工具（@Tool 方法）来获取数据，不要凭自己的知识回答。\n" +
                    "  2. 当你调用工具并获得返回结果（ToolResult）时，请只提取其中的 message 字段内容，用自然、友好的语气润色后回复用户。不要输出 JSON 格式、字段名或工具名称。\n" +
                    "  3. 如果用户说“不要了”、“算了”、“取消”等否定词，表示用户想退出当前流程，请尊重用户意图，不要继续追问。\n" +
                    "  4. 你是桔刻健身的智能助手，专注于解答健身相关问题。如果用户问的是通用健身知识（如“深蹲怎么做”），你可以用自己的知识回答，但要注明“以下内容来自通用知识，非本馆内部数据”。\n" +
                    "  5. 当用户明确表示要预约课程（“我要约力量训练”“我要约团课”“预约课程”等）时，你必须调用 queryAvailableClasses 工具查询课程列表，不得自己编造或简化课程信息。查询结果中的价格、时间、余位等字段必须完整呈现给用户。\n" +
                    "  6. 绝对禁止在没有调用 bookGroupClass 工具并获得成功结果的情况下，告知用户“预约成功”或“已确认预约”。预约成功的结果必须来自工具返回，而非 AI 自己编造。\n" +
                    "  7. 当用户查询课表、预约、课程安排时，以【当前用户实时上下文】中的“今日待上课：X 节”为准，禁止参考历史消息中的课程安排数据，更不得将历史中的日期混淆为当前日期。\n" +
                    "11. 场景限定规则：\n" +
                    "   - 当用户询问团课（如'我要约团课'、'有什么团课'、'团课列表'）时，只回复团课相关信息，不得提及私教课、免费私教次数或课程包课时。这些信息仅用于私教预约场景。\n" +
                    "   - 当用户询问私教预约时，方可提及免费私教次数和课程包课时。\n" +
                    "12. 工具调用优先级：\n" +
                    "   - 当用户询问饮食建议、减脂饮食、健康饮食、食谱时，必须调用 generateMealPlanSkeleton 工具获取个性化数据，而不是使用自己的通用知识。\n" +
                    "   - 当用户询问训练计划、健身计划、一周训练安排时，必须调用 generateWorkoutPlanSkeleton 工具获取个性化数据，而不是使用自己的通用知识。\n" +
                    "   - 仅当工具返回数据不足或调用失败时，才使用通用知识作为备选。\n";

    public interface Assistant {
        String chat(String userMessage);
    }

    // ====== 供 Web 层清理 Assistant 缓存 ======
    public void clearAssistant(String sessionId) {
        sessionAssistants.remove(sessionId);
    }

    public void clearAllAssistants() {
        sessionAssistants.clear();
    }
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

    private void saveToMemory(String memoryId, String userMessage, String assistantReply, String imageUrl) {
        List<ChatMessage> existing = memoryStore.getMessages(memoryId);
        boolean lastIsSameUser = !existing.isEmpty() &&
                existing.get(existing.size() - 1) instanceof UserMessage && ((UserMessage) existing.get(existing.size() - 1)).singleText().equals(userMessage);
        if (!lastIsSameUser) {
            memoryStore.saveMessageRecord(memoryId, "user", userMessage, null);
            memoryStore.saveMessageRecord(memoryId, "assistant", assistantReply, imageUrl);
        }
    }

    public String normalChat(String sessionId, String userMessage, Long memberId, String memoryId) {
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

    public void streamingNormalChat(String sessionId, String userMessage, Long memberId, String memoryId, SseEmitter emitter) {
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

    public Assistant getOrCreateAssistant(String sessionId, String memoryId) {
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
        log.info("buildUserContext 查询今日私教预约: memberId={}, todayStart={}, todayEnd={}", memberId, todayStart, todayEnd);
        long todayPT = personalTrainingMapper.selectCount(ptWrapper);
        log.info("buildUserContext 查询今日私教预约结果: count={}", todayPT);

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
}
