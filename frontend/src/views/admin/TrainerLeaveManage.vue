<template>
  <div>
    <el-card>
      <template #header><span>请假审批</span></template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="待审批" name="pending">
          <el-table :data="pendingRows" stripe v-loading="loading">
            <el-table-column prop="trainerName" label="教练姓名" width="120" />
            <el-table-column prop="leaveDate" label="请假日期" width="120" />
            <el-table-column label="时段" width="90" align="center">
              <template #default="{ row }">{{ periodText(row.period) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="请假原因" min-width="160" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="申请时间" width="180" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button size="small" color="#4A6CF7" @click="approve(row, 'approved')">通过</el-button>
                <el-button size="small" color="#FF5252" @click="approve(row, 'rejected')">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && pendingRows.length === 0" description="暂无待审批申请" />
        </el-tab-pane>
        <el-tab-pane label="已处理" name="done">
          <el-table :data="doneRows" stripe v-loading="loading">
            <el-table-column prop="trainerName" label="教练姓名" width="120" />
            <el-table-column prop="leaveDate" label="请假日期" width="120" />
            <el-table-column label="时段" width="90" align="center">
              <template #default="{ row }">{{ periodText(row.period) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="请假原因" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="180" />
          </el-table>
          <el-empty v-if="!loading && doneRows.length === 0" description="暂无已处理记录" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('pending')
const loading = ref(false)
const list = ref([])

const pendingRows = computed(() => list.value.filter(i => i.status === 'pending'))
const doneRows = computed(() => list.value.filter(i => i.status !== 'pending'))

const periodText = (p) => ({ full_day: '全天', morning: '上午', afternoon: '下午' }[p] || '全天')
const statusText = (s) => ({ pending: '待审批', approved: '已通过', rejected: '已拒绝' }[s] || s)
const statusTag = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger' }[s] || '')

const load = async () => {
  loading.value = true
  try {
    const r = await axios.get('/api/trainers/leaves/pending')
    list.value = r.data || []
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const approve = async (row, status) => {
  const action = status === 'approved' ? '通过' : '拒绝'
  try {
    await ElMessageBox.confirm('确认' + action + '该请假申请？', '提示')
    const r = await axios.put('/api/trainers/leaves/' + row.id + '/approve', null, { params: { status } })
    if (r.data && r.data.success) {
      ElMessage.success(r.data.message || (status === 'approved' ? '已通过' : '已拒绝'))
      load()
    } else {
      ElMessage.warning((r.data && r.data.message) || '操作失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

onMounted(load)
</script>
