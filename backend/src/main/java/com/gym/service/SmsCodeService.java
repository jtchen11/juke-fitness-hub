package com.gym.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsCodeService {
    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${sms.mock-code:123456}")
    private String mockCode;

    private final ConcurrentHashMap<String, CodeEntry> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            store.values().removeIf(e -> (now - e.timestamp) > 300_000);
        }, 60, 60, TimeUnit.SECONDS);
        log.info("SmsCodeService started, smsEnabled={}, mockCode={}", smsEnabled, mockCode);
    }

    public String generateCode(String phone) {
        String code = smsEnabled ? String.format("%06d", new Random().nextInt(1000000)) : mockCode;
        store.put(phone, new CodeEntry(code, System.currentTimeMillis()));
        log.info("Code for {}: {}", phone, code);
        return code;
    }

    public boolean verifyCode(String phone, String code) {
        CodeEntry entry = store.get(phone);
        if (entry == null) return false;
        long elapsed = System.currentTimeMillis() - entry.timestamp;
        if (elapsed > 300_000) {
            store.remove(phone);
            return false;
        }
        return entry.code.equals(code);
    }

    public void removeCode(String phone) {
        store.remove(phone);
    }

    private static class CodeEntry {
        String code;
        long timestamp;
        CodeEntry(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }
    }
}
