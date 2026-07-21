package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.PointsReward;
import com.gym.mapper.PointsRewardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/points/rewards")
public class PointsRewardController {

    @Autowired private PointsRewardMapper rewardMapper;

    @GetMapping
    public IPage<PointsReward> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        LambdaQueryWrapper<PointsReward> w = new LambdaQueryWrapper<>();
        w.orderByAsc(PointsReward::getSortOrder)
                .orderByDesc(PointsReward::getCreatedAt);
        return rewardMapper.selectPage(new Page<>(page, size), w);
    }

    @GetMapping("/{id}")
    public PointsReward get(@PathVariable Long id) {
        return rewardMapper.selectById(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody PointsReward reward) {
        reward.setCreatedAt(LocalDateTime.now());
        reward.setUpdatedAt(LocalDateTime.now());
        if (reward.getIsActive() == null) reward.setIsActive(true);
        rewardMapper.insert(reward);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("data", reward);
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody PointsReward reward) {
        reward.setId(id);
        reward.setUpdatedAt(LocalDateTime.now());
        rewardMapper.updateById(reward);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        return r;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        rewardMapper.deleteById(id);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        return r;
    }
}