package com.gym.assessment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.assessment.engine.GymAssessmentScoringEngine;
import com.gym.assessment.model.dto.AssessmentReportDTO;
import com.gym.assessment.model.entity.AssessmentRecord;
import com.gym.assessment.service.AssessmentRecordService;
import com.gym.assessment.service.AssessmentReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {
    @Autowired private GymAssessmentScoringEngine scoringEngine;
    @Autowired private AssessmentRecordService recordService;
    @Autowired private AssessmentReportService reportService;

    @PostMapping("/score")
    public Map<String, Object> score(@RequestBody Map<String, Object> params) {
        Long memberId = Long.valueOf(params.get("memberId").toString());
        Double weightKg = Double.valueOf(params.get("weightKg").toString());
        Double bodyFat = params.containsKey("bodyFatPercent") ? Double.valueOf(params.get("bodyFatPercent").toString()) : null;
        Double muscle = params.containsKey("muscleMassKg") ? Double.valueOf(params.get("muscleMassKg").toString()) : null;

        AssessmentReportDTO dto = scoringEngine.score(memberId, weightKg, bodyFat, muscle);
        if (dto == null) return Map.of("success", false, "message", "\u4f1a\u5458\u4fe1\u606f\u4e0d\u5b8c\u6574");

        AssessmentRecord record = new AssessmentRecord();
        record.setMemberId(memberId); record.setWeightKg(weightKg); record.setBodyFatPercent(bodyFat);
        record.setMuscleMassKg(muscle); record.setBmi(dto.getBmi()); record.setBmiScore(dto.getBmiScore());
        record.setFatScore(dto.getFatScore()); record.setTotalScore(dto.getTotalScore());
        record.setGender(dto.getGender()); record.setHeightCm(dto.getHeightCm());
        recordService.save(record);

        dto.setRecordId(record.getId());
        reportService.generateAiSuggestion(dto);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true); result.put("data", dto);
        return result;
    }

    @GetMapping("/records")
    public Page<AssessmentRecord> records(@RequestParam(required = false) Long memberId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return recordService.list(memberId, page, size);
    }
}