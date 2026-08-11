<template>
  <div class="points-manage">
    <el-card>
      <template #header><span>积分兑换审批</span></template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="待审批" name="pending">
          <el-table :data="pendingList" stripe v-loading="loading">
            <el-table-column label="会员" min-width="140">
              <template #default="{ row }">
                <div>{{ row.memberName || ('会员ID:' + row.memberId) }}</div>
                <div style="font-size:12px;color:#999">{{ row.memberPhone || '' }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="rewardName" label="兑换商品" min-width="150">
              <template #default="{ row }">{{ row.rewardName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="redemptionType" label="兑换类型" width="120">
              <template #default="{ row }">
                <el-tag :type="typeTag(row.redemptionType)" size="small">{{ typeLabel(row.redemptionType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pointsSpent" label="消耗积分" width="100" />
            <el-table-column prop="createdAt" label="申请时间" width="180" />
            <el-table-column label="操作" min-width="200">
              <template #default="{ row }">
                <el-button size="small" color="#4A6CF7" @click="approve(row)">通过</el-button>
                <el-button size="small" color="#FF5252" @click="reject(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && pendingList.length === 0" description="暂无待审批申请" />
        </el-tab-pane>
        <el-tab-pane label="已处理" name="done">
          <el-table :data="doneList" stripe v-loading="loadingDone">
            <el-table-column label="会员" min-width="140">
              <template #default="{ row }">
                <div>{{ row.memberName || ('会员ID:' + row.memberId) }}</div>
                <div style="font-size:12px;color:#999">{{ row.memberPhone || '' }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="rewardName" label="兑换商品" min-width="150">
              <template #default="{ row }">{{ row.rewardName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="redemptionType" label="兑换类型" width="120">
              <template #default="{ row }">
                <el-tag :type="typeTag(row.redemptionType)" size="small">{{ typeLabel(row.redemptionType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pointsSpent" label="消耗积分" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'approved' ? 'success' : 'danger'" size="small">{{ row.status === 'approved' ? '已通过' : '已驳回' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="adminRemark" label="审批备注" min-width="150" />
            <el-table-column prop="createdAt" label="申请时间" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('pending')
const loading = ref(false)
const loadingDone = ref(false)
const pendingList = ref([])
const doneList = ref([])

const typeLabel = (t) => ({ pt_session: '私教课', coupon: '优惠券', physical: '实物商品', physical_goods: '实物商品', course: '课程' }[t] || t)
const typeTag = (t) => ({ pt_session: '', coupon: 'warning', physical: 'danger', physical_goods: 'danger', course: '' }[t] || '')

onMounted(() => { loadPending(); loadDone() })

const loadPending = async () => {
  loading.value = true
  try { const r = await axios.get('/api/points/admin/pending'); pendingList.value = r.data.list || [] }
  catch (e) { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const loadDone = async () => {
  loadingDone.value = true
  try {
    const r = await axios.get('/api/points/admin/list', { params: { page: 1, size: 100, status: 'approved,rejected' } })
    doneList.value = r.data.list || []
  } catch (e) {}
  finally { loadingDone.value = false }
}

const approve = async (row) => {
  try {
    await ElMessageBox.confirm('确认通过该兑换申请？', '提示')
    const r = await axios.post('/api/points/admin/approve/' + row.id, { remark: '' })
    if (r.data && r.data.success) {
      ElMessage.success('已通过')
      loadPending(); loadDone()
    } else {
      ElMessage.warning((r.data && r.data.message) || '操作失败')
    }
  } catch (e) { if (e !== 'cancel') ElMessage.error('操作失败') }
}

const reject = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { inputType: 'textarea' })
    const r = await axios.post('/api/points/admin/reject/' + row.id, { remark: value })
    if (r.data && r.data.success) {
      ElMessage.success('已驳回')
      loadPending(); loadDone()
    } else {
      ElMessage.warning((r.data && r.data.message) || '操作失败')
    }
  } catch (e) { if (e !== 'cancel') ElMessage.error('操作失败') }
}
</script>
