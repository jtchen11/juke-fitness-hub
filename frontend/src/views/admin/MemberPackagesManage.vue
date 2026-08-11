<template>
  <div>
    <!-- ====== 筛选与搜索 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input
              v-model="keyword"
              placeholder="搜索会员姓名 / 手机号"
              clearable
              prefix-icon="Search"
              @keyup.enter="loadData"
          />
        </el-col>
        <el-col :span="8">
          <el-select
              v-model="statusFilter"
              placeholder="全部状态"
              clearable
              @change="loadData"
              style="width:100%"
          >
            <el-option label="使用中" value="active" />
            <el-option label="待激活" value="pending" />
            <el-option label="已过期" value="expired" />
            <el-option label="已用完" value="used_up" />
            <el-option label="已退款" value="refunded" />
          </el-select>
        </el-col>
        <el-col :span="8" style="text-align: right">
          <el-button type="primary" @click="loadData">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 课程包列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            📦 会员课程包
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag>
          </span>
          <el-button size="small" text @click="loadData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading" row-key="id">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="会员" min-width="140">
          <template #default="{ row }">
            <div>{{ row.memberName || '-' }}</div>
            <div style="font-size:12px;color:#999">{{ row.memberPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="packageName" label="套餐名称" min-width="160" />
        <el-table-column label="课时" width="120" align="center">
          <template #default="{ row }">
            <span>{{ row.usedSessions || 0 }} / {{ row.totalSessions }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remainingSessions" label="剩余" width="70" align="center">
          <template #default="{ row }">
            <span style="font-weight:bold;color:#E6A23C">{{ row.remainingSessions }}</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">
            <div v-if="row.startDate">{{ row.startDate }} ~ {{ row.endDate || '长期' }}</div>
            <div v-else style="color:#999">未激活（截止 {{ row.activationDeadline || '-' }}）</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row)" size="small" effect="dark">
              {{ getStatusText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实付价" width="100" align="center">
          <template #default="{ row }">
            <span>¥{{ row.price != null ? row.price : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
                size="small"
                type="danger"
                plain
                :disabled="!canRefund(row)"
                @click="openRefund(row)"
            >退款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadData"
            @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- ====== 退款确认弹窗 ====== -->
    <el-dialog v-model="refundVisible" title="课程包退款" width="480px" destroy-on-close>
      <div v-if="currentRow" class="refund-info">
        <div class="refund-line"><span class="label">会员</span><span>{{ currentRow.memberName }}（{{ currentRow.memberPhone }}）</span></div>
        <div class="refund-line"><span class="label">套餐</span><span>{{ currentRow.packageName }}</span></div>
        <div class="refund-line"><span class="label">剩余课时</span><span>{{ currentRow.remainingSessions }} 节</span></div>
        <div class="refund-line"><span class="label">预计退款</span><span class="amount">¥{{ currentRow.price }}</span></div>
      </div>
      <el-form label-width="80px" style="margin-top:12px">
        <el-form-item label="退款原因">
          <el-input
              v-model="refundReason"
              type="textarea"
              :rows="3"
              placeholder="请输入退款原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refunding" @click="doRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const keyword = ref('')
const statusFilter = ref('')
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const refundVisible = ref(false)
const refunding = ref(false)
const refundReason = ref('')
const currentRow = ref(null)

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const getStatusText = (row) => {
  if (row.status === 'refunded') return '已退款'
  if (!row.remainingSessions || row.remainingSessions <= 0) return '已用完'
  if (!row.startDate) {
    return row.activationDeadline && row.activationDeadline < today() ? '已失效' : '待激活'
  }
  if (row.endDate && row.endDate < today()) return '已过期'
  return '使用中'
}

const getStatusType = (row) => {
  const text = getStatusText(row)
  const map = { '使用中': 'success', '待激活': 'warning', '已过期': 'danger', '已失效': 'danger', '已用完': 'info', '已退款': 'info' }
  return map[text] || 'info'
}

const canRefund = (row) => {
  if (row.status === 'refunded') return false
  if (!row.remainingSessions || row.remainingSessions <= 0) return false
  if (row.endDate && row.endDate < today()) return false
  return true
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: pageNum.value, size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await axios.get('/api/private-packages/admin/list', { params })
    let list = res.data.list || []
    if (statusFilter.value) {
      list = list.filter((r) => getStatusText(r) === statusFilter.value || (statusFilter.value === 'active' && getStatusText(r) === '使用中'))
    }
    tableData.value = list
    total.value = res.data.total || 0
  } catch (error) {
    console.error('加载课程包失败', error)
    ElMessage.error('加载课程包失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  keyword.value = ''
  statusFilter.value = ''
  pageNum.value = 1
  loadData()
}

const openRefund = (row) => {
  currentRow.value = row
  refundReason.value = ''
  refundVisible.value = true
}

const doRefund = async () => {
  if (!refundReason.value.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  refunding.value = true
  try {
    const res = await axios.post('/api/private-packages/refund', {
      packageId: currentRow.value.id,
      memberId: currentRow.value.memberId,
      reason: refundReason.value.trim()
    })
    if (res.data && res.data.success === false) {
      ElMessage.error(res.data.message || '退款失败')
      return
    }
    ElMessage.success(`退款成功，退款金额 ¥${res.data.refundAmount}`)
    refundVisible.value = false
    loadData()
  } catch (error) {
    console.error('退款失败', error)
    ElMessage.error(error.response?.data?.message || '退款失败，请重试')
  } finally {
    refunding.value = false
  }
}

onMounted(loadData)
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
.refund-info {
  border: 1px solid #EBEEF5;
  border-radius: 8px;
  padding: 12px 16px;
}
.refund-line {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}
.refund-line .label {
  color: #999;
}
.refund-line .amount {
  color: #F56C6C;
  font-weight: bold;
}
</style>
