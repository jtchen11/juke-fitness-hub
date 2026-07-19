<template>
  <div>
    <!-- ====== 搜索与筛选 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索套餐名称"
              clearable
              @keyup.enter="loadData"
              prefix-icon="Search"
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
            <el-option label="已上架" value="true" />
            <el-option label="已下架" value="false" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
        <el-col :span="10" style="text-align: right">
          <el-button type="primary" @click="handleAdd">+ 添加套餐</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 套餐列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>📦 私教套餐管理（共 {{ total }} 个）</span>
          <el-button size="small" text @click="loadData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="套餐名称" width="140" />
        <el-table-column prop="typeLabel" label="类型" width="80" />
        <el-table-column prop="sessions" label="课时" width="70" />
        <el-table-column prop="validDays" label="有效期(天)" width="100" />
        <el-table-column label="价格" width="140">
          <template #default="{ row }">
            <span style="color:#E6A23C;font-weight:bold">¥{{ row.price }}</span>
            <span v-if="row.originalPrice" style="color:#999;text-decoration:line-through;margin-left:8px">
              ¥{{ row.originalPrice }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'">
              {{ row.isActive ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.isActive ? 'warning' : 'success'" plain @click="toggleActive(row)">
              {{ row.isActive ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[5, 10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadData"
            @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 添加/编辑对话框（保持不变） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <!-- 原有表单内容不变 -->
        <el-form-item label="套餐名称" prop="name">
          <el-input v-model="form.name" placeholder="如：塑形包周" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" placeholder="请选择" style="width:100%">
                <el-option label="包周" value="weekly" />
                <el-option label="包月" value="monthly" />
                <el-option label="包季" value="quarterly" />
                <el-option label="单次" value="single" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型显示名" prop="typeLabel">
              <el-input v-model="form.typeLabel" placeholder="包周/包月/包季/单次" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="课时数" prop="sessions">
              <el-input-number v-model="form.sessions" :min="1" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期(天)" prop="validDays">
              <el-input-number v-model="form.validDays" :min="1" :max="365" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

// ============ 列表数据 ============
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const filterStatus = ref('')

// ============ 对话框 ============
const dialogVisible = ref(false)
const dialogTitle = ref('添加套餐')
const saving = ref(false)
const formRef = ref(null)
const isEdit = ref(false)

const form = ref({
  id: null,
  name: '',
  type: 'weekly',
  typeLabel: '',
  sessions: 3,
  validDays: 7,
  price: 1000,
  originalPrice: null,
  isActive: true,
  sortOrder: 0,
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入套餐名称' }],
  type: [{ required: true, message: '请选择类型' }],
  sessions: [{ required: true, message: '请输入课时数' }],
  validDays: [{ required: true, message: '请输入有效期' }],
  price: [{ required: true, message: '请输入售价' }]
}

// ============ 加载数据 ============
const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/admin/packages', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        status: filterStatus.value || undefined
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchKeyword.value = ''
  filterStatus.value = ''
  pageNum.value = 1
  loadData()
}

// ============ 增删改 ============
const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加套餐'
  form.value = { id: null, name: '', type: 'weekly', typeLabel: '', sessions: 3, validDays: 7, price: 1000, originalPrice: null, isActive: true, sortOrder: 0, description: '' }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑套餐'
  form.value = { ...row }
  dialogVisible.value = true
}

const toggleActive = async (row) => {
  const newStatus = !row.isActive
  await axios.put(`/api/admin/packages/${row.id}`, { ...row, isActive: newStatus })
  ElMessage.success(newStatus ? '已上架' : '已下架')
  loadData()
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除「${row.name}」吗？`, '确认删除', { type: 'warning' })
      .then(async () => {
        await axios.delete(`/api/admin/packages/${row.id}`)
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
      await axios.put(`/api/admin/packages/${form.value.id}`, form.value)
    } else {
      await axios.post('/api/admin/packages', form.value)
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

// ============ 生命周期 ============
onMounted(() => {
  loadData()
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
</style>