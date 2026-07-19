package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.Competition;
import com.gym.mapper.CompetitionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompetitionService {

    @Autowired
    private CompetitionMapper competitionMapper;

    /**
     * 分页查询比赛（管理员端）
     */
    public IPage<Competition> pageQuery(Integer page, Integer size, String keyword, String status) {
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Competition::getName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Competition::getStatus, status);
        }
        wrapper.orderByDesc(Competition::getCreatedAt);
        return competitionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 获取所有已上架且报名中的比赛（会员端）
     */
    public List<Competition> getActiveCompetitions() {
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Competition::getIsActive, true)
                .eq(Competition::getStatus, "open")
                .ge(Competition::getDeadline, LocalDateTime.now())
                .orderByAsc(Competition::getDeadline);
        return competitionMapper.selectList(wrapper);
    }

    public Competition getById(Long id) {
        return competitionMapper.selectById(id);
    }

    public boolean save(Competition competition) {
        if (competition.getEnrolled() == null) {
            competition.setEnrolled(0);
        }
        return competitionMapper.insert(competition) > 0;
    }

    public boolean update(Competition competition) {
        return competitionMapper.updateById(competition) > 0;
    }

    public boolean delete(Long id) {
        return competitionMapper.deleteById(id) > 0;
    }

    /**
     * 报名人数+1
     */
    public void incrementEnrolled(Long competitionId) {
        Competition competition = competitionMapper.selectById(competitionId);
        if (competition != null) {
            competition.setEnrolled((competition.getEnrolled() == null ? 0 : competition.getEnrolled()) + 1);
            competitionMapper.updateById(competition);
        }
    }
}