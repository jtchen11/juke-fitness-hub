package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.Competition;
import com.gym.entity.CompetitionRegistration;
import com.gym.entity.Member;
import com.gym.mapper.CompetitionRegistrationMapper;
import com.gym.mapper.MemberMapper;
import com.gym.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/competition-registrations")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class CompetitionRegistrationController {

    @Autowired
    private CompetitionRegistrationMapper registrationMapper;

    @Autowired
    private CompetitionService competitionService;
    @Autowired
    private MemberMapper memberMapper;

    /**
     * 会员报名比赛
     */
    @PostMapping
    @Transactional
    public Map<String, Object> register(@RequestBody Map<String, Long> request) {
        Long competitionId = request.get("competitionId");
        Long memberId = request.get("memberId");

        Map<String, Object> result = new HashMap<>();

        if (competitionId == null || memberId == null) {
            result.put("success", false);
            result.put("message", "缺少比赛或用户信息");
            return result;
        }

        // 检查比赛是否存在
        Competition competition = competitionService.getById(competitionId);
        if (competition == null) {
            result.put("success", false);
            result.put("message", "比赛不存在");
            return result;
        }
        if (competition.getIsActive() != null && !competition.getIsActive()) {
            result.put("success", false);
            result.put("message", "该比赛未开放报名");
            return result;
        }

        // 检查报名截止时间
        if (competition.getDeadline() != null && competition.getDeadline().isBefore(LocalDateTime.now())) {
            result.put("success", false);
            result.put("message", "报名已截止");
            return result;
        }

        // 检查是否已报名
        LambdaQueryWrapper<CompetitionRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompetitionRegistration::getCompetitionId, competitionId)
                .eq(CompetitionRegistration::getMemberId, memberId)
                .eq(CompetitionRegistration::getStatus, "registered");
        if (registrationMapper.selectCount(wrapper) > 0) {
            result.put("success", false);
            result.put("message", "您已报名该比赛");
            return result;
        }

        // 检查是否已满
        int enrolled = competition.getEnrolled() == null ? 0 : competition.getEnrolled();
        if (competition.getMaxParticipants() != null && enrolled >= competition.getMaxParticipants()) {
            result.put("success", false);
            result.put("message", "比赛名额已满");
            return result;
        }

        // 创建报名记录
        CompetitionRegistration registration = new CompetitionRegistration();
        registration.setCompetitionId(competitionId);
        registration.setMemberId(memberId);
        registration.setRegistrationTime(LocalDateTime.now());
        registration.setStatus("registered");
        registrationMapper.insert(registration);

        // 报名人数+1
        competitionService.incrementEnrolled(competitionId);

        result.put("success", true);
        result.put("message", "报名成功！");
        return result;
    }

    /**
     * 取消报名
     */
    @DeleteMapping
    @Transactional
    public Map<String, Object> cancel(@RequestParam Long competitionId, @RequestParam Long memberId) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<CompetitionRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompetitionRegistration::getCompetitionId, competitionId)
                .eq(CompetitionRegistration::getMemberId, memberId)
                .eq(CompetitionRegistration::getStatus, "registered");
        CompetitionRegistration registration = registrationMapper.selectOne(wrapper);

        if (registration == null) {
            result.put("success", false);
            result.put("message", "未找到报名记录");
            return result;
        }

        registration.setStatus("cancelled");
        registrationMapper.updateById(registration);

        // 报名人数-1
        Competition competition = competitionService.getById(competitionId);
        if (competition != null && competition.getEnrolled() > 0) {
            competition.setEnrolled(competition.getEnrolled() - 1);
            competitionService.update(competition);
        }

        result.put("success", true);
        result.put("message", "取消报名成功");
        return result;
    }

    /**
     * 查询会员报名记录
     */
    @GetMapping("/member/{memberId}")
    public List<CompetitionRegistration> getByMemberId(@PathVariable Long memberId) {
        LambdaQueryWrapper<CompetitionRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompetitionRegistration::getMemberId, memberId)
                .eq(CompetitionRegistration::getStatus, "registered")
                .orderByDesc(CompetitionRegistration::getRegistrationTime);
        return registrationMapper.selectList(wrapper);
    }
    /**
     * 查询会员已报名的比赛（含比赛信息，会员端“我的报名”）
     */
    @GetMapping("/member/{memberId}/competitions")
    public List<Map<String, Object>> getMyCompetitions(@PathVariable Long memberId) {
        LambdaQueryWrapper<CompetitionRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompetitionRegistration::getMemberId, memberId)
                .eq(CompetitionRegistration::getStatus, "registered")
                .orderByDesc(CompetitionRegistration::getRegistrationTime);
        List<CompetitionRegistration> regs = registrationMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CompetitionRegistration reg : regs) {
            Competition competition = competitionService.getById(reg.getCompetitionId());
            if (competition == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", competition.getId());
            item.put("name", competition.getName());
            item.put("startTime", competition.getStartTime());
            item.put("endTime", competition.getEndTime());
            item.put("deadline", competition.getDeadline());
            item.put("status", competition.getStatus());
            item.put("enrolled", competition.getEnrolled());
            item.put("maxParticipants", competition.getMaxParticipants());
            item.put("rewardGranted", competition.getRewardGranted());
            item.put("registrationTime", reg.getRegistrationTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 获取某场比赛的报名名单（管理员用）
     */
    @GetMapping("/competition/{competitionId}")
    public List<Map<String, Object>> getRegistrationsByCompetition(@PathVariable Long competitionId) {
        // 1. 查询报名记录
        LambdaQueryWrapper<CompetitionRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompetitionRegistration::getCompetitionId, competitionId)
                .eq(CompetitionRegistration::getStatus, "registered")
                .orderByDesc(CompetitionRegistration::getRegistrationTime);
        List<CompetitionRegistration> registrations = registrationMapper.selectList(wrapper);

        // 2. 组装返回数据（含会员信息）
        List<Map<String, Object>> result = new ArrayList<>();
        for (CompetitionRegistration reg : registrations) {
            Member member = memberMapper.selectById(reg.getMemberId());
            Map<String, Object> item = new HashMap<>();
            item.put("memberId", reg.getMemberId());
            item.put("memberName", member != null ? member.getName() : "未知会员");
            item.put("memberPhone", member != null ? member.getPhone() : "");
            item.put("registrationTime", reg.getRegistrationTime());
            result.add(item);
        }
        return result;
    }
}