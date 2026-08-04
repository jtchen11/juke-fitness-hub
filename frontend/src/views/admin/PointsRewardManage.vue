<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            <el-icon style="vertical-align:middle;margin-right:6px"><Goods /></el-icon>
            积分商品管理
          </span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 添加商品
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" border v-loading="loading" style="width:100%">
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="name" label="商品名称" min-width="140" />
        <el-table-column prop="pointsRequired" label="所需积分" width="100" align="center" />
        <el-table-column prop="stock" label="库存" width="80" align="center">
          <template #default="{ row }">
            {{ row.stock === -1 ? '无限' : row.stock }}
          </template>
        </el-table-column>
        <el-table-column prop="rewardType" label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.rewardType)">{{ typeText(row.rewardType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approvalType" label="审批" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.approvalType === 'manual' ? 'warning' : 'success'">{{ row.approvalType === 'manual' ? '人工' : '自动' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column label="操作" width="250" align="center">
          <template #default="{ row }">
            <el-button size="small" :type="row.isActive ? 'warning' : 'success'" plain @click="handleToggleActive(row)">{{ row.isActive ? '下架' : '上架' }}</el-button>
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑商品' : '添加商品'" width="500px" destroy-on-close>
      <el-form :model="formData" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="formData.name" placeholder="如：私教课1节" />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="商品描述" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所需积分" prop="pointsRequired">
              <el-input-number v-model="formData.pointsRequired" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="formData.stock" :min="-1" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="商品类型" prop="rewardType">
              <el-select v-model="formData.rewardType" style="width:100%">
                <el-option label="私教课（自动兑换）" value="pt_session" />
                <el-option label="优惠券（自动兑换）" value="coupon" />
                <el-option label="实物商品（人工审批）" value="physical" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="formData.rewardType === 'pt_session' ? '课时数' : '面值/说明'" prop="rewardValue">
              <el-input v-if="formData.rewardType !== 'pt_session'" v-model="formData.rewardValue" placeholder="如：10元券 / 实物规格" />
              <el-input-number v-else v-model="formData.sessions" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
            v-if="formData.rewardType === 'coupon'"
            type="info"
            :closable="false"
            show-icon
            title="该类型不关联系统核销流程，仅作记录；兑换成功后请至前台出示核销"
            style="margin-bottom:12px"
        />
        <el-form-item label="图片地址" prop="imageUrl">
          <el-input v-model="formData.imageUrl" placeholder="商品缩略图 URL（可选）" />
        </el-form-item>
        <el-form-item label="上下架" prop="isActive">
          <el-switch v-model="formData.isActive" active-color="#4A6CF7" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="审批方式" prop="approvalType">
              <el-select v-model="formData.approvalType" style="width:100%">
                <el-option label="自动审批" value="auto" />
                <el-option label="人工审批" value="manual" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="formData.sortOrder" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveData" :loading="saving">{{ isEdit ? '更新' : '添加' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Goods } from '@element-plus/icons-vue'
import axios from 'axios'

const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)

const formData = ref({
  name: '',
  description: '',
  pointsRequired: 100,
  stock: -1,
  rewardType: 'pt_session',
  rewardValue: '',
  sessions: 1,
  approvalType: 'auto',
  imageUrl: '',
  isActive: true,
  sortOrder: 0
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  pointsRequired: [{ required: true, message: '请输入所需积分', trigger: 'blur' }],
  rewardType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/admin/points/rewards', { params: { page: page.value, size: size.value } })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  isEdit.value = false
  formData.value = { name: '', description: '', pointsRequired: 100, stock: -1, rewardType: 'pt_session', rewardValue: '', sessions: 1, approvalType: 'auto', imageUrl: '', isActive: true, sortOrder: 0 }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  formData.value = { ...row, rewardValue: row.rewardType === 'pt_session' ? '' : (row.rewardValue || ''), sessions: row.sessions || 1, approvalType: row.approvalType || 'auto' }
  dialogVisible.value = true
}

const saveData = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    formData.value.rewardValue = String(formData.value.rewardValue)
    if (isEdit.value) {
      await axios.put('/api/admin/points/rewards/' + formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/api/admin/points/rewards', formData.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

const typeMap = { pt_session: '私教课', coupon: '优惠券', physical: '实物商品', course: '课程' }
const typeText = (t) => typeMap[t] || t
const typeTag = (t) => (t === 'physical' ? 'warning' : t === 'pt_session' ? 'success' : 'primary')

const handleToggleActive = async (row) => {
  try {
    await axios.put('/api/admin/points/rewards/' + row.id, { ...row, isActive: !row.isActive })
    ElMessage.success(row.isActive ? '已下架' : '已上架')
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除商品「' + row.name + '」？', '提示', { type: 'warning' }).then(async () => {
    await axios.delete('/api/admin/points/rewards/' + row.id)
    ElMessage.success('已删除')
    loadData()
  }).catch(() => {})
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
