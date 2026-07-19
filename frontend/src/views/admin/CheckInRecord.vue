<template>
  <div>
    <!-- ====== 顶部统计卡片 ====== -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6" v-for="stat in stats" :key="stat.title">
        <el-card class="stat-card" shadow="hover" :body-style="{ padding: '16px 20px' }">
          <div class="stat-item">
            <div class="stat-icon" :style="{ background: stat.color }">
              <el-icon :size="24"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 今日签到明细（分类统计） ====== -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="24">
        <el-card shadow="hover" :body-style="{ padding: '12px 20px' }">
          <div style="display:flex;align-items:center;gap:24px;flex-wrap:wrap;">
            <span style="font-weight:bold;color:#333;font-size:14px;">📊 今日签到明细</span>
            <span><span style="color:#409EFF;font-weight:bold;">{{ todayDetail.total }}</span> 总签到</span>
            <span>🏃 自助：<span style="color:#67C23A;font-weight:bold;">{{ todayDetail.normal }}</span></span>
            <span>📅 团课：<span style="color:#E6A23C;font-weight:bold;">{{ todayDetail.class }}</span></span>
            <span>🏋️ 私教：<span style="color:#F56C6C;font-weight:bold;">{{ todayDetail.pt }}</span></span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 筛选条件 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-select
              v-model="filterMemberId"
              placeholder="全部会员"
              clearable
              @change="loadRecords"
              style="width:100%"
              filterable
          >
            <el-option
                v-for="m in memberList"
                :key="m.id"
                :label="m.name + ' (' + m.phone + ')'"
                :value="m.id"
            />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-date-picker
              v-model="filterStartDate"
              type="date"
              placeholder="开始日期"
              value-format="YYYY-MM-DD"
              @change="loadRecords"
              style="width:100%"
          />
        </el-col>
        <el-col :span="4">
          <el-date-picker
              v-model="filterEndDate"
              type="date"
              placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="loadRecords"
              style="width:100%"
          />
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadRecords">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 打卡记录列表（含类型Tabs） ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            📋 打卡记录
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag>
          </span>
          <div>
            <el-button size="small" text @click="refresh">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 类型筛选 Tabs -->
      <el-tabs v-model="activeType" @tab-change="loadRecords" style="margin-bottom: 16px;">
        <el-tab-pane label="📚 全部" name="all" />
        <el-tab-pane label="🏃 自助训练" name="normal" />
        <el-tab-pane label="📅 团课签到" name="class" />
        <el-tab-pane label="🏋️ 私教签到" name="pt" />
      </el-tabs>

      <el-table
          :data="tableData"
          border
          style="width:100%"
          v-loading="loading"
          row-key="id"
      >
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="会员" width="130">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="30" :style="{ backgroundColor: getAvatarColor(row.memberName) }">
                {{ row.memberName?.charAt(0) || '?' }}
              </el-avatar>
              <span>{{ row.memberName || '未知会员' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="checkInTime" label="打卡时间" width="180" />
        <el-table-column label="签到类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.checkInType)" size="small" effect="dark">
              {{ getTypeLabel(row.checkInType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联信息" min-width="160">
          <template #default="{ row }">
            <span v-if="row.checkInType === 'class' && row.className" style="color:#409EFF;">
              📅 {{ row.className }}
            </span>
            <span v-else-if="row.checkInType === 'pt' && row.ptInfo" style="color:#F56C6C;">
              🏋️ {{ row.ptInfo }}
            </span>
            <span v-else style="color:#999;">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="classId" label="课程ID" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.classId || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadRecords"
            @current-change="loadRecords"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

// =============================================
// 状态变量
// =============================================

// 统计数据（4个核心指标）
const stats = ref([
  { title: '总签到', value: 0, icon: 'Document', color: '#409EFF' },
  { title: '今日签到', value: 0, icon: 'Clock', color: '#67C23A' },
  { title: '本周签到', value: 0, icon: 'Calendar', color: '#E6A23C' },
  { title: '本月签到', value: 0, icon: 'TrendCharts', color: '#F56C6C' }
])

// 今日签到明细
const todayDetail = ref({
  total: 0,
  normal: 0,
  class: 0,
  pt: 0
})

// 列表数据
const tableData = ref([])
const memberList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filterMemberId = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')
const activeType = ref('all')  // all / normal / class / pt
const loading = ref(false)

// =============================================
// 工具函数
// =============================================

const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

const getTypeLabel = (type) => {
  const map = { normal: '自助训练', class: '团课签到', pt: '私教签到' }
  return map[type] || '未知'
}

const getTypeTag = (type) => {
  const map = { normal: 'info', class: 'warning', pt: 'success' }
  return map[type] || 'info'
}

// =============================================
// 加载数据
// =============================================

const loadStats = async () => {
  try {
    const res = await axios.get('/api/check-in/stats/summary')
    if (res.data) {
      stats.value = [
        { title: '总签到', value: res.data.total || 0, icon: 'Document', color: '#409EFF' },
        { title: '今日签到', value: res.data.today || 0, icon: 'Clock', color: '#67C23A' },
        { title: '本周签到', value: res.data.thisWeek || 0, icon: 'Calendar', color: '#E6A23C' },
        { title: '本月签到', value: res.data.thisMonth || 0, icon: 'TrendCharts', color: '#F56C6C' }
      ]
      // 今日明细
      todayDetail.value = {
        total: res.data.today || 0,
        normal: res.data.todayNormal || 0,
        class: res.data.todayClass || 0,
        pt: res.data.todayPt || 0
      }
    }
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const loadRecords = async () => {
  loading.value = true
  try {
    const params = {
      page: pageNum.value,
      size: pageSize.value,
      memberId: filterMemberId.value || undefined,
      startDate: filterStartDate.value || undefined,
      endDate: filterEndDate.value || undefined
    }
    // 根据 Tab 筛选类型
    if (activeType.value !== 'all') {
      params.type = activeType.value
    }

    const res = await axios.get('/api/check-in', { params })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('加载打卡记录失败', error)
    ElMessage.error('加载打卡记录失败')
  } finally {
    loading.value = false
  }
}

const loadMembers = async () => {
  try {
    const res = await axios.get('/api/members/all')
    memberList.value = res.data || []
  } catch (error) {
    console.error('加载会员列表失败', error)
  }
}

const resetSearch = () => {
  filterMemberId.value = ''
  filterStartDate.value = ''
  filterEndDate.value = ''
  pageNum.value = 1
  loadRecords()
}

const refresh = () => {
  loadStats()
  loadRecords()
}

// =============================================
// 生命周期
// =============================================

onMounted(() => {
  loadStats()
  loadRecords()
  loadMembers()
})
</script>

<style scoped>
.stat-card {
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}
.stat-info {
  flex: 1;
}
.stat-number {
  font-size: 24px;
  font-weight: bold;
  line-height: 1.2;
}
.stat-label {
  color: #999;
  font-size: 14px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>