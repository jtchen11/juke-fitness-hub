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

    <!-- ====== 筛选条件 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="4">
          <el-select
              v-model="filterMemberId"
              placeholder="全部会员"
              clearable
              @change="loadBookings"
              style="width:100%"
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
          <el-select
              v-model="filterTrainerId"
              placeholder="全部教练"
              clearable
              @change="loadBookings"
              style="width:100%"
          >
            <el-option
                v-for="t in trainerList"
                :key="t.id"
                :label="t.name"
                :value="t.id"
            />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select
              v-model="filterStatus"
              placeholder="全部状态"
              clearable
              @change="loadBookings"
              style="width:100%"
          >
            <el-option label="待上课" value="scheduled" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-date-picker
              v-model="filterStartDate"
              type="date"
              placeholder="开始日期"
              value-format="YYYY-MM-DD"
              @change="loadBookings"
              style="width:100%"
          />
        </el-col>
        <el-col :span="3">
          <el-date-picker
              v-model="filterEndDate"
              type="date"
              placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="loadBookings"
              style="width:100%"
          />
        </el-col>
        <el-col :span="7" style="text-align: right">
          <el-button type="success" plain @click="exportBookings">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <!-- 已移除“创建预约”按钮 -->
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 预约列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            📋 私教预约列表
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag>
          </span>
          <div>
            <el-button size="small" text @click="refresh">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table
          :data="tableData"
          border
          style="width:100%"
          v-loading="loading"
          row-key="id"
      >
        <el-table-column prop="id" label="ID" width="60" align="center" />
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
        <el-table-column label="教练" width="130">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="30" :style="{ backgroundColor: getAvatarColor(row.trainerName) }">
                {{ row.trainerName?.charAt(0) || '?' }}
              </el-avatar>
              <span>{{ row.trainerName || '未知教练' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="appointmentTime" label="预约时间" width="160" />
        <el-table-column prop="durationMinutes" label="时长(分钟)" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">
              {{ row.durationMinutes || 60 }} min
            </el-tag>
          </template>
        </el-table-column>
        <!-- ====== 状态列：支持 cancelled_by_trainer ====== -->
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- ====== 备注列：优先显示取消原因 ====== -->
        <el-table-column prop="notes" label="备注" min-width="120">
          <template #default="{ row }">
            <span v-if="row.cancelReason" style="color:#f56c6c;font-size:13px;">
              {{ row.cancelReason }}
            </span>
            <span v-else style="color:#666;font-size:13px">{{ row.notes || '-' }}</span>
          </template>
        </el-table-column>
        <!-- ====== 操作列：只保留状态变更 + 详情 ====== -->
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
                v-if="row.status === 'scheduled'"
                size="small"
                type="success"
                plain
                @click="updateStatus(row, 'completed')"
            >
              完成
            </el-button>
            <el-button
                v-if="row.status === 'scheduled'"
                size="small"
                type="warning"
                plain
                @click="updateStatus(row, 'cancelled')"
            >
              取消
            </el-button>
            <el-button
                size="small"
                type="info"
                plain
                @click="showDetail(row)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[5, 10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadBookings"
            @current-change="loadBookings"
        />
      </div>
    </el-card>

    <!-- ====== 预约详情抽屉 ====== -->
    <el-drawer
        v-model="detailVisible"
        :title="detailTitle"
        direction="rtl"
        size="450px"
        destroy-on-close
    >
      <div v-if="detailData">
        <div style="text-align:center;margin-bottom:24px">
          <el-tag :type="getStatusType(detailData.status)" size="large">
            {{ getStatusText(detailData.status) }}
          </el-tag>
          <h2 style="margin-top:12px">私教预约 #{{ detailData.id }}</h2>
          <div style="color:#999">预约时间：{{ detailData.appointmentTime || '-' }}</div>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="会员">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="24" :style="{ backgroundColor: getAvatarColor(detailData.memberName) }">
                {{ detailData.memberName?.charAt(0) || '?' }}
              </el-avatar>
              {{ detailData.memberName || '未知' }}
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="教练">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="24" :style="{ backgroundColor: getAvatarColor(detailData.trainerName) }">
                {{ detailData.trainerName?.charAt(0) || '?' }}
              </el-avatar>
              {{ detailData.trainerName || '未知' }}
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="预约时间">{{ detailData.appointmentTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ detailData.durationMinutes || 60 }} 分钟</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detailData.status)" size="small">
              {{ getStatusText(detailData.status) }}
            </el-tag>
          </el-descriptions-item>
          <!-- ====== 备注/取消原因 ====== -->
          <el-descriptions-item label="备注">
            {{ detailData.cancelReason || detailData.notes || '无' }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:16px;display:flex;gap:10px;justify-content:flex-end">
          <el-button
              v-if="detailData.status === 'scheduled'"
              type="success"
              plain
              @click="updateStatus(detailData, 'completed'); detailVisible = false"
          >
            标记已完成
          </el-button>
          <el-button
              v-if="detailData.status === 'scheduled'"
              type="warning"
              plain
              @click="updateStatus(detailData, 'cancelled'); detailVisible = false"
          >
            取消预约
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'

// ============ 统计数据 ============
const stats = ref([
  { title: '总预约', value: 0, icon: 'Notebook', color: '#409EFF' },
  { title: '待上课', value: 0, icon: 'Clock', color: '#67C23A' },
  { title: '已完成', value: 0, icon: 'CircleCheck', color: '#909399' },
  { title: '已取消', value: 0, icon: 'CircleClose', color: '#F56C6C' }
])

// ============ 列表数据 ============
const tableData = ref([])
const memberList = ref([])
const trainerList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(5)
const filterMemberId = ref('')
const filterTrainerId = ref('')
const filterStatus = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')
const loading = ref(false)

// ============ 详情抽屉 ============
const detailVisible = ref(false)
const detailTitle = ref('预约详情')
const detailData = ref(null)

// ============ 工具函数 ============
const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

// ====== 修改：增加 cancelled_by_trainer 状态映射 ======
const getStatusType = (status) => {
  const map = {
    scheduled: 'success',
    completed: 'info',
    cancelled: 'danger',
    cancelled_by_trainer: 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    scheduled: '待上课',
    completed: '已完成',
    cancelled: '已取消',
    cancelled_by_trainer: '已取消'
  }
  return map[status] || '未知'
}

// ============ 加载数据 ============
const loadStats = async () => {
  try {
    const res = await axios.get('/api/personal-trainings/stats')
    const data = res.data
    stats.value = [
      { title: '总预约', value: data.total || 0, icon: 'Notebook', color: '#409EFF' },
      { title: '待上课', value: data.scheduled || 0, icon: 'Clock', color: '#67C23A' },
      { title: '已完成', value: data.completed || 0, icon: 'CircleCheck', color: '#909399' },
      { title: '已取消', value: data.cancelled || 0, icon: 'CircleClose', color: '#F56C6C' }
    ]
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const loadBookings = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/personal-trainings', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        memberId: filterMemberId.value || undefined,
        trainerId: filterTrainerId.value || undefined,
        status: filterStatus.value || undefined,
        startDate: filterStartDate.value || undefined,
        endDate: filterEndDate.value || undefined
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('加载预约列表失败', error)
    ElMessage.error('加载预约列表失败')
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

const loadTrainers = async () => {
  try {
    const res = await axios.get('/api/trainers/all')
    trainerList.value = res.data || []
  } catch (error) {
    console.error('加载教练列表失败', error)
  }
}

const resetSearch = () => {
  filterMemberId.value = ''
  filterTrainerId.value = ''
  filterStatus.value = ''
  filterStartDate.value = ''
  filterEndDate.value = ''
  pageNum.value = 1
  loadBookings()
}

const refresh = () => {
  loadStats()
  loadBookings()
}

// ============ 状态变更（完成/取消） ============
const updateStatus = async (row, status) => {
  const statusText = getStatusText(status)
  try {
    await ElMessageBox.confirm(
        `确定要将预约 #${row.id} 标记为「${statusText}」吗？`,
        '状态变更',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await axios.patch(`/api/personal-trainings/${row.id}/status`, { status })
    ElMessage.success(`已标记为「${statusText}」`)
    refresh()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('状态更新失败', error)
      ElMessage.error('状态更新失败')
    }
  }
}

// ============ 详情 ============
const showDetail = (row) => {
  detailData.value = row
  detailTitle.value = `预约 #${row.id} 的详情`
  detailVisible.value = true
}

// ============ 导出 ============
const exportBookings = async () => {
  try {
    const res = await axios.get('/api/personal-trainings/export', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `私教预约_${new Date().toLocaleDateString()}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// ============ 生命周期 ============
onMounted(() => {
  loadStats()
  loadBookings()
  loadMembers()
  loadTrainers()
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