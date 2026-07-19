package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gym.dto.LoginRequest;
import com.gym.entity.AdminUser;
import com.gym.entity.Member;
import com.gym.mapper.AdminUserMapper;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(
        originPatterns = "*",
        allowCredentials = "true",
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class LoginController {

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private MemberMapper memberMapper;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        String role = request.getRole();
        String username = request.getUsername();
        String password = request.getPassword();

        if ("ADMIN".equals(role)) {
            // 管理员登录（使用 QueryWrapper 避免 Lambda 解析问题）
            QueryWrapper<AdminUser> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username);
            AdminUser user = adminUserMapper.selectOne(wrapper);

            if (user == null) {
                result.put("success", false);
                result.put("message", "管理员账号不存在");
                return result;
            }

            if ("disabled".equals(user.getStatus())) {
                result.put("success", false);
                result.put("message", "账号已被禁用");
                return result;
            }

            // 明文密码比对（假设数据库中 password 存的是明文）
            if (!password.equals(user.getPassword())) {
                result.put("success", false);
                result.put("message", "密码错误");
                return result;
            }

            session.setAttribute("adminId", user.getId());
            session.setAttribute("adminName", user.getNickname());
            session.setAttribute("role", "ADMIN");
            session.setAttribute("userId", user.getId());

            result.put("success", true);
            result.put("message", "登录成功");
            result.put("role", "ADMIN");
            result.put("adminName", user.getNickname());
            result.put("userId", user.getId());

        } else if ("MEMBER".equals(role)) {
            // 会员登录（用手机号）
            QueryWrapper<Member> wrapper = new QueryWrapper<>();
            wrapper.eq("phone", username);
            Member member = memberMapper.selectOne(wrapper);

            if (member == null) {
                result.put("success", false);
                result.put("message", "会员账号不存在");
                return result;
            }

            // 会员默认密码：member123
            if (!"member123".equals(password)) {
                result.put("success", false);
                result.put("message", "密码错误（默认密码：member123）");
                return result;
            }

            session.setAttribute("memberId", member.getId());
            session.setAttribute("memberName", member.getName());
            session.setAttribute("role", "MEMBER");
            session.setAttribute("userId", member.getId());

            result.put("success", true);
            result.put("message", "登录成功");
            result.put("role", "MEMBER");
            result.put("memberName", member.getName());
            result.put("userId", member.getId());

        } else {
            result.put("success", false);
            result.put("message", "请选择登录角色");
        }

        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已退出");
        return result;
    }

    @GetMapping("/check")
    public Map<String, Object> check(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Long adminId = (Long) session.getAttribute("adminId");
        if (adminId != null) {
            result.put("loggedIn", true);
            result.put("role", "ADMIN");
            result.put("adminName", session.getAttribute("adminName"));
            return result;
        }

        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId != null) {
            result.put("loggedIn", true);
            result.put("role", "MEMBER");
            result.put("memberName", session.getAttribute("memberName"));
            return result;
        }

        result.put("loggedIn", false);
        return result;
    }
}