package com.gym.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gym.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {

    @Select("SELECT COUNT(*) FROM user_message WHERE member_id = #{memberId} AND is_read = 0")
    int countUnread(Long memberId);
}