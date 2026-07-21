package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.DietRecord;
import com.gym.mapper.DietRecordMapper;
import com.gym.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/diet-records")
public class DietRecordController {

    @Autowired
    private DietRecordMapper dietRecordMapper;

    @Autowired
    private MemberMapper memberMapper;

    @GetMapping("/{memberId}")
    public Map<String, Object> list(@PathVariable Long memberId,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size,
                                     @RequestParam(required = false) String date,
                                     @RequestParam(required = false) String mealType) {
        LambdaQueryWrapper<DietRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DietRecord::getMemberId, memberId);
        if (date != null && !date.isEmpty()) {
            wrapper.eq(DietRecord::getRecordDate, date);
        }
        if (mealType != null && !mealType.isEmpty()) {
            wrapper.eq(DietRecord::getMealType, mealType);
        }
        wrapper.orderByDesc(DietRecord::getRecordDate, DietRecord::getCreatedAt);

        Page<DietRecord> pageObj = new Page<>(page, size);
        IPage<DietRecord> result = dietRecordMapper.selectPage(pageObj, wrapper);

        Map<String, Object> map = new HashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        map.put("page", result.getCurrent());
        map.put("size", result.getSize());
        return map;
    }

    @PostMapping("/{memberId}")
    public Map<String, Object> add(@PathVariable Long memberId, @RequestBody DietRecord record) {
        record.setMemberId(memberId);
        dietRecordMapper.insert(record);
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        map.put("id", record.getId());
        return map;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        dietRecordMapper.deleteById(id);
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        return map;
    }
}