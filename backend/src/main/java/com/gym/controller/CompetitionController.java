package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gym.entity.Competition;
import com.gym.entity.CompetitionRegistration;
import com.gym.mapper.CompetitionRegistrationMapper;
import com.gym.service.CompetitionService;
import com.gym.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/competitions")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;

    @Autowired
    private CompetitionRegistrationMapper registrationMapper;

    @Autowired
    private PointsService pointsService;

    /**
     * 管理员端：分页查询
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        IPage<Competition> pageResult = competitionService.pageQuery(page, size, keyword, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
    }

    /**
     * 会员端：获取所有进行中的比赛
     */
    @GetMapping("/active")
    public List<Competition> getActive() {
        return competitionService.getActiveCompetitions();
    }

    @GetMapping("/{id}")
    public Competition getById(@PathVariable Long id) {
        return competitionService.getById(id);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Competition competition) {
        boolean success = competitionService.save(competition);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "添加成功" : "添加失败");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Competition competition) {
        competition.setId(id);
        boolean success = competitionService.update(competition);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "更新成功" : "更新失败");
        return result;
    }

    /**
     * 发放比赛获奖积分（第一期）
     * body: { "winners": [ { "memberId": 1, "rank": 1 } ] }  rank: 1=冠军 2=亚军 3=季军
     * 规则：冠军/亚军/季军按档位加分，其余已报名参赛者自动获得参与积分
     */
    @PostMapping("/{id}/grant-rewards")
    @Transactional
    public Map<String, Object> grantRewards(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        Competition competition = competitionService.getById(id);
        if (competition == null) {
            result.put("success", false);
            result.put("message", "比赛不存在");
            return result;
        }
        if (competition.getRewardGranted() != null && competition.getRewardGranted()) {
            result.put("success", false);
            result.put("message", "奖励已发放，不可重复操作");
            return result;
        }

        // 解析获奖名单
        List<Map<String, Object>> winners = (List<Map<String, Object>>) request.get("winners");
        if (winners == null || winners.isEmpty()) {
            result.put("success", false);
            result.put("message", "请先选择获奖参赛者");
            return result;
        }
        Map<Long, Integer> winnerRanks = new LinkedHashMap<>();
        Set<Integer> rankSet = new HashSet<>();
        for (Map<String, Object> w : winners) {
            Object mid = w.get("memberId");
            Object rank = w.get("rank");
            if (mid == null || rank == null) {
                continue;
            }
            long memberId = Long.parseLong(mid.toString());
            int r = Integer.parseInt(rank.toString());
            if (r < 1 || r > 3) {
                result.put("success", false);
                result.put("message", "名次无效（仅支持冠军/亚军/季军）");
                return result;
            }
            if (!rankSet.add(r)) {
                result.put("success", false);
                result.put("message", "同一名次只能选择一名参赛者");
                return result;
            }
            winnerRanks.put(memberId, r);
        }
        if (winnerRanks.isEmpty()) {
            result.put("success", false);
            result.put("message", "请先选择获奖参赛者");
            return result;
        }

        // 校验获奖者均已报名
        LambdaQueryWrapper<CompetitionRegistration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(CompetitionRegistration::getCompetitionId, id)
                .eq(CompetitionRegistration::getStatus, "registered");
        List<CompetitionRegistration> regs = registrationMapper.selectList(regWrapper);
        Map<Long, Boolean> registeredMap = new HashMap<>();
        for (CompetitionRegistration reg : regs) {
            registeredMap.put(reg.getMemberId(), true);
        }
        for (Long memberId : winnerRanks.keySet()) {
            if (!registeredMap.containsKey(memberId)) {
                result.put("success", false);
                result.put("message", "参赛者未报名该比赛，无法发放奖励");
                return result;
            }
        }

        // 发放积分
        int champion = competition.getChampionPoints() == null ? 0 : competition.getChampionPoints();
        int runnerUp = competition.getRunnerUpPoints() == null ? 0 : competition.getRunnerUpPoints();
        int third = competition.getThirdPlacePoints() == null ? 0 : competition.getThirdPlacePoints();
        int participation = competition.getParticipationPoints() == null ? 0 : competition.getParticipationPoints();
        for (Map.Entry<Long, Integer> entry : winnerRanks.entrySet()) {
            int points = entry.getValue() == 1 ? champion : (entry.getValue() == 2 ? runnerUp : third);
            if (points <= 0) continue;
            String rankName = entry.getValue() == 1 ? "冠军" : (entry.getValue() == 2 ? "亚军" : "季军");
            pointsService.addPoints(entry.getKey(), points, "competition_reward", id,
                    "比赛奖励-" + rankName + ": " + competition.getName());
        }
        for (CompetitionRegistration reg : regs) {
            if (!winnerRanks.containsKey(reg.getMemberId()) && participation > 0) {
                pointsService.addPoints(reg.getMemberId(), participation, "competition_reward", id,
                        "比赛参与奖: " + competition.getName());
            }
        }

        // 标记已发放
        competition.setRewardGranted(true);
        competitionService.update(competition);

        result.put("success", true);
        result.put("message", "奖励发放成功");
        result.put("grantedCount", winnerRanks.size());
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        boolean success = competitionService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "删除成功" : "删除失败");
        return result;
    }
}