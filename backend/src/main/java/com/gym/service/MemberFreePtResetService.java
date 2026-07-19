package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gym.entity.Member;
import com.gym.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
public class MemberFreePtResetService {

    @Autowired
    private MemberMapper memberMapper;

    /**
     * 每月1日凌晨0点重置所有会员的免费私教次数
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    public void resetFreePt() {
        LambdaUpdateWrapper<Member> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Member::getFreePtUsedMonth, 0)
                .set(Member::getFreePtMonthReset, LocalDate.now());
        int updated = memberMapper.update(null, wrapper);
        log.info("每月免费私教次数重置完成，更新记录数：{}", updated);
    }
}