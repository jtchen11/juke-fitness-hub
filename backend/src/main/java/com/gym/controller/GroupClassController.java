package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.GroupClass;
import com.gym.entity.Trainer;
import com.gym.mapper.GroupClassMapper;
import com.gym.mapper.TrainerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classes")
@Slf4j
public class GroupClassController {

    @Autowired
    private GroupClassMapper groupClassMapper;

    @Autowired
    private TrainerMapper trainerMapper;

    /**
     * 分页查询团课列表（支持关键词、教练、状态、类型筛选）
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String trainerId,   // 参数名改为 trainerId，与前端一致
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String date) {

        try {
        LambdaQueryWrapper<GroupClass> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索（课程名称）
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(GroupClass::getName, keyword);
        }

        // 教练筛选（直接使用 trainerId）
        if (trainerId != null && !trainerId.isEmpty()) {
            try {
                wrapper.eq(GroupClass::getTrainerId, Long.parseLong(trainerId));
            } catch (NumberFormatException e) {
                // 忽略无效数字
            }
        }

        // 状态筛选
        if (status != null && !status.isEmpty()) {
            wrapper.eq(GroupClass::getStatus, status);
        }

        // 类型筛选（付费/公益）
        if (type != null && !type.isEmpty()) {
            wrapper.eq(GroupClass::getType, type);
        }

        // 日期筛选（查询指定日期的课程）
        if (date != null && !date.isEmpty()) {
            try {
                LocalDate targetDate = LocalDate.parse(date);
                wrapper.ge(GroupClass::getStartTime, targetDate.atStartOfDay())
                        .le(GroupClass::getStartTime, targetDate.atTime(23, 59, 59));
            } catch (Exception e) {
                // 日期格式无效时忽略
            }
        }

        // 按开始时间倒序
        wrapper.orderByDesc(GroupClass::getStartTime);

        // 分页查询
        IPage<GroupClass> pageResult = groupClassMapper.selectPage(
                new Page<>(page, size), wrapper
        );

        // 补充教练姓名
        pageResult.getRecords().forEach(gc -> {
            if (gc.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(gc.getTrainerId());
                if (trainer != null) {
                    gc.setTrainerName(trainer.getName());
                }
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
        } catch (Exception e) {
            log.error("list classes error", e);
            Map<String, Object> result = new HashMap<>();
            result.put("list", java.util.Collections.emptyList());
            result.put("total", 0);
            return result;
        }
    }

    @GetMapping("/all")
    public List<GroupClass> getAll() {
        return groupClassMapper.selectList(null);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = groupClassMapper.selectCount(null);
        long scheduled = groupClassMapper.selectCount(
                new LambdaQueryWrapper<GroupClass>().eq(GroupClass::getStatus, "scheduled")
        );
        long completed = groupClassMapper.selectCount(
                new LambdaQueryWrapper<GroupClass>().eq(GroupClass::getStatus, "completed")
        );
        long cancelled = groupClassMapper.selectCount(
                new LambdaQueryWrapper<GroupClass>().eq(GroupClass::getStatus, "cancelled")
        );

        List<GroupClass> allClasses = groupClassMapper.selectList(null);
        long totalEnrolled = allClasses.stream()
                .mapToInt(gc -> gc.getEnrolled() != null ? gc.getEnrolled() : 0)
                .sum();
        long totalCapacity = allClasses.stream()
                .mapToInt(GroupClass::getMaxCapacity)
                .sum();
        int overallRate = totalCapacity > 0 ? (int) Math.round((double) totalEnrolled / totalCapacity * 100) : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("scheduled", scheduled);
        result.put("completed", completed);
        result.put("cancelled", cancelled);
        result.put("overallRate", overallRate);
        return result;
    }

    @GetMapping("/{id}")
    public GroupClass getById(@PathVariable Long id) {
        GroupClass gc = groupClassMapper.selectById(id);
        if (gc != null && gc.getTrainerId() != null) {
            Trainer trainer = trainerMapper.selectById(gc.getTrainerId());
            if (trainer != null) {
                gc.setTrainerName(trainer.getName());
            }
        }
        return gc;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody GroupClass groupClass) {
        if (groupClass.getStatus() == null || groupClass.getStatus().isEmpty()) {
            groupClass.setStatus("scheduled");
        }
        if (groupClass.getEnrolled() == null) {
            groupClass.setEnrolled(0);
        }
        // 如果 type 为空，默认为付费
        if (groupClass.getType() == null || groupClass.getType().isEmpty()) {
            groupClass.setType("paid");
        }
        if (groupClass.getPrice() == null) {
            groupClass.setPrice(BigDecimal.ZERO);
        }
        groupClassMapper.insert(groupClass);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "添加成功");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody GroupClass groupClass) {
        groupClass.setId(id);
        groupClassMapper.updateById(groupClass);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        groupClassMapper.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }

    @DeleteMapping("/batch")
    public Map<String, Object> batchDelete(@RequestBody Map<String, List<Long>> payload) {
        List<Long> ids = payload.get("ids");
        if (ids == null || ids.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请选择要删除的课程");
            return error;
        }
        int deleted = 0;
        for (Long id : ids) {
            deleted += groupClassMapper.deleteById(id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "成功删除 " + deleted + " 门课程");
        return result;
    }

    @GetMapping("/export")
    public void exportClasses(HttpServletResponse response) throws IOException {
        List<GroupClass> list = groupClassMapper.selectList(
                new LambdaQueryWrapper<GroupClass>().orderByDesc(GroupClass::getStartTime)
        );
        list.forEach(gc -> {
            if (gc.getTrainerId() != null) {
                Trainer trainer = trainerMapper.selectById(gc.getTrainerId());
                if (trainer != null) {
                    gc.setTrainerName(trainer.getName());
                }
            }
        });

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=团课列表_" + LocalDate.now().toString() + ".csv");

        try (PrintWriter writer = response.getWriter()) {
            writer.write("\uFEFF");
            writer.println("ID,课程名称,教练,开始时间,结束时间,已预约,最大容量,状态,类型");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (GroupClass gc : list) {
                writer.printf("%d,%s,%s,%s,%s,%d,%d,%s,%s%n",
                        gc.getId(),
                        nullToEmpty(gc.getName()),
                        nullToEmpty(gc.getTrainerName()),
                        gc.getStartTime() != null ? gc.getStartTime().format(fmt) : "",
                        gc.getEndTime() != null ? gc.getEndTime().format(fmt) : "",
                        gc.getEnrolled() != null ? gc.getEnrolled() : 0,
                        gc.getMaxCapacity(),
                        getStatusText(gc.getStatus()),
                        nullToEmpty(gc.getType())
                );
            }
            writer.flush();
        }
    }

    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private String getStatusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "scheduled": return "待上课";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            default: return status;
        }
    }
}