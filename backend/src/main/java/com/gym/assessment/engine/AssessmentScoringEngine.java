package com.gym.assessment.engine;
import com.gym.assessment.model.dto.AssessmentReportDTO;

public interface AssessmentScoringEngine {
    AssessmentReportDTO score(Long memberId, Double weightKg, Double bodyFatPercent, Double muscleMassKg);
}