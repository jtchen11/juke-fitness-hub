<template>
  <div class="fitness-home">
    <!-- ============================================================ -->
    <!-- 左右分栏布局                                                -->
    <!-- ============================================================ -->
    <el-row :gutter="20">
      <!-- ======================== -->
      <!-- 左侧：个人信息            -->
      <!-- ======================== -->
      <el-col :span="6">
        <el-card shadow="hover" class="profile-card">
          <!-- 头像 -->
          <div class="profile-avatar">
            <el-avatar :size="80" :style="{ backgroundColor: getAvatarColor(userInfo.name) }">
              {{ userInfo.name?.charAt(0) || '?' }}
            </el-avatar>
          </div>

          <!-- 姓名 + 等级 -->
          <div class="profile-name">
            <el-tag
                :type="getLevelTagType(userInfo.level)"
                size="large"
                class="level-tag"
                style="cursor:pointer;"
                @click="showBenefits"
            >
              {{ userInfo.level || '普通会员' }}
            </el-tag>
            {{ userInfo.name || '会员' }}
          </div>

          <!-- 有效期 -->
          <div class="profile-item">
            <span class="label">📅 有效期</span>
            <span class="value" :style="{ color: getExpireColor(userInfo.expireDate) }">
              {{ userInfo.expireDate || '未设置' }}
            </span>
          </div>

          <!-- 剩余课时 -->
          <div class="profile-item">
            <span class="label">📦 剩余课时</span>
            <span class="value highlight">{{ remainingSessions }} 节</span>
          </div>

          <!-- 今日签到 -->
          <div class="profile-item">
            <span class="label">✅ 今日签到</span>
            <span class="value" :style="{ color: todayCheckedIn ? '#67C23A' : '#909399' }">
              {{ todayCheckedIn ? '已签到' : '未签到' }}
            </span>
          </div>

          <!-- ====== 新增：本月免费私教剩余 ====== -->
          <div class="profile-item">
            <span class="label">🎁 本月免费私教</span>
            <span class="value highlight">{{ freePtRemaining }} / {{ freePtTotal }} 次</span>
          </div>

          <!-- 待上课预约 -->
          <div class="profile-item">
            <span class="label">⏳ 待上课</span>
            <span class="value highlight">{{ upcomingBookings }} 节</span>
          </div>

          <!-- 签到按钮 -->
          <el-button
              type="primary"
              plain
              style="width:100%;margin-top:16px;"
              :loading="checkInLoading"
              @click="handleQuickCheckIn"
          >
            📸 刷脸签到
          </el-button>

          <!-- 体测数据摘要 -->
          <div class="profile-divider"></div>
          <div class="profile-section-title">📊 最新体测</div>
          <div class="fitness-data-grid">
            <div class="fitness-item">
              <span class="fitness-label">体重</span>
              <span class="fitness-value">{{ latestTest?.weightKg || '-' }} kg</span>
            </div>
            <div class="fitness-item">
              <span class="fitness-label">体脂率</span>
              <span class="fitness-value">{{ latestTest?.bodyFatPercent || '-' }}%</span>
            </div>
            <div class="fitness-item">
              <span class="fitness-label">肌肉量</span>
              <span class="fitness-value">{{ latestTest?.muscleMassKg || '-' }} kg</span>
            </div>
          </div>
          <div style="text-align:right;margin-top:4px;">
            <el-button size="small" text @click="openTestDrawer">查看全部 →</el-button>
          </div>

          <!-- 我的比赛 -->
          <div class="profile-divider"></div>
          <div class="profile-section-title">🏆 我的比赛</div>
          <div v-if="myCompetitions.length > 0" class="my-competition-list">
            <div v-for="comp in myCompetitions" :key="comp.competitionId" class="my-competition-item">
              <div class="comp-name">{{ comp.name }}</div>
              <div class="comp-deadline">截止：{{ comp.deadline?.slice(0,10) }}</div>
              <el-button
                  size="small"
                  type="danger"
                  text
                  @click="cancelCompetition(comp)"
                  style="padding:0;"
              >取消</el-button>
            </div>
          </div>
          <div v-else style="color:#909399;font-size:13px;padding:4px 0;">暂无报名比赛</div>
        </el-card>
      </el-col>

      <!-- ======================== -->
      <!-- 右侧：内容区             -->
      <!-- ======================== -->
      <el-col :span="18">
        <!-- 1️⃣ 热门推荐 -->
        <el-card shadow="hover" class="right-section" style="margin-bottom:20px;">
          <template #header>
            <div class="card-header">
              <span>🔥 热门推荐</span>
              <el-tag type="danger" size="small">今日火爆</el-tag>
            </div>
          </template>
          <el-row :gutter="16" v-if="hotClasses.length > 0">
            <el-col :span="8" v-for="item in hotClasses" :key="item.id">
              <div class="hot-card" @click="openPaymentDialog(item)">
                <div class="hot-card-content">
                  <div class="hot-card-title">{{ item.name }}</div>
                  <div class="hot-card-info">
                    <span>{{ item.trainerName || '待定' }}</span>
                    <span>{{ formatTime(item.startTime) }}</span>
                  </div>
                  <div class="hot-card-bottom">
                    <el-progress
                        :percentage="getBookingRate(item)"
                        :color="getProgressColor(item)"
                        :stroke-width="6"
                        :show-text="false"
                        style="flex:1;"
                    />
                    <span style="font-size:12px;color:#999;margin-left:8px;">
                      {{ item.enrolled }}/{{ item.maxCapacity }}
                    </span>
                  </div>
                  <el-tag :type="item.type === 'free' ? 'success' : 'warning'" size="small" class="hot-tag">
                    {{ item.type === 'free' ? '公益' : '付费' }}
                  </el-tag>
                </div>
              </div>
            </el-col>
          </el-row>
          <div v-else style="text-align:center;padding:20px;color:#999;">暂无热门课程</div>
        </el-card>

        <!-- 2️⃣ 私教套餐（横向滑动） -->
        <el-card shadow="hover" class="right-section" style="margin-bottom:20px;">
          <template #header>
            <div class="card-header">
              <span>🏋️ 私教套餐</span>
              <div style="display:flex;gap:8px;">
                <el-tag type="danger" size="small">限时优惠</el-tag>
                <el-tag type="warning" size="small">新客专享</el-tag>
              </div>
            </div>
          </template>

          <div class="package-scroll-wrapper" v-if="privatePackages.length > 0">
            <el-button
                size="small"
                text
                @click="scrollPackages(-1)"
                :disabled="packageScrollLeft === 0"
                class="scroll-btn"
            >
              ‹
            </el-button>

            <div class="package-scroll" ref="packageScrollRef" @scroll="updateScrollLeft">
              <div
                  v-for="pkg in privatePackages"
                  :key="pkg.id"
                  class="package-card"
                  :class="{
                    'featured': pkg.originalPrice && pkg.originalPrice > pkg.price,
                    'hot': getPackageBadge(pkg)?.type === 'danger'
                  }"
              >
                <div class="package-badge" v-if="getPackageBadge(pkg)">
                  <span class="badge-text">{{ getPackageBadge(pkg).text }}</span>
                </div>
                <div class="package-name">{{ pkg.name }}</div>
                <div class="package-type">{{ pkg.typeLabel }}</div>
                <div class="package-desc" v-if="pkg.description">{{ pkg.description }}</div>
                <div class="package-info">
                  <span>{{ pkg.sessions }} 节课</span>
                  <span>{{ pkg.validDays }} 天</span>
                </div>
                <div class="package-price">
                  <span class="price-current">¥{{ pkg.price }}</span>
                  <span class="price-original" v-if="pkg.originalPrice && pkg.originalPrice > pkg.price">
                    ¥{{ pkg.originalPrice }}
                  </span>
                </div>
                <el-button
                    size="small"
                    type="primary"
                    plain
                    @click="openPaymentDialog({ ...pkg, isPrivate: true })"
                    style="width:100%;margin-top:8px;"
                >
                  立即购买
                </el-button>
              </div>
            </div>

            <el-button
                size="small"
                text
                @click="scrollPackages(1)"
                :disabled="packageScrollLeft >= packageMaxScroll"
                class="scroll-btn"
            >
              ›
            </el-button>
          </div>
          <div v-else style="text-align:center;padding:20px;color:#999;">暂无可用套餐</div>
        </el-card>

        <!-- 3️⃣ 团课推荐 -->
        <el-card shadow="hover" class="right-section" style="margin-bottom:20px;">
          <template #header>
            <div class="card-header">
              <span>📅 团课推荐</span>
              <el-tag type="warning" size="small">需预约</el-tag>
            </div>
          </template>

          <el-tabs v-model="classTab" @tab-change="onClassTabChange" style="margin-bottom:16px;">
            <el-tab-pane label="📚 全部" name="all" />
            <el-tab-pane label="💰 付费" name="paid" />
            <el-tab-pane label="❤️ 公益" name="free" />
          </el-tabs>

          <el-table :data="paginatedClassTable" border v-loading="loading" style="width:100%" size="small">
            <el-table-column prop="name" label="课程名称" min-width="100" />
            <el-table-column label="教练" width="80">
              <template #default="{ row }">{{ row.trainerName || '待定' }}</template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="150" />
            <el-table-column label="价格" width="70" align="center">
              <template #default="{ row }">
                <span v-if="row.type === 'free'" style="color:#67C23A;font-weight:bold;">免费</span>
                <span v-else style="color:#E6A23C;font-weight:bold;">¥{{ row.price || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="名额" width="120" align="center">
              <template #default="{ row }">
                <el-progress
                    :percentage="getBookingRate(row)"
                    :color="getProgressColor(row)"
                    :stroke-width="6"
                    :format="() => `${row.enrolled || 0}/${row.maxCapacity}`"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                    size="small"
                    :type="getButtonType(row)"
                    plain
                    :disabled="!canBook(row) || isBooked(row)"
                    :loading="bookingLoading && bookingTargetId === row.id"
                    @click="openPaymentDialog(row)"
                >
                  {{ getButtonText(row) }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper" v-if="classTotal > classPageSize">
            <el-pagination
                v-model:current-page="classPageNum"
                v-model:page-size="classPageSize"
                :page-sizes="[5, 10, 20]"
                :total="classTotal"
                layout="total, sizes, prev, pager, next"
                @size-change="onPageSizeChange"
                @current-change="onPageChange"
            />
          </div>

          <div v-if="filteredClassTable.length === 0 && !loading" style="text-align:center;padding:20px;color:#999;">
            暂无符合条件的团课
          </div>
        </el-card>

        <!-- 4️⃣ 比赛报名 -->
        <el-card class="right-section" shadow="hover" v-if="competitions.length > 0">
          <template #header>
            <div class="card-header">
              <span>🏆 近期比赛</span>
              <el-tag type="info" size="small">报名中</el-tag>
            </div>
          </template>
          <div class="competition-list">
            <div class="competition-item" v-for="comp in competitions" :key="comp.id">
              <div class="comp-info">
                <h4>{{ comp.name }}</h4>
                <p>{{ comp.description || '暂无介绍' }}</p>
                <span>报名截止：{{ comp.deadline }}</span>
                <span style="margin-left:12px;color:#409EFF;">
                  已报名 {{ comp.enrolled || 0 }}/{{ comp.maxParticipants }}
                </span>
              </div>
              <el-button
                  type="danger"
                  size="small"
                  @click="signUp(comp)"
                  :disabled="comp.enrolled >= comp.maxParticipants"
              >
                {{ comp.enrolled >= comp.maxParticipants ? '已满员' : '立即报名' }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================ -->
    <!-- 支付弹窗                                                    -->
    <!-- ============================================================ -->
    <el-dialog
        v-model="paymentDialogVisible"
        title="💰 确认支付"
        width="460px"
        destroy-on-close
        :close-on-click-modal="false"
    >
      <div style="padding:8px 0;">
        <div style="text-align:center;margin-bottom:16px;">
          <div style="font-size:20px;font-weight:bold;color:#303133;">{{ paymentTarget?.name }}</div>
          <div v-if="paymentTarget?.isPrivate" style="color:#909399;font-size:14px;margin-top:4px;">
            {{ paymentTarget?.typeLabel }} · {{ paymentTarget?.sessions }} 节课 · {{ paymentTarget?.validDays }} 天有效期
          </div>
          <div v-if="paymentTarget?.description" style="color:#666;font-size:13px;margin-top:6px;padding:8px 12px;background:#f5f7fa;border-radius:6px;">
            {{ paymentTarget.description }}
          </div>
          <div v-else-if="!paymentTarget?.isPrivate" style="color:#909399;font-size:14px;margin-top:4px;">
            {{ paymentTarget?.startTime }}
          </div>
          <div style="margin-top:12px;font-size:28px;font-weight:bold;color:#E6A23C;">
            ¥{{ paymentTarget?.price || 0 }}
          </div>
          <div v-if="paymentTarget?.originalPrice && paymentTarget?.originalPrice > paymentTarget?.price" style="color:#999;font-size:13px;text-decoration:line-through;">
            原价 ¥{{ paymentTarget?.originalPrice }}
          </div>
        </div>

        <!-- 显示折扣信息（若有） -->
        <div v-if="discountInfo && discountInfo.discountPercent > 0"
             style="margin-top:8px;padding:8px 12px;background:#fdf6ec;border-radius:6px;border:1px solid #f5dab1;">
          <span style="color:#E6A23C;font-size:14px;">
            🎉 {{ discountInfo.levelName }} 享受 {{ discountInfo.discountPercent }}% 折扣
            <span style="font-weight:bold;font-size:16px;">
              省 ¥{{ discountInfo.savedAmount }}
            </span>
          </span>
          <div style="font-size:12px;color:#999;margin-top:2px;">
            原价 ¥{{ discountInfo.originalPrice }} → 会员价 ¥{{ discountInfo.discountedPrice }}
          </div>
        </div>

        <el-form label-width="80px">
          <el-form-item label="支付方式">
            <el-select v-model="payMethod" placeholder="请选择支付方式" style="width:100%;">
              <el-option label="💚 微信支付" value="wechat" />
              <el-option label="💙 支付宝" value="alipay" />
              <el-option label="🏦 银行卡支付" value="bank" />
            </el-select>
          </el-form-item>
        </el-form>

        <p style="color:#909399;font-size:13px;text-align:center;margin-top:8px;">
          {{ paymentTarget?.type === 'free' || paymentTarget?.price === 0 ? '公益课程，免费预约' : '选择支付方式后点击确认' }}
        </p>
      </div>

      <template #footer>
        <el-button @click="paymentDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            :loading="paying"
            :disabled="!payMethod"
            @click="confirmPayment"
        >
          {{ paymentTarget?.type === 'free' || paymentTarget?.price === 0 ? '确认预约' : '确认支付' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ============================================================ -->
    <!-- 体测记录抽屉                                                -->
    <!-- ============================================================ -->
    <el-drawer
        v-model="testDrawerVisible"
        title="📊 全部体测记录"
        direction="rtl"
        size="550px"
        destroy-on-close
    >
      <div v-if="testDrawerLoading" style="text-align:center;padding:40px;color:#999;">
        加载中...
      </div>
      <div v-else-if="allTests.length === 0" style="text-align:center;padding:40px;color:#999;">
        暂无体测记录
      </div>
      <el-table :data="allTests" border size="small" max-height="500" v-else>
        <el-table-column prop="testDate" label="日期" width="110" />
        <el-table-column prop="weightKg" label="体重(kg)" width="90" align="center">
          <template #default="{ row }">
            <span style="font-weight:bold;color:#409EFF;">{{ row.weightKg || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bodyFatPercent" label="体脂率(%)" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getFatTagType(row.bodyFatPercent)" size="small" effect="plain">
              {{ row.bodyFatPercent || '-' }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="muscleMassKg" label="肌肉量(kg)" width="90" align="center">
          <template #default="{ row }">
            <span style="font-weight:bold;color:#67C23A;">{{ row.muscleMassKg || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remarks" label="备注" min-width="100">
          <template #default="{ row }">
            <span style="color:#666;font-size:12px;">{{ row.remarks || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// ===== 我的比赛 =====
const myCompetitions = ref([])

// ===== 新增：会员权益（免费私教次数） =====
const freePtTotal = ref(0)
const freePtRemaining = ref(0)

// 加载会员权益
const loadMemberBenefits = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get(`/api/members/${memberId}/benefits`)
    freePtTotal.value = res.data.freePtTotal || 0
    freePtRemaining.value = res.data.freePtRemaining || 0
  } catch (error) {
    console.error('加载权益失败', error)
  }
}

// ===== 加载我的比赛 =====
const loadMyCompetitions = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const regRes = await axios.get(`/api/competition-registrations/member/${memberId}`)
    const registrations = regRes.data || []
    if (registrations.length === 0) {
      myCompetitions.value = []
      return
    }
    const detailPromises = registrations
        .filter(reg => reg.status === 'registered')
        .map(reg => axios.get(`/api/competitions/${reg.competitionId}`))
    const detailResponses = await Promise.all(detailPromises)
    myCompetitions.value = registrations
        .filter(reg => reg.status === 'registered')
        .map((reg, index) => ({
          registrationId: reg.id,
          competitionId: reg.competitionId,
          registrationTime: reg.registrationTime,
          ...detailResponses[index].data
        }))
    console.log('✅ 我的比赛:', myCompetitions.value)
  } catch (error) {
    console.error('加载我的比赛失败', error)
    myCompetitions.value = []
  }
}

// ===== 取消报名 =====
const cancelCompetition = async (comp) => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    await ElMessageBox.confirm(
        `确定要取消「${comp.name}」的报名吗？`,
        '取消报名',
        { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' }
    )
    const res = await axios.delete('/api/competition-registrations', {
      params: { competitionId: comp.competitionId, memberId: Number(memberId) }
    })
    if (res.data.success) {
      ElMessage.success('已取消报名')
      await loadMyCompetitions()
      await loadCompetitions()
    } else {
      ElMessage.error(res.data.message || '取消失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '取消失败，请重试')
    }
  }
}

// ================================================================
// 辅助函数
// ================================================================
function getTypeLabelByDays(days) {
  if (days <= 1) return '单次'
  if (days <= 7) return '包周'
  if (days <= 30) return '包月'
  if (days <= 90) return '包季'
  return '长期'
}

// ================================================================
// 状态变量
// ================================================================
const router = useRouter()

const userInfo = ref({ name: '', level: '普通会员', expireDate: '', phone: '' })
const userLevel = ref('普通会员')
const todayCheckedIn = ref(false)
const checkInLoading = ref(false)

const loading = ref(false)
const allClasses = ref([])

const privatePackages = ref([])
const myPackages = ref([])

const bookedClassIds = ref([])
const bookingLoading = ref(false)
const bookingTargetId = ref(null)

const paymentDialogVisible = ref(false)
const paying = ref(false)
const paymentTarget = ref(null)
const payMethod = ref('')

const classTab = ref('all')
const classPageNum = ref(1)
const classPageSize = ref(5)

const competitions = ref([])

const latestTest = ref(null)
const testDrawerVisible = ref(false)
const testDrawerLoading = ref(false)
const allTests = ref([])

const packageScrollRef = ref(null)
const packageScrollLeft = ref(0)
const packageMaxScroll = ref(0)

// ===== 折扣信息（用于支付弹窗） =====
const discountInfo = ref({
  discountPercent: 0,
  levelName: '',
  originalPrice: 0,
  discountedPrice: 0,
  savedAmount: 0
})

// ================================================================
// 计算属性
// ================================================================
const remainingSessions = computed(() => {
  let total = 0
  myPackages.value.forEach(pkg => {
    total += pkg.remainingSessions || 0
  })
  return total
})

const upcomingBookings = computed(() => {
  return bookedClassIds.value.length
})

const hotClasses = computed(() => {
  return allClasses.value
      .filter(c => c.status === 'scheduled' && (c.enrolled || 0) > 0)
      .sort((a, b) => {
        const rateA = (a.enrolled || 0) / a.maxCapacity
        const rateB = (b.enrolled || 0) / b.maxCapacity
        return rateB - rateA
      })
      .slice(0, 3)
})

const filteredClassTable = computed(() => {
  if (classTab.value === 'all') {
    return allClasses.value.filter(c => c.status === 'scheduled')
  }
  return allClasses.value.filter(c => c.status === 'scheduled' && c.type === classTab.value)
})

const paginatedClassTable = computed(() => {
  const start = (classPageNum.value - 1) * classPageSize.value
  const end = start + classPageSize.value
  return filteredClassTable.value.slice(start, end)
})

const classTotal = computed(() => filteredClassTable.value.length)

// ================================================================
// 工具函数
// ================================================================
const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  return colors[name.charCodeAt(0) % colors.length]
}

const getLevelTagType = (level) => {
  const map = { '铂金会员': 'warning', '黄金会员': 'success' }
  return map[level] || 'info'
}

const getExpireColor = (date) => {
  if (!date) return '#909399'
  const now = new Date()
  const expire = new Date(date)
  const days = Math.ceil((expire - now) / (1000 * 60 * 60 * 24))
  if (days < 0) return '#F56C6C'
  if (days < 7) return '#E6A23C'
  return '#67C23A'
}

const formatTime = (time) => {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

const getBookingRate = (row) => {
  if (!row.maxCapacity || row.maxCapacity === 0) return 0
  return Math.round(((row.enrolled || 0) / row.maxCapacity) * 100)
}

const getProgressColor = (row) => {
  const rate = getBookingRate(row)
  if (rate >= 90) return '#F56C6C'
  if (rate >= 70) return '#E6A23C'
  return '#67C23A'
}

const isExpired = (row) => {
  if (!row.endTime) return false
  return new Date(row.endTime) < new Date()
}

const isBooked = (row) => {
  return bookedClassIds.value.includes(row.id)
}

const canBook = (row) => {
  if (isExpired(row)) return false
  if (row.status !== 'scheduled') return false
  if (isBooked(row)) return false

  const isFull = (row.enrolled || 0) >= row.maxCapacity
  if (isFull && userLevel.value === '铂金会员') return true
  if (isFull) return false
  return true
}

const getButtonText = (row) => {
  if (isExpired(row)) return '已过期'
  if (isBooked(row)) return '已预约'

  const isFull = (row.enrolled || 0) >= row.maxCapacity
  if (isFull && userLevel.value === '铂金会员') return '⭐ 铂金超额'
  if (isFull) return '已满员'
  if (row.status !== 'scheduled') return '已结束'
  return '预约'
}

const getButtonType = (row) => {
  if (isBooked(row)) return 'info'
  if (isExpired(row)) return 'info'

  const isFull = (row.enrolled || 0) >= row.maxCapacity
  if (isFull && userLevel.value === '铂金会员') return 'warning'
  if (isFull) return 'info'
  if (row.type === 'free') return 'success'
  return 'primary'
}

const getPackageBadge = (pkg) => {
  if (pkg.originalPrice && pkg.originalPrice > pkg.price) {
    return { text: '🔥 超值', type: 'danger' }
  }
  if (pkg.sessions >= 6) {
    return { text: '💎 热销', type: 'warning' }
  }
  return null
}

const getFatTagType = (fat) => {
  if (!fat) return 'info'
  if (fat < 15) return 'success'
  if (fat < 25) return 'warning'
  return 'danger'
}

// ================================================================
// 私教套餐滚动控制
// ================================================================
const updateScrollLeft = () => {
  const el = packageScrollRef.value
  if (!el) return
  packageScrollLeft.value = el.scrollLeft
  packageMaxScroll.value = el.scrollWidth - el.clientWidth
}

const scrollPackages = (direction) => {
  const el = packageScrollRef.value
  if (!el) return
  const scrollAmount = 260
  const target = el.scrollLeft + direction * scrollAmount
  el.scrollTo({
    left: Math.max(0, Math.min(target, el.scrollWidth - el.clientWidth)),
    behavior: 'smooth'
  })
}

// ================================================================
// 体测抽屉
// ================================================================
const loadAllTests = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  testDrawerLoading.value = true
  try {
    const res = await axios.get('/api/fitness-tests', {
      params: { memberId, page: 1, size: 100 }
    })
    allTests.value = res.data.list || []
  } catch (error) {
    console.error('加载体测记录失败', error)
    ElMessage.error('加载体测记录失败')
  } finally {
    testDrawerLoading.value = false
  }
}

const openTestDrawer = () => {
  testDrawerVisible.value = true
  loadAllTests()
}

// ================================================================
// 数据加载
// ================================================================
const fetchUserInfo = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get(`/api/members/${memberId}`)
    userInfo.value = res.data || {}
    userLevel.value = res.data.level || '普通会员'
  } catch (error) {
    console.error('获取用户信息失败', error)
  }
}

const fetchTodayCheckIn = async () => {
  try {
    const res = await axios.get('/api/check-in/stats/summary')
    todayCheckedIn.value = (res.data.today || 0) > 0
  } catch (error) {
    console.error('获取签到状态失败', error)
  }
}

const fetchLatestTest = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get('/api/fitness-tests', {
      params: { memberId, page: 1, size: 1 }
    })
    latestTest.value = res.data.list?.[0] || null
  } catch (error) {
    console.error('加载体测数据失败', error)
  }
}

const loadMyPackages = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get('/api/private-packages/mine', {
      params: { memberId: Number(memberId), size: 100 }
    })
    myPackages.value = res.data || []
  } catch (error) {
    console.error('加载已购套餐失败', error)
  }
}

const loadPrivatePackages = async () => {
  try {
    const res = await axios.get('/api/member/packages/list', {
      params: { size: 100 }
    })
    const rawData = res.data || []
    privatePackages.value = rawData.map(item => ({
      id: item.id,
      name: item.name,
      typeLabel: item.typeLabel,
      sessions: item.sessions,
      validDays: item.validDays,
      price: item.price,
      originalPrice: item.originalPrice || null,
      description: item.description || '',
      remainingSessions: 0
    }))
    console.log('✅ 已上架套餐数据:', privatePackages.value)
  } catch (error) {
    console.error('加载套餐列表失败', error)
    privatePackages.value = []
    ElMessage.error('加载套餐列表失败，请刷新重试')
  }
}

const loadBookedClasses = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get('/api/class-bookings', {
      params: { memberId: Number(memberId), size: 100 }
    })
    const list = res.data.list || []
    const ids = list.filter(item => item.status !== 'cancelled').map(item => item.classId)
    bookedClassIds.value = [...new Set(ids)]
  } catch (error) {
    console.error('加载已预约课程失败', error)
  }
}

const loadClasses = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/classes', { params: { size: 100 } })
    allClasses.value = res.data.list || []
  } catch (error) {
    console.error('加载课程失败', error)
    ElMessage.error('加载课程失败')
  } finally {
    loading.value = false
  }
}

const loadCompetitions = async () => {
  try {
    const res = await axios.get('/api/competitions/active')
    competitions.value = res.data || []
  } catch (error) {
    console.error('加载比赛列表失败', error)
  }
}

// ================================================================
// 团课分页
// ================================================================
const onClassTabChange = () => { classPageNum.value = 1 }
const onPageChange = (page) => { classPageNum.value = page }
const onPageSizeChange = (size) => {
  classPageSize.value = size
  classPageNum.value = 1
}

// ================================================================
// 快速签到
// ================================================================
const handleQuickCheckIn = () => {
  router.push('/member/face-checkin')
}

// ================================================================
// 支付与预约
// ===============================================
const openPaymentDialog = (row) => {
paymentTarget.value = { ...row }
payMethod.value = ''

// 重置折扣信息
discountInfo.value = {
  discountPercent: 0,
  levelName: '',
  originalPrice: 0,
  discountedPrice: 0,
  savedAmount: 0
}

// 根据会员等级计算折扣（如果有价格且非免费）
const price = row.price || 0
if (price > 0) {
  const level = userInfo.value.level || '普通会员'
  const discountMap = {
    '普通会员': 0,
    '黄金会员': 10,
    '铂金会员': 20
  }
  const discountPercent = discountMap[level] || 0
  if (discountPercent > 0) {
    // 原价：优先使用 row.originalPrice，否则用 row.price
    const originalPrice = row.originalPrice && row.originalPrice > price ? row.originalPrice : price
    const discountedPrice = price * (1 - discountPercent / 100)
    const savedAmount = originalPrice - discountedPrice
    discountInfo.value = {
      discountPercent: discountPercent,
      levelName: level,
      originalPrice: originalPrice,
      discountedPrice: discountedPrice,
      savedAmount: savedAmount
    }
    // 将显示价格改为折后价
    paymentTarget.value.price = discountedPrice
    paymentTarget.value.originalPrice = originalPrice
  }
}

paymentDialogVisible.value = true
}

const confirmPayment = async () => {
  if (!paymentTarget.value) return
  if (!payMethod.value) {
    ElMessage.warning('请选择支付方式')
    return
  }

  paying.value = true
  const memberId = localStorage.getItem('userId')
  if (!memberId) {
    ElMessage.warning('请先登录')
    paying.value = false
    return
  }

  try {
    // ===== 私教课程购买 =====
    if (paymentTarget.value.isPrivate) {
      const buyRes = await axios.post('/api/private-packages/buy', {
        memberId: Number(memberId),
        packageId: paymentTarget.value.id,
      })

      if (!buyRes.data.success) {
        ElMessage.error(buyRes.data.message || '购买失败')
        paying.value = false
        return
      }

      discountInfo.value = {
        discountPercent: buyRes.data.discountPercent || 0,
        levelName: buyRes.data.levelName || '',
        originalPrice: buyRes.data.originalPrice || paymentTarget.value.price,
        discountedPrice: buyRes.data.discountedPrice || paymentTarget.value.price,
        savedAmount: buyRes.data.savedAmount || 0
      }

      paymentTarget.value.price = buyRes.data.discountedPrice || paymentTarget.value.price
      paymentTarget.value.originalPrice = buyRes.data.originalPrice || paymentTarget.value.originalPrice

      const payMethodMap = { wechat: '微信支付', alipay: '支付宝', bank: '银行卡' }
      ElMessage.success(`🎉 购买成功！${paymentTarget.value.name}（${payMethodMap[payMethod.value]}）`)

      paymentDialogVisible.value = false
      await loadPrivatePackages()
      return
    }

    // ===== 团课预约支付 =====
    const bookRes = await axios.post('/api/class-bookings', {
      memberId: Number(memberId),
      classId: paymentTarget.value.id
    })

    if (!bookRes.data.success) {
      ElMessage.error(bookRes.data.message || '预约创建失败')
      paying.value = false
      return
    }

    const bookingId = bookRes.data.bookingId
    const payRes = await axios.post(`/api/class-bookings/${bookingId}/pay`)

    if (payRes.data.success) {
      // 更新折扣信息（从支付返回获取）
      discountInfo.value = {
        discountPercent: payRes.data.discountApplied || 0,
        levelName: '会员等级',   // 可后续优化
        originalPrice: payRes.data.originalPrice || paymentTarget.value.price,
        discountedPrice: payRes.data.paidAmount || paymentTarget.value.price,
        savedAmount: (payRes.data.originalPrice || paymentTarget.value.price) - (payRes.data.paidAmount || paymentTarget.value.price)
      }

      const payMethodMap = { wechat: '微信支付', alipay: '支付宝', bank: '银行卡' }
      ElMessage.success(`🎉 ${payRes.data.message} (实付 ¥${payRes.data.paidAmount}) (${payMethodMap[payMethod.value]})`)
      paymentDialogVisible.value = false
      await Promise.all([loadClasses(), loadBookedClasses()])
    } else {
      ElMessage.error(payRes.data.message || '支付失败')
    }
  } catch (error) {
    console.error('操作失败', error)
    ElMessage.error(error.response?.data?.message || '操作失败，请重试')
  } finally {
    paying.value = false
  }
}

// ================================================================
// 比赛报名
// ================================================================
const signUp = async (comp) => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) {
    ElMessage.warning('请先登录')
    return
  }

  try {
    const res = await axios.post('/api/competition-registrations', {
      competitionId: comp.id,
      memberId: Number(memberId)
    })
    if (res.data.success) {
      ElMessage.success('🎉 报名成功！')
      await loadCompetitions()
    } else {
      ElMessage.error(res.data.message || '报名失败')
    }
  } catch (error) {
    console.error('报名失败', error)
    ElMessage.error(error.response?.data?.message || '报名失败，请重试')
  }
}

// ================================================================
// 展示会员权益
// ================================================================
const showBenefits = () => {
  const level = userInfo.value.level || '普通会员'
  const benefits = {
    '铂金会员': {
      discount: '20%',
      freeSessions: '每月2次免费私教',
      overbook: '可超额预约满员团课（+2名额）',
      priority: '优先预约热门课程'
    },
    '黄金会员': {
      discount: '10%',
      freeSessions: '每月1次免费私教',
      overbook: '普通预约',
      priority: '优先预约热门课程'
    },
    '普通会员': {
      discount: '无',
      freeSessions: '无',
      overbook: '普通预约',
      priority: '标准预约'
    }
  }
  const data = benefits[level] || benefits['普通会员']

  const usedFree = userInfo.value.freePtUsedMonth || 0
  const maxFree = data.freeSessions === '无' ? 0 : parseInt(data.freeSessions.match(/\d+/)?.[0] || 0)
  const remaining = maxFree > 0 ? maxFree - usedFree : 0

  ElMessageBox.alert(
      `
      <div style="line-height:2.0;font-size:14px;">
        <p><strong>💰 课程折扣：</strong>${data.discount}</p>
        <p><strong>🏋️ 免费私教：</strong>${data.freeSessions}</p>
        <p style="font-size:12px;color:#999;margin-top:-4px;">
          ${maxFree > 0 ? `本月已使用 ${usedFree} 次，剩余 ${remaining} 次` : ''}
        </p>
        <p><strong>📅 预约权限：</strong>${data.overbook}</p>
        <p><strong>⭐ 预约优先级：</strong>${data.priority}</p>
      </div>
      `,
      `🌟 ${level} 权益说明`,
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '知道了',
        type: 'info'
      }
  )
}

// ================================================================
// 初始化
// ================================================================
onMounted(async () => {
  await Promise.all([
    fetchUserInfo(),
    fetchTodayCheckIn(),
    fetchLatestTest(),
    loadMyPackages(),
    loadPrivatePackages(),
    loadClasses(),
    loadBookedClasses(),
    loadCompetitions(),
    loadMyCompetitions(),
    loadMemberBenefits()   // 新增：加载免费次数
  ])
  nextTick(() => {
    updateScrollLeft()
  })
})
</script>

<style scoped>
.fitness-home {
  max-width: 1400px;
  margin: 0 auto;
  padding: 4px;
}

/* ===== 左侧个人信息 ===== */
.profile-card {
  height: 100%;
}
.profile-avatar {
  text-align: center;
  padding: 16px 0 8px;
}
.profile-name {
  font-size: 20px;
  font-weight: bold;
  text-align: center;
  padding: 12px 0 16px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.name-level-tag {
  font-size: 14px !important;
  padding: 0 10px;
  height: 24px;
  line-height: 24px;
}
.profile-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 8px;
  border-bottom: 1px solid #f5f7fa;
}
.profile-item .label {
  color: #909399;
  font-size: 14px;
}
.profile-item .value {
  color: #303133;
  font-weight: 500;
}
.profile-item .value.highlight {
  color: #409EFF;
  font-weight: bold;
}
.profile-divider {
  height: 1px;
  background: #f0f2f5;
  margin: 12px 0 8px;
}
.profile-section-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}
.fitness-data-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
}
.fitness-item {
  text-align: center;
  padding: 6px 4px;
  background: #f5f7fa;
  border-radius: 6px;
}
.fitness-label {
  display: block;
  font-size: 11px;
  color: #909399;
}
.fitness-value {
  display: block;
  font-size: 15px;
  font-weight: bold;
  color: #303133;
}

/* ===== 右侧区域 ===== */
.right-section {
  height: auto;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ===== 热门推荐 ===== */
.hot-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.3s;
  background: #fff;
  height: 120px;
  display: flex;
  align-items: center;
  position: relative;
}
.hot-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  transform: translateY(-2px);
}
.hot-card-content {
  flex: 1;
  min-width: 0;
}
.hot-card-title {
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 4px;
  padding-right: 40px;
}
.hot-card-info {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
}
.hot-card-bottom {
  display: flex;
  align-items: center;
}
.hot-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}

/* ===== 私教套餐横向滑动 ===== */
.package-scroll-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.package-scroll {
  flex: 1;
  display: flex;
  gap: 16px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 8px 4px;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.package-scroll::-webkit-scrollbar {
  display: none;
}
.scroll-btn {
  flex-shrink: 0;
  font-size: 22px;
  font-weight: bold;
  color: #409EFF;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f5f7fa;
  transition: all 0.2s;
}
.scroll-btn:hover:not(:disabled) {
  background: #ecf5ff;
  color: #66b1ff;
}
.scroll-btn:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  background: #f5f7fa;
}
.package-card {
  flex: 0 0 200px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px;
  background: #fff;
  transition: all 0.3s;
  position: relative;
}
.package-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}
.package-card.featured {
  border-color: #E6A23C;
  background: #fdf6ec;
}
.package-card.hot {
  border-color: #F56C6C;
  background: #fef0f0;
}
.package-badge {
  position: absolute;
  top: -6px;
  right: 8px;
  background: #E6A23C;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
}
.badge-text {
  font-weight: bold;
}
.package-name {
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 4px;
}
.package-type {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.package-desc {
  font-size: 12px;
  color: #666;
  line-height: 1.4;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 34px;
}
.package-info {
  font-size: 12px;
  color: #606266;
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
}
.package-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.price-current {
  font-size: 18px;
  font-weight: bold;
  color: #E6A23C;
}
.price-original {
  font-size: 13px;
  color: #909399;
  text-decoration: line-through;
}

/* ===== 比赛列表 ===== */
.competition-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.competition-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.competition-item .comp-info h4 {
  margin: 0 0 4px;
}
.competition-item .comp-info p {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
}
.competition-item .comp-info span {
  font-size: 13px;
  color: #909399;
}

/* ===== 分页 ===== */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

:deep(.el-dialog__body) {
  padding-top: 8px;
}
:deep(.el-form-item) {
  margin-bottom: 12px;
}
/* 我的比赛 */
.my-competition-list {
  margin-top: 4px;
}
.my-competition-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
  font-size: 13px;
}
.my-competition-item .comp-name {
  font-weight: 500;
  color: #303133;
  flex: 1;
}
.my-competition-item .comp-deadline {
  color: #909399;
  font-size: 12px;
  margin-right: 8px;
}
</style>