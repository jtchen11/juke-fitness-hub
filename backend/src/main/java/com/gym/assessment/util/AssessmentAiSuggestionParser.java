package com.gym.assessment.util;

import com.gym.assessment.model.dto.AssessmentReportDTO;

public class AssessmentAiSuggestionParser {
    public static String buildPrompt(AssessmentReportDTO dto) {
        return String.format(
            "\u4f53\u6d4b\u7ed3\u679c\uff1aBMI=%.1f(%d\u5206)\uff0c\u4f53\u8102\u7387=%.1f%%(%d\u5206)\uff0c\u7efc\u5408\u5f97\u5206=%d\u3002" +
            "\u8bf7\u7ed9\u51fa\u5065\u5eb7\u8bc4\u4ef7\u548c\u8bad\u7ec3\u5efa\u8bae\uff0c100\u5b57\u4ee5\u5185\u3002",
            dto.getBmi(), dto.getBmiScore(), dto.getBodyFatPercent(), dto.getFatScore(), dto.getTotalScore()
        );
    }

    public static String parseResponse(String raw) {
        return raw != null ? raw.replaceAll("[\\[\\]]", "").trim() : "\u66b2\u751f\u5efa\u8bae\u5931\u8d25";
    }
}