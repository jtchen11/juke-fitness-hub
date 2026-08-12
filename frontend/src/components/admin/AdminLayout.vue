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
          <el-menu-item index="/admin/member-packages"><el-icon><Goods /></el-icon><span>会员课程包</span></el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/admin/classes">
          <template #title>
            <el-icon><Calendar /></el-icon>
            <span>团课与赛事</span>
          </template>
          <el-menu-item index="/admin/classes"><el-icon><Calendar /></el-icon><span>全部团课</span></el-menu-item>
          <el-menu-item index="/admin/competitions"><el-icon><Trophy /></el-icon><span>比赛管理</span></el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/admin/points-group">
          <template #title>
            <el-icon><Coin /></el-icon>
            <span>积分管理</span>
          </template>
          <el-menu-item index="/admin/points-rewards"><el-icon><Goods /></el-icon><span>积分商品管理</span></el-menu-item>
          <el-menu-item index="/admin/points"><el-icon><Checked /></el-icon><span>兑换审批</span></el-menu-item>
          <el-menu-item index="/admin/points-history"><el-icon><Tickets /></el-icon><span>积分流水</span></el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/admin/settings">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/admin/settings/system"><el-icon><Tools /></el-icon><span>功能配置</span></el-menu-item>
          <el-menu-item index="/admin/messages"><el-icon><Bell /></el-icon><span>消息管理</span></el-menu-item>
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
          <el-popover v-model:visible="searchVisible" placement="bottom-end" :width="380" trigger="click" popper-class="header-popover">
            <template #reference>
              <el-icon class="header-icon" @click="openSearch"><Search /></el-icon>
            </template>
            <div class="search-box">
              <el-input
                  v-model="searchKeyword"
                  placeholder="搜索会员/教练（姓名或手机号）"
                  clearable
                  prefix-icon="Search"
                  @input="onSearchInput"
              />
              <div v-if="searchLoading" class="search-tip">搜索中...</div>
              <template v-else-if="searchKeyword">
                <div v-if="searchMembers.length" class="search-group-title">会员</div>
                <div v-for="m in searchMembers" :key="'m' + m.id" class="search-item" @click="goSearchResult('member', m)">
                  <el-icon><User /></el-icon>
                  <span class="search-item-name">{{ m.name }}</span>
                  <span class="search-item-sub">{{ m.phone }}</span>
                </div>
                <div v-if="searchTrainers.length" class="search-group-title">教练</div>
                <div v-for="t in searchTrainers" :key="'t' + t.id" class="search-item" @click="goSearchResult('trainer', t)">
                  <el-icon><Notebook /></el-icon>
                  <span class="search-item-name">{{ t.name }}</span>
                  <span class="search-item-sub">{{ t.phone }}</span>
                </div>
                <div v-if="!searchMembers.length && !searchTrainers.length" class="search-empty">未找到相关会员或教练</div>
              </template>
            </div>
          </el-popover>
          <el-popover v-model:visible="messageVisible" placement="bottom-end" :width="360" trigger="click" popper-class="header-popover" @show="loadUnreadMessages">
            <template #reference>
              <div class="msg-icon-wrap">
                <el-icon class="header-icon"><Bell /></el-icon>
                <span v-if="unreadCount > 0" class="msg-dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
              </div>
            </template>
            <div class="msg-box">
              <div class="msg-box-title">未读消息（{{ unreadCount }}）</div>
              <div v-if="!unreadMessages.length" class="search-empty">暂无未读消息</div>
              <div v-for="m in unreadMessages" :key="m.id" class="msg-item">
                <div class="msg-item-top">
                  <span class="msg-item-name">{{ m.memberName }}</span>
                  <span class="msg-item-time">{{ formatTime(m.createdAt) }}</span>
                </div>
                <div class="msg-item-content">{{ m.content }}</div>
              </div>
              <div class="msg-more" @click="goMessages">查看更多 →</div>
            </div>
          </el-popover>
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  DataLine, User, Calendar, Notebook, TrendCharts,
  Checked, Goods, Trophy, Setting, Tools, Fold, Search, Bell, Coin, Food, Tickets
} from '@element-plus/icons-vue'

const router = useRouter()
const adminName = ref('管理员')
const isCollapsed = ref(false)
const sidebarWidth = ref('220px')

onMounted(() => {
  adminName.value = localStorage.getItem('adminName') || '管理员'
  loadUnreadCount()
})

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
})

const toggleSidebar = () => {
  isCollapsed.value = !isCollapsed.value
  sidebarWidth.value = isCollapsed.value ? '64px' : '220px'
}

// ============ 消息中心 ============
const unreadCount = ref(0)
const messageVisible = ref(false)
const unreadMessages = ref([])

const loadUnreadCount = async () => {
  try {
    const res = await axios.get('/api/messages/unread-count')
    unreadCount.value = res.data.count || 0
  } catch (e) {
    console.warn('加载未读数失败', e)
  }
}

const loadUnreadMessages = async () => {
  try {
    const [countRes, listRes] = await Promise.all([
      axios.get('/api/messages/unread-count'),
      axios.get('/api/messages', { params: { isRead: false, page: 1, size: 5 } })
    ])
    unreadCount.value = countRes.data.count || 0
    unreadMessages.value = listRes.data.list || []
  } catch (e) {
    console.warn('加载未读消息失败', e)
  }
}

const goMessages = () => {
  messageVisible.value = false
  router.push('/admin/messages')
}

const formatTime = (t) => {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 19)
}

// ============ 全局搜索 ============
const searchVisible = ref(false)
const searchKeyword = ref('')
const searchLoading = ref(false)
const searchMembers = ref([])
const searchTrainers = ref([])
let searchTimer = null

const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 300)
}

const doSearch = async () => {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchMembers.value = []
    searchTrainers.value = []
    return
  }
  searchLoading.value = true
  try {
    const [mRes, tRes] = await Promise.all([
      axios.get('/api/members', { params: { keyword: kw, page: 1, size: 10 } }),
      axios.get('/api/trainers', { params: { keyword: kw, page: 1, size: 10 } })
    ])
    searchMembers.value = mRes.data.list || []
    searchTrainers.value = tRes.data.list || []
  } catch (e) {
    console.warn('搜索失败', e)
  } finally {
    searchLoading.value = false
  }
}

const openSearch = () => {
  if (searchKeyword.value.trim()) doSearch()
}

const goSearchResult = (type, item) => {
  searchVisible.value = false
  if (type === 'member') {
    router.push({ path: '/admin/members', query: { keyword: item.name || '' } })
  } else {
    router.push({ path: '/admin/trainers', query: { keyword: item.name || '' } })
  }
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

<style>
/* 弹窗内容被 teleport 到 body，需用全局样式 */
.header-popover .search-box { padding: 4px 0; }
.header-popover .search-tip { color: #909399; font-size: 13px; padding: 12px 8px; text-align: center; }
.header-popover .search-empty { color: #909399; font-size: 13px; padding: 16px 8px; text-align: center; }
.header-popover .search-group-title { font-size: 12px; color: #909399; margin: 10px 0 4px; padding-left: 8px; }
.header-popover .search-item { display: flex; align-items: center; gap: 8px; padding: 8px; border-radius: 6px; cursor: pointer; }
.header-popover .search-item:hover { background: #F1F5F9; }
.header-popover .search-item-name { font-weight: 500; color: #1A1A2E; }
.header-popover .search-item-sub { color: #909399; font-size: 12px; }
.header-popover .msg-box { padding: 4px 0; }
.header-popover .msg-box-title { font-weight: bold; margin-bottom: 8px; }
.header-popover .msg-item { padding: 8px; border-bottom: 1px dashed #EEE; }
.header-popover .msg-item:last-of-type { border-bottom: none; }
.header-popover .msg-item-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.header-popover .msg-item-name { font-weight: 500; font-size: 13px; }
.header-popover .msg-item-time { color: #909399; font-size: 12px; }
.header-popover .msg-item-content { color: #606266; font-size: 13px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.header-popover .msg-more { margin-top: 8px; text-align: center; color: #4A6CF7; font-size: 13px; cursor: pointer; }
.msg-icon-wrap { position: relative; display: inline-flex; }
.msg-dot { position: absolute; top: -6px; right: -10px; background: #F56C6C; color: #FFF; font-size: 11px; line-height: 1; padding: 3px 5px; border-radius: 10px; min-width: 16px; text-align: center; }
</style>
