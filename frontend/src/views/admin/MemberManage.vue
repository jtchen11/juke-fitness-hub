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
              <div class="stat-change" v-if="stat.change !== undefined">
                <span :class="stat.change >= 0 ? 'up' : 'down'">
                  {{ stat.change >= 0 ? '↑' : '↓' }} {{ Math.abs(stat.change) }}%
                </span>
                <span style="color:#999;font-size:12px;margin-left:4px">较上月</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 筛选与搜索 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索姓名或手机号"
              clearable
              prefix-icon="Search"
              @keyup.enter="loadMembers"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterLevel" placeholder="全部等级" clearable @change="loadMembers">
            <el-option label="普通会员" value="普通会员" />
            <el-option label="黄金会员" value="黄金会员" />
            <el-option label="铂金会员" value="铂金会员" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable @change="loadMembers">
            <el-option label="正常" value="active" />
            <el-option label="即将到期" value="expiring" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadMembers">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
        <el-col :span="6" style="text-align: right">
          <el-button type="success" plain @click="exportMembers">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 添加会员
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 会员列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            👥 会员列表
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
        <el-table-column label="会员" width="120">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="36" :style="{ backgroundColor: getAvatarColor(row.name) }">
                {{ row.name?.charAt(0) || '?' }}
              </el-avatar>
              <div>
                <div style="font-weight:500">{{ row.name }}</div>
                <div style="font-size:12px;color:#999">ID: {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="gender" label="性别" width="70" align="center">
          <template #default="{ row }">
            <span>{{ row.gender === 'MALE' ? '👨 男' : row.gender === 'FEMALE' ? '👩 女' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="birthday" label="生日" width="120" align="center">
          <template #default="{ row }">
            <span>{{ row.birthday || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag
                :type="row.level === '铂金会员' ? 'warning' : row.level === '黄金会员' ? 'success' : 'info'"
                effect="dark"
                size="small"
            >
              {{ row.level || '普通会员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireDate" label="有效期" width="120" align="center">
          <template #default="{ row }">
            <span :style="{ color: getExpireColor(row.expireDate) }">
              {{ row.expireDate || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.expireDate)" size="small" effect="plain">
              {{ getStatusText(row.expireDate) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="height" label="身高(cm)" width="80" align="center">
          <template #default="{ row }">{{ row.height || '-' }}</template>
        </el-table-column>
        <el-table-column prop="weight" label="体重(kg)" width="80" align="center">
          <template #default="{ row }">{{ row.weight || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <!-- ====== 操作列 - 只有打卡（补签）、编辑、权益、删除，没有人脸 ====== -->
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="success" plain @click="handleCheckIn(row)">
              补签
            </el-button>
            <el-button size="small" type="primary" plain @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button size="small" type="warning" plain @click="showBenefits(row)">
              权益
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadMembers"
            @current-change="loadMembers"
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
          label-width="90px"
          label-position="right"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name" required>
              <el-input v-model="formData.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone" required>
              <el-input v-model="formData.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-select v-model="formData.gender" placeholder="请选择性别" style="width:100%">
                <el-option label="男" value="MALE" />
                <el-option label="女" value="FEMALE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生日" prop="birthday">
              <el-date-picker
                  v-model="formData.birthday"
                  type="date"
                  placeholder="选择生日"
                  value-format="YYYY-MM-DD"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="会员等级" prop="level">
              <el-select v-model="formData.level" placeholder="请选择等级" style="width:100%">
                <el-option label="普通会员" value="普通会员" />
                <el-option label="黄金会员" value="黄金会员" />
                <el-option label="铂金会员" value="铂金会员" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期" prop="expireDate">
              <el-date-picker
                  v-model="formData.expireDate"
                  type="date"
                  placeholder="选择有效期"
                  value-format="YYYY-MM-DD"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="身高(cm)" prop="height">
              <el-input-number v-model="formData.height" :min="50" :max="250" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="体重(kg)" prop="weight">
              <el-input-number v-model="formData.weight" :min="10" :max="300" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMember" :loading="saving">
          {{ isEdit ? '更新' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Plus } from '@element-plus/icons-vue'

// ============ 统计数据 ============
const stats = ref([
  { title: '总会员', value: 0, icon: 'User', color: '#409EFF', change: 0 },
  { title: '本月新增', value: 0, icon: 'Plus', color: '#67C23A', change: 0 },
  { title: '黄金会员', value: 0, icon: 'Star', color: '#E6A23C', change: 0 },
  { title: '即将到期', value: 0, icon: 'Clock', color: '#F56C6C', change: 0 }
])

// ============ 列表数据 ============
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const filterLevel = ref('')
const filterStatus = ref('')
const loading = ref(false)

// ============ 对话框 ============
const dialogVisible = ref(false)
const dialogTitle = ref('添加会员')
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const formData = ref({
  id: null,
  name: '',
  phone: '',
  gender: '',
  birthday: '',
  level: '普通会员',
  expireDate: '',
  height: null,
  weight: null
})

const formRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号必须以1开头，共11位数字', trigger: 'blur' }
  ]
}

// ============ 工具函数 ============
const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

const getExpireColor = (date) => {
  if (!date) return '#909399'
  const now = new Date()
  const expire = new Date(date)
  const days = Math.ceil((expire - now) / (1000 * 60 * 60 * 24))
  if (days < 0) return '#F56C6C'
  if (days < 7) return '#E6A23C'
  return '#67C23A'
}

const getStatusType = (date) => {
  if (!date) return 'info'
  const now = new Date()
  const expire = new Date(date)
  const days = Math.ceil((expire - now) / (1000 * 60 * 60 * 24))
  if (days < 0) return 'danger'
  if (days < 7) return 'warning'
  return 'success'
}

const getStatusText = (date) => {
  if (!date) return '未知'
  const now = new Date()
  const expire = new Date(date)
  const days = Math.ceil((expire - now) / (1000 * 60 * 60 * 24))
  if (days < 0) return '已过期'
  if (days < 7) return `剩余${days}天`
  return '正常'
}

const showBenefits = async (row) => {
  try {
    const res = await axios.get(`/api/members/${row.id}/benefits`);
    const data = res.data;

    let msg = `🌟 ${data.levelName} 专属权益：\n`;
    if (data.discount > 0) {
      msg += `• 所有课程享受 ${data.discount}% 折扣\n`;
    }
    if (data.freeSessions > 0) {
      msg += `• 每月 ${data.freeSessions} 次免费私教课\n`;
    }
    if (data.canOverbook) {
      msg += `• 可超额预约已满课程（+2名额）\n`;
    }
    if (!data.discount && !data.freeSessions && !data.canOverbook) {
      msg += '• 标准课程预约（无额外权益）\n';
    }

    ElMessageBox.alert(msg, `${data.levelName} 权益说明`, {
      confirmButtonText: '知道了',
      type: 'info'
    });
  } catch (error) {
    console.error('获取权益失败', error);
    ElMessage.error('获取权益信息失败');
  }
};

// ============ 加载数据 ============
const loadStats = async () => {
  try {
    const res = await axios.get('/api/members/stats')
    const data = res.data
    stats.value = [
      { title: '总会员', value: data.total || 0, icon: 'User', color: '#409EFF', change: data.totalChange || 0 },
      { title: '本月新增', value: data.monthlyNew || 0, icon: 'Plus', color: '#67C23A', change: data.monthlyChange || 0 },
      { title: '黄金会员', value: data.goldCount || 0, icon: 'Star', color: '#E6A23C', change: 0 },
      { title: '即将到期', value: data.expiringCount || 0, icon: 'Clock', color: '#F56C6C', change: 0 }
    ]
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const loadMembers = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/members', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        level: filterLevel.value || undefined,
        status: filterStatus.value || undefined
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('加载会员失败', error)
    ElMessage.error('加载会员失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchKeyword.value = ''
  filterLevel.value = ''
  filterStatus.value = ''
  pageNum.value = 1
  loadMembers()
}

const refresh = () => {
  loadStats()
  loadMembers()
}

// ============ 添加/编辑 ============
const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加会员'
  formData.value = {
    id: null,
    name: '',
    phone: '',
    gender: '',
    birthday: '',
    level: '普通会员',
    expireDate: '',
    height: null,
    weight: null
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑会员'
  formData.value = { ...row }
  dialogVisible.value = true
}

const saveMember = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  if (!formData.value.name) {
    ElMessage.warning('请输入姓名')
    return
  }

  const submitData = { ...formData.value }
  if (submitData.birthday === '') submitData.birthday = null
  if (submitData.expireDate === '') submitData.expireDate = null

  saving.value = true
  try {
    if (isEdit.value) {
      await axios.put(`/api/members/${submitData.id}`, submitData)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/api/members', submitData)
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
    phone: '',
    gender: '',
    birthday: '',
    level: '普通会员',
    expireDate: '',
    height: null,
    weight: null
  }
}

// ============ 删除 ============
const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除会员「${row.name}」吗？此操作不可恢复。`,
      '危险操作',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      await axios.delete(`/api/members/${row.id}`)
      ElMessage.success('删除成功')
      refresh()
    } catch (error) {
      console.error('删除失败', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// ============ 导出 ============
const exportMembers = async () => {
  try {
    const res = await axios.get('/api/members/export', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `会员列表_${new Date().toLocaleDateString()}.xlsx`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// ====== 补签（管理员手动打卡） ======
const handleCheckIn = async (row) => {
  try {
    const res = await axios.post(`/api/check-in/member/${row.id}`)
    if (res.data.success) {
      ElMessage.success(`✅ ${res.data.memberName || row.name} 补签成功！`)
      refresh()
    } else {
      ElMessage.error(res.data.message || '补签失败')
    }
  } catch (error) {
    console.error('补签失败', error)
    ElMessage.error('补签失败，请重试')
  }
}

// ============ 生命周期 ============
onMounted(() => {
  loadStats()
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
.stat-change {
  margin-top: 2px;
  font-size: 13px;
}
.stat-change .up { color: #67C23A; }
.stat-change .down { color: #F56C6C; }

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