<template>
  <div class="dashboard">
    <!-- 顶部 6 张统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="4" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '20px' }">
          <div class="stat-body">
            <div class="stat-icon" :style="{ background: card.bg }">
              <el-icon :size="22"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-title">{{ card.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span>近 7 天预约趋势</span></template>
          <div ref="trendChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="chart-card">
          <template #header><span>热门课程排行 TOP5</span></template>
          <div ref="hotChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="chart-card">
          <template #header><span>教练工作量分布</span></template>
          <div ref="coachChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部列表 -->
    <el-row :gutter="16" class="list-row">
      <el-col :span="8">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="list-header">
              <span>待审批请假</span>
              <el-tag size="small" type="warning">{{ pendingLeaveTotal }} 条</el-tag>
            </div>
          </template>
          <el-table :data="pendingLeaves" size="small" v-loading="leavesLoading" :show-header="true">
            <el-table-column prop="trainerName" label="教练" width="80" />
            <el-table-column prop="leaveDate" label="日期" width="100" />
            <el-table-column label="时段" width="70">
              <template #default="{ row }">{{ periodText(row.period) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="90" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!leavesLoading && pendingLeaves.length === 0" description="暂无待审批请假" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="list-header">
              <span>待处理兑换</span>
              <el-tag size="small" type="danger">{{ pendingRedeemTotal }} 条</el-tag>
            </div>
          </template>
          <el-table :data="pendingRedemptions" size="small" v-loading="redeemLoading">
            <el-table-column prop="memberName" label="会员" width="90" show-overflow-tooltip />
            <el-table-column prop="rewardName" label="商品" min-width="110" show-overflow-tooltip />
            <el-table-column prop="pointsSpent" label="积分" width="70" align="center" />
            <el-table-column prop="createdAt" label="申请时间" width="110" />
          </el-table>
          <el-empty v-if="!redeemLoading && pendingRedemptions.length === 0" description="暂无待处理兑换" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="list-header"><span>近期预约</span></div>
          </template>
          <el-table :data="recentBookings" size="small" v-loading="bookingsLoading">
            <el-table-column prop="memberName" label="会员" width="90" show-overflow-tooltip />
            <el-table-column prop="trainerName" label="教练" width="80" show-overflow-tooltip />
            <el-table-column prop="appointmentTime" label="时间" width="130" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="ptStatusType(row.status)">{{ ptStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!bookingsLoading && recentBookings.length === 0" description="暂无近期预约" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts'
import { Calendar, Coin, UserFilled, DataLine, Bell, Goods } from '@element-plus/icons-vue'

const statCards = ref([
  { title: '今日预约', value: 0, icon: Calendar, bg: '#4A6CF7' },
  { title: '本月营收', value: '¥0', icon: Coin, bg: '#67C23A' },
  { title: '活跃会员(30天)', value: 0, icon: UserFilled, bg: '#409EFF' },
  { title: '课程满员率', value: '0%', icon: DataLine, bg: '#E6A23C' },
  { title: '待审批请假', value: 0, icon: Bell, bg: '#F56C6C' },
  { title: '积分待处理', value: 0, icon: Goods, bg: '#909399' }
])

const pendingLeaves = ref([])
const pendingLeaveTotal = ref(0)
const leavesLoading = ref(false)
const pendingRedemptions = ref([])
const pendingRedeemTotal = ref(0)
const redeemLoading = ref(false)
const recentBookings = ref([])
const bookingsLoading = ref(false)

const trendChartRef = ref(null)
const hotChartRef = ref(null)
const coachChartRef = ref(null)
let trendChart = null
let hotChart = null
let coachChart = null

const periodText = (p) => ({ full_day: '全天', morning: '上午', afternoon: '下午' }[p] || '全天')
const ptStatusType = (s) => ({ scheduled: 'success', ongoing: 'warning', completed: 'info', cancelled: 'danger', cancelled_by_trainer: 'danger' }[s] || 'info')
const ptStatusText = (s) => ({ scheduled: '待上课', ongoing: '进行中', completed: '已完成', cancelled: '已取消', cancelled_by_trainer: '已取消' }[s] || s)

const loadOverview = async () => {
  try {
    const res = await axios.get('/api/dashboard/overview')
    const d = res.data || {}
    statCards.value[0].value = d.todayBookings || 0
    statCards.value[1].value = '¥' + (d.monthRevenueText || '0')
    statCards.value[2].value = d.activeMembers || 0
    statCards.value[3].value = (d.fullRate || 0) + '%'
    statCards.value[4].value = d.pendingLeaves || 0
    statCards.value[5].value = d.pendingRedemptions || 0
    pendingLeaveTotal.value = d.pendingLeaves || 0
    pendingRedeemTotal.value = d.pendingRedemptions || 0
    renderCoachChart(d.coachWorkload || [])
  } catch (e) {
    console.error('加载总览失败', e)
  }
}

const loadTrend = async () => {
  try {
    const res = await axios.get('/api/dashboard/stats', { params: { days: 7 } })
    const d = res.data || {}
    renderTrendChart(d.trendDates || [], d.trendData || [])
  } catch (e) {
    console.error('加载趋势失败', e)
  }
}

const loadHotClasses = async () => {
  try {
    const res = await axios.get('/api/dashboard/hot-classes')
    renderHotChart(res.data || [])
  } catch (e) {
    console.error('加载热门课程失败', e)
  }
}

const loadPendingLeaves = async () => {
  leavesLoading.value = true
  try {
    const res = await axios.get('/api/trainers/leaves/pending', { params: { status: 'pending' } })
    pendingLeaves.value = ((res.data || []).filter(i => i.status === 'pending')).slice(0, 5)
  } catch (e) {} finally { leavesLoading.value = false }
}

const loadPendingRedemptions = async () => {
  redeemLoading.value = true
  try {
    const res = await axios.get('/api/points/admin/pending')
    pendingRedemptions.value = ((res.data && res.data.list) || []).slice(0, 5)
  } catch (e) {} finally { redeemLoading.value = false }
}

const loadRecentBookings = async () => {
  bookingsLoading.value = true
  try {
    const res = await axios.get('/api/personal-trainings', { params: { page: 1, size: 10 } })
    recentBookings.value = (res.data && res.data.list) || []
  } catch (e) {} finally { bookingsLoading.value = false }
}

const renderTrendChart = (dates, data) => {
  nextTick(() => {
    if (!trendChartRef.value) return
    if (!trendChart) trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        name: '预约数', type: 'line', smooth: true, data: data,
        lineStyle: { color: '#4A6CF7', width: 3 },
        itemStyle: { color: '#4A6CF7' },
        areaStyle: { color: 'rgba(74,108,247,0.12)' }
      }]
    })
  })
}

const renderHotChart = (list) => {
  nextTick(() => {
    if (!hotChartRef.value) return
    if (!hotChart) hotChart = echarts.init(hotChartRef.value)
    const names = list.map(i => i.name)
    const values = list.map(i => i.bookings || 0)
    hotChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 20, bottom: 60 },
      xAxis: { type: 'category', data: names, axisLabel: { rotate: 30, fontSize: 10 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'bar', data: values, barWidth: 18,
        itemStyle: { color: '#E6A23C', borderRadius: [4, 4, 0, 0] }
      }]
    })
  })
}

const renderCoachChart = (workload) => {
  nextTick(() => {
    if (!coachChartRef.value) return
    if (!coachChart) coachChart = echarts.init(coachChartRef.value)
    const data = workload.map(i => ({ name: i.name, value: i.value }))
    coachChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 10 } },
      series: [{
        type: 'pie', radius: ['38%', '65%'], center: ['50%', '44%'],
        data: data,
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 12, fontWeight: 'bold' } }
      }]
    })
  })
}

const handleResize = () => {
  if (trendChart) trendChart.resize()
  if (hotChart) hotChart.resize()
  if (coachChart) coachChart.resize()
}

onMounted(() => {
  loadOverview()
  loadTrend()
  loadHotClasses()
  loadPendingLeaves()
  loadPendingRedemptions()
  loadRecentBookings()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) { trendChart.dispose(); trendChart = null }
  if (hotChart) { hotChart.dispose(); hotChart = null }
  if (coachChart) { coachChart.dispose(); coachChart = null }
})
</script>

<style scoped>
.stat-row { margin-bottom: 16px; }
.stat-card { border-radius: 12px; }
.stat-body { display: flex; align-items: center; gap: 14px; }
.stat-icon { width: 46px; height: 46px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #FFF; flex-shrink: 0; }
.stat-value { font-size: 26px; font-weight: bold; color: #1A1A2E; line-height: 1.2; }
.stat-title { font-size: 13px; color: #8A8AA0; margin-top: 4px; }
.chart-row { margin-bottom: 16px; }
.chart-card { border-radius: 12px; }
.chart-box { height: 300px; }
.list-row { }
.list-card { border-radius: 12px; }
.list-header { display: flex; align-items: center; justify-content: space-between; }
</style>
