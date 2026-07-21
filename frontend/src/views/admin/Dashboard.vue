<template>
  <div class="dashboard">
    <!-- ====== 统计卡片（4个核心指标） ====== -->
    <el-row :gutter="20">
      <el-col :span="6" v-for="stat in stats" :key="stat.title">
        <el-card class="stat-card" :body-style="{ padding: '20px' }" shadow="hover">
          <div class="stat-icon" :style="{ background: stat.color }">
            <el-icon :size="32"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-title">{{ stat.title }}</div>
            <div class="stat-trend" v-if="stat.trend !== undefined">
              <span :class="stat.trend >= 0 ? 'trend-up' : 'trend-down'">
                {{ stat.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(stat.trend) }}%
              </span>
              <span style="color:#999;font-size:12px;margin-left:4px">较上月</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 图表区域（双栏布局） ====== -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📈 近7天预约趋势</span>
              <el-radio-group v-model="trendRange" size="small" @change="loadDashboard">
                <el-radio-button label="7">7天</el-radio-button>
                <el-radio-button label="30">30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <span>🎯 预约类型分布</span>
          </template>
          <div ref="pieChartRef" class="chart-container" style="height:280px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 热门课程排行 + 快捷功能 ====== -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>🔥 热门课程排行</span>
          </template>
          <el-table :data="hotClasses" border style="width:100%" size="small">
            <el-table-column type="index" label="排名" width="60" align="center" />
            <el-table-column prop="name" label="课程名称" />
            <el-table-column prop="bookings" label="预约次数" align="center" />
            <el-table-column prop="status" label="状态" align="center" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'scheduled' ? 'success' : 'info'" size="small">
                  {{ row.status === 'scheduled' ? '进行中' : '已结束' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>📋 快捷功能</span>
          </template>
          <el-row :gutter="16">
            <el-col :span="12" v-for="item in quickActions.slice(0, 2)" :key="item.name">
              <el-button
                  :type="item.type"
                  plain
                  style="width:100%;height:72px;border-radius:12px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;font-size:14px;"
                  @click="item.action"
              >
                <el-icon :size="22"><component :is="item.icon" /></el-icon>
                <span>{{ item.name }}</span>
              </el-button>
            </el-col>
          </el-row>
          <el-row :gutter="16" style="margin-top:12px">
            <el-col :span="12" v-for="item in quickActions.slice(2, 4)" :key="item.name">
              <el-button
                  :type="item.type"
                  plain
                  style="width:100%;height:72px;border-radius:12px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;font-size:14px;"
                  @click="item.action"
              >
                <el-icon :size="22"><component :is="item.icon" /></el-icon>
                <span>{{ item.name }}</span>
              </el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 最近预约列表 ====== -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📋 最近预约</span>
              <el-button size="small" type="primary" plain @click="router.push('/admin/bookings')">
                查看全部 →
              </el-button>
            </div>
          </template>
          <el-table :data="recentBookings" border style="width:100%">
            <el-table-column prop="memberName" label="会员" width="100" />
            <el-table-column prop="trainerName" label="教练" width="100" />
            <el-table-column prop="appointmentTime" label="预约时间" width="180" />
            <el-table-column prop="durationMinutes" label="时长(分钟)" width="90" align="center" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'scheduled' ? 'success' : row.status === 'completed' ? 'info' : 'danger'" size="small">
                  {{ row.status === 'scheduled' ? '待上课' : row.status === 'completed' ? '已完成' : '已取消' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="notes" label="备注" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 教练统计 ====== -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>👨‍🏫 教练本月统计</span>
              <el-button size="small" type="primary" plain @click="loadCoachStats">刷新</el-button>
            </div>
          </template>
          <el-table :data="coachStats" border style="width:100%" v-loading="coachLoading" size="small">
            <el-table-column prop="name" label="教练姓名" width="120" />
            <el-table-column prop="sessionsThisMonth" label="本月上课数" width="110" align="center" />
            <el-table-column prop="checkInsThisMonth" label="本月核销数" width="110" align="center" />
            <el-table-column prop="checkInRate" label="核销率" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.checkInRate &gt;= 0.8 ? 'success' : row.checkInRate &gt;= 0.5 ? 'warning' : 'danger'" size="small">
                  {{ (row.checkInRate * 100).toFixed(1) }}%
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalStudents" label="学员数" width="80" align="center" />
            <el-table-column prop="avgRating" label="平均评分" width="80" align="center">
              <template #default="{ row }">{{ row.avgRating ? row.avgRating.toFixed(1) : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import * as echarts from 'echarts'
import { User, UserFilled, Calendar, Notebook, Checked, ChatDotRound } from '@element-plus/icons-vue'

const router = useRouter()

// ============ 数据 ============
const stats = ref([
  { title: '会员总数', value: 0, icon: 'User', color: '#409EFF', trend: 0 },
  { title: '教练人数', value: 0, icon: 'UserFilled', color: '#67C23A', trend: 0 },
  { title: '本月预约', value: 0, icon: 'Calendar', color: '#E6A23C', trend: 0 },
  { title: '私教预约', value: 0, icon: 'Notebook', color: '#F56C6C', trend: 0 }
])

const recentBookings = ref([])
const hotClasses = ref([])
const coachStats = ref([])
const coachLoading = ref(false)
const trendRange = ref('7')

// ============ ECharts 图表 ============
const trendChartRef = ref(null)
const pieChartRef = ref(null)
let trendChart = null
let pieChart = null

// ============ 快捷功能（所有路径已加 /admin） ============
const quickActions = ref([
  { name: '会员管理', icon: 'User', type: 'primary', action: () => router.push('/admin/members') },
  { name: '教练管理', icon: 'UserFilled', type: 'success', action: () => router.push('/admin/trainers') },
  { name: '团课管理', icon: 'Calendar', type: 'warning', action: () => router.push('/admin/classes') },
  { name: '打卡记录', icon: 'Checked', type: 'danger', action: () => router.push('/admin/check-in-records') }
])

// ============ 加载数据 ============
const loadDashboard = async () => {
  try {
    const res = await axios.get('/api/dashboard/stats', {
      params: { days: trendRange.value }
    })
    const data = res.data
    stats.value = [
      { title: '会员总数', value: data.memberCount || 0, icon: 'User', color: '#409EFF', trend: data.memberTrend || 0 },
      { title: '教练人数', value: data.trainerCount || 0, icon: 'UserFilled', color: '#67C23A', trend: data.trainerTrend || 0 },
      { title: '本月预约', value: data.bookingCount || 0, icon: 'Calendar', color: '#E6A23C', trend: data.bookingTrend || 0 },
      { title: '私教预约', value: data.ptCount || 0, icon: 'Notebook', color: '#F56C6C', trend: data.ptTrend || 0 }
    ]
    await nextTick()
    renderCharts(data)
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const loadRecentBookings = async () => {
  try {
    const res = await axios.get('/api/personal-trainings?page=1&size=5')
    recentBookings.value = res.data.list || []
  } catch (error) {}
}

const loadCoachStats = async () => {
  coachLoading.value = true
  try {
    const res = await axios.get('/api/dashboard/coach-stats')
    coachStats.value = res.data || []
  } catch (error) {
    console.error('加载教练统计失败', error)
  } finally {
    coachLoading.value = false
  }
}

const loadHotClasses = async () => {
  try {
    const res = await axios.get('/api/dashboard/hot-classes')
    hotClasses.value = res.data || []
  } catch (error) {
    console.error('加载热门课程失败', error)
  }
}

// ============ 图表渲染 ============
const renderCharts = (data) => {
  if (trendChartRef.value) {
    if (!trendChart) {
      trendChart = echarts.init(trendChartRef.value)
    }
    const option = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: data.trendDates || ['06-18', '06-19', '06-20', '06-21', '06-22', '06-23', '06-24']
      },
      yAxis: { type: 'value' },
      series: [{
        data: data.trendData || [3, 5, 2, 8, 6, 9, 4],
        type: 'line',
        smooth: true,
        lineStyle: { color: '#409EFF', width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        }
      }]
    }
    trendChart.setOption(option)
    trendChart.resize()
  }

  if (pieChartRef.value) {
    if (!pieChart) {
      pieChart = echarts.init(pieChartRef.value)
    }
    const option = {
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', right: 10, top: 'center' },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{d}%' },
        data: data.pieData || [
          { value: 45, name: '团课', itemStyle: { color: '#409EFF' } },
          { value: 35, name: '私教', itemStyle: { color: '#67C23A' } },
          { value: 20, name: '其他', itemStyle: { color: '#E6A23C' } }
        ]
      }]
    }
    pieChart.setOption(option)
    pieChart.resize()
  }
}

// ============ 窗口自适应 ============
const handleResize = () => {
  trendChart?.resize()
  pieChart?.resize()
}

// ============ 生命周期 ============
onMounted(() => {
  loadDashboard()
  loadRecentBookings()
  loadHotClasses()
  window.addEventListener('resize', handleResize)
})

watch(trendRange, () => {
  loadDashboard()
})
</script>

<style scoped>
.dashboard {
  padding: 4px;
}
.stat-card {
  height: 240px;
  transition: transform 0.2s;
}
.stat-card:hover {
  transform: translateY(-4px);
}
.stat-card :deep(.el-card__body) {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  box-sizing: border-box;
}
.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  margin-bottom: 14px;
  flex-shrink: 0;
}
.stat-icon :deep(.el-icon) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
.stat-info {
  text-align: center;
  width: 100%;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  line-height: 1;
  margin: 0;
}
.stat-title {
  color: #999;
  font-size: 14px;
  margin-top: 6px;
  margin-bottom: 0;
  line-height: 1;
}
.stat-trend {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1;
}
.trend-up {
  color: #67C23A;
}
.trend-down {
  color: #F56C6C;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart-container {
  width: 100%;
  height: 280px;
}
.el-table {
  border-radius: 8px;
}
</style>