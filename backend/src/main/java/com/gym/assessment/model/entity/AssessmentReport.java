package com.gym.assessment.model.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("assessment_report")
public class AssessmentReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long recordId;
    private Integer totalScore;
    private String grade;
    private String aiSuggestion;
    private LocalDateTime createdAt;
}