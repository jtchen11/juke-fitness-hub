package com.gym.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gym.entity.Competition;
import com.gym.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/competitions")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;

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

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        boolean success = competitionService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "删除成功" : "删除失败");
        return result;
    }
}