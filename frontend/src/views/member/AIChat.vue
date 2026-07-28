<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>🏋️ 智能健身助手</h2>
      <button class="clear-btn" @click="clearHistory" title="清空聊天记录">
        🗑️ 清空记录
      </button>
    </div>

    <div class="chat-box" ref="chatBox">
      <div v-if="messages.length === 0" class="empty-tip">
        💬 暂无消息，开始聊天吧！
      </div>
      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg', msg.role]">
        <div class="avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
        <div class="content">
          <div v-if="msg.content">
            {{ msg.content }}
            <span v-if="msg.isStreaming" class="cursor-blink">▌</span>
          </div>
          <img
              v-if="msg.imageUrl"
              :src="msg.imageUrl"
              alt="健身动作示意图"
              style="max-width:100%; border-radius:8px; margin-top:8px;"
              @error="handleImageError(idx)"
          />
          <div v-if="msg.imageError" style="color: #999; font-size: 12px; margin-top:4px;">
            ⚠️ 图片加载失败，请稍后重试
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <input
          v-model="inputText"
          @keyup.enter="sendMessage"
          placeholder="问关于课程、预约、健身知识... 说'画'可生成图片"
          :disabled="isStreaming"
      />
      <button @click="sendMessage" :disabled="isStreaming || !inputText.trim()">
        {{ isStreaming ? '思考中...' : '发送' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import axios from 'axios'

// 获取当前登录的会员 ID
const memberId = localStorage.getItem('userId') || '1'
// 生成固定会话 ID：基于会员 ID，确保同一个会员在任何端口下都能共用历史
const fixedSessionId = `member_${memberId}_chat`
// 优先使用 localStorage 中已有的，如果没有则使用固定值
const stored = localStorage.getItem('chatSessionId')
const sessionId = ref(stored || fixedSessionId)
// 将这个固定值也存回 localStorage，以便下次直接使用
if (!stored) {
  localStorage.setItem('chatSessionId', fixedSessionId)
}

const inputText = ref('')
const messages = reactive([])
const chatBox = ref(null)
const isStreaming = ref(false)
let eventSource = null

// ========== 加载历史消息 ==========
const loadHistory = async () => {
  try {
    const res = await axios.get('/api/ai/chat/history', {
      params: { sessionId: sessionId.value }
    })
    if (res.data.success && res.data.history) {
      messages.splice(0, messages.length)
      res.data.history.forEach(msg => {
        let content = msg.content || ''
        // 如果存在 imageUrl，从文本中移除该 URL（避免重复显示）
        if (msg.imageUrl) {
          // 使用正则移除文本中的图片URL（包括可能的查询参数）
          const escapedUrl = msg.imageUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
          content = content.replace(new RegExp(escapedUrl, 'g'), '').trim()
          // 如果移除后文本为空或只剩空白，则设置默认提示
          if (!content) {
            content = '根据您的要求，生成了以下健身动作示意图：'
          }
        }
        messages.push({
          role: msg.role,
          content: content,
          imageUrl: msg.imageUrl || null,
          imageError: false,
          isStreaming: false
        })
      })
      await nextTick()
      scrollToBottom()
    }
  } catch (error) {
    console.error('加载历史消息失败:', error)
  }
}

// ========== 清空聊天记录 ==========
const clearHistory = async () => {
  if (!confirm('确定要清空所有聊天记录吗？此操作不可恢复！')) {
    return
  }
  try {
    await axios.delete('/api/ai/chat/history', {
      params: { sessionId: sessionId.value }
    })
    // 关闭已有的 SSE 连接
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    messages.splice(0, messages.length)
    sessionId.value = Date.now().toString()
    localStorage.setItem('chatSessionId', sessionId.value)
    await loadHistory()
  } catch (error) {
    console.error('清空聊天记录失败:', error)
    alert('清空失败，请稍后重试')
  }
}

// ========== 图片加载失败处理 ==========
const handleImageError = (idx) => {
  messages[idx].imageError = true
}

// ========== 滚动到底部 ==========
const scrollToBottom = () => {
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

// ========== 流式发送消息 ==========
const sendMessageStream = (text) => {
  const memberId = localStorage.getItem('userId') || '1'
  const url = `/api/ai/chat/stream?sessionId=${sessionId.value}&message=${encodeURIComponent(text)}&memberId=${Number(memberId)}`

  // 创建占位消息（用于流式更新）
  const placeholderIdx = messages.length
  messages.push({
    role: 'assistant',
    content: '',
    imageUrl: null,
    imageError: false,
    isStreaming: true
  })
  isStreaming.value = true



  // 创建 EventSource 连接
  eventSource = new EventSource(url)

  // 监听 delta 事件（逐字推送）
  eventSource.addEventListener('delta', (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'tool_result') {
        // 工具调用结果：作为系统提示追加
        messages[placeholderIdx].content += '\n📌 ' + (data.content || '')
      } else if (data.content) {
        // 普通 token：追加到当前消息
        messages[placeholderIdx].content += data.content
      }
      scrollToBottom()
    } catch (e) {
      console.error('解析 SSE delta 事件失败:', e)
    }
  })

  // 监听 end 事件（流式结束）
  eventSource.addEventListener('end', (event) => {
    messages[placeholderIdx].isStreaming = false
    isStreaming.value = false
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    scrollToBottom()
  })

  // 监听 error 事件（后端推送的异常）
  eventSource.addEventListener('error', (event) => {
    try {
      const data = JSON.parse(event.data)
      messages[placeholderIdx].content = '❌ ' + (data.content || '流式输出出错，请重试')
    } catch (e) {
      messages[placeholderIdx].content = '❌ 流式输出出错，请重试'
    }
    messages[placeholderIdx].isStreaming = false
    isStreaming.value = false
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    scrollToBottom()
  })


  // 通用错误处理（连接断开等）
  eventSource.onerror = (error) => {
    // 如果还没有收到完成事件，说明连接意外断开
    if (messages[placeholderIdx] && messages[placeholderIdx].isStreaming) {
      messages[placeholderIdx].content = '❌ 连接中断，请重试'
      messages[placeholderIdx].isStreaming = false
      isStreaming.value = false
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    scrollToBottom()
  }

  // 超时保护（120秒后自动关闭）
  setTimeout(() => {
    if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
      eventSource.close()
      eventSource = null
      if (messages[placeholderIdx] && messages[placeholderIdx].isStreaming) {
        messages[placeholderIdx].content = '⏰ 响应超时，请重试'
        messages[placeholderIdx].isStreaming = false
        isStreaming.value = false
        scrollToBottom()
      }
    }
  }, 120000)
}

// ========== 发送消息（智能路由：流式/非流式） ==========
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  // 添加用户消息
  messages.push({ role: 'user', content: text })
  inputText.value = ''
  await nextTick()
  scrollToBottom()

  // 判断是否为图片生成请求（使用非流式）
  const lowerMsg = text.toLowerCase()
  const isImageRequest = lowerMsg.includes('画') || lowerMsg.includes('图片') ||
      lowerMsg.includes('图示') || lowerMsg.includes('姿势') ||
      lowerMsg.includes('图解')

  if (isImageRequest) {
    try {
      const memberId = localStorage.getItem('userId')
      const res = await axios.post('/api/ai/chat', {
        sessionId: sessionId.value,
        message: text,
        memberId: Number(memberId) || 1
      })
      const answer = res.data.answer
      // 匹配 URL（包括查询参数）
      const urlMatch = answer.match(/https?:\/\/[^\s]+/)
      if (urlMatch) {
        const imageUrl = urlMatch[0]
        // 判断是否为图片（包含图片扩展名或 OSS 标识）
        if (/\.(png|jpg|jpeg|gif|webp)/i.test(imageUrl) || imageUrl.includes('dashscope')) {
          const text = answer.replace(imageUrl, '').trim()
          messages.push({
            role: 'assistant',
            content: text || '根据您的要求，生成了以下健身动作示意图：',
            imageUrl: imageUrl,
            imageError: false,
            isStreaming: false
          })
        } else {
          // 不是图片，当作文本处理
          messages.push({
            role: 'assistant',
            content: answer || '抱歉，我没有理解你的问题。',
            isStreaming: false
          })
        }
      } else {
        // 没有 URL，当作文本
        messages.push({
          role: 'assistant',
          content: answer || '抱歉，我没有理解你的问题。',
          isStreaming: false
        })
      }
    } catch (error) {
      console.error('AI 接口调用失败:', error)
      messages.push({
        role: 'assistant',
        content: '❌ 服务暂时不可用，请稍后再试。',
        isStreaming: false
      })
    }
    await nextTick()
    scrollToBottom()
    localStorage.setItem('chatSessionId', sessionId.value)
    return
  }

  // 非图片请求使用流式输出
  sendMessageStream(text)
}

// ========== 组件卸载时关闭 SSE 连接 ==========
onMounted(async () => {
  await loadHistory()
  // 如果没有任何消息，自动发送一条"你好"，触发AI主动问候
  if (messages.length === 0) {
    // 等待 500ms 让界面渲染完
    setTimeout(() => {
      inputText.value = '你好'
      sendMessage()
    }, 500)
  }
})
// ========== 页面加载时加载历史 ==========
onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.chat-container {
  max-width: 800px;
  margin: 20px auto;
  border: 1px solid #ccc;
  border-radius: 12px;
  padding: 20px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.chat-header h2 {
  margin: 0;
}

.clear-btn {
  padding: 6px 14px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.2s;
}
.clear-btn:hover {
  background: #dc2626;
}

.chat-box {
  height: 700px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  padding: 12px;
  border-radius: 8px;
  background: #fafafa;
  margin-bottom: 12px;
}

.empty-tip {
  text-align: center;
  color: #9ca3af;
  padding: 60px 0;
  font-size: 16px;
}

/* ========== 消息样式 ========== */
.msg {
  display: flex;
  margin-bottom: 12px;
  align-items: flex-start;
}
.msg.user {
  justify-content: flex-end;
}
.msg.assistant {
  justify-content: flex-start;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 8px;
  flex-shrink: 0;
}

.content {
  max-width: 70%;
  padding: 8px 14px;
  border-radius: 18px;
  background: #e5e7eb;
  word-break: break-word;
  line-height: 1.6;
  white-space: pre-wrap;
}

.msg.user .content {
  background: #3b82f6;
  color: white;
}
.msg.assistant .content {
  background: #e5e7eb;
}

/* ========== 流式光标闪烁动画 ========== */
.cursor-blink {
  display: inline-block;
  color: #3b82f6;
  font-weight: bold;
  animation: blink 0.8s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ========== 输入区域 ========== */
.input-area {
  display: flex;
  gap: 8px;
}
.input-area input {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid #ccc;
  border-radius: 24px;
  outline: none;
  font-size: 15px;
}
.input-area input:focus {
  border-color: #3b82f6;
}
.input-area input:disabled {
  background: #f3f4f6;
  cursor: not-allowed;
}
.input-area button {
  padding: 10px 24px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 24px;
  cursor: pointer;
  font-size: 15px;
  font-weight: bold;
  transition: background 0.2s;
  white-space: nowrap;
}
.input-area button:hover:not(:disabled) {
  background: #2563eb;
}
.input-area button:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}
</style>