package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.entity.SystemConfig;
import com.gym.mapper.SystemConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemConfigService {
    @Autowired
    private SystemConfigMapper mapper;

    /** 遗留 key -> 规范 key 映射 */
    private static final Map<String, String> KEY_ALIASES = new HashMap<>();
    static {
        KEY_ALIASES.put("diet_16_8_enabled", "FASTING_16_8_ENABLED");
        KEY_ALIASES.put("IF_16_8", "FASTING_16_8_ENABLED");
        KEY_ALIASES.put("carbon_cycle_enabled", "CARB_CYCLE_ENABLED");
        KEY_ALIASES.put("CARB_CYCLE", "CARB_CYCLE_ENABLED");
    }

    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        for (SystemConfig c : mapper.selectList(null)) {
            result.put(c.getConfigKey(), normalizeValue(c.getConfigValue()));
        }
        return result;
    }

    public void update(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = normalizeKey(entry.getKey());
            String value = normalizeValue(entry.getValue());
            if (key == null || key.isEmpty()) continue;

            LambdaQueryWrapper<SystemConfig> w = new LambdaQueryWrapper<>();
            w.eq(SystemConfig::getConfigKey, key);
            SystemConfig c = mapper.selectOne(w);
            if (c == null) {
                c = new SystemConfig();
                c.setConfigKey(key);
                c.setConfigValue(value);
                mapper.insert(c);
            } else {
                c.setConfigValue(value);
                mapper.updateById(c);
            }
        }
    }

    private String normalizeKey(String key) {
        if (key == null) return null;
        return KEY_ALIASES.getOrDefault(key, key);
    }

    private String normalizeValue(String value) {
        if (value == null) return "0";
        String v = value.trim().toLowerCase();
        if (v.equals("true") || v.equals("on") || v.equals("yes") || v.equals("y") || v.equals("1")) {
            return "1";
        }
        if (v.equals("false") || v.equals("off") || v.equals("no") || v.equals("n") || v.equals("0")) {
            return "0";
        }
        return value.trim();
    }
}
