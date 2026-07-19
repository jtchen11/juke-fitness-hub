package com.gym.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    @Value("${gym.location.latitude:0}")
    private double gymLat;

    @Value("${gym.location.longitude:0}")
    private double gymLng;

    @Value("${gym.location.radius:100}")
    private double radiusMeters;

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, Double> params) {
        double userLat = params.getOrDefault("latitude", 0.0);
        double userLng = params.getOrDefault("longitude", 0.0);
        Map<String, Object> result = new HashMap<>();

        double distance = haversine(userLat, userLng, gymLat, gymLng);
        boolean withinRange = distance <= radiusMeters;

        result.put("success", withinRange);
        result.put("distance", Math.round(distance * 100.0) / 100.0);
        result.put("message", withinRange ? "位置验证通过" : "请到达健身房后打卡");
        return result;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
