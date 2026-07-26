package com.gym.controller;

import com.gym.config.FaceConfig;
import com.gym.dto.FaceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/face")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class FaceController {

    @Autowired
    private FaceConfig faceConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 人脸注册（带异常处理）
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody FaceRequest request) {
        Map<String, Object> result = new HashMap<>();
        String url = faceConfig.getFaceServiceUrl() + "/api/face/register";
        log.info("收到注册请求 userId={}, image长度={}, image开头50字={}", request.getUserId(), request.getImage() != null ? request.getImage().length() : 0, request.getImage() != null ? request.getImage().substring(0, Math.min(50, request.getImage().length())) : "null");
        try {
            // 转发请求并获取响应
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response == null) {
                result.put("success", false);
                result.put("message", "人脸服务返回空响应");
            } else {
                // 直接透传服务返回的结果（保持字段一致）
                result.putAll(response);
            }
        } catch (RestClientException e) {
            // 捕获网络异常（连接失败、超时等）
            result.put("success", false);
            String msg = e.getMessage();
            if (msg != null && msg.contains("Connection refused")) {
                result.put("message", "人脸服务未启动，请先启动 Python 人脸服务（端口5001）");
            } else if (msg != null && msg.contains("timeout")) {
                result.put("message", "人脸服务响应超时，请检查服务状态");
            } else {
                result.put("message", "调用人脸服务失败：" + msg);
            }
            // 打印详细错误日志，便于排查
            e.printStackTrace();
        } catch (Exception e) {
            // 其他异常
            result.put("success", false);
            result.put("message", "注册失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 人脸验证（带异常处理）
     */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody FaceRequest request) {
        Map<String, Object> result = new HashMap<>();
        String url = faceConfig.getFaceServiceUrl() + "/api/face/verify";
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response == null) {
                result.put("success", false);
                result.put("message", "人脸服务返回空响应");
            } else {
                result.putAll(response);
            }
        } catch (RestClientException e) {
            result.put("success", false);
            String msg = e.getMessage();
            if (msg != null && msg.contains("Connection refused")) {
                result.put("message", "人脸服务未启动，请先启动 Python 人脸服务（端口5001）");
            } else if (msg != null && msg.contains("timeout")) {
                result.put("message", "人脸服务响应超时，请检查服务状态");
            } else {
                result.put("message", "调用人脸服务失败：" + msg);
            }
            e.printStackTrace();
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "验证失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 删除人脸（带异常处理）
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        String url = faceConfig.getFaceServiceUrl() + "/api/face/delete";
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response == null) {
                result.put("success", false);
                result.put("message", "人脸服务返回空响应");
            } else {
                result.putAll(response);
            }
        } catch (RestClientException e) {
            result.put("success", false);
            result.put("message", "人脸服务不可用：" + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 检查人脸注册状态（带异常处理）
     */
    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        String url = faceConfig.getFaceServiceUrl() + "/api/face/check?userId=" + userId;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                result.put("success", false);
                result.put("message", "人脸服务返回空响应");
            } else {
                result.putAll(response);
            }
        } catch (RestClientException e) {
            result.put("success", false);
            result.put("message", "人脸服务不可用：" + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "检查失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
}