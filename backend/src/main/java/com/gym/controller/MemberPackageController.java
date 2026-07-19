package com.gym.controller;

import com.gym.entity.PrivatePackage;
import com.gym.service.PrivatePackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/member/packages")
public class MemberPackageController {

    @Autowired
    private PrivatePackageService packageService;

    /**
     * 会员端：获取所有已上架的私教套餐
     */
    @GetMapping("/list")
    public List<PrivatePackage> list() {
        return packageService.getActivePackages();
    }
}