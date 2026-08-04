package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.controller.CheckInController;
import com.gym.entity.CheckIn;
import com.gym.entity.GroupClass;
import com.gym.entity.PersonalTraining;
import com.gym.mapper.CheckInMapper;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.MemberPrivatePackageMapper;
import com.gym.mapper.PersonalTrainingMapper;
import com.gym.mapper.TrainerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签到积分发放逻辑单元测试（Mock 数据，不依赖真实数据库课程时间）。
 * 覆盖：私教签退 +10、付费团课 +10、公益团课 +1、
 * 自主训练签退 >=40 分钟 +1（每日前 2 次）、<40 分钟不加、重复签到不重复发积分。
 */
@ExtendWith(MockitoExtension.class)
public class PointsIntegrationTest {

    private static final long MEMBER = 5L;
    private static final long CLASS = 10L;
    private static final long PT = 1L;

    @Mock private CheckInMapper checkInMapper;
    @Mock private MemberMapper memberMapper;
    @Mock private GroupClassMapper groupClassMapper;
    @Mock private PersonalTrainingMapper personalTrainingMapper;
    @Mock private TrainerMapper trainerMapper;
    @Mock private MemberPrivatePackageMapper packageMapper;
    @Mock private PointsService pointsService;

    @InjectMocks private CheckInController controller;

    // ============ 场景 1：私教签退 -> 积分 +10 ============

    @Test
    @DisplayName("私教签退(end) -> 积分 +10")
    void ptCheckOutGrants10Points() {
        PersonalTraining pt = new PersonalTraining();
        pt.setId(PT);
        pt.setMemberId(MEMBER);
        pt.setStatus("scheduled");
        when(personalTrainingMapper.selectById(PT)).thenReturn(pt);
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> r = controller.checkInPt(PT, MEMBER, "end");

        assertTrue((Boolean) r.get("success"));
        verify(pointsService).addPoints(MEMBER, 10, "PT_COMPLETED", PT, "私教完成");
        verify(personalTrainingMapper).updateById(pt);
    }

    // ============ 场景 2：付费团课签到 -> 积分 +10 ============

    @Test
    @DisplayName("付费团课签到 -> 积分 +10")
    void paidClassCheckInGrants10Points() {
        GroupClass gc = new GroupClass();
        gc.setId(CLASS);
        gc.setType("paid");
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(groupClassMapper.selectById(CLASS)).thenReturn(gc);

        controller.checkInClass(MEMBER, CLASS);

        verify(pointsService).addPoints(MEMBER, 10, "CLASS_CHECKIN", CLASS, "团课签到");
    }

    // ============ 场景 3：公益团课签到 -> 积分 +1 ============

    @Test
    @DisplayName("公益团课签到 -> 积分 +1")
    void freeClassCheckInGrants1Point() {
        GroupClass gc = new GroupClass();
        gc.setId(CLASS);
        gc.setType("free");
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(groupClassMapper.selectById(CLASS)).thenReturn(gc);

        controller.checkInClass(MEMBER, CLASS);

        verify(pointsService).addPoints(MEMBER, 1, "CLASS_CHECKIN", CLASS, "团课签到");
    }

    // ============ 场景 4：自主训练签退 >=40 分钟 -> 积分 +1（每日前 2 次） ============

    @Test
    @DisplayName("自主训练签退 >=40 分钟且当日次数<=2 -> 积分 +1")
    void normalCheckOutOver40MinGrants1Point() {
        CheckIn ci = new CheckIn();
        ci.setId(100L);
        ci.setMemberId(MEMBER);
        ci.setCheckInType("normal");
        ci.setCheckInTime(LocalDateTime.now().minusMinutes(45));
        when(checkInMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ci);
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        Map<String, Object> r = controller.checkOut(MEMBER);

        assertTrue((Boolean) r.get("success"));
        verify(pointsService).addPoints(MEMBER, 1, "NORMAL_TRAINING", 100L, "自主训练积分");
    }

    @Test
    @DisplayName("自主训练签退 >=40 分钟但当日已满 2 次 -> 不再加积分")
    void normalCheckOutOver40MinBeyondDailyLimitNoPoints() {
        CheckIn ci = new CheckIn();
        ci.setId(100L);
        ci.setMemberId(MEMBER);
        ci.setCheckInType("normal");
        ci.setCheckInTime(LocalDateTime.now().minusMinutes(45));
        when(checkInMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ci);
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        Map<String, Object> r = controller.checkOut(MEMBER);

        assertTrue((Boolean) r.get("success"));
        verify(pointsService, never()).addPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    // ============ 场景 5：自主训练签退 <40 分钟 -> 积分不变 ============

    @Test
    @DisplayName("自主训练签退 <40 分钟 -> 不加积分")
    void normalCheckOutUnder40MinNoPoints() {
        CheckIn ci = new CheckIn();
        ci.setId(100L);
        ci.setMemberId(MEMBER);
        ci.setCheckInType("normal");
        ci.setCheckInTime(LocalDateTime.now().minusMinutes(30));
        when(checkInMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ci);

        Map<String, Object> r = controller.checkOut(MEMBER);

        assertTrue((Boolean) r.get("success"));
        verify(pointsService, never()).addPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    // ============ 场景 6：同一会员同一课程不能重复获得积分 ============

    @Test
    @DisplayName("团课重复签到 -> 拦截且不重复发积分")
    void classCheckInDuplicateBlocked() {
        GroupClass gc = new GroupClass();
        gc.setId(CLASS);
        gc.setType("paid");
        // 第一次签到：无重复记录，正常发积分
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(groupClassMapper.selectById(CLASS)).thenReturn(gc);
        controller.checkInClass(MEMBER, CLASS);
        verify(pointsService).addPoints(MEMBER, 10, "CLASS_CHECKIN", CLASS, "团课签到");

        // 第二次签到同一课程：已存在记录，拦截且不再发积分
        reset(checkInMapper);
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        clearInvocations(pointsService);
        Map<String, Object> r = controller.checkInClass(MEMBER, CLASS);

        assertFalse((Boolean) r.get("success"));
        verify(pointsService, never()).addPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("私教重复签退(end) -> 拦截且不重复发积分")
    void ptCheckOutDuplicateBlocked() {
        PersonalTraining pt = new PersonalTraining();
        pt.setId(PT);
        pt.setMemberId(MEMBER);
        pt.setStatus("scheduled");
        when(personalTrainingMapper.selectById(PT)).thenReturn(pt);
        when(checkInMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        Map<String, Object> r = controller.checkInPt(PT, MEMBER, "end");

        assertFalse((Boolean) r.get("success"));
        verify(pointsService, never()).addPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }
}
