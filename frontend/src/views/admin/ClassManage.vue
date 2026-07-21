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
              <div class="stat-sub" v-if="stat.sub !== undefined">
                <span style="color:#999;font-size:12px">预约率 {{ stat.sub }}%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 团课类型 Tabs ====== -->
    <el-card style="margin-bottom: 20px">
      <el-tabs v-model="activeType" @tab-change="loadClasses">
        <el-tab-pane label="📚 全部团课" name="all" />
        <el-tab-pane label="💰 付费团课" name="paid" />
        <el-tab-pane label="❤️ 公益团课" name="free" />
      </el-tabs>
    </el-card>

    <!-- ====== 筛选与搜索 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="4">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索课程名称"
              clearable
              prefix-icon="Search"
              @keyup.enter="loadClasses"
          />
        </el-col>
        <el-col :span="4">
          <el-select
              v-model="filterTrainerId"
              placeholder="全部教练"
              clearable
              @change="loadClasses"
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
              @change="loadClasses"
              style="width:100%"
          >
            <el-option label="待上课" value="scheduled" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-date-picker
              v-model="filterDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              @change="loadClasses"
              style="width:100%"
          />
        </el-col>
        <el-col :span="10" style="text-align: right">
          <el-button type="success" plain @click="exportClasses">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <el-button type="warning" plain @click="handleBatchDelete" :disabled="selectedIds.length === 0">
            <el-icon><Delete /></el-icon> 批量删除
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 添加团课
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 团课列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            📅 团课列表
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 门</el-tag>
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
          @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column label="课程名称" width="150">
          <template #default="{ row }">
            <span style="color:#409EFF;cursor:pointer;font-weight:500" @click="showDetail(row)">
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="教练" width="120">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="30" :style="{ backgroundColor: getAvatarColor(row.trainerName) }">
                {{ row.trainerName?.charAt(0) || '?' }}
              </el-avatar>
              <span>{{ row.trainerName || '未分配' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column label="预约情况" width="140" align="center">
          <template #default="{ row }">
            <div>
              <span style="font-weight:bold;color:#409EFF">{{ row.enrolled || 0 }}</span>
              <span style="color:#999"> / {{ row.maxCapacity }}</span>
            </div>
            <el-progress
                :percentage="getBookingRate(row)"
                :color="getProgressColor(row)"
                :stroke-width="6"
                :show-text="false"
            />
          </template>
        </el-table-column>
        <el-table-column label="课程类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'free' ? 'success' : 'warning'" size="small" effect="dark">
              {{ row.type === 'free' ? '❤️ 公益' : '💰 付费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="info" plain @click="showDetail(row)">
              详情
            </el-button>
            <el-button size="small" type="warning" plain @click="handleCopy(row)">
              复制
            </el-button>
            <el-button size="small" type="success" plain @click="showCheckIns(row)">
              核销记录
            </el-button>
            <el-button size="small" type="primary" plain @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[5, 10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadClasses"
            @current-change="loadClasses"
        />
      </div>
    </el-card>

    <!-- ====== 添加/编辑对话框 ====== -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="560px"
        @close="resetForm"
        destroy-on-close
    >
      <el-form
          :model="formData"
          :rules="formRules"
          ref="formRef"
          label-width="100px"
      >
        <el-form-item label="课程名称" prop="name" required>
          <el-input v-model="formData.name" placeholder="如：热力搏击" />
        </el-form-item>
        <el-form-item label="教练" prop="trainerId">
          <el-select v-model="formData.trainerId" placeholder="请选择教练" style="width:100%">
            <el-option
                v-for="t in trainerList"
                :key="t.id"
                :label="t.name + ' (' + t.specialty + ')'"
                :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                  v-model="formData.startTime"
                  type="datetime"
                  placeholder="选择开始时间"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker
                  v-model="formData.endTime"
                  type="datetime"
                  placeholder="选择结束时间"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大容量" prop="maxCapacity">
              <el-input-number
                  v-model="formData.maxCapacity"
                  :min="1"
                  :max="50"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="formData.status" placeholder="请选择状态" style="width:100%">
                <el-option label="待上课" value="scheduled" />
                <el-option label="已完成" value="completed" />
                <el-option label="已取消" value="cancelled" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- ====== 课程介绍（新增） ====== -->
        <el-form-item label="课程介绍" prop="description">
          <el-input
              v-model="formData.description"
              type="textarea"
              :rows="3"
              placeholder="请输入课程介绍，如：适合初学者、减脂塑形等"
          />
        </el-form-item>
        <!-- ====== 课程类型（新增） ====== -->
        <el-form-item label="课程类型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio label="paid">💰 付费团课</el-radio>
            <el-radio label="free">❤️ 公益团课（免费）</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- ====== 价格（仅付费团课显示） ====== -->
        <el-form-item v-if="formData.type === 'paid'" label="价格(元)" prop="price">
          <el-input-number
              v-model="formData.price"
              :min="0"
              :precision="2"
              :step="10"
              style="width:100%"
          />
        </el-form-item>
        <el-form-item label="允许访客体验">
          <el-switch v-model="formData.allowVisitor" active-color="#4A6CF7" />
          <span class="switch-desc">{{ formData.allowVisitor ? '允许' : '不允许' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveClass" :loading="saving">
          {{ isEdit ? '更新' : '添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ====== 课程详情抽屉（增强版） ====== -->
    <el-drawer
        v-model="detailVisible"
        :title="detailTitle"
        direction="rtl"
        size="650px"
        destroy-on-close
    >
      <div v-if="detailData">
        <div style="text-align:center;margin-bottom:24px">
          <el-tag :type="getStatusType(detailData.status)" size="large">
            {{ getStatusText(detailData.status) }}
          </el-tag>
          <el-tag :type="detailData.type === 'free' ? 'success' : 'warning'" size="large" style="margin-left:8px">
            {{ detailData.type === 'free' ? '❤️ 公益' : '💰 付费' }}
          </el-tag>
          <h2 style="margin-top:12px">{{ detailData.name }}</h2>
          <div style="color:#999">教练：{{ detailData.trainerName || '未分配' }}</div>
          <div style="margin-top:8px">
            <el-progress
                :percentage="getBookingRate(detailData)"
                :color="getProgressColor(detailData)"
                :stroke-width="12"
                :format="() => `${detailData.enrolled || 0}/${detailData.maxCapacity} 人`"
            />
          </div>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="开始时间">{{ detailData.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detailData.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="已预约/最大容量">
            {{ detailData.enrolled || 0 }} / {{ detailData.maxCapacity }}
          </el-descriptions-item>
          <el-descriptions-item label="预约率">
            <span :style="{ color: getProgressColor(detailData) }">
              {{ getBookingRate(detailData) }}%
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detailData.status)" size="small">
              {{ getStatusText(detailData.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="课程类型">
            <el-tag :type="detailData.type === 'free' ? 'success' : 'warning'" size="small">
              {{ detailData.type === 'free' ? '公益免费' : '付费团课' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.price && detailData.price > 0" label="价格">
            ¥{{ detailData.price }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- ====== 课程介绍 ====== -->
        <div v-if="detailData.description" style="margin: 16px 0; padding: 12px; background: #f5f7fa; border-radius: 8px;">
          <div style="font-weight:bold;margin-bottom:8px;font-size:14px;color:#333;">📖 课程介绍</div>
          <div style="color:#666;font-size:14px;line-height:1.8;white-space:pre-wrap;">
            {{ detailData.description }}
          </div>
        </div>

        <!-- ====== 报名名单 ====== -->
        <div style="margin: 16px 0;">
          <div style="font-weight:bold;margin-bottom:12px;font-size:14px;color:#333;">
            📋 报名名单（{{ enrollmentList.length }} 人）
            <el-button size="small" type="primary" plain @click="loadEnrollments" style="margin-left:12px;">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
          <el-table :data="enrollmentList" border size="small" v-loading="enrollmentLoading" max-height="250">
            <el-table-column prop="memberName" label="会员姓名" width="100" />
            <el-table-column prop="memberPhone" label="手机号" width="120" />
            <el-table-column prop="bookingTime" label="预约时间" width="160" />
            <el-table-column label="支付状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.paymentStatus === 'paid' ? 'success' : 'warning'" size="small">
                  {{ row.paymentStatus === 'paid' ? '已支付' : '待支付' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="enrollmentList.length === 0 && !enrollmentLoading" style="text-align:center;color:#999;padding:20px 0;">
            暂无会员报名
          </div>
        </div>

        <div style="margin-top:16px;display:flex;gap:10px;justify-content:flex-end">
          <el-button type="warning" plain @click="handleCopy(detailData); detailVisible = false">
            复制课程
          </el-button>
          <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">
            编辑课程
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>

    <!-- ====== 核销记录弹窗 ====== -->
    <el-dialog v-model="checkInVisible" title="核销记录" width="600px" destroy-on-close>
      <div v-if="checkInLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>加载中...</p>
      </div>
      <div v-else>
        <div style="margin-bottom:16px">
          <span style="font-weight:bold">课程：</span>{{ checkInClassData?.name }}
          <span style="margin-left:16px;font-weight:bold">日期：</span>{{ checkInClassData?.classDate }}
        </div>
        <el-table :data="checkInRecords" border style="width:100%" size="small">
          <el-table-column prop="memberName" label="会员姓名" width="100" />
          <el-table-column prop="checkInTime" label="签到时间" width="160" />
          <el-table-column prop="checkInType" label="签到方式" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.checkInType === 'code' ? 'success' : 'info'" size="small">
                {{ row.checkInType === "code" ? "签到码" : "手动" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'checked_in' ? 'success' : 'warning'" size="small">
                {{ row.status === "checked_in" ? "已签到" : "待签到" }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!checkInRecords.length && !checkInLoading" description="暂无核销记录" />
      </div>
    </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Plus, Delete } from '@element-plus/icons-vue'

// =============================================
// 状态变量
// =============================================

const activeType = ref('all')  // all / paid / free

// 统计数据
const stats = ref([
  { title: '总课程', value: 0, icon: 'Calendar', color: '#409EFF', sub: 0 },
  { title: '待上课', value: 0, icon: 'Clock', color: '#67C23A', sub: 0 },
  { title: '已完成', value: 0, icon: 'CircleCheck', color: '#909399', sub: 0 },
  { title: '已取消', value: 0, icon: 'CircleClose', color: '#F56C6C', sub: 0 }
])

// 列表数据
const tableData = ref([])
const trainerList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(5)
const searchKeyword = ref('')
const filterTrainerId = ref('')
const filterStatus = ref('')
const filterDate = ref('')
const loading = ref(false)
const selectedIds = ref([])

// 报名名单
const enrollmentList = ref([])
const enrollmentLoading = ref(false)

// 添加/编辑对话框
const dialogVisible = ref(false)
const dialogTitle = ref('添加团课')
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const formData = ref({
  id: null,
  name: '',
  trainerId: null,
  startTime: '',
  endTime: '',
  maxCapacity: 20,
  enrolled: 0,
  status: 'scheduled',
  type: 'paid',        // 新增：paid / free
  price: 99.00,        // 新增：价格
  description: '',
  allowVisitor: false 
})

// 详情抽屉
const detailVisible = ref(false)
const detailTitle = ref('课程详情')
const detailData = ref(null)

// =============================================
// 表单校验规则
// =============================================

const formRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  maxCapacity: [{ required: true, message: '请输入最大容量', trigger: 'blur' }],
  type: [{ required: true, message: '请选择课程类型', trigger: 'change' }]
}

// =============================================
// 工具函数
// =============================================

const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

const getStatusType = (status) => {
  const map = { scheduled: 'success', completed: 'info', cancelled: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { scheduled: '待上课', completed: '已完成', cancelled: '已取消' }
  return map[status] || '未知'
}

const getBookingRate = (row) => {
  if (!row.maxCapacity || row.maxCapacity === 0) return 0
  return Math.round(((row.enrolled || 0) / row.maxCapacity) * 100)
}

const getProgressColor = (row) => {
  const rate = getBookingRate(row)
  if (rate >= 90) return '#F56C6C'
  if (rate >= 70) return '#E6A23C'
  return '#67C23A'
}

// =============================================
// 加载数据
// =============================================

const loadStats = async () => {
  try {
    const res = await axios.get('/api/classes/stats')
    const data = res.data
    stats.value = [
      {
        title: '总课程',
        value: data.total || 0,
        icon: 'Calendar',
        color: '#409EFF',
        sub: data.overallRate !== undefined ? data.overallRate : 0
      },
      { title: '待上课', value: data.scheduled || 0, icon: 'Clock', color: '#67C23A', sub: 0 },
      { title: '已完成', value: data.completed || 0, icon: 'CircleCheck', color: '#909399', sub: 0 },
      { title: '已取消', value: data.cancelled || 0, icon: 'CircleClose', color: '#F56C6C', sub: 0 }
    ]
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const loadClasses = async () => {
  loading.value = true
  try {
    const params = {
      page: pageNum.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined,
      trainerId: filterTrainerId.value || undefined,
      status: filterStatus.value || undefined,
      date: filterDate.value || undefined
    }
    if (activeType.value !== 'all') {
      params.type = activeType.value
    }

    const res = await axios.get('/api/classes', { params })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('加载团课失败', error)
    ElMessage.error('加载团课失败')
  } finally {
    loading.value = false
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

// =============================================
// 报名名单
// =============================================

const loadEnrollments = async () => {
  if (!detailData.value) return
  enrollmentLoading.value = true
  try {
    const res = await axios.get(`/api/class-bookings/class/${detailData.value.id}`)
    enrollmentList.value = res.data || []
  } catch (error) {
    console.error('加载报名名单失败', error)
    ElMessage.error('加载报名名单失败')
  } finally {
    enrollmentLoading.value = false
  }
}

// =============================================
// 刷新与重置
// =============================================

const resetSearch = () => {
  searchKeyword.value = ''
  filterTrainerId.value = ''
  filterStatus.value = ''
  filterDate.value = ''
  pageNum.value = 1
  loadClasses()
}

const refresh = () => {
  loadStats()
  loadClasses()
}

// =============================================
// 批量操作
// =============================================

const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的课程')
    return
  }
  ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.length} 门课程吗？此操作不可恢复。`,
      '批量删除',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await axios.delete('/api/classes/batch', { data: { ids: selectedIds.value } })
      ElMessage.success('删除成功')
      refresh()
    } catch (error) {
      console.error('批量删除失败', error)
      ElMessage.error('批量删除失败')
    }
  }).catch(() => {})
}

// =============================================
// 详情
// =============================================

const showDetail = (row) => {
  detailData.value = row
  detailTitle.value = `${row.name} 的详情`
  detailVisible.value = true
  setTimeout(() => {
    loadEnrollments()
  }, 100)
}

// =============================================
// 复制课程
// =============================================

const handleCopy = (row) => {
  isEdit.value = false
  dialogTitle.value = `复制课程：${row.name}`
  formData.value = {
    id: null,
    name: row.name + ' (复制)',
    trainerId: row.trainerId,
    startTime: row.startTime,
    endTime: row.endTime,
    maxCapacity: row.maxCapacity,
    enrolled: 0,
    status: 'scheduled',
    type: row.type || 'paid',
    price: row.price || 99.00,
    description: row.description || ''
  }
  dialogVisible.value = true
}

// =============================================
// 添加/编辑
// =============================================

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加团课'
  formData.value = {
    id: null,
    name: '',
    trainerId: null,
    startTime: '',
    endTime: '',
    maxCapacity: 20,
    enrolled: 0,
    status: 'scheduled',
    type: 'paid',
    price: 99.00,
    description: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑团课'
  formData.value = { ...row }
  dialogVisible.value = true
}

const saveClass = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await axios.put(`/api/classes/${formData.value.id}`, formData.value)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/api/classes', formData.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    refresh()
  } catch (error) {
    console.error('保存失败', error)
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

const resetForm = () => {
  formData.value = {
    id: null,
    name: '',
    trainerId: null,
    startTime: '',
    endTime: '',
    maxCapacity: 20,
    enrolled: 0,
    status: 'scheduled',
    type: 'paid',
    price: 99.00,
    description: ''
  }
}

// =============================================
// 删除
// =============================================

const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除课程「${row.name}」吗？此操作不可恢复。`,
      '危险操作',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await axios.delete(`/api/classes/${row.id}`)
      ElMessage.success('删除成功')
      refresh()
    } catch (error) {
      console.error('删除失败', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// =============================================
// 导出
// =============================================

const exportClasses = async () => {
  try {
    const res = await axios.get('/api/classes/export', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `团课列表_${new Date().toLocaleDateString()}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// =============================================
// 生命周期
// =============================================

onMounted(() => {
  loadStats()
  loadClasses()
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
.stat-sub {
  margin-top: 2px;
}
.switch-desc { margin-left: 8px; font-size: 13px; color: #999; }
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