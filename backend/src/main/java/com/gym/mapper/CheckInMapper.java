package com.gym.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gym.entity.CheckIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface CheckInMapper extends BaseMapper<CheckIn> {

    /**
     * 统计会员本月签到次数
     */
    @Select("SELECT COUNT(*) FROM check_in WHERE member_id = #{memberId} AND check_in_time >= #{startOfMonth}")
    int countThisMonth(Long memberId, LocalDateTime startOfMonth);

    /**
     * 统计会员总签到次数
     */
    @Select("SELECT COUNT(*) FROM check_in WHERE member_id = #{memberId}")
    int countTotal(Long memberId);
}