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

    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        for (SystemConfig c : mapper.selectList(null)) {
            result.put(c.getConfigKey(), c.getConfigValue());
        }
        return result;
    }

    public void update(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            LambdaQueryWrapper<SystemConfig> w = new LambdaQueryWrapper<>();
            w.eq(SystemConfig::getConfigKey, entry.getKey());
            SystemConfig c = mapper.selectOne(w);
            if (c == null) {
                c = new SystemConfig();
                c.setConfigKey(entry.getKey());
                c.setConfigValue(entry.getValue());
                mapper.insert(c);
            } else {
                c.setConfigValue(entry.getValue());
                mapper.updateById(c);
            }
        }
    }
}
