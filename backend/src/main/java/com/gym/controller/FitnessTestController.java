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

@RestController
@RequestMapping("/api/fitness-tests")
public class FitnessTestController {

    @Autowired
    private FitnessTestMapper testMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private GymAssessmentScoringEngine scoringEngine;

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
        result.put("height", 170);
        result.put("weight", test.getWeightKg());

        double weightVal = test.getWeightKg() != null ? test.getWeightKg().doubleValue() : 65;
        double heightM = 1.7;
        double bmi = heightM > 0 ? Math.round(weightVal / (heightM * heightM) * 10) / 10.0 : 0;
        result.put("bmi", bmi);

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

        result.put("report", report.toString());
        return result;
    }
}
