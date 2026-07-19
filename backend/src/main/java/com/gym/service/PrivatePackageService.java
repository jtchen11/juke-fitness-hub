package com.gym.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gym.entity.PrivatePackage;
import com.gym.mapper.PrivatePackageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PrivatePackageService {

    @Autowired
    private PrivatePackageMapper packageMapper;

    /**
     * 获取所有已上架的套餐（会员端展示用）
     */
    public List<PrivatePackage> getActivePackages() {
        LambdaQueryWrapper<PrivatePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivatePackage::getIsActive, true)
                .orderByAsc(PrivatePackage::getSortOrder);
        return packageMapper.selectList(wrapper);
    }

    /**
     * 根据ID获取套餐（含校验是否存在）
     */
    public PrivatePackage getById(Integer id) {
        return packageMapper.selectById(id);
    }

    /**
     * 管理端：分页查询（含下架商品）
     */
    public List<PrivatePackage> listAll() {
        LambdaQueryWrapper<PrivatePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(PrivatePackage::getSortOrder);
        return packageMapper.selectList(wrapper);
    }

    public boolean save(PrivatePackage pkg) {
        return packageMapper.insert(pkg) > 0;
    }

    public boolean update(PrivatePackage pkg) {
        return packageMapper.updateById(pkg) > 0;
    }

    public boolean delete(Integer id) {
        return packageMapper.deleteById(id) > 0;
    }
    /**
     * 分页查询套餐（支持条件）
     */
    public IPage<PrivatePackage> pageQuery(Integer page, Integer size, LambdaQueryWrapper<PrivatePackage> wrapper) {
        return packageMapper.selectPage(new Page<>(page, size), wrapper);
    }
}