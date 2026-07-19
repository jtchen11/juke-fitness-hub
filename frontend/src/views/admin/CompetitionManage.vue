<template>
  <div>
    <!-- 统计卡片 -->
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

    <!-- 操作栏 -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索比赛名称"
              clearable
              @keyup.enter="loadData"
          />
        </el-col>
        <el-col :span="4">
          <el-select
              v-model="filterStatus"
              placeholder="全部状态"
              clearable
              @change="loadData"
              style="width:100%"
          >
            <el-option label="报名中" value="open" />
            <el-option label="已结束" value="closed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-col>
        <el-col :span="10" style="text-align: right">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 添加比赛
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>🏆 比赛列表（共 {{ total }} 场）</span>
          <el-button size="small" text @click="loadData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="name" label="比赛名称" min-width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="报名截止" width="170" />
        <el-table-column prop="maxParticipants" label="名额" width="80" align="center">
          <template #default="{ row }">
            {{ row.enrolled || 0 }} / {{ row.maxParticipants }}
          </template>
        </el-table-column>
        <el-table-column label="上架状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">
              {{ row.isActive ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" plain @click="showRegistrationList(row)">
              📋 报名名单
            </el-button>
            <el-button size="small" type="primary" plain @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.isActive ? 'warning' : 'success'" plain @click="toggleActive(row)">
              {{ row.isActive ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[5, 10, 20]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadData"
            @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="比赛名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入比赛名称" />
        </el-form-item>
        <el-form-item label="比赛介绍" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入比赛介绍" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker v-model="formData.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker v-model="formData.endTime" type="datetime" placeholder="选择结束时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报名截止" prop="deadline">
              <el-date-picker v-model="formData.deadline" type="datetime" placeholder="报名截止时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大名额" prop="maxParticipants">
              <el-input-number v-model="formData.maxParticipants" :min="1" :max="500" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择状态" style="width:100%">
            <el-option label="报名中" value="open" />
            <el-option label="已结束" value="closed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
    <!-- ====== 报名名单弹窗 ====== -->
    <el-dialog
        v-model="registrationDialogVisible"
        :title="`📋 ${registrationTarget?.name || ''} - 报名名单（${registrationList.length} 人）`"
        width="600px"
        destroy-on-close
    >
      <div style="margin-bottom:12px;text-align:right;">
        <el-button size="small" type="primary" plain @click="loadRegistrationList" :loading="registrationLoading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <el-table :data="registrationList" border v-loading="registrationLoading" max-height="400">
        <el-table-column prop="memberName" label="会员姓名" width="120" />
        <el-table-column prop="memberPhone" label="手机号" width="140" />
        <el-table-column prop="registrationTime" label="报名时间" width="180" />
      </el-table>

      <div v-if="!registrationLoading && registrationList.length === 0" style="text-align:center;padding:30px 0;color:#999;">
        📭 暂无会员报名
      </div>

      <template #footer>
        <el-button @click="registrationDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Trophy, Clock, CircleCheck, CircleClose } from '@element-plus/icons-vue'
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(5)
const searchKeyword = ref('')
const filterStatus = ref('')
// ============ 报名名单弹窗 ============
const registrationDialogVisible = ref(false)
const registrationLoading = ref(false)
const registrationTarget = ref(null)
const registrationList = ref([])

// 显示报名名单
const showRegistrationList = (row) => {
  registrationTarget.value = row
  registrationDialogVisible.value = true
  loadRegistrationList()
}

// 加载报名名单
const loadRegistrationList = async () => {
  if (!registrationTarget.value) return
  registrationLoading.value = true
  try {
    const res = await axios.get(`/api/competition-registrations/competition/${registrationTarget.value.id}`)
    registrationList.value = res.data || []
  } catch (error) {
    console.error('加载报名名单失败', error)
    ElMessage.error('加载报名名单失败')
  } finally {
    registrationLoading.value = false
  }
}
const stats = ref([
  { title: '总比赛', value: 0, icon: Trophy, color: '#409EFF' },
  { title: '报名中', value: 0, icon: Clock, color: '#67C23A' },
  { title: '已结束', value: 0, icon: CircleCheck, color: '#909399' },
  { title: '已取消', value: 0, icon: CircleClose, color: '#F56C6C' }
])

const dialogVisible = ref(false)
const dialogTitle = ref('添加比赛')
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const formData = ref({
  id: null,
  name: '',
  description: '',
  startTime: '',
  endTime: '',
  deadline: '',
  maxParticipants: 50,
  status: 'open',
  isActive: true
})

const formRules = {
  name: [{ required: true, message: '请输入比赛名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择报名截止时间', trigger: 'change' }],
  maxParticipants: [{ required: true, message: '请输入最大名额', trigger: 'blur' }]
}

const getStatusType = (status) => {
  const map = { open: 'success', closed: 'info', cancelled: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { open: '报名中', closed: '已结束', cancelled: '已取消' }
  return map[status] || '未知'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/competitions', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        status: filterStatus.value || undefined
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
    updateStats(tableData.value)
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const updateStats = (data) => {
  stats.value[0].value = data.length
  stats.value[1].value = data.filter(c => c.status === 'open').length
  stats.value[2].value = data.filter(c => c.status === 'closed').length
  stats.value[3].value = data.filter(c => c.status === 'cancelled').length
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加比赛'
  formData.value = { id: null, name: '', description: '', startTime: '', endTime: '', deadline: '', maxParticipants: 50, status: 'open', isActive: true }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑比赛'
  formData.value = { ...row }
  dialogVisible.value = true
}

const toggleActive = async (row) => {
  const newStatus = !row.isActive
  await axios.put(`/api/competitions/${row.id}`, { ...row, isActive: newStatus })
  ElMessage.success(newStatus ? '已上架' : '已下架')
  loadData()
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除比赛「${row.name}」吗？`, '确认删除', { type: 'warning' })
      .then(async () => {
        await axios.delete(`/api/competitions/${row.id}`)
        ElMessage.success('删除成功')
        loadData()
      })
      .catch(() => {})
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await axios.put(`/api/competitions/${formData.value.id}`, formData.value)
    } else {
      await axios.post('/api/competitions', formData.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
    dialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.stat-card { transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-4px); }
.stat-item { display: flex; align-items: center; gap: 16px; }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: white; flex-shrink: 0; }
.stat-info { flex: 1; }
.stat-number { font-size: 24px; font-weight: bold; }
.stat-label { color: #999; font-size: 14px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>