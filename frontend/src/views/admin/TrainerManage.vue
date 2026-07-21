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

    <!-- ====== 筛选与搜索 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索教练姓名"
              clearable
              prefix-icon="Search"
              @keyup.enter="loadTrainers"
          />
        </el-col>
        <el-col :span="5">
          <el-select
              v-model="filterSpecialties"
              multiple
              collapse-tags
              placeholder="全部专长"
              clearable
              @change="loadTrainers"
              style="width:100%"
          >
            <el-option label="减脂" value="减脂" />
            <el-option label="增肌" value="增肌" />
            <el-option label="康复" value="康复" />
            <el-option label="塑形" value="塑形" />
            <el-option label="力量" value="力量" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select
              v-model="filterStatus"
              placeholder="全部状态"
              clearable
              @change="loadTrainers"
              style="width:100%"
          >
            <el-option label="在职" value="active" />
            <el-option label="休假" value="vacation" />
            <el-option label="离职" value="resigned" />
          </el-select>
        </el-col>
        <el-col :span="10" style="text-align: right">
          <el-button type="success" plain @click="exportTrainers">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 添加教练
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 教练列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            🏋️ 教练列表
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 人</el-tag>
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
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="教练" width="160">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:10px;cursor:pointer" @click="showDetail(row)">
              <el-avatar :size="40" :style="{ backgroundColor: getAvatarColor(row.name) }">
                {{ row.name?.charAt(0) || '?' }}
              </el-avatar>
              <div>
                <div style="font-weight:500;color:#409EFF">{{ row.name }}</div>
                <div style="font-size:12px;color:#999">ID: {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="specialty" label="专长" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="success" effect="plain">
              {{ row.specialty || '未设置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pricePerHour" label="价格(元/小时)" width="140" align="center">
          <template #default="{ row }">
            <span style="font-weight:bold;color:#E6A23C">¥{{ row.pricePerHour }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- ====== 操作列：增加【请假】按钮 ====== -->
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="warning" plain @click="openLeaveDialog(row)">
              请假
            </el-button>
            <el-button size="small" type="info" plain @click="showDetail(row)">
              详情
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
            @size-change="loadTrainers"
            @current-change="loadTrainers"
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
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name" required>
              <el-input v-model="formData.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="formData.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="专长" prop="specialty">
              <el-input v-model="formData.specialty" placeholder="如：减脂塑形" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格(元/小时)" prop="pricePerHour">
              <el-input-number
                  v-model="formData.pricePerHour"
                  :min="0"
                  :step="50"
                  controls-position="right"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择状态" style="width:100%">
            <el-option label="在职" value="active" />
            <el-option label="休假" value="vacation" />
            <el-option label="离职" value="resigned" />
          </el-select>
        </el-form-item>
        <el-form-item label="个人简介" prop="intro">
          <el-input
              v-model="formData.intro"
              type="textarea"
              :rows="3"
              placeholder="请输入教练简介"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTrainer" :loading="saving">
          {{ isEdit ? '更新' : '添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ====== 教练详情抽屉 ====== -->
    <el-drawer
        v-model="detailVisible"
        :title="detailTitle"
        direction="rtl"
        size="450px"
        destroy-on-close
    >
      <div v-if="detailData">
        <div style="text-align:center;margin-bottom:24px">
          <el-avatar :size="80" :style="{ backgroundColor: getAvatarColor(detailData.name), fontSize: '32px' }">
            {{ detailData.name?.charAt(0) || '?' }}
          </el-avatar>
          <h2 style="margin-top:12px">{{ detailData.name }}</h2>
          <el-tag :type="getStatusType(detailData.status)" size="large">
            {{ getStatusText(detailData.status) }}
          </el-tag>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="手机号">{{ detailData.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="专长">{{ detailData.specialty || '-' }}</el-descriptions-item>
          <el-descriptions-item label="价格(元/小时)">¥{{ detailData.pricePerHour }}</el-descriptions-item>
          <el-descriptions-item label="个人简介">{{ detailData.intro || '暂无简介' }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:16px;text-align:right">
          <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">
            编辑信息
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- ====== 请假审批列表 ====== -->
    <el-card style="margin-bottom:20px">
      <template #header>
        <div class="card-header">
          <span>📋 请假审批 <el-tag size="small" type="warning" v-if="pendingLeaves.length">{{ pendingLeaves.length }} 待审批</el-tag></span>
          <el-button size="small" text @click="loadPendingLeaves"><el-icon><Refresh /></el-icon> 刷新</el-button>
        </div>
      </template>
      <el-table :data="pendingLeaves" border style="width:100%" v-loading="leaveLoading" size="small">
        <el-table-column prop="trainerName" label="教练" width="100" />
        <el-table-column prop="leaveDate" label="请假日期" width="120" align="center" />
        <el-table-column prop="reason" label="原因" min-width="150" />
        <el-table-column prop="createdAt" label="申请时间" width="160" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'pending' ? 'warning' : row.status === 'approved' ? 'success' : 'danger'" size="small">
              {{ row.status === "pending" ? "待审批" : row.status === "approved" ? "已通过" : "已拒绝" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" v-if="hasPending">
          <template #default="{ row }">
            <el-button size="small" type="success" plain @click="approveLeave(row, 'approved')" :disabled="row.status !== 'pending'">通过</el-button>
            <el-button size="small" type="danger" plain @click="approveLeave(row, 'rejected')" :disabled="row.status !== 'pending'">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!pendingLeaves.length && !leaveLoading" description="暂无待审批请假" />
    </el-card>

    <!-- ====== 新增：请假对话框 ====== -->
    <el-dialog
        v-model="leaveDialogVisible"
        title="📅 教练请假设置"
        width="500px"
        destroy-on-close
    >
      <el-form :model="leaveForm" label-width="100px">
        <el-form-item label="请假日期" required>
          <el-date-picker
              v-model="leaveForm.leaveDate"
              type="date"
              placeholder="请选择请假日期"
              value-format="YYYY-MM-DD"
              style="width:100%"
              :disabled-date="disabledLeaveDate"
          />
        </el-form-item>
        <el-form-item label="请假原因">
          <el-input
              v-model="leaveForm.reason"
              type="textarea"
              :rows="2"
              placeholder="请输入请假原因（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leaveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitLeave" :loading="leaveLoading">
          确认请假
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Plus } from '@element-plus/icons-vue'

// ============ 统计数据 ============
const stats = ref([
  { title: '总教练', value: 0, icon: 'UserFilled', color: '#409EFF' },
  { title: '减脂专长', value: 0, icon: 'Star', color: '#67C23A' },
  { title: '增肌专长', value: 0, icon: 'Star', color: '#E6A23C' },
  { title: '康复专长', value: 0, icon: 'Star', color: '#F56C6C' }
])

// ============ 列表数据 ============
const tableData = ref([])
const allData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(5)
const searchKeyword = ref('')
const filterSpecialties = ref([])
const filterStatus = ref('')
const loading = ref(false)

// ============ 对话框 ============
const dialogVisible = ref(false)
const dialogTitle = ref('添加教练')
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const formData = ref({
  id: null,
  name: '',
  phone: '',
  specialty: '',
  pricePerHour: 300,
  status: 'active',
  intro: ''
})

// ============ 详情抽屉 ============
const detailVisible = ref(false)
const detailTitle = ref('教练详情')
const detailData = ref(null)

// ============ 新增：请假相关 ============
const pendingLeaves = ref([])
const leaveDialogVisible = ref(false)
const leaveLoading = ref(false)
const currentLeaveTrainer = ref(null)
const hasPending = computed(() => pendingLeaves.value.some(l => l.status === 'pending'))
const leaveForm = ref({
  leaveDate: '',
  reason: ''
})

// ============ 表单校验规则 ============
const formRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1\d{10}$/, message: '请输入以1开头的11位手机号', trigger: 'blur' }
  ],
  pricePerHour: [
    { required: true, message: '请输入价格', trigger: 'blur' }
  ]
}

// ============ 工具函数 ============
const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

const getStatusType = (status) => {
  const map = { active: 'success', vacation: 'warning', resigned: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { active: '在职', vacation: '休假', resigned: '离职' }
  return map[status] || '未知'
}

// ============ 新增：请假相关方法 ============
// 打开请假对话框
const openLeaveDialog = (row) => {
  currentLeaveTrainer.value = row
  leaveForm.value = {
    leaveDate: '',
    reason: ''
  }
  leaveDialogVisible.value = true
}

// 禁用今天之前的日期（只能选今天及未来）
const disabledLeaveDate = (time) => {
  return time.getTime() < new Date().setHours(0, 0, 0, 0)
}

// 提交请假
const submitLeave = async () => {
  if (!leaveForm.value.leaveDate) {
    ElMessage.warning('请选择请假日期')
    return
  }

  leaveLoading.value = true
  try {
    const res = await axios.post(
        `/api/trainers/${currentLeaveTrainer.value.id}/leave`,
        null,
        {
          params: {
            leaveDate: leaveForm.value.leaveDate,
            reason: leaveForm.value.reason || '临时请假'
          }
        }
    )

    if (res.data.success) {
      const cancelCount = res.data.cancelCount || 0
      ElMessage.success({
        message: `✅ 请假设置成功！\n当日有 ${cancelCount} 个会员预约已自动取消，请提醒会员重新预约。`,
        duration: 5000
      })
      leaveDialogVisible.value = false
      loadTrainers()
    } else {
      ElMessage.error(res.data.message || '请假失败')
    }
  } catch (error) {
    console.error('请假失败', error)
    ElMessage.error(error.response?.data?.message || '请假失败，请重试')
  } finally {
    leaveLoading.value = false
  }
}

// ====== 加载待审批请假 ======
const loadPendingLeaves = async () => {
  leaveLoading.value = true
  try {
    const res = await axios.get('/api/trainers/leaves/pending')
    pendingLeaves.value = res.data || []
  } catch (error) {
    console.error('加载待审批请假失败', error)
  } finally {
    leaveLoading.value = false
  }
}

// ====== 审批请假 ======
const approveLeave = async (row, status) => {
  const actionText = status === 'approved' ? '通过' : '拒绝'
  try {
    await ElMessageBox.confirm(
      '确定' + actionText + '该请假申请吗？',
      '审批确认',
      { confirmButtonText: '确定' + actionText, cancelButtonText: '取消', type: 'info' }
    )
    await axios.put('/api/trainers/leaves/' + row.id + '/approve', null, { params: { status: status } })
    ElMessage.success(actionText + '成功')
    loadPendingLeaves()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('审批失败', error)
      ElMessage.error(error.response?.data?.message || '审批失败')
    }
  }
}

// ============ 加载数据 ============
// 替换原有的 loadTrainers 方法
const loadTrainers = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/trainers', {
      params: {
        page: pageNum.value,          // ← 新增
        size: pageSize.value,         // ← 新增
        keyword: searchKeyword.value || undefined,
        specialties: filterSpecialties.value.length ? filterSpecialties.value.join(',') : undefined,
        status: filterStatus.value || undefined
      }
    });
    // ====== 直接使用后端返回的分页数据 ======
    tableData.value = res.data.list || [];
    total.value = res.data.total || 0;

  } catch (error) {
    console.error('加载教练失败', error);
    ElMessage.error('加载教练失败');
  } finally {
    loading.value = false;
  }
};

const updateStats = (data) => {
  const totalCount = data.length
  const fatLoss = data.filter(t => t.specialty?.includes('减脂')).length
  const muscleGain = data.filter(t => t.specialty?.includes('增肌')).length
  const rehab = data.filter(t => t.specialty?.includes('康复')).length

  stats.value = [
    { title: '总教练', value: totalCount, icon: 'UserFilled', color: '#409EFF' },
    { title: '减脂专长', value: fatLoss, icon: 'Star', color: '#67C23A' },
    { title: '增肌专长', value: muscleGain, icon: 'Star', color: '#E6A23C' },
    { title: '康复专长', value: rehab, icon: 'Star', color: '#F56C6C' }
  ]
}

const resetSearch = () => {
  searchKeyword.value = ''
  filterSpecialties.value = []
  filterStatus.value = ''
  pageNum.value = 1
  loadTrainers()
}

const refresh = () => {
  loadTrainers()
}

// ============ 详情 ============
const showDetail = (row) => {
  detailData.value = row
  detailTitle.value = `${row.name} 的详情`
  detailVisible.value = true
}

// ============ 添加/编辑 ============
const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加教练'
  formData.value = {
    id: null,
    name: '',
    phone: '',
    specialty: '',
    pricePerHour: 300,
    status: 'active',
    intro: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑教练'
  formData.value = { ...row }
  dialogVisible.value = true
}

const saveTrainer = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await axios.put(`/api/trainers/${formData.value.id}`, formData.value)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/api/trainers', formData.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadTrainers()
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
    phone: '',
    specialty: '',
    pricePerHour: 300,
    status: 'active',
    intro: ''
  }
}

// ============ 删除 ============
const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除教练「${row.name}」吗？此操作不可恢复。`,
      '危险操作',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await axios.delete(`/api/trainers/${row.id}`)
      ElMessage.success('删除成功')
      loadTrainers()
    } catch (error) {
      console.error('删除失败', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// ============ 导出 ============
const exportTrainers = async () => {
  try {
    const res = await axios.get('/api/trainers/export', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `教练列表_${new Date().toLocaleDateString()}.csv`)
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
  loadTrainers()
  loadPendingLeaves()
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