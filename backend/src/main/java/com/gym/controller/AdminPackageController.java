package com.gym.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.PrivatePackage;
import com.gym.service.PrivatePackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/packages")
public class AdminPackageController {

    @Autowired
    private PrivatePackageService packageService;

    /**
     * 分页查询套餐列表（支持关键词和状态筛选）
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {  // status: true/false

        LambdaQueryWrapper<PrivatePackage> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(PrivatePackage::getName, keyword);
        }

        if (status != null && !status.isEmpty()) {
            wrapper.eq(PrivatePackage::getIsActive, Boolean.parseBoolean(status));
        }

        wrapper.orderByAsc(PrivatePackage::getSortOrder);

        IPage<PrivatePackage> pageResult = packageService.pageQuery(page, size, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        return result;
    }

    @GetMapping("/{id}")
    public PrivatePackage get(@PathVariable Integer id) {
        return packageService.getById(id);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody PrivatePackage pkg) {
        boolean success = packageService.save(pkg);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "添加成功" : "添加失败");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Integer id, @RequestBody PrivatePackage pkg) {
        pkg.setId(id);
        boolean success = packageService.update(pkg);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "更新成功" : "更新失败");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Integer id) {
        boolean success = packageService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "删除成功" : "删除失败");
        return result;
    }
}