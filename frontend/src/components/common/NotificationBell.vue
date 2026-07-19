<template>
  <el-popover
      placement="bottom-end"
      :width="360"
      trigger="click"
      @show="fetchMessages"
  >
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger">
        <el-icon :size="20" style="cursor:pointer;color:#bfcbd9;">
          <Bell />
        </el-icon>
      </el-badge>
    </template>
    <div class="notification-panel">
      <div class="notification-header">
        <span>📬 消息通知</span>
        <el-button size="small" text @click="markAllRead" v-if="unreadCount > 0">
          全部已读
        </el-button>
      </div>
      <div v-if="loading" style="text-align:center;padding:20px;color:#999;">加载中...</div>
      <div v-else-if="messageList.length === 0" style="text-align:center;padding:30px;color:#999;">
        🎉 暂无消息
      </div>
      <div v-else class="message-list">
        <div
            v-for="msg in messageList"
            :key="msg.id"
            class="message-item"
            :class="{ unread: !msg.isRead }"
            @click="markRead(msg)"
        >
          <div class="msg-content">{{ msg.content }}</div>
          <div class="msg-time">{{ formatTime(msg.createdAt) }}</div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'

const props = defineProps({
  memberId: {
    type: Number,
    required: true
  }
})

const unreadCount = ref(0)
const messageList = ref([])
const loading = ref(false)

const fetchUnreadCount = async () => {
  try {
    const res = await axios.get('/api/messages/unread-count', {
      params: { memberId: props.memberId }
    })
    unreadCount.value = res.data.count || 0
  } catch (error) {
    console.error('获取未读数量失败', error)
  }
}

const fetchMessages = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/messages', {
      params: { memberId: props.memberId, page: 1, size: 20 }
    })
    messageList.value = res.data.list || []
  } catch (error) {
    console.error('获取消息列表失败', error)
  } finally {
    loading.value = false
  }
}

const markRead = async (msg) => {
  if (msg.isRead) return
  try {
    await axios.put(`/api/messages/${msg.id}/read`)
    msg.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (error) {
    console.error('标记已读失败', error)
  }
}

const markAllRead = async () => {
  try {
    await axios.put('/api/messages/read-all', null, {
      params: { memberId: props.memberId }
    })
    messageList.value.forEach(m => m.isRead = true)
    unreadCount.value = 0
    ElMessage.success('全部已读')
  } catch (error) {
    console.error('全部已读失败', error)
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  fetchUnreadCount()
  // 每30秒轮询一次（演示够用）
  setInterval(fetchUnreadCount, 30000)
})
</script>

<style scoped>
.notification-panel {
  max-height: 400px;
  display: flex;
  flex-direction: column;
}
.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #eee;
  font-weight: bold;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.message-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.2s;
}
.message-item:hover {
  background: #f0f2f5;
}
.message-item.unread {
  background: #ecf5ff;
  border-left: 3px solid #409EFF;
}
.msg-content {
  font-size: 13px;
  color: #333;
  line-height: 1.5;
}
.msg-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}
</style>