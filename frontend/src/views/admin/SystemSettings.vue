<template>
  <div class="system-settings">
    <el-card class="setting-card">
      <template #header><span>功能开关</span></template>
      <el-form label-width="160px">
        <el-form-item label="饮食记录功能">
          <el-switch v-model="config.DIET_RECORD_ENABLED" active-value="ON" inactive-value="OFF" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.DIET_RECORD_ENABLED === "ON" ? "已开启" : "已关闭" }}</span>
        </el-form-item>
        <el-form-item label="16+8 断食法">
          <el-switch v-model="config.IF_16_8" active-value="ON" inactive-value="OFF" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.IF_16_8 === 'ON' ? '已开启' : '已关闭' }}</span>
        </el-form-item>
        <el-form-item label="碳循环饮食法">
          <el-switch v-model="config.CARB_CYCLE" active-value="ON" inactive-value="OFF" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.CARB_CYCLE === 'ON' ? '已开启' : '已关闭' }}</span>
        </el-form-item>
        <el-form-item label="准会员体验课">
          <el-switch v-model="config.VISITOR_EXPERIENCE_ENABLED" active-value="ON" inactive-value="OFF" active-color="#4A6CF7" />
          <span class="switch-desc">{{ config.VISITOR_EXPERIENCE_ENABLED === 'ON' ? '已开启' : '已关闭' }}</span>
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

const config = ref({ DIET_RECORD_ENABLED: 'OFF', IF_16_8: 'OFF', CARB_CYCLE: 'OFF', VISITOR_EXPERIENCE_ENABLED: 'OFF' })
const loading = ref(false)
const saved = ref(false)

onMounted(async () => {
  try {
    const res = await axios.get('/api/system/config')
    config.value = { ...config.value, ...res.data }
  } catch (e) { console.warn('加载配置失败', e) }
})

const save = async () => {
  loading.value = true
  try {
    await axios.post('/api/system/config', config.value)
    saved.value = true
    setTimeout(() => saved.value = false, 3000)
  } catch (e) { console.warn('保存失败', e) }
  finally { loading.value = false }
}
</script>

<style scoped>
.system-settings { max-width: 600px; margin: 0 auto; }
.setting-card { margin-bottom: 16px; }
.switch-desc { margin-left: 12px; font-size: 14px; color: #8A8AA0; }
</style>
