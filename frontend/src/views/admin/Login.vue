<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>🏋️ 智能健身房</h1>
        <p>请选择角色并输入账号密码登录</p>
      </div>
      <el-form :model="loginForm" :rules="loginRules" ref="formRef" label-width="0">
        <!-- 角色选择 -->
        <el-form-item>
          <el-radio-group v-model="loginForm.role" size="large" style="width:100%;display:flex;">
            <!-- ====== label 改为 value ====== -->
            <el-radio-button value="ADMIN" style="flex:1;">管理员</el-radio-button>
            <el-radio-button value="MEMBER" style="flex:1;">会员</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="username">
          <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              prefix-icon="User"
              size="large"
              @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
              v-model="loginForm.password"
              type="password"
              :placeholder="loginForm.role === 'ADMIN' ? '密码（默认：admin123）' : '密码（默认：member123）'"
              prefix-icon="Lock"
              size="large"
              show-password
              @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
              type="primary"
              size="large"
              style="width:100%"
              :loading="loading"
              @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <span style="color:#999;font-size:13px">
          管理员：admin / admin123 ｜ 会员：13900000001 / member123
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const formRef = ref(null)

const loginForm = reactive({
  username: '',
  password: '',
  role: 'ADMIN'
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
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

      // 存储用户信息
      localStorage.setItem('role', role)
      localStorage.setItem('userId', String(userId))
      if (role === 'ADMIN') {
        localStorage.setItem('adminName', userName)
      } else {
        localStorage.setItem('userName', userName)
      }

      // ====== 新增：写入 authStatus 缓存（路由守卫用） ======
      localStorage.setItem('authStatus', JSON.stringify({
        loggedIn: true,
        role: role,
        userId: userId,
        timestamp: Date.now()
      }))

      ElMessage.success(`登录成功！欢迎 ${userName}`)

      // ====== 改用 replace 跳转 ======
      if (role === 'ADMIN') {
        router.replace('/admin/dashboard')
      } else {
        router.replace('/member/classes')
      }
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
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px 30px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.login-header {
  text-align: center;
  margin-bottom: 30px;
}
.login-header h1 {
  font-size: 22px;
  color: #333;
  margin-bottom: 8px;
}
.login-header p {
  color: #999;
  font-size: 14px;
}
.login-footer {
  text-align: center;
  margin-top: 16px;
}
:deep(.el-radio-group) {
  display: flex !important;
}
:deep(.el-radio-button) {
  flex: 1 !important;
}
:deep(.el-radio-button .el-radio-button__inner) {
  width: 100% !important;
  text-align: center !important;
}
</style>