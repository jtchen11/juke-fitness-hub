package com.gym.assessment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.assessment.model.entity.AssessmentRule;
import com.gym.mapper.AssessmentRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AssessmentRuleService {
    @Autowired private AssessmentRuleMapper ruleMapper;

    public List<AssessmentRule> getRulesByGender(String gender) {
        LambdaQueryWrapper<AssessmentRule> w = new LambdaQueryWrapper<>();
        w.eq(AssessmentRule::getGender, gender).or().isNull(AssessmentRule::getGender);
        return ruleMapper.selectList(w);
    }

    public boolean save(AssessmentRule r) { return ruleMapper.insert(r) > 0; }
    public boolean update(AssessmentRule r) { return ruleMapper.updateById(r) > 0; }
    public boolean delete(Long id) { return ruleMapper.deleteById(id) > 0; }
}