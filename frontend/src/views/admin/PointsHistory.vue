<template>
  <div>
    <!-- ====== 顶部操作 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-input
              v-model="keyword"
              placeholder="搜索会员姓名 / 手机号"
              clearable
              prefix-icon="Search"
              @keyup.enter="loadData"
          />
        </el-col>
        <el-col :span="5">
          <el-select
              v-model="filterType"
              placeholder="全部类型"
              clearable
              @change="loadData"
              style="width:100%"
          >
            <el-option label="签到（自助训练）" value="NORMAL_TRAINING" />
            <el-option label="团课签到" value="CLASS_CHECKIN" />
            <el-option label="私教完成" value="PT_COMPLETED" />
            <el-option label="兑换" value="redemption_group" />
            <el-option label="比赛奖励" value="competition_reward" />
            <el-option label="补签" value="makeup" />
            <el-option label="管理员调整" value="admin_adjust" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-date-picker
              v-model="startDate"
              type="date"
              placeholder="开始日期"
              value-format="YYYY-MM-DD"
              @change="loadData"
              style="width:100%"
          />
        </el-col>
        <el-col :span="4">
          <el-date-picker
              v-model="endDate"
              type="date"
              placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="loadData"
              style="width:100%"
          />
        </el-col>
        <el-col :span="6" style="text-align: right">
          <el-button type="primary" @click="loadData">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="warning" plain @click="openAdjust">
            <el-icon><EditPen /></el-icon> 手动调整
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 积分流水列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            📒 积分流水
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag>
          </span>
          <el-button size="small" text @click="loadData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading" row-key="id">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="会员" min-width="140">
          <template #default="{ row }">
            <div>{{ row.memberName || '未知' }}</div>
            <div style="font-size:12px;color:#999">{{ row.memberPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="变动时间" width="170" />
        <el-table-column label="变动类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.changeType, row.description)" size="small" effect="plain">
              {{ typeLabel(row.changeType, row.description) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动值" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.points >= 0 ? '#67C23A' : '#F56C6C', fontWeight: 'bold' }">
              {{ row.points > 0 ? '+' : '' }}{{ row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="变动后余额" width="100" align="center">
          <template #default="{ row }">
            <span style="color:#4A6CF7;font-weight:bold">{{ row.balance }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="来源描述" min-width="180" show-overflow-tooltip />
      </el-table>

      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadData"
            @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- ====== 手动调整弹窗 ====== -->
    <el-dialog v-model="adjustVisible" title="🎯 手动调整积分" width="480px" destroy-on-close>
      <el-form :model="adjustForm" label-width="90px">
        <el-form-item label="选择会员" required>
          <el-select
              v-model="adjustForm.memberId"
              placeholder="搜索并选择会员"
              filterable
              style="width:100%"
          >
            <el-option
                v-for="m in memberOptions"
                :key="m.id"
                :label="m.name + '（' + m.phone + '）'"
                :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整值" required>
          <el-input-number
              v-model="adjustForm.points"
              :min="-10000"
              :max="10000"
              :step="10"
              style="width:100%"
          />
          <div class="form-tip">正数=加分，负数=扣分</div>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input
              v-model="adjustForm.reason"
              type="textarea"
              :rows="3"
              placeholder="请填写调整原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="warning" :loading="adjusting" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Search, Refresh, EditPen } from '@element-plus/icons-vue'

const keyword = ref('')
const filterType = ref('')
const startDate = ref('')
const endDate = ref('')
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)

// 手动调整
const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = ref({ memberId: null, points: 10, reason: '' })
const memberOptions = ref([])

const typeLabel = (type, desc) => {
  const makeup = (desc || '').includes('补签')
  const map = {
    NORMAL_TRAINING: makeup ? '补签（自助训练）' : '签到（自助训练）',
    CLASS_CHECKIN: makeup ? '补签（团课）' : '团课签到',
    PT_COMPLETED: makeup ? '补签（私教）' : '私教完成',
    redemption: '兑换',
    redemption_refund: '兑换驳回退回',
    competition_reward: '比赛奖励',
    admin_adjust: '管理员调整'
  }
  return map[type] || type
}

const typeTag = (type, desc) => {
  const label = typeLabel(type, desc)
  if (label.startsWith('补签')) return 'warning'
  const map = {
    '签到（自助训练）': 'info',
    '团课签到': 'success',
    '私教完成': 'success',
    '兑换': 'danger',
    '兑换驳回退回': 'danger',
    '比赛奖励': 'primary',
    '管理员调整': 'warning'
  }
  return map[label] || 'info'
}

const buildParams = () => {
  const params = { page: pageNum.value, size: pageSize.value }
  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  if (filterType.value === 'redemption_group') {
    params.changeTypes = 'redemption,redemption_refund'
  } else if (filterType.value === 'makeup') {
    params.category = 'makeup'
  } else if (filterType.value) {
    params.changeTypes = filterType.value
  }
  if (startDate.value) params.startDate = startDate.value
  if (endDate.value) params.endDate = endDate.value
  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/points/admin/history', { params: buildParams() })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('加载积分流水失败', error)
    ElMessage.error('加载积分流水失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  keyword.value = ''
  filterType.value = ''
  startDate.value = ''
  endDate.value = ''
  pageNum.value = 1
  loadData()
}

const loadMembers = async () => {
  try {
    const res = await axios.get('/api/members/all')
    memberOptions.value = res.data || []
  } catch (error) {
    console.error('加载会员列表失败', error)
  }
}

const openAdjust = () => {
  adjustForm.value = { memberId: null, points: 10, reason: '' }
  adjustVisible.value = true
}

const submitAdjust = async () => {
  if (!adjustForm.value.memberId) {
    ElMessage.warning('请选择会员')
    return
  }
  if (adjustForm.value.points === 0 || adjustForm.value.points == null) {
    ElMessage.warning('调整值不能为 0')
    return
  }
  if (!adjustForm.value.reason.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  adjusting.value = true
  try {
    const res = await axios.post('/api/points/admin/adjust', {
      memberId: adjustForm.value.memberId,
      points: adjustForm.value.points,
      reason: adjustForm.value.reason.trim()
    })
    if (res.data.success) {
      ElMessage.success(`调整成功，当前积分 ${res.data.balance}`)
      adjustVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.data.message || '调整失败')
    }
  } catch (error) {
    console.error('调整积分失败', error)
    ElMessage.error(error.response?.data?.message || '调整失败，请重试')
  } finally {
    adjusting.value = false
  }
}

onMounted(() => {
  loadData()
  loadMembers()
})
</script>

<style scoped>
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
.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
