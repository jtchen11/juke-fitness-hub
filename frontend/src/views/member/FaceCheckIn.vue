<template>
  <div class="face-checkin-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>📸 刷脸打卡</span>
          <el-tag type="success" size="small">会员自助</el-tag>
        </div>
      </template>

      <div style="text-align:center;padding:20px 0;">
        <!-- 摄像头画面 -->
        <div v-if="!showResult" class="camera-wrapper">
          <video
              ref="videoRef"
              autoplay
              class="camera-video"
              :class="{ 'camera-loading': !cameraReady }"
          ></video>
          <canvas ref="canvasRef" style="display:none"></canvas>
          <div v-if="!cameraReady" class="camera-placeholder">
            <el-icon :size="48"><Camera /></el-icon>
            <p>正在启动摄像头...</p>
          </div>
        </div>

        <!-- 打卡结果展示 -->
        <div v-else class="result-wrapper">
          <div :class="['result-icon', checkInSuccess ? 'success' : 'fail']">
            <el-icon :size="64">
              <CircleCheck v-if="checkInSuccess" />
              <CircleClose v-else />
            </el-icon>
          </div>
          <h2>{{ checkInSuccess ? '✅ 打卡成功！' : '❌ 打卡失败' }}</h2>
          <p style="color:#999;">{{ checkInMessage }}</p>
          <el-button type="primary" @click="resetCheckIn" style="margin-top:16px;">
            重新打卡
          </el-button>
        </div>

        <!-- 操作按钮 -->
        <div v-if="!showResult" style="margin-top:20px;display:flex;justify-content:center;gap:16px;">
          <el-button
              type="primary"
              size="large"
              @click="captureFace"
              :loading="capturing"
              :disabled="!cameraReady"
          >
            {{ capturing ? '验证中...' : '📸 拍照打卡' }}
          </el-button>
          <el-button size="large" @click="resetCamera" :disabled="!cameraReady">
            重新对焦
          </el-button>
        </div>

        <!-- 提示信息 -->
        <div v-if="!showResult" style="margin-top:16px;color:#999;font-size:13px;">
          <p>📌 请确保光线充足，面部正对摄像头</p>
          <p>👤 当前登录：<span style="font-weight:bold;color:#409EFF">{{ memberName }}</span></p>
          <p v-if="faceRegistered" style="color:#67C23A;">✅ 已注册人脸，可直接打卡</p>
          <p v-else style="color:#E6A23C;">⚠️ 尚未注册人脸，将自动为您注册</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElLoading } from 'element-plus'
import { Camera, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
// ============ 当前会员信息（从 localStorage 读取） ============
const memberId = ref(localStorage.getItem('userId') || '')
const memberName = ref(localStorage.getItem('userName') || '会员')

// ============ 状态 ============
const videoRef = ref(null)
const canvasRef = ref(null)
const cameraReady = ref(false)
const capturing = ref(false)
const showResult = ref(false)
const checkInSuccess = ref(false)
const checkInMessage = ref('')
const faceRegistered = ref(false)
let stream = null

// ============ 检查人脸是否已注册 ============
const checkFaceRegistered = async () => {
  if (!memberId.value) {
    ElMessage.warning('请先登录')
    return false
  }
  try {
    const res = await axios.get(`/api/face/check?userId=${memberId.value}`)
    faceRegistered.value = res.data.registered || false
    return faceRegistered.value
  } catch (error) {
    console.error('检查人脸注册失败', error)
    return false
  }
}

// ============ 注册人脸 ============
const registerFace = async (imageBase64) => {
  const res = await axios.post('/api/face/register', {
    userId: String(memberId.value),
    image: imageBase64
  })
  return res.data
}

// ============ 人脸打卡验证 ============
const verifyFace = async (imageBase64) => {
  const res = await axios.post('/api/face/verify', {
    userId: String(memberId.value),
    image: imageBase64,
    tolerance: 0.5
  })
  return res.data
}

// ============ 打开摄像头 ============
const openCamera = async () => {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    ElMessage.error('您的浏览器不支持摄像头，请使用最新版 Chrome/Edge')
    return false
  }

  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: 'user',
        width: { ideal: 480 },
        height: { ideal: 360 }
      }
    })

    if (!videoRef.value) {
      ElMessage.error('视频元素未加载，请刷新重试')
      return false
    }

    videoRef.value.srcObject = stream
    await videoRef.value.play()
    cameraReady.value = true
    return true
  } catch (error) {
    console.error('摄像头打开失败:', error)
    let msg = '无法打开摄像头'
    if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
      msg = '摄像头权限被拒绝，请在浏览器设置中允许此站点使用摄像头'
    } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      msg = '未检测到摄像头设备，请检查摄像头是否连接'
    } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
      msg = '摄像头被其他应用占用，请关闭其他使用摄像头的程序'
    } else {
      msg = `摄像头打开失败：${error.message || '未知错误'}`
    }
    ElMessage.error(msg)
    return false
  }
}

// ============ 关闭摄像头 ============
const closeCamera = () => {
  if (stream) {
    stream.getTracks().forEach(track => track.stop())
    stream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
  cameraReady.value = false
}

// ============ 重置摄像头 ============
const resetCamera = async () => {
  closeCamera()
  cameraReady.value = false
  await nextTick()
  await openCamera()
}

// ============ 拍照打卡（核心逻辑） ============
const captureFace = async () => {
  if (!videoRef.value || !canvasRef.value) {
    ElMessage.warning('摄像头未就绪')
    return
  }

  if (!cameraReady.value) {
    ElMessage.warning('请等待摄像头启动')
    return
  }

  capturing.value = true
  try {
    // 1. 拍照
    const canvas = canvasRef.value
    const ctx = canvas.getContext('2d')
    canvas.width = videoRef.value.videoWidth || 480
    canvas.height = videoRef.value.videoHeight || 360
    ctx.drawImage(videoRef.value, 0, 0, canvas.width, canvas.height)
    const imageBase64 = canvas.toDataURL('image/jpeg', 0.8)

    // 2. 检查是否已注册人脸
    const registered = await checkFaceRegistered()
    const loadingInstance = ElLoading.service({
      text: registered ? '正在进行人脸验证...' : '正在注册人脸...',
      fullscreen: true
    })

    try {
      let result
      if (registered) {
        // 已注册 → 验证打卡
        result = await verifyFace(imageBase64)
        if (result.success && result.matched) {
          // ====== 检查是否是私教打卡 ======
          const ptId = route.query.ptId
          const action = route.query.action || 'start'

          if (ptId) {
            // 私教打卡
            const ptRes = await axios.post(`/api/check-in/pt/${ptId}`, null, {
              params: {
                memberId: memberId.value,
                action: action
              }
            })
            loadingInstance.close()
            if (ptRes.data.success) {
              checkInSuccess.value = true
              checkInMessage.value = ptRes.data.message || (action === 'end' ? '✅ 下课打卡成功！' : '✅ 上课打卡成功！')
              showResult.value = true
              // 成功后延迟跳转回预约列表
              setTimeout(() => {
                router.push('/member/bookings')
              }, 2000)
            } else {
              checkInSuccess.value = false
              checkInMessage.value = ptRes.data.message || '私教打卡失败'
              showResult.value = true
            }
          } else {
            // 普通签到
            await axios.post(`/api/check-in/member/${memberId.value}`)
            loadingInstance.close()
            checkInSuccess.value = true
            checkInMessage.value = `欢迎回来，${memberName.value}！打卡成功！`
            showResult.value = true
          }
        } else {
          loadingInstance.close()
          checkInSuccess.value = false
          checkInMessage.value = result.message || '人脸不匹配，请重试'
          showResult.value = true
        }
      } else {
        // 未注册 → 自动注册
        result = await registerFace(imageBase64)
        loadingInstance.close()
        if (result.success) {
          faceRegistered.value = true
          checkInSuccess.value = true
          checkInMessage.value = `✅ ${memberName.value} 人脸注册成功！打卡完成！`
          // 注册成功后是否也进行私教打卡？这里简单处理，走普通签到
          await axios.post(`/api/check-in/member/${memberId.value}`)
          showResult.value = true
        } else {
          checkInSuccess.value = false
          checkInMessage.value = result.message || '注册失败，请重试'
          showResult.value = true
        }
      }
    } catch (error) {
      loadingInstance.close()
      console.error('人脸验证/注册失败:', error)
      let errMsg = '操作失败'
      if (error.response) {
        errMsg = error.response.data?.message || `HTTP ${error.response.status}`
      } else if (error.request) {
        errMsg = '网络请求失败，请检查人脸服务是否启动（默认端口5001）'
      } else {
        errMsg = error.message
      }
      checkInSuccess.value = false
      checkInMessage.value = errMsg
      showResult.value = true
    }
  } catch (error) {
    console.error('拍照打卡失败:', error)
    ElMessage.error('拍照打卡失败，请重试')
  } finally {
    capturing.value = false
    // 打卡完成后关闭摄像头（无论成败，如果是私教打卡，跳转时关闭，其他情况也关闭）
    if (checkInSuccess.value) {
      closeCamera()
    }
  }
}

// ============ 重置打卡状态 ============
const resetCheckIn = () => {
  showResult.value = false
  checkInSuccess.value = false
  checkInMessage.value = ''
  // 重新打开摄像头
  setTimeout(async () => {
    await openCamera()
  }, 300)
}

// ============ 初始化 ============
const init = async () => {
  if (!memberId.value) {
    ElMessage.warning('请先登录后再使用刷脸打卡功能')
    return
  }
  await checkFaceRegistered()
  await openCamera()
}

// ============ 生命周期 ============
onMounted(() => {
  init()
})

onBeforeUnmount(() => {
  closeCamera()
})
</script>

<style scoped>
.face-checkin-container {
  max-width: 600px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.camera-wrapper {
  position: relative;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 4/3;
  display: flex;
  align-items: center;
  justify-content: center;
}
.camera-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.camera-loading {
  opacity: 0.3;
}
.camera-placeholder {
  position: absolute;
  color: #666;
  text-align: center;
}
.camera-placeholder p {
  margin-top: 8px;
  color: #999;
}
.result-wrapper {
  padding: 40px 20px;
}
.result-icon {
  font-size: 64px;
  margin-bottom: 16px;
}
.result-icon.success {
  color: #67C23A;
}
.result-icon.fail {
  color: #F56C6C;
}
</style>