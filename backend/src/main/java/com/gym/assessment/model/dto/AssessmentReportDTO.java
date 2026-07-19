package com.gym.assessment.model.dto;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AssessmentReportDTO {
    private Long memberId;
    private LocalDate testDate;
    private Double bmi;
    private Integer bmiScore;
    private Double bodyFatPercent;
    private Integer fatScore;
    private Integer totalScore;
    private String gender;
    private Double heightCm;
    private Double weightKg;
    private String aiSuggestion;
    private Long recordId;
    private String grade;
}