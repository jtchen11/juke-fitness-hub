<template>
  <div>
    <!-- ====== 顶部会员信息 ====== -->
    <el-card style="margin-bottom: 20px">
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div style="display:flex;align-items:center;gap:12px;">
          <span style="font-size:18px;font-weight:bold;">🍽️ 饮食记录</span>
          <el-tag type="info" size="small">{{ memberName }}</el-tag>
        </div>
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon> 添加记录
        </el-button>
      </div>
    </el-card>

    <!-- ====== 筛选条件 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-date-picker
              v-model="filterDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              @change="loadRecords"
              style="width:100%"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterMeal" placeholder="全部餐次" clearable @change="loadRecords" style="width:100%">
            <el-option label="早餐" value="breakfast" />
            <el-option label="午餐" value="lunch" />
            <el-option label="晚餐" value="dinner" />
            <el-option label="加餐" value="snack" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadRecords">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 饮食列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>📋 饮食记录 <el-tag size="small" type="info">共 {{ total }} 条</el-tag></span>
          <el-button size="small" text @click="refresh"><el-icon><Refresh /></el-icon> 刷新</el-button>
        </div>
      </template>

      <el-table :data="tableData" border style="width:100%" v-loading="loading">
        <el-table-column prop="recordDate" label="日期" width="120" align="center" />
        <el-table-column prop="mealType" label="餐次" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.mealType === 'breakfast' ? 'warning' : row.mealType === 'lunch' ? 'success' : row.mealType === 'dinner' ? 'primary' : 'info'" size="small">
              {{ row.mealType === 'breakfast' ? '早餐' : row.mealType === 'lunch' ? '午餐' : row.mealType === 'dinner' ? '晚餐' : '加餐' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="foodName" label="食物名称" min-width="150" />
        <el-table-column prop="quantity" label="份量" width="80" align="center" />
        <el-table-column prop="calories" label="热量(kcal)" width="100" align="center" />
        <el-table-column prop="protein" label="蛋白质(g)" width="90" align="center" />
        <el-table-column prop="fat" label="脂肪(g)" width="80" align="center" />
        <el-table-column prop="carbs" label="碳水(g)" width="80" align="center" />
        <el-table-column prop="notes" label="备注" min-width="120" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="danger" text @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="loadRecords"
        />
      </div>
    </el-card>

    <!-- ====== 添加记录对话框 ====== -->
    <el-dialog v-model="dialogVisible" title="添加饮食记录" width="500px" @close="resetForm" destroy-on-close>
      <el-form :model="formData" label-width="80px" label-position="right">
        <el-form-item label="日期" required>
          <el-date-picker v-model="formData.recordDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="餐次" required>
          <el-select v-model="formData.mealType" placeholder="选择餐次" style="width:100%">
            <el-option label="早餐" value="breakfast" />
            <el-option label="午餐" value="lunch" />
            <el-option label="晚餐" value="dinner" />
            <el-option label="加餐" value="snack" />
          </el-select>
        </el-form-item>
        <el-form-item label="食物名称" required>
          <el-input v-model="formData.foodName" placeholder="请输入食物名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="份量">
              <el-input v-model="formData.quantity" placeholder="如 200g" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="热量">
              <el-input-number v-model="formData.calories" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="蛋白质">
              <el-input-number v-model="formData.protein" :min="0" :precision="1" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="脂肪">
              <el-input-number v-model="formData.fat" :min="0" :precision="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="碳水">
              <el-input-number v-model="formData.carbs" :min="0" :precision="1" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="formData.notes" type="textarea" :rows="2" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRecord" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue"
import { useRoute, useRouter } from "vue-router"
import axios from "axios"
import { ElMessage, ElMessageBox } from "element-plus"
import { Plus, Refresh } from "@element-plus/icons-vue"

const route = useRoute()
const router = useRouter()
const memberId = route.query.memberId || ""
const memberName = route.query.name || "未知会员"

const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const filterDate = ref("")
const filterMeal = ref("")
const dialogVisible = ref(false)
const saving = ref(false)
const formData = ref({
  recordDate: "",
  mealType: "breakfast",
  foodName: "",
  quantity: "",
  calories: 0,
  protein: 0,
  fat: 0,
  carbs: 0,
  notes: ""
})

const goBack = () => router.back()

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await axios.get("/api/diet-records/" + memberId, {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        date: filterDate.value || undefined,
        mealType: filterMeal.value || undefined
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error("加载饮食记录失败", error)
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  filterDate.value = ""
  filterMeal.value = ""
  pageNum.value = 1
  loadRecords()
}

const refresh = () => { pageNum.value = 1; loadRecords() }

const showAddDialog = () => {
  formData.value = { recordDate: "", mealType: "breakfast", foodName: "", quantity: "", calories: 0, protein: 0, fat: 0, carbs: 0, notes: "" }
  dialogVisible.value = true
}

const resetForm = () => { formData.value = { recordDate: "", mealType: "breakfast", foodName: "", quantity: "", calories: 0, protein: 0, fat: 0, carbs: 0, notes: "" } }

const saveRecord = async () => {
  if (!formData.value.recordDate || !formData.value.foodName) {
    ElMessage.warning("请填写日期和食物名称")
    return
  }
  saving.value = true
  try {
    await axios.post("/api/diet-records/" + memberId, formData.value)
    ElMessage.success("添加成功")
    dialogVisible.value = false
    loadRecords()
  } catch (error) {
    ElMessage.error("添加失败")
  } finally {
    saving.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm("确定删除这条饮食记录吗?", "确认", { type: "warning" }).then(async () => {
    try {
      await axios.delete("/api/diet-records/" + row.id)
      ElMessage.success("删除成功")
      loadRecords()
    } catch (error) {
      ElMessage.error("删除失败")
    }
  }).catch(() => {})
}

onMounted(() => { loadRecords() })
</script>

<style scoped>
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
