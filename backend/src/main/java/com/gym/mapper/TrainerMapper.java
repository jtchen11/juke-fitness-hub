package com.gym.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gym.entity.Trainer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TrainerMapper extends BaseMapper<Trainer> {
}