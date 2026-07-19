package com.gym.controller;

import com.gym.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemConfigController {
    @Autowired
    private SystemConfigService configService;

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return configService.getAll();
    }

    @PostMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody Map<String, String> configs) {
        configService.update(configs);
        return Map.of("success", true, "message", "保存成功");
    }
}
