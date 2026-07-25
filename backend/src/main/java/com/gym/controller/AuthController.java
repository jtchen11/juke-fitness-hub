package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gym.auth.JwtUtil;
import com.gym.entity.Member;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.TrainerMapper;
import com.gym.entity.Trainer;
import com.gym.service.SmsCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private SmsCodeService smsCodeService;
    @Autowired private MemberMapper memberMapper;
    @Autowired private TrainerMapper trainerMapper;

    @PostMapping("/send-code")
    public Map<String, Object> sendCode(@RequestBody Map<String, String> params) {
        String phone = params.get("phone");
        Map<String, Object> result = new HashMap<>();
        if (phone == null || !phone.matches("\\d{11}")) {
            result.put("code", 400);
            result.put("message", "手机号格式不正确");
            return result;
        }
        String code = smsCodeService.generateCode(phone);
        result.put("code", 200);
        result.put("message", "验证码已发送");
        Map<String, Object> data = new HashMap<>();
        data.put("expireIn", 300);
        result.put("data", data);
        return result;
    }

    @PostMapping("/login-by-code")
    public Map<String, Object> loginByCode(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
        String phone = params.get("phone");
        String code = params.get("code");
        String nickname = params.get("nickname");

        if (phone == null || code == null) {
            result.put("code", 400);
            result.put("message", "手机号和验证码不能为空");
            return result;
        }

        if (!smsCodeService.verifyCode(phone, code)) {
            result.put("code", 401);
            result.put("message", "验证码错误或已过期");
            return result;
        }

        smsCodeService.removeCode(phone);

        QueryWrapper<Member> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        Member member = memberMapper.selectOne(wrapper);

        // 同时查询 member 和 trainer 表
        QueryWrapper<Trainer> tw = new QueryWrapper<>();
        tw.eq("phone", phone);
        Trainer trainer = trainerMapper.selectOne(tw);

        boolean isNew = false;
        String role;
        if (member == null && trainer == null) {
            member = new Member();
            member.setPhone(phone);
            member.setName(nickname != null && !nickname.isEmpty() ? nickname : "新用户");
            member.setLevel("访客");
            member.setCreatedAt(LocalDateTime.now());
            member.setExperienceUsed(false);
            memberMapper.insert(member);
            isNew = true;
            role = "member";
        } else if (member != null && trainer == null) {
            role = "member";
            if (nickname != null && !nickname.isEmpty()) {
                member.setName(nickname);
                memberMapper.updateById(member);
            }
        } else if (member == null && trainer != null) {
            role = "trainer";
            // 自动为教练创建会员记录（memberId 不为 null 才能使用会员端）
            member = new Member();
            member.setPhone(phone);
            member.setName(trainer.getName());
            member.setLevel("普通会员");
            member.setExpireDate(java.time.LocalDate.now().plusYears(10));
            member.setCreatedAt(LocalDateTime.now());
            memberMapper.insert(member);
        } else {
            role = "both";
            if (nickname != null && !nickname.isEmpty()) {
                member.setName(nickname);
                memberMapper.updateById(member);
            }
        }

        long uid = member != null ? member.getId() : trainer.getId();
        String token = JwtUtil.generateToken(uid, role, phone);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("memberId", member != null ? member.getId() : null);
        userInfo.put("trainerId", trainer != null ? trainer.getId() : null);
        userInfo.put("phone", phone);
        userInfo.put("nickname", member != null ? member.getName() : trainer.getName());
        userInfo.put("level", member != null ? member.getLevel() : "教练");
        userInfo.put("role", role);
        userInfo.put("isActiveMember", member != null ? member.isActiveMember() : false);
        userInfo.put("expireDate", member != null ? member.getExpireDate() : null);
        userInfo.put("experienceUsed", member != null ? member.getExperienceUsed() : null);

        result.put("code", 200);
        result.put("token", token);
        result.put("userInfo", userInfo);
        return result;
        } catch (Exception e) {
            log.error("login-by-code error", e);
            result.put("code", 500);
            result.put("message", "登录失败，请联系管理员");
            return result;
        }
    }
}
