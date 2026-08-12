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
            <el-option v-for="sp in specialtyPresets" :key="sp" :label="sp" :value="sp" />
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
        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="36" :src="row.avatar" :style="{ backgroundColor: getAvatarColor(row.name) }">
              {{ row.name?.charAt(0) || '?' }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="140">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:10px;cursor:pointer" @click="showDetail(row)">
              <span style="font-weight:500;color:#409EFF">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="专长" min-width="180">
          <template #default="{ row }">
            <el-tag
                v-for="sp in splitSpecialties(row.specialty)"
                :key="sp"
                size="small"
                type="success"
                effect="plain"
                style="margin-right:6px"
            >
              {{ sp }}
            </el-tag>
            <span v-if="!row.specialty" style="color:#999">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="120" align="center">
          <template #default="{ row }">
            <span style="font-weight:bold;color:#E6A23C">{{ fmtPrice(row.pricePerHour) }}/小时</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalClasses" label="累计上课" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.totalClasses ?? 0 }} 节</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="info" plain @click="showDetail(row)">
              详情
            </el-button>
            <el-button size="small" type="primary" plain @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button size="small" type="warning" plain @click="openLeaveDialog(row)">
              请假
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
        width="600px"
        @close="resetForm"
        destroy-on-close
    >
      <el-form
          :model="formData"
          :rules="formRules"
          ref="formRef"
          label-width="120px"
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
        <el-form-item label="专长" prop="specialty">
          <el-select
              v-model="formData.specialty"
              multiple
              collapse-tags
              placeholder="请选择专长（可多选）"
              style="width:100%"
          >
            <el-option v-for="sp in specialtyPresets" :key="sp" :label="sp" :value="sp" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
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
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="formData.status" placeholder="请选择状态" style="width:100%">
                <el-option label="在职" value="active" />
                <el-option label="休假" value="vacation" />
                <el-option label="离职" value="resigned" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
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
        size="480px"
        destroy-on-close
    >
      <div v-if="detailData" v-loading="detailLoading">
        <!-- 顶部：头像 + 姓名 + 状态 -->
        <div style="display:flex;align-items:center;gap:16px;margin-bottom:20px">
          <el-avatar :size="56" :src="detailData.avatar" :style="{ backgroundColor: getAvatarColor(detailData.name), fontSize: '24px' }">
            {{ detailData.name?.charAt(0) || '?' }}
          </el-avatar>
          <div style="flex:1">
            <div style="font-size:20px;font-weight:bold">{{ detailData.name }}</div>
            <el-tag :type="getStatusType(detailData.status)" size="small" style="margin-top:6px">
              {{ getStatusText(detailData.status) }}
            </el-tag>
          </div>
        </div>

        <!-- 统计卡片：横向三列 -->
        <el-row :gutter="12" style="margin-bottom:20px">
          <el-col :span="8" v-for="sc in statCards" :key="sc.label">
            <el-card shadow="hover" :body-style="{ padding: '14px 10px', textAlign: 'center' }">
              <div style="font-size:22px;font-weight:bold;color:#4A6CF7">{{ sc.value }}</div>
              <div style="font-size:12px;color:#999;margin-top:4px">{{ sc.label }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 基本信息 -->
        <el-descriptions :column="1" border>
          <el-descriptions-item label="手机号">{{ detailData.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="专长">
            <el-tag
                v-for="sp in splitSpecialties(detailData.specialty)"
                :key="sp"
                size="small"
                type="success"
                effect="plain"
                style="margin-right:6px"
            >{{ sp }}</el-tag>
            <span v-if="!detailData.specialty">未设置</span>
          </el-descriptions-item>
          <el-descriptions-item label="价格(元/小时)">{{ fmtPrice(detailData.pricePerHour) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 个人简介 -->
        <div style="margin-top:16px">
          <div class="section-title">📝 个人简介</div>
          <div style="color:#606266;line-height:1.7">{{ detailData.intro || '暂无简介' }}</div>
        </div>

        <!-- 近期上课记录 -->
        <div style="margin-top:16px">
          <div class="section-title">🕒 近期上课记录</div>
          <div v-if="recentRecords.length === 0" style="color:#999;padding:8px 0">暂无记录</div>
          <div v-for="(rec, i) in recentRecords" :key="i" class="recent-row">
            <span style="color:#999;font-size:12px;flex-shrink:0">{{ rec.time }}</span>
            <span style="flex:1;margin:0 8px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
              {{ rec.type === 'pt' ? '私教' : '团课' }} · {{ rec.name }}
            </span>
            <el-tag :type="getStatusType(rec.status)" size="small" effect="plain">
              {{ rec.statusText || rec.status }}
            </el-tag>
          </div>
        </div>

        <div style="margin-top:20px;text-align:right">
          <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">
            编辑信息
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- ====== 请假对话框 ====== -->
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
        <el-form-item label="请假时段" required>
          <el-select v-model="leaveForm.period" placeholder="请选择请假时段" style="width:100%">
            <el-option label="全天（07:00-21:00）" value="full_day" />
            <el-option label="上午（07:00-12:00）" value="morning" />
            <el-option label="下午（12:00-21:00）" value="afternoon" />
          </el-select>
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
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Plus } from '@element-plus/icons-vue'

// ============ 专长预设 ============
const specialtyPresets = [
  '减脂塑形', '增肌力量', '康复拉伸', '功能性训练',
  '孕产康复', '瑜伽', '普拉提', '体能训练'
]

// ============ 统计数据 ============
const stats = ref([
  { title: '总教练', value: 0, icon: 'UserFilled', color: '#409EFF' },
  { title: '减脂专长', value: 0, icon: 'Star', color: '#67C23A' },
  { title: '增肌专长', value: 0, icon: 'Star', color: '#E6A23C' },
  { title: '康复专长', value: 0, icon: 'Star', color: '#F56C6C' }
])

// ============ 列表数据 ============
const tableData = ref([])
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
  specialty: [],
  pricePerHour: 300,
  status: 'active',
  intro: ''
})

// ============ 详情抽屉 ============
const detailVisible = ref(false)
const detailTitle = ref('教练详情')
const detailData = ref(null)
const detailLoading = ref(false)
const recentRecords = ref([])
const statCards = ref([
  { label: '累计上课', value: 0 },
  { label: '本月上课', value: 0 },
  { label: '学员数', value: 0 }
])

// ============ 请假相关 ============
const leaveDialogVisible = ref(false)
const leaveLoading = ref(false)
const leaveForm = ref({
  trainerId: null,
  trainerName: '',
  leaveDate: '',
  period: 'full_day',
  reason: ''
})

const openLeaveDialog = (row) => {
  leaveForm.value = {
    trainerId: row.id,
    trainerName: row.name,
    leaveDate: '',
    period: 'full_day',
    reason: ''
  }
  leaveDialogVisible.value = true
}

const disabledLeaveDate = (date) => {
  return date.getTime() < Date.now() - 86400000
}

const submitLeave = async () => {
  if (!leaveForm.value.leaveDate) {
    ElMessage.warning('请选择请假日期')
    return
  }
  leaveLoading.value = true
  try {
    const res = await axios.post(`/api/trainers/${leaveForm.value.trainerId}/leave`, {
      leaveDate: leaveForm.value.leaveDate,
      period: leaveForm.value.period,
      reason: leaveForm.value.reason || '教练请假'
    })
    if (res.data && res.data.success === false) {
      ElMessage.error(res.data.message || '请假申请提交失败')
      return
    }
    ElMessage.success('请假申请已提交，等待管理员审批')
    leaveDialogVisible.value = false
  } catch (error) {
    console.error('提交请假失败', error)
    ElMessage.error('请假申请提交失败，请重试')
  } finally {
    leaveLoading.value = false
  }
}

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
  const map = { active: 'success', vacation: 'warning', resigned: 'info' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { active: '在职', vacation: '休假', resigned: '离职' }
  return map[status] || '未知'
}

const splitSpecialties = (specialty) => {
  if (!specialty) return []
  return String(specialty).split(',').map(s => s.trim()).filter(Boolean)
}

const fmtPrice = (price) => {
  if (price === null || price === undefined) return '¥0'
  return '¥' + Number(price).toFixed(2).replace(/\.00$/, '')
}

// ============ 加载数据 ============
const loadTrainers = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/trainers', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        specialties: filterSpecialties.value.length ? filterSpecialties.value.join(',') : undefined,
        status: filterStatus.value || undefined
      }
    });
    tableData.value = res.data.list || [];
    total.value = res.data.total || 0;
  } catch (error) {
    console.error('加载教练失败', error);
    ElMessage.error('加载教练失败');
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    const res = await axios.get('/api/dashboard/coach-stats')
    const specialties = res.data.specialties || {}
    stats.value = [
      { title: '总教练', value: res.data.total || 0, icon: 'UserFilled', color: '#409EFF' },
      { title: '减脂专长', value: specialties['减脂塑形'] || 0, icon: 'Star', color: '#67C23A' },
      { title: '增肌专长', value: specialties['增肌力量'] || 0, icon: 'Star', color: '#E6A23C' },
      { title: '康复专长', value: specialties['康复拉伸'] || 0, icon: 'Star', color: '#F56C6C' }
    ]
  } catch (error) {
    console.error('加载教练统计失败', error)
  }
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
  loadStats()
}

// ============ 详情 ============
const showDetail = async (row) => {
  detailData.value = { ...row }
  detailTitle.value = `${row.name} 的详情`
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [detailRes, statsRes] = await Promise.all([
      axios.get(`/api/trainers/${row.id}`),
      axios.get(`/api/trainers/${row.id}/stats`)
    ])
    detailData.value = { ...detailData.value, ...(detailRes.data || {}) }
    const st = statsRes.data || {}
    statCards.value = [
      { label: '累计上课', value: st.totalClasses ?? 0 },
      { label: '本月上课', value: st.monthClasses ?? st.thisMonthSessions ?? 0 },
      { label: '学员数', value: st.studentCount ?? 0 }
    ]
    recentRecords.value = st.recentRecords || []
  } catch (error) {
    console.error('加载教练详情失败', error)
    ElMessage.error('加载教练详情失败')
  } finally {
    detailLoading.value = false
  }
}

// ============ 添加/编辑 ============
const resetForm = () => {
  formData.value = {
    id: null,
    name: '',
    phone: '',
    specialty: [],
    pricePerHour: 300,
    status: 'active',
    intro: ''
  }
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加教练'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑教练'
  formData.value = {
    ...row,
    specialty: splitSpecialties(row.specialty)
  }
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
    const payload = {
      ...formData.value,
      specialty: (formData.value.specialty || []).join(',')
    }
    if (isEdit.value) {
      await axios.put(`/api/trainers/${formData.value.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/api/trainers', payload)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadTrainers()
    loadStats()
  } catch (error) {
    console.error('保存失败', error)
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
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
      loadStats()
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
  const route = useRoute()
  if (route.query.keyword) {
    searchKeyword.value = String(route.query.keyword)
  }
  loadTrainers()
  loadStats()
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
.section-title {
  font-weight: bold;
  margin-bottom: 8px;
}
.recent-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed #eee;
}
.recent-row:last-child {
  border-bottom: none;
}
:deep(.el-form-item__label) {
  white-space: nowrap;
}
</style>