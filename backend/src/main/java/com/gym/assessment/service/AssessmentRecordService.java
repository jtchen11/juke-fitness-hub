package com.gym.assessment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.assessment.model.entity.AssessmentRecord;
import com.gym.assessment.model.entity.AssessmentReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssessmentRecordService {
    @Autowired private com.gym.mapper.AssessmentRecordMapper recordMapper;

    public Page<AssessmentRecord> list(Long memberId, int page, int size) {
        LambdaQueryWrapper<AssessmentRecord> w = new LambdaQueryWrapper<>();
        if (memberId != null) w.eq(AssessmentRecord::getMemberId, memberId);
        w.orderByDesc(AssessmentRecord::getTestDate);
        return recordMapper.selectPage(new Page<>(page, size), w);
    }

    public AssessmentRecord getById(Long id) { return recordMapper.selectById(id); }
    public boolean save(AssessmentRecord r) { return recordMapper.insert(r) > 0; }
}