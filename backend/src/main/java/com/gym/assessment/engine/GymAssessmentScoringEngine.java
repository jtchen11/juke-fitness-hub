package com.gym.assessment.engine;

import com.gym.assessment.model.dto.AssessmentReportDTO;
import com.gym.assessment.model.entity.AssessmentRecord;
import com.gym.assessment.service.AssessmentReportService;
import com.gym.entity.Member;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class GymAssessmentScoringEngine implements AssessmentScoringEngine {
    @Autowired private MemberMapper memberMapper;
    @Autowired private AssessmentReportService reportService;

    @Override
    public AssessmentReportDTO score(Long memberId, Double weightKg, Double bodyFatPercent, Double muscleMassKg) {
        Member member = memberMapper.selectById(memberId);
        if (member == null || member.getHeight() == null) return null;
        double heightM = member.getHeight().doubleValue() / 100.0;
        double bmi = weightKg / (heightM * heightM);
        String gender = member.getGender() != null ? member.getGender() : "\u7537";
        int bmiScore = scoreBMI(bmi);
        int fatScore = scoreBodyFat(bodyFatPercent, gender);
        int totalScore = (int) Math.round(bmiScore * 0.5 + fatScore * 0.5);

        AssessmentReportDTO dto = new AssessmentReportDTO();
        dto.setMemberId(memberId); dto.setTestDate(LocalDate.now());
        dto.setBmi(Math.round(bmi * 10) / 10.0);
        dto.setBmiScore(bmiScore); dto.setBodyFatPercent(bodyFatPercent);
        dto.setFatScore(fatScore); dto.setTotalScore(totalScore);
        dto.setGender(gender); dto.setHeightCm(member.getHeight().doubleValue());
        dto.setWeightKg(weightKg);
        return dto;
    }

    private int scoreBMI(double bmi) {
        if (bmi >= 18.5 && bmi <= 24.9) return 90;
        if (bmi >= 17.0 && bmi < 18.5) return 70;
        if (bmi >= 25.0 && bmi <= 27.9) return 70;
        return 50;
    }

    private int scoreBodyFat(Double pct, String gender) {
        if (pct == null) return 0;
        if ("\u5973".equals(gender)) {
            if (pct >= 20 && pct <= 28) return 90;
            if (pct >= 15 && pct < 20) return 80;
            if (pct > 28 && pct <= 32) return 70;
            return 50;
        } else {
            if (pct >= 10 && pct <= 18) return 90;
            if (pct >= 6 && pct < 10) return 80;
            if (pct > 18 && pct <= 22) return 70;
            return 50;
        }
    }
}