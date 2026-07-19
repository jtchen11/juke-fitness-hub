package com.gym.assessment.service;

import com.gym.assessment.model.dto.AssessmentReportDTO;
import com.gym.assessment.model.entity.AssessmentReport;
import com.gym.assessment.util.AssessmentAiSuggestionParser;
import com.gym.mapper.AssessmentReportMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AssessmentReportService {
    @Autowired private AssessmentReportMapper reportMapper;
    @Autowired private ChatLanguageModel dashScopeChatModel;

    public String generateAiSuggestion(AssessmentReportDTO dto) {
        String prompt = AssessmentAiSuggestionParser.buildPrompt(dto);
        String suggestion;
        try {
            dev.langchain4j.data.message.ChatMessage msg = UserMessage.from(prompt);
            dev.langchain4j.data.message.AiMessage response = (dev.langchain4j.data.message.AiMessage) dashScopeChatModel.generate(java.util.List.of(msg)).content();
            suggestion = response.text();
        } catch (Exception e) {
            suggestion = String.format(
                "您的BMI指数%.1f，体脂率%.1f%%。请咨询教练获取专业建议。",
                dto.getBmi(), dto.getBodyFatPercent());
        }
        dto.setAiSuggestion(suggestion);
        int totalScore = dto.getTotalScore();
        String grade = totalScore >= 85 ? "优秀" : totalScore >= 70 ? "良好" : totalScore >= 60 ? "合格" : "需改善";
        dto.setGrade(grade);

        AssessmentReport report = new AssessmentReport();
        report.setMemberId(dto.getMemberId()); report.setRecordId(dto.getRecordId());
        report.setTotalScore(totalScore);
        report.setGrade(grade); report.setAiSuggestion(suggestion);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
        return suggestion;
    }
}