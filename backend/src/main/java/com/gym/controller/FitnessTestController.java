package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.FitnessTest;
import com.gym.entity.Member;
import com.gym.mapper.FitnessTestMapper;
import com.gym.assessment.model.dto.AssessmentReportDTO;
import com.gym.assessment.engine.GymAssessmentScoringEngine;
import com.gym.mapper.MemberMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/fitness-tests")
public class FitnessTestController {

    @Autowired
    private FitnessTestMapper testMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private GymAssessmentScoringEngine scoringEngine;

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    /**
     * 分页查询体测记录（支持筛选）
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();

        if (memberId != null && memberId > 0) {
            wrapper.eq(FitnessTest::getMemberId, memberId);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(FitnessTest::getTestDate, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(FitnessTest::getTestDate, endDate);
        }

        wrapper.orderByDesc(FitnessTest::getTestDate);

        IPage<FitnessTest> pageResult = testMapper.selectPage(
                new Page<>(page, size), wrapper
        );

        pageResult.getRecords().forEach(test -> {
            if (test.getMemberId() != null) {
                Member member = memberMapper.selectById(test.getMemberId());
                if (member != null) {
                    test.setMemberName(member.getName());
                }
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/{id}")
    public FitnessTest getById(@PathVariable Long id) {
        return testMapper.selectById(id);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<FitnessTest> all = testMapper.selectList(null);
        long total = all.size();

        // 计算平均值
        double avgWeight = 0, avgBodyFat = 0, avgMuscle = 0;
        if (total > 0) {
            avgWeight = all.stream()
                    .mapToDouble(t -> t.getWeightKg() != null ? t.getWeightKg().doubleValue() : 0)
                    .average().orElse(0);
            avgBodyFat = all.stream()
                    .mapToDouble(t -> t.getBodyFatPercent() != null ? t.getBodyFatPercent().doubleValue() : 0)
                    .average().orElse(0);
            avgMuscle = all.stream()
                    .mapToDouble(t -> t.getMuscleMassKg() != null ? t.getMuscleMassKg().doubleValue() : 0)
                    .average().orElse(0);
        }

        // 四舍五入保留一位小数
        BigDecimal avgWeightBd = BigDecimal.valueOf(avgWeight).setScale(1, RoundingMode.HALF_UP);
        BigDecimal avgBodyFatBd = BigDecimal.valueOf(avgBodyFat).setScale(1, RoundingMode.HALF_UP);
        BigDecimal avgMuscleBd = BigDecimal.valueOf(avgMuscle).setScale(1, RoundingMode.HALF_UP);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("avgWeight", avgWeightBd);
        result.put("avgBodyFat", avgBodyFatBd);
        result.put("avgMuscle", avgMuscleBd);

        return result;
    }

    /**
     * 获取体脂趋势数据（按日期分组）
     * 如果未传 memberId 或 memberId 无效，返回空数据
     */
    @GetMapping("/trend")
    public Map<String, Object> getTrend(@RequestParam(required = false) Long memberId) {
        Map<String, Object> result = new HashMap<>();

        // ====== 核心改动：如果未指定会员，返回空列表 ======
        if (memberId == null || memberId <= 0) {
            result.put("dates", new ArrayList<>());
            result.put("weights", new ArrayList<>());
            result.put("bodyFats", new ArrayList<>());
            result.put("muscles", new ArrayList<>());
            return result;
        }

        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FitnessTest::getMemberId, memberId)
                .orderByAsc(FitnessTest::getTestDate);

        List<FitnessTest> list = testMapper.selectList(wrapper);

        List<String> dates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<Double> bodyFats = new ArrayList<>();
        List<Double> muscles = new ArrayList<>();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (FitnessTest t : list) {
            if (t.getTestDate() != null) {
                dates.add(t.getTestDate().format(fmt));
                weights.add(t.getWeightKg() != null ? t.getWeightKg().doubleValue() : null);
                bodyFats.add(t.getBodyFatPercent() != null ? t.getBodyFatPercent().doubleValue() : null);
                muscles.add(t.getMuscleMassKg() != null ? t.getMuscleMassKg().doubleValue() : null);
            }
        }

        result.put("dates", dates);
        result.put("weights", weights);
        result.put("bodyFats", bodyFats);
        result.put("muscles", muscles);
        return result;
    }

        @GetMapping("/export")
    public void exportTests(HttpServletResponse response) throws IOException {
        List<FitnessTest> list = testMapper.selectList(
                new LambdaQueryWrapper<FitnessTest>().orderByDesc(FitnessTest::getTestDate)
        );
        list.forEach(test -> {
            if (test.getMemberId() != null) {
                Member member = memberMapper.selectById(test.getMemberId());
                if (member != null) {
                    test.setMemberName(member.getName());
                }
            }
        });

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=体测记录_" + LocalDate.now().toString() + ".csv");

        try (PrintWriter writer = response.getWriter()) {
            writer.write("\uFEFF");
            writer.println("ID,会员,测试日期,体重(kg),体脂率(%),肌肉量(kg),备注");
            for (FitnessTest t : list) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s%n",
                        t.getId(),
                        nullToEmpty(t.getMemberName()),
                        t.getTestDate() != null ? t.getTestDate().toString() : "",
                        t.getWeightKg() != null ? t.getWeightKg().toString() : "",
                        t.getBodyFatPercent() != null ? t.getBodyFatPercent().toString() : "",
                        t.getMuscleMassKg() != null ? t.getMuscleMassKg().toString() : "",
                        nullToEmpty(t.getRemarks())
                );
            }
            writer.flush();
        }
    }
    /**
     * 获取某会员的体测统计数据（用于顶部卡片）
     */
    @GetMapping("/member/{memberId}/stats")
    public Map<String, Object> getMemberStats(@PathVariable Long memberId) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FitnessTest::getMemberId, memberId)
                .orderByDesc(FitnessTest::getTestDate);
        List<FitnessTest> list = testMapper.selectList(wrapper);

        if (list.isEmpty()) {
            result.put("total", 0);
            result.put("latestWeight", null);
            result.put("latestBodyFat", null);
            result.put("latestMuscle", null);
            return result;
        }

        result.put("total", list.size());
        FitnessTest latest = list.get(0);
        result.put("latestWeight", latest.getWeightKg());
        result.put("latestBodyFat", latest.getBodyFatPercent());
        result.put("latestMuscle", latest.getMuscleMassKg());
        return result;
    }

    @GetMapping("/member/{memberId}")
    public Map<String, Object> getMemberRecords(@PathVariable Long memberId) {
        LambdaQueryWrapper<FitnessTest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FitnessTest::getMemberId, memberId)
                .orderByDesc(FitnessTest::getTestDate);
        List<FitnessTest> list = testMapper.selectList(wrapper);
        Map<String, Object> r = new HashMap<>();
        r.put("records", list);
        r.put("total", list.size());
        return r;
    }
    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }
    @GetMapping("/{id}/ai-report")
    public Map<String, Object> getAIReport(@PathVariable Long id) {
        FitnessTest test = testMapper.selectById(id);
        Map<String, Object> result = new HashMap<>();
        if (test == null) {
            result.put("error", "体测记录不存在");
            return result;
        }
        result.put("memberName", test.getMemberName());
        result.put("testDate", test.getTestDate() != null ? test.getTestDate().toString() : "");
        double heightCm = 170;
        Member member = test.getMemberId() != null ? memberMapper.selectById(test.getMemberId()) : null;
        if (member != null && member.getHeight() != null && member.getHeight().doubleValue() > 0) {
            heightCm = member.getHeight().doubleValue();
        }
        result.put("height", heightCm);
        result.put("weight", test.getWeightKg());

        double weightVal = test.getWeightKg() != null ? test.getWeightKg().doubleValue() : 65;
        double heightM = heightCm / 100;
        double bmi = heightM > 0 ? Math.round(weightVal / (heightM * heightM) * 10) / 10.0 : 0;
        result.put("bmi", bmi);

        // 优先调用 AI 生成个性化建议，失败时降级为规则报告
        String reportText = null;
        try {
            reportText = generateAIReport(member, heightCm, weightVal, bmi, test);
        } catch (Exception e) {
            log.warn("AI体测建议生成失败，降级为规则报告: {}", e.getMessage());
        }
        if (reportText == null || reportText.trim().isEmpty()) {
            reportText = buildRuleReport(bmi, test);
        }
        result.put("report", reportText);
        return result;
    }

    /**
     * 调用 ChatLanguageModel 生成个性化体测建议
     */
    private String generateAIReport(Member member, double heightCm, double weightVal, double bmi, FitnessTest test) {
        String genderText = "未知";
        if (member != null && member.getGender() != null) {
            String g = member.getGender().trim();
            if (g.contains("男") || "male".equalsIgnoreCase(g) || "M".equalsIgnoreCase(g)) {
                genderText = "男";
            } else if (g.contains("女") || "female".equalsIgnoreCase(g) || "F".equalsIgnoreCase(g)) {
                genderText = "女";
            }
        }
        String bfText = test.getBodyFatPercent() != null ? test.getBodyFatPercent() + "%" : "未知";
        String mmText = test.getMuscleMassKg() != null ? test.getMuscleMassKg() + "kg" : "未知";
        String prompt = "你是一名专业的健身教练和健康顾问。请根据以下会员的体测数据，输出100-150字的个性化健康分析建议，"
                + "包含：1）总体评价；2）亮点与问题；3）具体的训练和饮食建议。直接输出纯文本，不要使用markdown格式。\n"
                + "性别：" + genderText + "\n"
                + "身高：" + (int) heightCm + "cm\n"
                + "体重：" + weightVal + "kg\n"
                + "BMI：" + bmi + "\n"
                + "体脂率：" + bfText + "\n"
                + "肌肉量：" + mmText;
        Response<AiMessage> resp = chatLanguageModel.generate(java.util.List.of(new UserMessage(prompt)));
        if (resp != null && resp.content() != null && resp.content().text() != null) {
            return resp.content().text().trim();
        }
        return null;
    }

    /**
     * 规则报告（AI 失败时的降级方案）
     */
    private String buildRuleReport(double bmi, FitnessTest test) {
        StringBuilder report = new StringBuilder();
        report.append("【健康评估报告】\n\n");
        report.append("基于本次体测数据，会员体质评估如下：\n");

        if (bmi < 18.5) {
            report.append("• BMI为").append(bmi).append("，偏瘦，建议增加营养摄入和力量训练。\n");
        } else if (bmi < 24) {
            report.append("• BMI为").append(bmi).append("，正常范围，建议保持规律运动。\n");
        } else if (bmi < 28) {
            report.append("• BMI为").append(bmi).append("，偏重，建议加强有氧运动和控制饮食。\n");
        } else {
            report.append("• BMI为").append(bmi).append("，肥胖风险较高，建议制定减脂计划。\n");
        }

        Double bf = test.getBodyFatPercent() != null ? test.getBodyFatPercent().doubleValue() : null;
        if (bf != null) {
            if (bf < 15) {
                report.append("• 体脂率为").append(bf).append("%，偏低，适合增肌训练。\n");
            } else if (bf < 22) {
                report.append("• 体脂率为").append(bf).append("%，标准范围，建议保持。\n");
            } else {
                report.append("• 体脂率为").append(bf).append("%，偏高，建议加强减脂训练。\n");
            }
        }

        Double mm = test.getMuscleMassKg() != null ? test.getMuscleMassKg().doubleValue() : null;
        if (mm != null) {
            if (mm < 25) {
                report.append("• 肌肉量为").append(mm).append("kg，偏低，建议增加力量训练频次。\n");
            } else {
                report.append("• 肌肉量为").append(mm).append("kg，良好，建议继续维持。\n");
            }
        }

        report.append("\n【训练建议】\n");
        report.append("建议每周进行3-5次训练，包含有氧和力量训练，配合合理饮食。\n");
        report.append("建议每隔2-4周复测一次，跟踪体质变化。\n");
        return report.toString();
    }

    /**
     * 体测评分：BMI/ 体脂率/肌肉量 三维度综合评分
     */
    @GetMapping("/{id}/score")
    public Map<String, Object> getScore(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        FitnessTest test = testMapper.selectById(id);
        if (test == null) {
            result.put("error", "体测记录不存在");
            return result;
        }
        Member member = test.getMemberId() != null ? memberMapper.selectById(test.getMemberId()) : null;
        boolean female = isFemale(member);
        double heightCm = (member != null && member.getHeight() != null && member.getHeight().doubleValue() > 0)
                ? member.getHeight().doubleValue() : 170;
        double weightVal = test.getWeightKg() != null ? test.getWeightKg().doubleValue() : 0;
        double bmi = heightCm > 0 ? Math.round(weightVal / Math.pow(heightCm / 100, 2) * 10) / 10.0 : 0;

        // BMI 30分
        int bmiScore;
        String bmiDesc;
        if (bmi >= 18.5 && bmi <= 24) { bmiScore = 30; bmiDesc = "正常范围"; }
        else if ((bmi >= 17 && bmi < 18.5) || (bmi > 24 && bmi <= 28)) { bmiScore = 20; bmiDesc = "轻微偏离"; }
        else { bmiScore = 10; bmiDesc = "明显偏离"; }

        // 体脂率 40分（分性别）
        int fatScore;
        String fatDesc;
        Double bf = test.getBodyFatPercent() != null ? test.getBodyFatPercent().doubleValue() : null;
        if (bf == null) { fatScore = 0; fatDesc = "数据缺失"; }
        else if (female) {
            if (bf >= 18 && bf <= 28) { fatScore = 40; fatDesc = "达标"; }
            else if ((bf >= 15 && bf < 18) || (bf > 28 && bf <= 33)) { fatScore = 25; fatDesc = "轻微偏离"; }
            else { fatScore = 10; fatDesc = "严重偏离"; }
        } else {
            if (bf >= 10 && bf <= 20) { fatScore = 40; fatDesc = "达标"; }
            else if ((bf >= 8 && bf < 10) || (bf > 20 && bf <= 25)) { fatScore = 25; fatDesc = "轻微偏离"; }
            else { fatScore = 10; fatDesc = "严重偏离"; }
        }

        // 肌肉量 30分（分性别，标准值=体重×40%/女35%）
        int muscleScore;
        String muscleDesc;
        Double mm = test.getMuscleMassKg() != null ? test.getMuscleMassKg().doubleValue() : null;
        double standard = weightVal * (female ? 0.35 : 0.40);
        if (mm == null) { muscleScore = 0; muscleDesc = "数据缺失"; }
        else if (mm >= standard) { muscleScore = 30; muscleDesc = "达标"; }
        else if (mm >= standard * 0.9) { muscleScore = 20; muscleDesc = "接近标准"; }
        else { muscleScore = 10; muscleDesc = "低于标准"; }

        int total = bmiScore + fatScore + muscleScore;
        String level;
        String levelText;
        String levelIcon;
        if (total >= 85) { level = "excellent"; levelText = "优秀"; levelIcon = "🌟"; }
        else if (total >= 70) { level = "good"; levelText = "良好"; levelIcon = "💪"; }
        else { level = "needs_improvement"; levelText = "待改善"; levelIcon = "📈"; }

        result.put("totalScore", total);
        result.put("level", level);
        result.put("levelText", levelText);
        result.put("levelIcon", levelIcon);
        result.put("bmi", scoreItem(bmiScore, 30, bmi, bmiDesc));
        result.put("bodyFat", scoreItem(fatScore, 40, bf, fatDesc));
        result.put("muscle", scoreItem(muscleScore, 30, mm, muscleDesc));
        return result;
    }

    private boolean isFemale(Member member) {
        if (member == null || member.getGender() == null) return false;
        String g = member.getGender().trim();
        return g.contains("女") || "female".equalsIgnoreCase(g) || "F".equalsIgnoreCase(g);
    }

    private Map<String, Object> scoreItem(int score, int full, Double value, String desc) {
        Map<String, Object> item = new HashMap<>();
        item.put("score", score);
        item.put("full", full);
        item.put("value", value != null ? value : "未知");
        item.put("desc", desc);
        return item;
    }
}
