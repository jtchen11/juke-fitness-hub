package com.gym.assessment.model.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("assessment_rule")
public class AssessmentRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleKey;
    private String gender;
    private Double minValue;
    private Double maxValue;
    private Integer score;
    private String description;
}