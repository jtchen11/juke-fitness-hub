<template>
  <el-container class="layout-container">
    <el-aside :width="sidebarWidth" class="sidebar">
      <div class="logo">
        <span>桔刻健身</span>
      </div>
      <el-menu
          :default-active="$route.path"
          router
          :collapse="isCollapsed"
          background-color="#0F172A"
          text-color="#94A3B8"
          active-text-color="#FFFFFF"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>管理员主页</span>
        </el-menu-item>
        <el-sub-menu index="/admin/members">
          <template #title>
            <el-icon><User /></el-icon>
            <span>会员管理</span>
          </template>
          <el-menu-item index="/admin/members"><el-icon><User /></el-icon><span>会员列表</span></el-menu-item>
          <el-menu-item index="/admin/fitness-tests"><el-icon><TrendCharts /></el-icon><span>体测记录</span></el-menu-item>
          <el-menu-item index="/admin/check-in-records"><el-icon><Checked /></el-icon><span>打卡记录</span></el-menu-item>
          <el-menu-item index="/admin/diet-record"><el-icon><Food /></el-icon><span>饮食记录</span></el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/admin/packages">
          <template #title>
            <el-icon><Notebook /></el-icon>
            <span>私教管理</span>
          </template>
          <el-menu-item index="/admin/trainers"><el-icon><Notebook /></el-icon><span>教练管理</span></el-menu-item>
          <el-menu-item index="/admin/packages"><el-icon><Goods /></el-icon><span>私教套餐</span></el-menu-item>
          <el-menu-item index="/admin/bookings"><el-icon><Notebook /></el-icon><span>私教预约</span></el-menu-item>
          <el-menu-item index="/admin/trainer-leaves"><el-icon><Calendar /></el-icon><span>请假审批</span></el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/admin/classes">
          <template #title>
            <el-icon><Calendar /></el-icon>
            <span>团课与赛事</span>
          </template>
          <el-menu-item index="/admin/classes"><el-icon><Calendar /></el-icon><span>全部团课</span></el-menu-item>
          <el-menu-item index="/admin/competitions"><el-icon><Trophy /></el-icon><span>比赛管理</span></el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/admin/settings">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-sub-menu index="/admin/points-group">
            <template #title>
              <el-icon><Coin /></el-icon>
              <span>积分管理</span>
            </template>
            <el-menu-item index="/admin/points"><el-icon><Checked /></el-icon><span>兑换审批</span></el-menu-item>
            <el-menu-item index="/admin/points-rewards"><el-icon><Goods /></el-icon><span>积分商品管理</span></el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/admin/settings/system"><el-icon><Tools /></el-icon><span>功能配置</span></el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar"><Fold /></el-icon>
          <span class="header-title">{{ $route.meta.title }}</span>
        </div>
        <div class="header-right">
          <el-icon class="header-icon"><Search /></el-icon>
          <el-icon class="header-icon"><Bell /></el-icon>
          <el-avatar :size="32" style="background:#4A6CF7;margin-left:12px">{{ adminName[0] }}</el-avatar>
          <span class="welcome">{{ adminName }}</span>
          <el-button size="small" color="#4A6CF7" @click="handleLogout" style="margin-left:12px">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  DataLine, User, Calendar, Notebook, TrendCharts,
  Checked, Goods, Trophy, Setting, Tools, Fold, Search, Bell, Coin, Food
} from '@element-plus/icons-vue'

const router = useRouter()
const adminName = ref('管理员')
const isCollapsed = ref(false)
const sidebarWidth = ref('220px')

onMounted(() => {
  adminName.value = localStorage.getItem('adminName') || '管理员'
})

const toggleSidebar = () => {
  isCollapsed.value = !isCollapsed.value
  sidebarWidth.value = isCollapsed.value ? '64px' : '220px'
}

const handleLogout = async () => {
  localStorage.removeItem('adminName'); localStorage.removeItem('memberName')
  localStorage.removeItem('userName'); localStorage.removeItem('role')
  localStorage.removeItem('userId'); localStorage.removeItem('authStatus')
  await axios.post('/api/auth/logout', {}, { withCredentials: true }).catch(() => {})
  ElMessage.success('已安全退出')
  router.replace('/login')
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.sidebar { background-color: #0F172A; height: 100vh; overflow-y: auto; transition: width .3s; }
.logo { height: 60px; display: flex; align-items: center; justify-content: center; color: #FFF; font-size: 20px; font-weight: bold; border-bottom: 1px solid #1E293B; }
.header { background: #FFF; display: flex; justify-content: space-between; align-items: center; padding: 0 24px; border-bottom: 1px solid #E8E8E8; height: 60px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { font-size: 20px; cursor: pointer; color: #8A8AA0; }
.header-title { font-size: 18px; font-weight: bold; color: #1A1A2E; }
.header-right { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 20px; color: #8A8AA0; cursor: pointer; }
.welcome { color: #8A8AA0; font-size: 14px; margin-left: 8px; }
.el-main { background: #F1F5F9; padding: 24px; }
.el-menu-item.is-active { border-left: 3px solid #4A6CF7; }
.el-menu { border-right: none; }
</style>
