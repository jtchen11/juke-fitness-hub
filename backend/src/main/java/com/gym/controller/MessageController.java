package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.Member;
import com.gym.entity.UserMessage;
import com.gym.mapper.MemberMapper;
import com.gym.mapper.UserMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MessageController {

    @Autowired
    private UserMessageMapper messageMapper;

    @Autowired
    private MemberMapper memberMapper;

    // 获取未读数量：传 memberId 查指定会员，不传则查全局（管理端）
    @GetMapping("/unread-count")
    public Map<String, Object> getUnreadCount(@RequestParam(required = false) Long memberId) {
        int count;
        if (memberId != null) {
            count = messageMapper.countUnread(memberId);
        } else {
            count = Math.toIntExact(messageMapper.selectCount(
                    new LambdaQueryWrapper<UserMessage>().eq(UserMessage::getIsRead, false)));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return result;
    }

    // 获取消息列表（分页）：传 memberId 查指定会员，不传则查全局（管理端）
    // 支持 keyword（会员姓名/手机号）与 isRead（true/false）筛选
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Long memberId,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Boolean isRead,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer size) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        if (memberId != null) {
            wrapper.eq(UserMessage::getMemberId, memberId);
        }
        if (isRead != null) {
            wrapper.eq(UserMessage::getIsRead, isRead);
        }
        if (keyword != null && !keyword.isEmpty()) {
            List<Member> matchedMembers = memberMapper.selectList(
                    new LambdaQueryWrapper<Member>()
                            .like(Member::getName, keyword).or().like(Member::getPhone, keyword));
            List<Long> memberIds = matchedMembers.stream().map(Member::getId).collect(Collectors.toList());
            if (memberIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", Collections.emptyList());
                empty.put("total", 0L);
                return empty;
            }
            wrapper.in(UserMessage::getMemberId, memberIds);
        }
        wrapper.orderByDesc(UserMessage::getCreatedAt);

        IPage<UserMessage> pageResult = messageMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserMessage m : pageResult.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("memberId", m.getMemberId());
            item.put("content", m.getContent());
            item.put("isRead", m.getIsRead());
            item.put("createdAt", m.getCreatedAt());
            Member member = m.getMemberId() != null ? memberMapper.selectById(m.getMemberId()) : null;
            item.put("memberName", member != null ? member.getName() : "会员#" + m.getMemberId());
            item.put("memberPhone", member != null ? member.getPhone() : "");
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
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