package com.gym.assessment.model.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("assessment_record")
public class AssessmentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private LocalDate testDate;
    private Double weightKg;
    private Double bodyFatPercent;
    private Double muscleMassKg;
    private Double bmi;
    private Integer bmiScore;
    private Integer fatScore;
    private Integer totalScore;
    private String gender;
    private Double heightCm;
    private String remark;
}