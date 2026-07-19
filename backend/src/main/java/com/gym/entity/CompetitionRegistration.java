package com.gym.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("competition_registration")
public class CompetitionRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long competitionId;
    private Long memberId;
    private LocalDateTime registrationTime;
    private String status;  // registered / cancelled
}