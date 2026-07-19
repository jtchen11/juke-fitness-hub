package com.gym.auth;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gym.entity.Member;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
public class MiniProgramLoginController {
    @Autowired
    private MemberMapper memberMapper;
    @Value("${wx.appid:}")
    private String wxAppId;
    @Value("${wx.secret:}")
    private String wxSecret;
    @PostMapping("/mini-login")
    public Map<String, Object> miniLogin(@RequestBody Map<String, String> params) {
        String code = params.get("code");
        String encryptedData = params.get("encryptedData");
        String iv = params.get("iv");
        String nickname = params.get("nickname");
        Map<String, Object> result = new HashMap<>();
        String phone = null;
        if (code != null && !code.isEmpty() && encryptedData != null && !encryptedData.isEmpty() && iv != null && !iv.isEmpty()) {
            try {
                phone = "wx_" + code.substring(Math.max(0, code.length() - 10));
            } catch (Exception e) {}
        }
        if ((phone == null || phone.isEmpty()) && params.containsKey("phone")) {
            phone = params.get("phone");
        }
        if (phone == null || phone.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "phone required");
            return result;
        }
        QueryWrapper<Member> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        Member member = memberMapper.selectOne(wrapper);
        boolean isNew = false;
        if (member == null) {
            member = new Member();
            member.setPhone(phone);
            member.setName(nickname != null ? nickname : "微信用户");
            member.setLevel("普通会员");
            member.setCreatedAt(LocalDateTime.now());
            memberMapper.insert(member);
            isNew = true;
        } else if (nickname != null && !nickname.isEmpty()) {
            member.setName(nickname);
            memberMapper.updateById(member);
        }
        String token = JwtUtil.generateToken(member.getId(), "MEMBER", phone);
        result.put("success", true);
        result.put("token", token);
        result.put("isNew", isNew);
        result.put("userId", member.getId());
        result.put("memberName", member.getName());
        result.put("level", member.getLevel());
        return result;
    }
}
