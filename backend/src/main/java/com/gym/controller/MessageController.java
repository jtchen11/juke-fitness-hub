package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.UserMessage;
import com.gym.mapper.UserMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MessageController {

    @Autowired
    private UserMessageMapper messageMapper;

    // 获取未读数量
    @GetMapping("/unread-count")
    public Map<String, Object> getUnreadCount(@RequestParam Long memberId) {
        int count = messageMapper.countUnread(memberId);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return result;
    }

    // 获取所有消息（分页）
    @GetMapping
    public Map<String, Object> list(@RequestParam Long memberId,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer size) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getMemberId, memberId)
                .orderByDesc(UserMessage::getCreatedAt);

        // 这里简单返回列表（如果你需要 IPage，和之前一样用 Page 包装）
        List<UserMessage> list = messageMapper.selectList(wrapper);
        int total = list.size();
        // 简单分页（演示够用）
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<UserMessage> pageList = list.subList(start, end);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        return result;
    }

    // 标记单条已读
    @PutMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable Long id) {
        UserMessage msg = messageMapper.selectById(id);
        if (msg != null) {
            msg.setIsRead(true);
            messageMapper.updateById(msg);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    // 全部已读
    @PutMapping("/read-all")
    public Map<String, Object> markAllRead(@RequestParam Long memberId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getMemberId, memberId)
                .eq(UserMessage::getIsRead, false);
        List<UserMessage> list = messageMapper.selectList(wrapper);
        for (UserMessage msg : list) {
            msg.setIsRead(true);
            messageMapper.updateById(msg);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }
}