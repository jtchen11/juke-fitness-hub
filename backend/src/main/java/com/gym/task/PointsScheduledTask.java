package com.gym.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gym.entity.GroupClass;
import com.gym.mapper.GroupClassMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class PointsScheduledTask {

    @Autowired private GroupClassMapper groupClassMapper;

    /**
     * 兜底清理：将所有已过结束时间但状态仍为 scheduled 的团课设为 completed
     * 说明：团课/私教积分已在签到或完成时发放，不再在定时任务中发放，避免重复
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupPastGroupClasses() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<GroupClass> gw = new LambdaQueryWrapper<>();
        gw.eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now);
        List<GroupClass> pastClasses = groupClassMapper.selectList(gw);
        if (pastClasses.isEmpty()) {
            return;
        }
        log.warn("[定时任务] cleanupPastGroupClasses: 发现 {} 个已过时间但状态仍为 scheduled 的团课", pastClasses.size());
        for (GroupClass gc : pastClasses) {
            log.warn("  - 团课ID={}, 名称={}, 结束时间={}", gc.getId(), gc.getName(), gc.getEndTime());
        }
        int updated = groupClassMapper.update(null, new LambdaUpdateWrapper<GroupClass>()
                .eq(GroupClass::getStatus, "scheduled")
                .lt(GroupClass::getEndTime, now)
                .set(GroupClass::getStatus, "completed"));
        log.warn("[定时任务] cleanupPastGroupClasses: 已更新 {} 条记录为 completed", updated);
    }
}