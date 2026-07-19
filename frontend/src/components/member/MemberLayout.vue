<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <span>🏋️ 智能健身</span>
      </div>
      <el-menu
          :default-active="$route.path"
          router
          background-color="#1f2d3d"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
      >
        <el-menu-item index="/member/classes">
          <el-icon><Calendar /></el-icon>
          <span>健身空间</span>
        </el-menu-item>
        <!-- ====== 新增：我的课程包 ====== -->
        <el-menu-item index="/member/packages">
          <el-icon><Box /></el-icon>
          <span>我的课程包</span>
        </el-menu-item>
        <el-menu-item index="/member/bookings">
          <el-icon><Notebook /></el-icon>
          <span>我的预约</span>
        </el-menu-item>
        <el-menu-item index="/member/ai-chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 智能助手</span>
        </el-menu-item>
        <el-menu-item index="/member/face-checkin">
          <el-icon><Camera /></el-icon>
          <span>刷脸签到</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ $route.meta.title }}</div>
        <div class="header-right">
          <NotificationBell :member-id="userId" style="margin-right:16px;" />
          <span class="welcome">欢迎，{{ memberName }}</span>
          <el-button size="small" type="danger" plain @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Calendar, Notebook, ChatDotRound, Camera, Box } from '@element-plus/icons-vue'
import NotificationBell from '@/components/common/NotificationBell.vue'
const router = useRouter()
const memberName = ref('会员')
const userId = ref(Number(localStorage.getItem('userId')) || 0)
onMounted(() => {
  memberName.value = localStorage.getItem('userName') || '会员'
})

const handleLogout = async () => {
  try {
    localStorage.removeItem('adminName')
    localStorage.removeItem('memberName')
    localStorage.removeItem('userName')
    localStorage.removeItem('role')
    localStorage.removeItem('userId')
    localStorage.removeItem('authStatus')

    await axios.post('/api/auth/logout', {}, { withCredentials: true }).catch(() => {})

    ElMessage.success('已安全退出')
    await router.replace('/login')
  } catch (error) {
    localStorage.removeItem('authStatus')
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.sidebar {
  background-color: #1f2d3d;
  height: 100vh;
  overflow-y: auto;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid #2a3a4f;
}
.header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid #e6e6e6;
}
.header-title {
  font-size: 18px;
  font-weight: bold;
}
.header-right {
  display: flex;
  align-items: center;
}
.welcome {
  color: #666;
  font-size: 14px;
}
.el-main {
  background: #f0f2f5;
}
</style>