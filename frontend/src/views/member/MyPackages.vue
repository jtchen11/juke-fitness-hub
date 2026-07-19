<template>
  <div class="my-packages">
    <el-row :gutter="20">
      <!-- ===== 左侧：统计概览 ===== -->
      <el-col :span="6">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-item">
            <div class="stats-number">{{ stats.totalSessions }}</div>
            <div class="stats-label">总课时</div>
          </div>
          <div class="stats-item">
            <div class="stats-number" style="color:#67C23A;">{{ stats.usedSessions }}</div>
            <div class="stats-label">已用课时</div>
          </div>
          <div class="stats-item">
            <div class="stats-number" style="color:#E6A23C;">{{ stats.remainingSessions }}</div>
            <div class="stats-label">剩余课时</div>
          </div>
          <div class="stats-divider"></div>
          <div class="stats-item">
            <div class="stats-number" style="font-size:20px;">{{ packageList.length }}</div>
            <div class="stats-label">课程包数量</div>
          </div>
          <el-button type="primary" plain style="width:100%;margin-top:16px;" @click="goShopping">
            + 购买新套餐
          </el-button>
        </el-card>
      </el-col>

      <!-- ===== 右侧：课程包列表 + 使用记录 ===== -->
      <el-col :span="18">
        <!-- 课程包列表 -->
        <el-card shadow="hover" style="margin-bottom:20px;">
          <template #header>
            <div class="card-header">
              <span>📦 我的课程包</span>
              <el-tag type="info" size="small">共 {{ packageList.length }} 个</el-tag>
            </div>
          </template>

          <div v-if="loading" style="text-align:center;padding:40px;color:#999;">加载中...</div>
          <div v-else-if="packageList.length === 0" style="text-align:center;padding:40px;color:#999;">
            <div style="font-size:48px;">📦</div>
            <p>暂无课程包</p>
            <el-button type="primary" plain @click="goShopping">去购买</el-button>
          </div>
          <div v-else class="package-grid">
            <div
                v-for="pkg in packageList"
                :key="pkg.id"
                class="package-card"
                :class="{ expired: isExpired(pkg) }"
            >
              <div v-if="isExpired(pkg)" class="expired-badge">已过期</div>
              <div class="pkg-header">
                <span class="pkg-name">{{ pkg.packageName }}</span>
                <el-tag :type="getStatusType(pkg)" size="small">{{ getStatusText(pkg) }}</el-tag>
              </div>
              <div class="pkg-progress">
                <div class="pkg-progress-label">
                  <span>{{ pkg.usedSessions || 0 }}/{{ pkg.totalSessions }} 节</span>
                  <span>{{ getUsageRate(pkg) }}%</span>
                </div>
                <el-progress
                    :percentage="getUsageRate(pkg)"
                    :color="getProgressColor(pkg)"
                    :stroke-width="6"
                />
              </div>
              <div class="pkg-info">
                <span>📅 {{ pkg.startDate || '未激活' }}</span>
                <span>💰 ¥{{ pkg.price }}</span>
              </div>
              <div class="pkg-actions">
                <el-button
                    size="small"
                    type="primary"
                    plain
                    :disabled="!canBook(pkg)"
                    @click="openBookingDialog(pkg)"
                >
                  {{ canBook(pkg) ? '预约教练' : '不可用' }}
                </el-button>
                <el-button size="small" type="info" plain @click="showDetail(pkg)">详情</el-button>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 使用记录 -->
        <el-card shadow="hover" v-if="usageRecords.length > 0">
          <template #header>
            <div class="card-header">
              <span>📋 最近使用记录</span>
              <el-button size="small" text @click="router.push('/member/bookings')">查看全部 →</el-button>
            </div>
          </template>
          <el-table :data="usageRecords" border size="small">
            <el-table-column prop="appointmentTime" label="日期" width="160" />
            <el-table-column prop="packageName" label="课程包" min-width="120" />
            <el-table-column label="教练" width="100">
              <template #default="{ row }">{{ row.trainerName || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'completed' ? 'success' : 'info'" size="small">
                  {{ row.status === 'completed' ? '已完成' : '待上课' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

// ================================================================
// 状态变量
// ================================================================

const loading = ref(false)
const packageList = ref([])
const usageRecords = ref([])

// ================================================================
// 计算属性
// ================================================================

const stats = computed(() => {
  let total = 0
  let used = 0
  let remaining = 0
  packageList.value.forEach(pkg => {
    total += pkg.totalSessions || 0
    used += pkg.usedSessions || 0
    remaining += pkg.remainingSessions || 0
  })
  return { totalSessions: total, usedSessions: used, remainingSessions: remaining }
})

// ================================================================
// 工具函数
// ================================================================

const isExpired = (pkg) => {
  if (!pkg.endDate) return false
  return new Date(pkg.endDate) < new Date()
}

const getUsageRate = (pkg) => {
  if (!pkg.totalSessions || pkg.totalSessions === 0) return 0
  return Math.round(((pkg.usedSessions || 0) / pkg.totalSessions) * 100)
}

const getStatusType = (pkg) => {
  if (isExpired(pkg)) return 'danger'
  if (pkg.remainingSessions <= 0) return 'info'
  if (!pkg.startDate) return 'warning'
  return 'success'
}

const getStatusText = (pkg) => {
  if (isExpired(pkg)) return '已过期'
  if (pkg.remainingSessions <= 0) return '已用完'
  if (!pkg.startDate) return '待激活'
  return '使用中'
}

const getProgressColor = (pkg) => {
  const rate = getUsageRate(pkg)
  if (rate >= 90) return '#67C23A'
  if (rate >= 70) return '#E6A23C'
  return '#409EFF'
}

const canBook = (pkg) => {
  if (isExpired(pkg)) return false
  if (pkg.remainingSessions <= 0) return false
  if (!pkg.startDate) return false
  return true
}

// ================================================================
// 加载数据
// ================================================================

const loadPackages = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) {
    ElMessage.warning('请先登录')
    return
  }

  loading.value = true
  try {
    const res = await axios.get('/api/private-packages/mine', {
      params: { memberId: Number(memberId) }
    })
    packageList.value = res.data || []
  } catch (error) {
    console.error('加载课程包失败', error)
    ElMessage.error('加载课程包失败')
  } finally {
    loading.value = false
  }
}

const loadUsageRecords = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return

  try {
    const res = await axios.get('/api/personal-trainings', {
      params: {
        page: 1,
        size: 20,
        memberId: memberId
      }
    })
    const all = res.data.list || []
    const filtered = all.filter(item => item.packageId)
    usageRecords.value = filtered
        .sort((a, b) => new Date(b.appointmentTime) - new Date(a.appointmentTime))
        .slice(0, 5)
  } catch (error) {
    console.error('加载使用记录失败', error)
  }
}

// ================================================================
// 操作
// ================================================================
const loadAvailablePackages = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get('/api/private-packages/mine', {
      params: { memberId: Number(memberId) }
    })
    availablePackages.value = res.data || []

    // 如果 URL 中有 packageId 参数，自动选中
    const packageIdFromUrl = route.query.packageId
    if (packageIdFromUrl) {
      const matched = availablePackages.value.find(pkg => pkg.id === Number(packageIdFromUrl))
      if (matched) {
        payMethod.value = 'package_' + matched.id
        ElMessage.success(`已选中「${matched.packageName}」，剩余 ${matched.remainingSessions} 节`)
      } else {
        // 如果找不到匹配的课程包，默认选第一个或单次
        if (availablePackages.value.length > 0) {
          payMethod.value = 'package_' + availablePackages.value[0].id
        } else {
          payMethod.value = 'single'
        }
      }
    } else {
      if (availablePackages.value.length > 0) {
        payMethod.value = 'package_' + availablePackages.value[0].id
      } else {
        payMethod.value = 'single'
      }
    }
  } catch (error) {
    console.error('加载课程包失败', error)
    availablePackages.value = []
    payMethod.value = 'single'
  }
}
const openBookingDialog = (pkg) => {
  router.push({
    path: '/member/bookings',
    query: { packageId: pkg.id }
  })
}
const showDetail = (pkg) => {
  ElMessageBox.alert(
      `
      <div style="line-height:1.8;">
        <p><strong>课程包名称：</strong>${pkg.packageName}</p>
        <p><strong>总课时：</strong>${pkg.totalSessions} 节</p>
        <p><strong>已用课时：</strong>${pkg.usedSessions || 0} 节</p>
        <p><strong>剩余课时：</strong>${pkg.remainingSessions || 0} 节</p>
        <p><strong>有效期：</strong>${pkg.startDate || '未激活'} ${pkg.startDate ? '~ ' + pkg.endDate : ''}</p>
        <p><strong>价格：</strong>¥${pkg.price}</p>
        <p><strong>状态：</strong>${getStatusText(pkg)}</p>
      </div>
      `,
      `📦 ${pkg.packageName} - 详情`,
      { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
  )
}

const goShopping = () => {
  router.push('/member/classes')
}

// ================================================================
// 生命周期
// ================================================================

onMounted(async () => {
  await loadPackages()
  await loadUsageRecords()
})
</script>

<style scoped>
.my-packages {
  max-width: 1200px;
  margin: 0 auto;
  padding: 4px;
}

/* ===== 左侧统计卡片 ===== */
.stats-card {
  height: 100%;
}
.stats-item {
  text-align: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}
.stats-item:last-of-type {
  border-bottom: none;
}
.stats-number {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}
.stats-label {
  font-size: 14px;
  color: #909399;
  margin-top: 2px;
}
.stats-divider {
  height: 1px;
  background: #f0f2f5;
  margin: 8px 0;
}

/* ===== 右侧 ===== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ===== 课程包卡片网格 ===== */
.package-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.package-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px 18px;
  background: #fff;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}
.package-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}
.package-card.expired {
  opacity: 0.6;
  background: #f5f7fa;
}

/* 过期角标 */
.expired-badge {
  position: absolute;
  top: 8px;
  right: -26px;
  background: #F56C6C;
  color: #fff;
  padding: 2px 40px;
  transform: rotate(45deg);
  font-size: 11px;
  font-weight: bold;
}

.pkg-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.pkg-name {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.pkg-progress {
  margin-bottom: 10px;
}
.pkg-progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
  margin-bottom: 2px;
}

.pkg-info {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
}

.pkg-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  border-top: 1px solid #f0f2f5;
  padding-top: 10px;
}
</style>