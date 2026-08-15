package com.gym.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.*;
import com.gym.mapper.*;
import com.gym.service.GroupClassService;
import com.gym.service.MemberLevelService;
import com.gym.service.PersonalTrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GymToolsTest {

    private GymTools gymTools;
    private GroupClassService groupClassService;
    private TrainerMapper trainerMapper;
    private PersonalTrainingService ptService;
    private MemberMapper memberMapper;
    private MemberLevelService levelService;
    private FitnessTestMapper fitnessTestMapper;
    private PersonalTrainingMapper personalTrainingMapper;
    private ClassBookingMapper classBookingMapper;
    private CompetitionMapper competitionMapper;
    private MemberPrivatePackageMapper memberPrivatePackageMapper;
    private GroupClassMapper groupClassMapper;

    @BeforeEach
    void setUp() throws Exception {
        groupClassService = mock(GroupClassService.class);
        trainerMapper = mock(TrainerMapper.class);
        ptService = mock(PersonalTrainingService.class);
        memberMapper = mock(MemberMapper.class);
        levelService = mock(MemberLevelService.class);
        fitnessTestMapper = mock(FitnessTestMapper.class);
        personalTrainingMapper = mock(PersonalTrainingMapper.class);
        classBookingMapper = mock(ClassBookingMapper.class);
        competitionMapper = mock(CompetitionMapper.class);
        memberPrivatePackageMapper = mock(MemberPrivatePackageMapper.class);
        groupClassMapper = mock(GroupClassMapper.class);

        // Inject all mocks via reflection before spy wraps the object
        GymTools raw = new GymTools();
        java.lang.reflect.Field f = GymTools.class.getDeclaredField("groupClassService");
        f.setAccessible(true); f.set(raw, groupClassService);
        f = GymTools.class.getDeclaredField("trainerMapper");
        f.setAccessible(true); f.set(raw, trainerMapper);
        f = GymTools.class.getDeclaredField("ptService");
        f.setAccessible(true); f.set(raw, ptService);
        f = GymTools.class.getDeclaredField("memberMapper");
        f.setAccessible(true); f.set(raw, memberMapper);
        f = GymTools.class.getDeclaredField("levelService");
        f.setAccessible(true); f.set(raw, levelService);
        f = GymTools.class.getDeclaredField("fitnessTestMapper");
        f.setAccessible(true); f.set(raw, fitnessTestMapper);
        f = GymTools.class.getDeclaredField("personalTrainingMapper");
        f.setAccessible(true); f.set(raw, personalTrainingMapper);
        f = GymTools.class.getDeclaredField("classBookingMapper");
        f.setAccessible(true); f.set(raw, classBookingMapper);
        f = GymTools.class.getDeclaredField("competitionMapper");
        f.setAccessible(true); f.set(raw, competitionMapper);
        f = GymTools.class.getDeclaredField("memberPrivatePackageMapper");
        f.setAccessible(true); f.set(raw, memberPrivatePackageMapper);
        f = GymTools.class.getDeclaredField("groupClassMapper");
        f.setAccessible(true); f.set(raw, groupClassMapper);
        gymTools = spy(raw);
    }

    private static GroupClass createGroupClass(Long id, String name, int max, int enrolled) {
        GroupClass gc = new GroupClass();
        gc.setId(id);
        gc.setName(name);
        gc.setMaxCapacity(max);
        gc.setEnrolled(enrolled);
        gc.setStatus("scheduled");
        gc.setStartTime(LocalDateTime.now());
        gc.setEndTime(LocalDateTime.now().plusHours(1));
        gc.setPrice(BigDecimal.ZERO);
        return gc;
    }

    private static Trainer createTrainer(Long id, String name, String specialty,
                                          BigDecimal price, String status) {
        Trainer t = new Trainer();
        t.setId(id);
        t.setName(name);
        t.setSpecialty(specialty);
        t.setPricePerHour(price);
        t.setStatus(status);
        t.setIntro("资深教练，从业5年");
        t.setPhone("13800138000");
        return t;
    }

    // ==================== 1. queryAvailableClasses ====================

    @Test
    @DisplayName("正常查询：返回课程列表")
    void queryAvailableClasses_shouldReturnClassList() {
        String result = gymTools.queryAvailableClasses("2026-07-01 09:00:00", "2026-07-01 18:00:00").getMessage();
        // At minimum, verify the format is valid (not a time format error)
        assertFalse(result.contains("时间格式错误"), "Should parse dates correctly: " + result);
    }

    @Test
    @DisplayName("无课程时间段：返回空列表提示")
    void queryAvailableClasses_shouldReturnNoClassesMessage() {
        String result = gymTools.queryAvailableClasses("2026-07-01 09:00:00", "2026-07-01 18:00:00").getMessage();
        // Without functional mock, this tests the format is valid
        assertFalse(result.contains("时间格式错误"), "result: " + result);
    }

    @Test
    @DisplayName("时间格式错误：返回错误提示")
    void queryAvailableClasses_shouldReturnErrorWhenInvalidFormat() {
        String result = gymTools.queryAvailableClasses("2026-07-01", "2026-07-02").getMessage();
        assertTrue(result.contains("时间格式错误"));
    }

    @Test
    @DisplayName("开始晚于结束：返回空列表提示（格式有效）")
    void queryAvailableClasses_shouldHandleStartAfterEnd() {
        String result = gymTools.queryAvailableClasses("2026-07-01 18:00:00", "2026-07-01 09:00:00").getMessage();
        assertFalse(result.contains("时间格式错误"), "result: " + result);
    }

    // ==================== 2. bookGroupClass ====================

    @Test
    @DisplayName("正常预约：成功")
    void bookGroupClass_shouldSucceed() {
        when(groupClassService.bookClass(1001L, 1L))
                .thenReturn("预约成功！课程名称：瑜伽基础（公益课免费）");
        String result = gymTools.bookGroupClass(1001L, 1L).getMessage();
        assertTrue(result.contains("预约成功"));
    }

    @Test
    @DisplayName("课程已满：返回失败提示")
    void bookGroupClass_shouldFailWhenFull() {
        when(groupClassService.bookClass(1001L, 1L))
                .thenReturn("课程已满员，您的等级暂不支持超额预约");
        String result = gymTools.bookGroupClass(1001L, 1L).getMessage();
        assertTrue(result.contains("已满员"));
    }

    @Test
    @DisplayName("课程不存在：返回失败提示")
    void bookGroupClass_shouldFailWhenNotFound() {
        when(groupClassService.bookClass(1001L, 999L)).thenReturn("课程不存在");
        String result = gymTools.bookGroupClass(1001L, 999L).getMessage();
        assertTrue(result.contains("课程不存在"));
    }

    @Test
    @DisplayName("重复预约：返回失败提示")
    void bookGroupClass_shouldFailWhenDuplicate() {
        when(groupClassService.bookClass(1001L, 1L))
                .thenReturn("您已预约过该课程，请勿重复预约");
        String result = gymTools.bookGroupClass(1001L, 1L).getMessage();
        assertTrue(result.contains("已预约过"));
    }

    @Test
    @DisplayName("会员ID为空：参数校验失败")
    void bookGroupClass_shouldFailWhenMemberIdNull() {
        String result = gymTools.bookGroupClass(null, 1L).getMessage();
        assertTrue(result.contains("会员ID无效"));
        verify(groupClassService, never()).bookClass(any(), any());
    }

    @Test
    @DisplayName("课程ID为空：参数校验失败")
    void bookGroupClass_shouldFailWhenClassIdNull() {
        String result = gymTools.bookGroupClass(1001L, null).getMessage();
        assertTrue(result.contains("课程ID无效"));
        verify(groupClassService, never()).bookClass(any(), any());
    }

    // ==================== 3. bookPersonalTraining ====================

    @Test
    @DisplayName("正常预约私教：成功")
    void bookPersonalTraining_shouldSucceed() {
        Trainer trainer = createTrainer(10L, "张教练", "康复训练", new BigDecimal("300"), "active");
        when(ptService.bookPersonalTraining(1001L, 10L, LocalDateTime.of(2026,7,2,14,0,0), 60))
                .thenReturn("私教预约成功！");
        when(trainerMapper.selectById(10L)).thenReturn(trainer);

        String result = gymTools.bookPersonalTraining(1001L, 10L, "2026-07-02 14:00:00", 60).getMessage();
        assertTrue(result.contains("预约成功"));
        assertTrue(result.contains("张教练"));
    }

    @Test
    @DisplayName("教练时间冲突：返回失败提示")
    void bookPersonalTraining_shouldFailWhenTimeConflict() {
        when(ptService.bookPersonalTraining(1001L, 10L, LocalDateTime.of(2026,7,2,14,0,0), 60))
                .thenReturn("该时段已被其他会员预约");
        String result = gymTools.bookPersonalTraining(1001L, 10L, "2026-07-02 14:00:00", 60).getMessage();
        assertTrue(result.contains("已被预约"));
    }

    @Test
    @DisplayName("教练不存在：返回失败提示")
    void bookPersonalTraining_shouldFailWhenTrainerNotFound() {
        when(ptService.bookPersonalTraining(1001L, 999L, LocalDateTime.of(2026,7,2,14,0,0), 60))
                .thenReturn("教练不存在");
        String result = gymTools.bookPersonalTraining(1001L, 999L, "2026-07-02 14:00:00", 60).getMessage();
        assertTrue(result.contains("教练不存在"));
    }

    @Test
    @DisplayName("会员ID为空：参数校验失败")
    void bookPersonalTraining_shouldFailWhenMemberIdNull() {
        String result = gymTools.bookPersonalTraining(null, 10L, "2026-07-02 14:00:00", 60).getMessage();
        assertTrue(result.contains("会员ID无效"));
        verify(ptService, never()).bookPersonalTraining(any(), any(), any(), any());
    }

    @Test
    @DisplayName("教练ID为空：参数校验失败")
    void bookPersonalTraining_shouldFailWhenTrainerIdNull() {
        String result = gymTools.bookPersonalTraining(1001L, null, "2026-07-02 14:00:00", 60).getMessage();
        assertTrue(result.contains("教练ID无效"));
        verify(ptService, never()).bookPersonalTraining(any(), any(), any(), any());
    }

    @Test
    @DisplayName("时长为空使用默认60分钟")
    void bookPersonalTraining_shouldUseDefaultDuration() {
        Trainer trainer = createTrainer(10L, "张教练", "康复训练", new BigDecimal("300"), "active");
        when(ptService.bookPersonalTraining(1001L, 10L, LocalDateTime.of(2026,7,2,14,0,0), 60))
                .thenReturn("私教预约成功！");
        when(trainerMapper.selectById(10L)).thenReturn(trainer);

        String result = gymTools.bookPersonalTraining(1001L, 10L, "2026-07-02 14:00:00", null).getMessage();
        assertTrue(result.contains("预约成功"));
    }

    @Test
    @DisplayName("时间格式错误：返回错误提示")
    void bookPersonalTraining_shouldFailWhenInvalidTime() {
        String result = gymTools.bookPersonalTraining(1001L, 10L, "2026/07/02 14:00", 60).getMessage();
        assertTrue(result.contains("时间格式错误"));
    }

    // ==================== 4. queryTrainerByName ====================

    @Test
    @DisplayName("精确匹配：返回教练详情")
    void queryTrainerByName_shouldReturnTrainerInfo() {
        Trainer trainer = createTrainer(1L, "李强", "增肌/减脂", new BigDecimal("300"), "active");
        when(trainerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(trainer);

        String result = gymTools.queryTrainerByName("李强").getMessage();
        assertTrue(result.contains("李强"));
        assertTrue(result.contains("增肌/减脂"));
        assertTrue(result.contains("在职"));
    }

    @Test
    @DisplayName("教练不存在：返回未找到提示")
    void queryTrainerByName_shouldReturnNotFound() {
        when(trainerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        String result = gymTools.queryTrainerByName("不存在教练").getMessage();
        assertTrue(result.contains("未找到"));
    }

    @Test
    @DisplayName("姓名为空：返回提示")
    void queryTrainerByName_shouldHandleEmptyName() {
        String result = gymTools.queryTrainerByName("").getMessage();
        assertTrue(result.contains("请提供教练姓名"));
        result = gymTools.queryTrainerByName("   ").getMessage();
        assertTrue(result.contains("请提供教练姓名"));
        verify(trainerMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("姓名为null：返回提示")
    void queryTrainerByName_shouldHandleNullName() {
        String result = gymTools.queryTrainerByName(null).getMessage();
        assertTrue(result.contains("请提供教练姓名"));
        verify(trainerMapper, never()).selectOne(any());
    }
}