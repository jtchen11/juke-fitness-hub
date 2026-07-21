import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../components/admin/AdminLayout.vue'
import MemberLayout from '../components/member/MemberLayout.vue'
import axios from 'axios'

// ====== 缓存工具 ======
const AUTH_KEY = 'authStatus'

// 获取缓存的认证信息
const getCachedAuth = () => {
    try {
        const raw = localStorage.getItem(AUTH_KEY)
        if (!raw) return null
        const data = JSON.parse(raw)
        if (data.timestamp && Date.now() - data.timestamp > 5 * 60 * 1000) {
            localStorage.removeItem(AUTH_KEY)
            return null
        }
        return data
    } catch {
        return null
    }
}

// 设置缓存
const setCachedAuth = (data) => {
    const payload = {
        ...data,
        timestamp: Date.now()
    }
    localStorage.setItem(AUTH_KEY, JSON.stringify(payload))
}

// 清除缓存
const clearCachedAuth = () => {
    localStorage.removeItem(AUTH_KEY)
}

// 实际检查登录状态（仅在缓存失效时调用）
const checkAuth = async () => {
    const cached = getCachedAuth()
    if (cached && cached.loggedIn) {
        return cached
    }

    try {
        const res = await axios.get('/api/auth/check', { withCredentials: true })
        const data = res.data
        if (data.loggedIn) {
            setCachedAuth(data)
        } else {
            clearCachedAuth()
        }
        return data
    } catch (error) {
        console.warn('认证检查失败:', error)
        clearCachedAuth()
        return { loggedIn: false }
    }
}

// ====== 路由配置 ======
const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/admin/Login.vue'),
        meta: { requiresAuth: false }
    },

    // ====== 管理员路由 ======
    {
        path: '/admin',
        component: AdminLayout,
        meta: { requiresAuth: true, role: 'ADMIN' },
        children: [
            { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '管理员首页' } },
            { path: 'members', name: 'AdminMemberManage', component: () => import('@/views/admin/MemberManage.vue'), meta: { title: '会员管理' } },
            { path: 'trainers', name: 'AdminTrainerManage', component: () => import('@/views/admin/TrainerManage.vue'), meta: { title: '教练管理' } },
            { path: 'classes', name: 'AdminClassManage', component: () => import('@/views/admin/ClassManage.vue'), meta: { title: '团课管理' } },
            { path: 'bookings', name: 'AdminBookingManage', component: () => import('@/views/admin/BookingManage.vue'), meta: { title: '私教预约' } },
            { path: 'fitness-tests', name: 'AdminFitnessTest', component: () => import('@/views/admin/FitnessTest.vue'), meta: { title: '体测记录' } },
            { path: 'check-in-records', name: 'AdminCheckInRecord', component: () => import('@/views/admin/CheckInRecord.vue'), meta: { title: '签到记录' } },
            { path: 'diet-record', name: 'AdminDietRecord', component: () => import('@/views/admin/DietRecord.vue'), meta: { title: '饮食记录' } },
            { path: 'points-rewards', name: 'AdminPointsRewards', component: () => import('@/views/admin/PointsRewardManage.vue'), meta: { title: '积分商品管理' } },
            { path: 'packages', name: 'AdminPackageManage', component: () => import('@/views/admin/PackageManage.vue'), meta: { title: '私教套餐管理' } },
            {
                path: 'competitions',
                name: 'AdminCompetitionManage',
                component: () => import('@/views/admin/CompetitionManage.vue'),
                meta: { title: '比赛管理' }
            },
            {
                path: 'points',
                name: 'AdminPointsManage',
                component: () => import('@/views/admin/PointsManage.vue'),
                meta: { title: '积分管理' }
            },
            {
                path: 'settings/system',
                name: 'AdminSystemSettings',
                component: () => import('@/views/admin/SystemSettings.vue'),
                meta: { title: '功能配置' }
            },
            { path: '', redirect: '/admin/dashboard' }
        ]
    },

    // ====== 会员路由 ======
    {
        path: '/member',
        component: MemberLayout,
        meta: { requiresAuth: true, role: 'MEMBER' },
        children: [
            {
                path: 'classes',
                name: 'MemberClassList',
                component: () => import('@/views/member/ClassList.vue'),
                meta: { title: '健身空间' }
            },
            // ====== 新增：我的课程包 ======
            {
                path: 'packages',
                name: 'MemberPackages',
                component: () => import('@/views/member/MyPackages.vue'),
                meta: { title: '我的课程包' }
            },
            {
                path: 'bookings',
                name: 'MemberMyBookings',
                component: () => import('@/views/member/MyBookings.vue'),
                meta: { title: '我的预约' }
            },
            {
                path: 'ai-chat',
                name: 'MemberAIChat',
                component: () => import('@/views/member/AIChat.vue'),
                meta: { title: 'AI 智能助手' }
            },
            {
                path: 'face-checkin',
                name: 'MemberFaceCheckIn',
                component: () => import('@/views/member/FaceCheckIn.vue'),
                meta: { title: '刷脸签到' }
            },
            { path: '', redirect: '/member/classes' }
        ]
    },

    { path: '/', redirect: '/login' },
    { path: '/:pathMatch(.*)*', redirect: '/login' }
]

// ====== 创建路由实例 ======
const router = createRouter({
    history: createWebHistory(),
    routes
})

// ====== 全局路由守卫 ======
router.beforeEach(async (to, from, next) => {
    if (to.path === '/login') {
        const cached = getCachedAuth()
        if (cached && cached.loggedIn) {
            const role = cached.role || 'MEMBER'
            return next('/' + role.toLowerCase() + '/dashboard')
        }
        return next()
    }

    if (to.meta.requiresAuth) {
        const auth = await checkAuth()
        if (!auth.loggedIn) {
            clearCachedAuth()
            return next('/login')
        }
        const requiredRole = to.meta.role
        if (requiredRole && auth.role !== requiredRole) {
            const role = auth.role || 'MEMBER'
            return next('/' + role.toLowerCase() + '/dashboard')
        }
        next()
    } else {
        next()
    }
})

// ====== 路由加载错误处理 ======
router.onError((error) => {
    console.warn('路由加载失败:', error)
})

export default router