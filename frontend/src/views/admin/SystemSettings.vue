<template>
  <div class="system-settings">
    <el-card class="setting-card">
      <template #header><span>功能开关</span></template>
      <el-form label-width="160px">
        <el-form-item label="饮食记录功能">
          <el-switch v-model="config.DIET_RECORD_ENABLED" active-value="1" inactive-value="0" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.DIET_RECORD_ENABLED === '1' ? '已开启' : '已关闭' }}</span>
        </el-form-item>
        <el-form-item label="16+8 断食法">
          <el-switch v-model="config.FASTING_16_8_ENABLED" active-value="1" inactive-value="0" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.FASTING_16_8_ENABLED === '1' ? '已开启' : '已关闭' }}</span>
        </el-form-item>
        <el-form-item label="碳循环饮食法">
          <el-switch v-model="config.CARB_CYCLE_ENABLED" active-value="1" inactive-value="0" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.CARB_CYCLE_ENABLED === '1' ? '已开启' : '已关闭' }}</span>
        </el-form-item>
        <el-form-item label="准会员体验课">
          <el-switch v-model="config.VISITOR_EXPERIENCE_ENABLED" active-value="1" inactive-value="0" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.VISITOR_EXPERIENCE_ENABLED === '1' ? '已开启' : '已关闭' }}</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" color="#4A6CF7" :loading="loading" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-alert v-if="saved" title="保存成功" type="success" show-icon :closable="true" @close="saved=false" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// 与小程序端保持一致的规范 key
const DEFAULT_CONFIG = {
  DIET_RECORD_ENABLED: '0',
  FASTING_16_8_ENABLED: '0',
  CARB_CYCLE_ENABLED: '0',
  VISITOR_EXPERIENCE_ENABLED: '0'
}

const config = ref({ ...DEFAULT_CONFIG })
const loading = ref(false)
const saved = ref(false)

// 归一化后端返回的值：true/ON/'1' -> '1'，false/OFF/'0' -> '0'
const normalizeValue = (v) => {
  const s = String(v ?? '').trim().toLowerCase()
  if (s === 'true' || s === 'on' || s === 'yes' || s === '1') return '1'
  if (s === 'false' || s === 'off' || s === 'no' || s === '0') return '0'
  return '0'
}

onMounted(async () => {
  try {
    const res = await axios.get('/api/system/config')
    const data = res.data || {}
    const merged = { ...DEFAULT_CONFIG }
    Object.keys(DEFAULT_CONFIG).forEach((key) => {
      if (data[key] !== undefined) merged[key] = normalizeValue(data[key])
    })
    config.value = merged
  } catch (e) {
    console.warn('加载配置失败', e)
  }
})

const save = async () => {
  loading.value = true
  try {
    // 只提交 4 个规范 key，值为 '1'/'0'
    const payload = {}
    Object.keys(DEFAULT_CONFIG).forEach((key) => {
      payload[key] = normalizeValue(config.value[key])
    })
    await axios.post('/api/system/config', payload)
    saved.value = true
    ElMessage.success('保存成功')
    setTimeout(() => saved.value = false, 3000)
  } catch (e) {
    console.warn('保存失败', e)
    ElMessage.error('保存失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.system-settings { max-width: 600px; margin: 0 auto; }
.setting-card { margin-bottom: 16px; }
.switch-desc { margin-left: 12px; font-size: 14px; color: #8A8AA0; }
</style>