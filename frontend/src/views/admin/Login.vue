<template>
  <div class="login-container">
    <!-- 左侧品牌区 -->
    <div class="brand-section">
      <!-- 左上角 Logo -->
      <div class="brand-logo-wrapper">
        <img src="@/assets/logo.png" alt="桔刻健身" class="brand-logo" />
      </div>
      <!-- 品牌信息 -->
      <div class="brand-text">
        <h1>桔刻健身</h1>
        <p class="brand-slogan">
          <span class="brand-en">JUKE FITNESS</span>
          <span class="brand-divider">·</span>
          让每一次训练都值得被记录
        </p>
      </div>
    </div>
    <!-- 右侧登录区 -->
    <div class="form-section">
      <div class="form-wrapper">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>登录您的账号</p>
        </div>

        <div class="role-tabs">
          <button
              type="button"
              class="role-tab"
              :class="{ active: loginForm.role === 'ADMIN' }"
              @click="switchRole('ADMIN')"
          >
            管理员
          </button>
          <button
              type="button"
              class="role-tab"
              :class="{ active: loginForm.role === 'MEMBER' }"
              @click="switchRole('MEMBER')"
          >
            会员
          </button>
        </div>

        <el-form ref="formRef" :model="loginForm" :rules="loginRules" label-width="0">
          <el-form-item prop="username">
            <el-input
                v-model="loginForm.username"
                :placeholder="accountPlaceholder"
                prefix-icon="User"
                size="large"
                @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item prop="captcha">
            <div class="captcha-row">
              <el-input
                  v-model="loginForm.captcha"
                  placeholder="请输入验证码"
                  prefix-icon="Key"
                  size="large"
                  class="captcha-input"
                  @keyup.enter="handleLogin"
              />
              <div class="captcha-img" @click="refreshCaptcha">
                <span>{{ captchaText }}</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <a href="#" class="forgot-link" @click.prevent="handleForgotPassword">忘记密码？</a>
        </div>

        <div class="form-footer">
          <span>© 2026 桔刻健身</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const formRef = ref(null)

// 验证码
const captchaText = ref('')

const generateCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let result = ''
  for (let i = 0; i < 4; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  captchaText.value = result
}

const refreshCaptcha = () => {
  generateCaptcha()
}

onMounted(() => {
  generateCaptcha()
})

const loginForm = reactive({
  username: '',
  password: '',
  captcha: '',
  role: 'ADMIN'
})

const accountPlaceholder = computed(() =>
    loginForm.role === 'ADMIN' ? '请输入工号/用户名' : '请输入手机号'
)

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value.toUpperCase() === captchaText.value) {
          callback()
        } else {
          callback(new Error('验证码错误'))
        }
      },
      trigger: 'blur'
    }
  ]
}

// 切换角色：清空账号密码，避免串号
const switchRole = (role) => {
  if (loginForm.role === role) return
  loginForm.role = role
  loginForm.username = ''
  loginForm.password = ''
  loginForm.captcha = ''
  formRef.value?.clearValidate()
  refreshCaptcha()
}

// 忘记密码：后端暂未实现对应接口，点击仅提示
const handleForgotPassword = () => {
  ElMessage.info('功能开发中，敬请期待')
}
const handleLogin = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await axios.post('/api/auth/login', {
      username: loginForm.username,
      password: loginForm.password,
      role: loginForm.role
    }, { withCredentials: true })

    if (res.data.success) {
      const role = res.data.role || loginForm.role
      const userId = res.data.userId
      const userName = res.data.adminName || res.data.memberName || loginForm.username

      localStorage.setItem('role', role)
      localStorage.setItem('userId', String(userId))
      if (role === 'ADMIN') {
        localStorage.setItem('adminName', userName)
      } else {
        localStorage.setItem('userName', userName)
      }
      localStorage.setItem('authStatus', JSON.stringify({
        loggedIn: true,
        role,
        userId,
        timestamp: Date.now()
      }))

      ElMessage.success(`登录成功！欢迎 ${userName}`)
      router.replace(role === 'ADMIN' ? '/admin/dashboard' : '/member/classes')
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch (error) {
    console.error('登录错误:', error)
    if (error.response) {
      ElMessage.error(`登录失败：${error.response.data?.message || error.response.statusText}`)
    } else if (error.request) {
      ElMessage.error('网络错误，请检查后端是否启动')
    } else {
      ElMessage.error(error.message || '登录失败，请重试')
    }
  } finally {
    loading.value = false
    refreshCaptcha()
  }
}
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.login-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: #F0F4FA;
}

/* ===== 左侧品牌区 ===== */
.brand-section {
  flex: 0 0 60%;
  background: #F0F4FA;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 48px 56px 48px 80px;
  position: relative;
  overflow: hidden;
}

/* runner 靠左一点 */
.brand-section::after {
  content: '';
  position: absolute;
  left: 5%;
  bottom: -10%;
  width: 55%;
  height: 65%;
  background-image: url('@/assets/runner.png');
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  opacity: 0.12;
  pointer-events: none;
}

/* 左上角 Logo */
.brand-logo-wrapper {
  position: absolute;
  top: 32px;
  left: 48px;
}

.brand-logo {
  width: 280px;
  height: 70px;
}

/* 品牌文字 */
.brand-text {
  position: relative;
  z-index: 1;
  text-align: left;
  margin-left: 400px;
  margin-top: -40px;
}

.brand-text h1 {
  font-size:48px;
  font-weight: 700;
  color: #1A1A2E;
  letter-spacing: 2px;
  margin-bottom: 12px;
}

.brand-slogan .brand-en {
  font-size: 18px;
  font-weight: 250;
  color: #6A7A8E;
  letter-spacing: 6px;
  margin-left: 16px;
}

.brand-slogan {
  font-size: 20px;
  font-weight: 400;
  color: #6A7A8E;
  letter-spacing: 1px;
  margin-bottom: 6px;
}
.brand-slogan .brand-divider {
  margin: 0 10px;
  color: #C8D0DC;
  font-weight: 300;
}

/* ===== 右侧登录区 ===== */
.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 48px 48px 48px 16px;
}

.form-wrapper {
  width: 100%;
  max-width: 400px;
  background: #FFFFFF;
  border-radius: 20px;
  padding: 40px 36px 32px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.06);
}

.form-header {
  margin-bottom: 24px;
  text-align: center;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #1A1A2E;
  margin-bottom: 4px;
}

.form-header p {
  font-size: 14px;
  color: #8A8AA0;
}

/* 角色切换 Tab */
.role-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 24px;
  background: #F1F4F9;
  border-radius: 10px;
}

.role-tab {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 14px;
  color: #8A8AA0;
  cursor: pointer;
  transition: all 0.25s ease;
}

.role-tab.active {
  background: #FFFFFF;
  color: #4A6CF7;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

/* 验证码行 */
.captcha-row {
  display: flex;
  gap: 16px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  flex-shrink: 0;
  width: 100px;
  height: 42px;
  background: #F1F4F9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 6px;
  color: #4A6CF7;
  font-family: 'Courier New', monospace;
  cursor: pointer;
  user-select: none;
  border: 1px solid #E4E9F0;
  transition: all 0.2s ease;
}

.captcha-img:hover {
  border-color: #4A6CF7;
  background: #E8EDF8;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.forgot-link {
  font-size: 13px;
  color: #8A8AA0;
  text-decoration: none;
  transition: color 0.2s ease;
}

.forgot-link:hover {
  color: #4A6CF7;
}

.form-footer {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #C0C6D0;
  border-top: 1px solid #F0F4FA;
  padding-top: 16px;
}

/* 表单项间距：保证错误提示完整显示，不与相邻元素重叠 */
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__error) {
  line-height: 1.4;
  padding-top: 2px;
}
:deep(.el-form-item__content) {
  overflow: visible;
}
/* ===== Element Plus 覆盖 ===== */
:deep(.el-input__wrapper) {
  border-radius: 8px !important;
  background-color: #F8FAFD !important;
  border: 1px solid #E4E9F0 !important;
  box-shadow: none !important;
  transition: all 0.25s ease;
  height: 44px;
}

:deep(.el-input__wrapper:hover) {
  border-color: #C8D0DC !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #4A6CF7 !important;
  box-shadow: 0 0 0 3px rgba(74, 108, 247, 0.08) !important;
  background-color: #FFFFFF !important;
}

:deep(.el-input__inner) {
  font-size: 14px;
}

:deep(.el-button.login-btn) {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 500;
  background-color: #4A6CF7;
  border-color: #4A6CF7;
  border-radius: 8px;
  transition: all 0.25s ease;
  margin-top: 10px;
}

:deep(.el-button.login-btn:hover),
:deep(.el-button.login-btn:focus) {
  background-color: #3A5BE0;
  border-color: #3A5BE0;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .brand-section {
    padding: 40px 40px 40px 48px;
  }

  .brand-text h1 {
    font-size: 44px;
  }

  .form-section {
    padding: 40px 24px 40px 16px;
  }

  .form-wrapper {
    max-width: 340px;
    padding: 32px 28px 28px;
  }
}

@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    height: auto;
    min-height: 100vh;
  }

  .brand-section {
    flex: 0 0 auto;
    min-height: 30vh;
    padding: 80px 32px 40px;
  }

  .brand-section::after {
    left: 20%;
    bottom: -10%;
    width: 60%;
    height: 50%;
    opacity: 0.08;
  }

  .brand-logo-wrapper {
    top: 20px;
    left: 24px;
  }

  .brand-logo {
    width: 48px;
    height: 48px;
  }

  .brand-text h1 {
    font-size: 36px;
  }

  .brand-text h1 .brand-en {
    font-size: 14px;
  }

  .form-section {
    flex: 0 0 auto;
    justify-content: center;
    padding: 24px 20px;
    min-height: 60vh;
  }

  .form-wrapper {
    max-width: 100%;
    padding: 28px 24px 24px;
  }

  .captcha-img {
    width: 80px;
    height: 38px;
    font-size: 17px;
    letter-spacing: 4px;
  }
}
</style>