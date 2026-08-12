<template>
  <div>
    <!-- ====== 筛选 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索会员姓名/手机号"
              clearable
              prefix-icon="Search"
              @keyup.enter="loadMessages"
          />
        </el-col>
        <el-col :span="5">
          <el-select v-model="filterRead" placeholder="全部状态" clearable @change="loadMessages" style="width:100%">
            <el-option label="未读" :value="false" />
            <el-option label="已读" :value="true" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="loadMessages">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 消息列表 ====== -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>💬 消息管理 <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag></span>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading" row-key="id" style="width:100%">
        <el-table-column prop="memberName" label="会员姓名" width="140">
          <template #default="{ row }">
            <span style="font-weight:500">{{ row.memberName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="memberPhone" label="手机号" width="140">
          <template #default="{ row }">
            {{ row.memberPhone || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="content" label="消息内容" min-width="260">
          <template #default="{ row }">
            <span :style="row.isRead ? 'color:#909399' : 'font-weight:500'">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="是否已读" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRead ? 'info' : 'danger'" size="small">
              {{ row.isRead ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="!row.isRead" size="small" type="primary" plain @click="markRead(row)">
              标记已读
            </el-button>
            <span v-else style="color:#C0C4CC;font-size:13px">—</span>
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
            @size-change="loadMessages"
            @current-change="loadMessages"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const filterRead = ref('')
const loading = ref(false)

const formatTime = (t) => {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 19)
}

const loadMessages = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/messages', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        isRead: filterRead.value === '' ? undefined : filterRead.value
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    console.error('加载消息失败', e)
    ElMessage.error('加载消息失败')
  } finally {
    loading.value = false
  }
}

const markRead = async (row) => {
  try {
    await axios.put(`/api/messages/${row.id}/read`)
    row.isRead = true
    ElMessage.success('已标记为已读')
  } catch (e) {
    console.error('标记已读失败', e)
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  loadMessages()
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