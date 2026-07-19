<template>
  <div class="booking-center">
    <!-- ============================================================ -->
    <!-- 上半部分：日期横向滑动 + 可预约教练                       -->
    <!-- ============================================================ -->
    <el-card shadow="hover" style="margin-bottom: 20px;">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span>📅 预约教练</span>
          <el-tag size="small" type="info">选择日期，预约教练</el-tag>
        </div>
      </template>

      <div class="date-scroll-wrapper">
        <el-button size="small" text @click="scrollDates(-1)" :disabled="dateOffset === 0" class="scroll-btn">‹</el-button>
        <div class="date-scroll" ref="dateScrollRef">
          <div
              v-for="(item, index) in dateList"
              :key="index"
              class="date-item"
              :class="{ 'active': selectedDateStr === item.dateStr }"
              @click="selectDate(item.dateStr)"
          >
            <div class="date-weekday">{{ item.weekday }}</div>
            <div class="date-day">{{ item.day }}</div>
            <div class="date-month">{{ item.month }}月</div>
          </div>
        </div>
        <el-button size="small" text @click="scrollDates(1)" :disabled="dateOffset === 13" class="scroll-btn">›</el-button>
      </div>

      <div style="margin-top: 16px;">
        <div v-if="availableTrainersLoading" style="text-align:center;padding:20px;color:#999;">
          <el-icon class="is-loading"><Loading /></el-icon> 加载教练中...
        </div>
        <div v-else-if="availableTrainers.length === 0" style="text-align:center;padding:20px;color:#999;">
          📭 当天无教练可预约（可能全员休假）
        </div>
        <div v-else class="trainer-grid">
          <div v-for="trainer in availableTrainers" :key="trainer.id" class="trainer-card">
            <div class="trainer-avatar">
              <el-avatar :size="48" :style="{ backgroundColor: getAvatarColor(trainer.name) }">
                {{ trainer.name?.charAt(0) || '?' }}
              </el-avatar>
            </div>
            <div class="trainer-info">
              <div class="trainer-name">{{ trainer.name }}</div>
              <div class="trainer-specialty">{{ trainer.specialty || '全能教练' }}</div>
              <div class="trainer-price">¥{{ trainer.pricePerHour }}/小时</div>
            </div>
            <el-button size="small" type="primary" plain @click="openBookingDialog(trainer)">预约</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ============================================================ -->
    <!-- 下半部分：日历 + 预约记录                                   -->
    <!-- ============================================================ -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span>📅 我的日历</span></template>
          <el-calendar v-model="selectedDate" @select="onCalendarSelect">
            <template #date-cell="{ data }">
              <div class="calendar-cell">
                <span>{{ data.day.split('-')[2] }}</span>
                <div class="dots">
                  <span v-if="hasGreenDot(data.day)" class="dot green">●</span>
                  <span v-if="hasRedDot(data.day)" class="dot red">●</span>
                </div>
              </div>
            </template>
          </el-calendar>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span>📋 {{ showAllFuture ? '全部预约记录' : calendarSelectedDate + ' 的预约记录' }}</span>
              <div>
                <el-button size="small" text @click="resetToAll" v-if="!showAllFuture">
                  📋 全部预约
                </el-button>
                <el-button size="small" text @click="loadAllBookings" v-else>
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>
            </div>
          </template>

          <el-tabs v-model="activeTab" @tab-click="onTabChange">
            <!-- ====== 私教预约 Tab ====== -->
            <el-tab-pane label="🏋️ 私教" name="pt">
              <el-table :data="filteredPtBookings" border v-loading="loading" style="width:100%">
                <el-table-column prop="appointmentTime" label="时间" width="160" />
                <el-table-column label="教练" width="120">
                  <template #default="{ row }">{{ row.trainerName || '未知' }}</template>
                </el-table-column>
                <el-table-column prop="durationMinutes" label="时长" width="80" align="center" />
                <el-table-column label="状态" width="130">
                  <template #default="{ row }">
                    <el-tag v-if="row.status === 'completed'" type="info" size="small" effect="dark">已完成</el-tag>
                    <el-tag v-else-if="row.status === 'cancelled'" type="danger" size="small" effect="dark">已取消</el-tag>
                    <el-tag v-else-if="row.status === 'cancelled_by_trainer'" type="danger" size="small" effect="dark">💔 教练请假</el-tag>
                    <el-tag v-else-if="row.status === 'scheduled' && isPtExpired(row)" type="danger" size="small" effect="dark">已过期</el-tag>
                    <el-tag v-else :type="getStatusType(row.status)" size="small" effect="dark">{{ getStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right" align="center">
                  <template #default="{ row }">
                    <el-button
                        v-if="row.status === 'scheduled' && !isPtExpired(row) && !isPtCheckedIn(row)"
                        size="small" type="success" plain
                        :loading="checkInLoading && checkInTargetId === row.id"
                        @click="checkInPt(row)">▶ 打卡</el-button>
                    <el-button
                        v-if="row.status === 'scheduled' && !isPtExpired(row)"
                        size="small" type="danger" plain
                        @click="cancelPtBooking(row)">取消</el-button>
                    <span v-else style="color:#999;font-size:12px;">-</span>
                  </template>
                </el-table-column>
              </el-table>
              <!-- 私教分页 -->
              <div class="pagination-wrapper" v-if="ptTotal > ptPageSize">
                <el-pagination
                    v-model:current-page="ptPageNum"
                    v-model:page-size="ptPageSize"
                    :page-sizes="[5, 10, 20]"
                    :total="ptTotal"
                    layout="total, sizes, prev, pager, next"
                    @size-change="onPtPageSizeChange"
                    @current-change="onPtPageChange"
                />
              </div>
              <div v-if="filteredPtBookings.length === 0" style="text-align:center;padding:20px;color:#999;">当日无私教预约</div>
            </el-tab-pane>

            <!-- ====== 团课预约 Tab ====== -->
            <el-tab-pane label="📅 团课" name="class">
              <el-table :data="filteredClassBookings" border v-loading="loading" style="width:100%">
                <el-table-column prop="bookingTime" label="预约时间" width="160" />
                <el-table-column label="课程" width="150">
                  <template #default="{ row }">{{ row.className || '未知课程' }}</template>
                </el-table-column>
                <el-table-column label="价格" width="80" align="center">
                  <template #default="{ row }">
                    <span v-if="row.paidAmount && row.paidAmount > 0" style="color:#E6A23C;">¥{{ row.paidAmount }}</span>
                    <span v-else style="color:#67C23A;">免费</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.status === 'checked_in'" type="info" size="small">已签到</el-tag>
                    <el-tag v-else-if="row.status === 'cancelled'" type="danger" size="small">已取消</el-tag>
                    <el-tag v-else-if="row.status === 'booked' && isClassExpired(row)" type="danger" size="small" effect="dark">已过期</el-tag>
                    <el-tag v-else-if="row.status === 'booked'" type="success" size="small">已预约</el-tag>
                    <el-tag v-else type="danger" size="small">未知</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center">
                  <template #default="{ row }">
                    <el-button
                        v-if="row.status === 'booked' && !isClassExpired(row)"
                        size="small" type="danger" plain
                        :loading="cancellingId === row.id"
                        @click="cancelClassBooking(row)">
                      {{ row.paymentStatus === 'paid' ? '退款' : '取消' }}
                    </el-button>
                    <span v-else style="color:#999;font-size:12px;">-</span>
                  </template>
                </el-table-column>
              </el-table>
              <!-- 团课分页 -->
              <div class="pagination-wrapper" v-if="classTotal > classPageSize">
                <el-pagination
                    v-model:current-page="classPageNum"
                    v-model:page-size="classPageSize"
                    :page-sizes="[5, 10, 20]"
                    :total="classTotal"
                    layout="total, sizes, prev, pager, next"
                    @size-change="onClassPageSizeChange"
                    @current-change="onClassPageChange"
                />
              </div>
              <div v-if="filteredClassBookings.length === 0" style="text-align:center;padding:20px;color:#999;">当日无团课预约</div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================ -->
    <!-- 预约弹窗                                                     -->
    <!-- ============================================================ -->
    <el-dialog v-model="bookingDialogVisible" title="📝 预约私教课" width="480px" destroy-on-close>
      <div v-if="bookingTargetTrainer">
        <div style="text-align:center;margin-bottom:16px;">
          <el-avatar :size="56" :style="{ backgroundColor: getAvatarColor(bookingTargetTrainer.name) }">
            {{ bookingTargetTrainer.name?.charAt(0) || '?' }}
          </el-avatar>
          <div style="font-size:18px;font-weight:bold;margin-top:8px;">{{ bookingTargetTrainer.name }}</div>
          <div style="color:#999;font-size:13px;">{{ bookingTargetTrainer.specialty || '全能教练' }} · ¥{{ bookingTargetTrainer.pricePerHour }}/小时</div>
        </div>
        <el-form label-width="80px">
          <el-form-item label="预约日期"><span style="font-weight:bold;">{{ selectedDateStr }}</span></el-form-item>
          <el-form-item label="选择时段" required>
            <el-radio-group v-model="selectedSlot" class="slot-group">
              <el-radio-button v-for="slot in availableSlots" :key="slot" :label="slot" :value="slot" style="margin-bottom:8px;" />
            </el-radio-group>
            <div v-if="availableSlotsLoading" style="color:#999;font-size:13px;">加载时段中...</div>
            <div v-else-if="availableSlots.length === 0" style="color:#f56c6c;font-size:13px;">该日已无可预约时段</div>
          </el-form-item>
          <el-form-item label="支付方式" required>
            <el-form-item label="使用免费次数" v-if="freePtRemaining > 0">
              <el-switch v-model="useFree" />
              <span style="margin-left:8px;color:#999;">剩余 {{ freePtRemaining }} 次</span>
            </el-form-item>
            <el-radio-group v-model="payMethod">
              <el-radio-button v-for="pkg in availablePackages" :key="pkg.id" :label="'package_' + pkg.id">
                📦 {{ pkg.packageName }}（剩余{{ pkg.remainingSessions }}节）
              </el-radio-button>
              <el-radio-button label="single">💰 单次付费 ¥{{ bookingTargetTrainer?.pricePerHour || 300 }}</el-radio-button>
            </el-radio-group>
            <div v-if="availablePackages.length === 0" style="color:#999;font-size:12px;margin-top:4px;">暂无可用课程包，将使用单次付费</div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="bookingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bookingSubmitLoading" @click="submitBooking" :disabled="!selectedSlot || !payMethod">确认预约</el-button>
      </template>
    </el-dialog>

    <!-- ====== 取消团课确认弹窗 ====== -->
    <el-dialog v-model="cancelDialogVisible" title="⚠️ 确认取消" width="400px" destroy-on-close>
      <div style="text-align:center;padding:8px 0;">
        <p>确定要取消 <strong>{{ cancelTarget?.className }}</strong> 吗？</p>
        <p v-if="cancelTarget?.paymentStatus === 'paid'" style="color:#E6A23C;font-size:14px;">💰 已支付 ¥{{ cancelTarget?.paidAmount }}，取消后将自动退款</p>
      </div>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">再想想</el-button>
        <el-button type="danger" :loading="cancellingId !== null" @click="confirmCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Loading } from '@element-plus/icons-vue'
const useFree = ref(false)
const freePtRemaining = ref(0)
const router = useRouter()

// ================================================================
// 状态变量
// ================================================================

// 日期相关
const selectedDate = ref(new Date())
const selectedDateStr = ref(new Date().toISOString().split('T')[0])
const dateOffset = ref(0)
const dateList = ref([])
const calendarSelectedDate = ref('')            // 空字符串表示全部模式
const showAllFuture = ref(true)                // true表示全部模式

// 教练预约相关
const availableTrainers = ref([])
const availableTrainersLoading = ref(false)
const bookingDialogVisible = ref(false)
const bookingTargetTrainer = ref(null)
const availableSlots = ref([])
const availableSlotsLoading = ref(false)
const selectedSlot = ref('')
const payMethod = ref('')
const availablePackages = ref([])
const bookingSubmitLoading = ref(false)

// 预约列表相关
const loading = ref(false)
const activeTab = ref('pt')

// ====== 私教分页 ======
const ptPageNum = ref(1)
const ptPageSize = ref(5)
const ptTotal = ref(0)
const ptBookings = ref([])          // 分页数据（当前页）
const allPtBookings = ref([])       // 全量数据（用于红绿点）

// ====== 团课分页 ======
const classPageNum = ref(1)
const classPageSize = ref(5)
const classTotal = ref(0)
const classBookings = ref([])       // 分页数据（当前页）
const allClassBookings = ref([])    // 全量数据（用于红绿点）

// 红绿点
const greenDates = ref([])
const redDates = ref([])

// 取消相关
const cancellingId = ref(null)
const cancelDialogVisible = ref(false)
const cancelTarget = ref(null)

// 打卡相关
const checkInLoading = ref(false)
const checkInTargetId = ref(null)

// ================================================================
// 计算属性：过滤 + 排序（基于全量数据）
// ================================================================

const filteredPtBookings = computed(() => {
  let source = allPtBookings.value
  if (!source || source.length === 0) {
    ptTotal.value = 0
    return []
  }

  // ====== 有日期筛选时，数据已由后端过滤，直接排序分页 ======
  if (calendarSelectedDate.value) {
    const sorted = [...source].sort((a, b) => {
      const order = { scheduled: 0, completed: 1, cancelled: 2, cancelled_by_trainer: 2 }
      return (order[a.status] || 0) - (order[b.status] || 0)
    })
    ptTotal.value = sorted.length
    const start = (ptPageNum.value - 1) * ptPageSize.value
    const end = start + ptPageSize.value
    return sorted.slice(start, end)
  }

  // ====== 全部预约模式：显示所有记录 ======
  const sorted = [...source].sort((a, b) => {
    const order = { scheduled: 0, completed: 1, cancelled: 2, cancelled_by_trainer: 2 }
    return (order[a.status] || 0) - (order[b.status] || 0)
  })
  ptTotal.value = sorted.length
  const start = (ptPageNum.value - 1) * ptPageSize.value
  const end = start + ptPageSize.value
  return sorted.slice(start, end)
})

const filteredClassBookings = computed(() => {
  let source = allClassBookings.value
  if (!source || source.length === 0) {
    classTotal.value = 0
    return []
  }

  // ====== 有日期筛选时，数据已由后端过滤，直接排序分页 ======
  if (calendarSelectedDate.value) {
    const sorted = [...source].sort((a, b) => {
      const order = { booked: 0, checked_in: 1, cancelled: 2 }
      return (order[a.status] || 0) - (order[b.status] || 0)
    })
    classTotal.value = sorted.length
    const start = (classPageNum.value - 1) * classPageSize.value
    const end = start + classPageSize.value
    return sorted.slice(start, end)
  }

  // ====== 全部预约模式：显示所有记录 ======
  const sorted = [...source].sort((a, b) => {
    const order = { booked: 0, checked_in: 1, cancelled: 2 }
    return (order[a.status] || 0) - (order[b.status] || 0)
  })
  classTotal.value = sorted.length
  const start = (classPageNum.value - 1) * classPageSize.value
  const end = start + classPageSize.value
  return sorted.slice(start, end)
})

// ================================================================
// 工具函数
// ================================================================

const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

const hasGreenDot = (day) => greenDates.value.includes(day)
const hasRedDot = (day) => redDates.value.includes(day)

const isPtExpired = (row) => {
  if (row.status !== 'scheduled') return false
  if (!row.appointmentTime) return false
  return new Date(row.appointmentTime) < new Date()
}

const isClassExpired = (row) => {
  if (row.status !== 'booked') return false
  const checkTime = row.endTime || row.bookingTime
  if (!checkTime) return false
  return new Date(checkTime) < new Date()
}

const isPtCheckedIn = (row) => row.status === 'completed'

const getStatusType = (status) => {
  const map = { scheduled: 'success', completed: 'info', cancelled: 'danger', cancelled_by_trainer: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { scheduled: '待上课', completed: '已完成', cancelled: '已取消', cancelled_by_trainer: '教练请假' }
  return map[status] || '未知'
}

// ================================================================
// 日期滑动
// ================================================================

const generateDateList = () => {
  const list = []
  const today = new Date()
  const offset = dateOffset.value
  for (let i = 0; i < 21; i++) {
    const d = new Date(today)
    d.setDate(d.getDate() + offset + i)
    const weekdays = ['日', '一', '二', '三', '四', '五', '六']
    list.push({
      dateStr: d.toISOString().split('T')[0],
      weekday: weekdays[d.getDay()],
      day: d.getDate(),
      month: d.getMonth() + 1
    })
  }
  return list
}

const scrollDates = (direction) => {
  const newOffset = dateOffset.value + direction
  if (newOffset < 0 || newOffset > 13) return
  dateOffset.value = newOffset
  dateList.value = generateDateList()
}

const selectDate = (dateStr) => {
  if (selectedDateStr.value === dateStr) return
  selectedDateStr.value = dateStr
  selectedDate.value = new Date(dateStr + 'T00:00:00')
  loadAvailableTrainers()
}

const onCalendarSelect = (date) => {
  // element-plus 日历 @select 返回 Date 对象
  const d = date instanceof Date ? date : new Date(date)
  if (isNaN(d.getTime())) {
    ElMessage.error('日期无效')
    return
  }
  const dateStr = d.toISOString().split('T')[0]
  calendarSelectedDate.value = dateStr
  showAllFuture.value = false
  ptPageNum.value = 1
  classPageNum.value = 1
  // ====== 传入日期字符串 ======
  loadAllBookings(dateStr)
}

const resetToAll = () => {
  calendarSelectedDate.value = ''
  showAllFuture.value = true
  ptPageNum.value = 1
  classPageNum.value = 1
  // ====== 加载全部（不传日期） ======
  loadAllBookings()
}
// ================================================================
// 加载：教练数据
// ================================================================

const loadAvailableTrainers = async () => {
  if (!selectedDateStr.value) return
  availableTrainersLoading.value = true
  try {
    const res = await axios.get('/api/trainers/available', {
      params: { date: selectedDateStr.value }
    })
    availableTrainers.value = res.data || []
  } catch (error) {
    console.error('加载可预约教练失败', error)
    ElMessage.error('加载教练列表失败')
  } finally {
    availableTrainersLoading.value = false
  }
}

const loadAvailableSlots = async (trainerId) => {
  if (!selectedDateStr.value) return
  availableSlotsLoading.value = true
  availableSlots.value = []
  try {
    const res = await axios.get(`/api/trainers/${trainerId}/slots`, {
      params: { date: selectedDateStr.value }
    })
    availableSlots.value = res.data || []
    if (availableSlots.value.length > 0) {
      selectedSlot.value = availableSlots.value[0]
    }
  } catch (error) {
    console.error('加载可用时段失败', error)
    ElMessage.error('加载时段失败')
  } finally {
    availableSlotsLoading.value = false
  }
}

const loadAvailablePackages = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get('/api/private-packages/mine', {
      params: { memberId: Number(memberId) }
    })
    availablePackages.value = res.data || []
    if (availablePackages.value.length > 0) {
      payMethod.value = 'package_' + availablePackages.value[0].id
    } else {
      payMethod.value = 'single'
    }
  } catch (error) {
    console.error('加载课程包失败', error)
    availablePackages.value = []
    payMethod.value = 'single'
  }
}

// ================================================================
// 加载：预约列表（全量数据）
// ================================================================

// 加载私教预约
const loadPtBookings = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const params = {
      page: 1,
      size: 999,
      memberId: memberId
    }
    // ====== 防御性检查：确保 calendarSelectedDate 是字符串 ======
    if (!showAllFuture.value && calendarSelectedDate.value && typeof calendarSelectedDate.value === 'string') {
      params.startDate = calendarSelectedDate.value
      params.endDate = calendarSelectedDate.value
    }
    const res = await axios.get('/api/personal-trainings', { params })
    allPtBookings.value = res.data?.list || []
  } catch (error) {
    console.error('加载私教预约(全量)失败', error)
    allPtBookings.value = []
  }
}

// 加载团课预约
const loadClassBookings = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const params = {
      page: 1,
      size: 999,
      memberId: memberId
    }
    // ====== 防御性检查：确保 calendarSelectedDate 是字符串 ======
    if (!showAllFuture.value && calendarSelectedDate.value && typeof calendarSelectedDate.value === 'string') {
      params.startDate = calendarSelectedDate.value
      params.endDate = calendarSelectedDate.value
    }
    const res = await axios.get('/api/class-bookings', { params })
    allClassBookings.value = res.data?.list || []
  } catch (error) {
    console.error('加载团课预约(全量)失败', error)
    allClassBookings.value = []
  }
}

// ================================================================
// 红绿点更新
// ================================================================

const updateMarkers = () => {
  const green = []
  const red = []
  const now = new Date()

  allPtBookings.value.forEach(item => {
    if (!item.appointmentTime) return
    const dateStr = item.appointmentTime.split(' ')[0]
    if (item.status === 'completed') red.push(dateStr)
    else if (item.status === 'scheduled' && new Date(item.appointmentTime) > now) green.push(dateStr)
  })

  allClassBookings.value.forEach(item => {
    if (!item.bookingTime) return
    const dateStr = item.bookingTime.split(' ')[0]
    if (item.status === 'checked_in') red.push(dateStr)
    else if (item.status === 'booked') {
      const checkTime = item.endTime || item.bookingTime
      if (new Date(checkTime) > now) green.push(dateStr)
    }
  })

  greenDates.value = [...new Set(green)]
  redDates.value = [...new Set(red)]
}

// ================================================================
// 加载全部
// ================================================================

const loadAllBookings = async (date) => {
  loading.value = true
  try {
    // 如果传入了日期，则按日期加载；否则加载全部
    await Promise.all([loadPtBookings(date), loadClassBookings(date)])
    updateMarkers()
  } catch (error) {
    console.error('加载预约失败', error)
  } finally {
    loading.value = false
  }
}

// ================================================================
// 分页事件处理
// ================================================================

const onPtPageChange = (page) => {
  ptPageNum.value = page
  // 数据已全量加载，只需更新页码即可，计算属性会自动重新计算
}

const onPtPageSizeChange = (size) => {
  ptPageSize.value = size
  ptPageNum.value = 1
}

const onClassPageChange = (page) => {
  classPageNum.value = page
}

const onClassPageSizeChange = (size) => {
  classPageSize.value = size
  classPageNum.value = 1
}

// ================================================================
// Tab切换事件
// ================================================================

const onTabChange = () => {
  // 切换Tab不需要重新加载数据，因为数据已经存在
}

// ================================================================
// 预约、打卡、取消等操作
// ================================================================

const openBookingDialog = async (trainer) => {
  bookingTargetTrainer.value = trainer
  selectedSlot.value = ''
  await loadAvailableSlots(trainer.id)
  await loadAvailablePackages()
  // 新增：加载会员权益
  await loadFreePtRemaining()
  bookingDialogVisible.value = true
}
const loadFreePtRemaining = async () => {
  const memberId = localStorage.getItem('userId')
  if (!memberId) return
  try {
    const res = await axios.get(`/api/members/${memberId}/benefits`)
    freePtRemaining.value = res.data.freePtRemaining || 0
  } catch (error) {
    console.error('加载免费次数失败', error)
  }
}

const submitBooking = async () => {
  if (!selectedSlot.value) {
    ElMessage.warning('请选择上课时段')
    return
  }
  if (!payMethod.value) {
    ElMessage.warning('请选择支付方式')
    return
  }
  const memberId = localStorage.getItem('userId')
  if (!memberId) {
    ElMessage.warning('请先登录')
    return
  }
  let packageId = null
  if (payMethod.value.startsWith('package_')) {
    packageId = parseInt(payMethod.value.replace('package_', ''))
  }
  const appointmentTime = `${selectedDateStr.value} ${selectedSlot.value}:00`
  bookingSubmitLoading.value = true
  try {
    const res = await axios.post('/api/personal-trainings', {
      memberId: Number(memberId),
      trainerId: bookingTargetTrainer.value.id,
      appointmentTime: appointmentTime,
      durationMinutes: 60,
      packageId: packageId,
      useFree: useFree.value
    })
    if (res.data.success) {
      ElMessage.success(res.data.message || '🎉 预约成功！')
      bookingDialogVisible.value = false
      await loadAvailableTrainers()
      await loadAllBookings()
    } else {
      ElMessage.error(res.data.message || '预约失败')
    }
  } catch (error) {
    console.error('预约失败', error)
    ElMessage.error(error.response?.data?.message || '预约失败，请重试')
  } finally {
    bookingSubmitLoading.value = false
  }
}

const checkInPt = async (row) => {
  const now = new Date()
  const appointmentTime = new Date(row.appointmentTime)
  const diffMinutes = (now - appointmentTime) / (1000 * 60)
  if (diffMinutes < -15) {
    ElMessage.warning('距离上课还有超过15分钟，请稍后再打卡')
    return
  }
  if (diffMinutes > 60) {
    try {
      await ElMessageBox.confirm(
          `该课程已开始超过1小时（${Math.round(diffMinutes)}分钟前），确定要打卡吗？`,
          '确认打卡',
          { confirmButtonText: '确定打卡', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }

  // 弹出选择上课/下课
  try {
    await ElMessageBox.confirm(
        `请选择打卡类型：`,
        '私教打卡',
        {
          distinguishCancelAndClose: true,
          confirmButtonText: '上课打卡',
          cancelButtonText: '下课打卡',
          type: 'info'
        }
    )
    // 点击上课
    router.push({
      path: '/member/face-checkin',
      query: {
        ptId: row.id,
        action: 'start'
      }
    })
  } catch (action) {
    if (action === 'cancel') {
      // 点击下课
      try {
        await ElMessageBox.confirm('确定要结束本次私教课吗？', '下课确认', { type: 'warning' })
        router.push({
          path: '/member/face-checkin',
          query: {
            ptId: row.id,
            action: 'end'
          }
        })
      } catch (e) {}
    }
  }
}

const cancelPtBooking = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确定要取消 ${row.appointmentTime} 的私教课吗？`,
        '确认取消',
        { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' }
    )
    const res = await axios.patch(`/api/personal-trainings/${row.id}/status`, { status: 'cancelled' })
    // 检查后端返回结果
    if (res.data.success) {
      ElMessage.success('已取消预约')
    } else {
      ElMessage.error(res.data.message || '取消失败')
      return
    }
    // 强制刷新数据（清除缓存重新加载）
    await loadAllBookings(calendarSelectedDate.value || '')
    // 同时刷新日历标记
    await updateMarkers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '取消失败，请重试')
    }
  }
}

const cancelClassBooking = (row) => {
  cancelTarget.value = row
  cancelDialogVisible.value = true
}

const confirmCancel = async () => {
  if (!cancelTarget.value) return
  cancellingId.value = cancelTarget.value.id
  try {
    const res = await axios.delete(`/api/class-bookings/${cancelTarget.value.id}`)
    if (res.data.success) {
      ElMessage.success(res.data.message || '取消成功')
      cancelDialogVisible.value = false
      await loadAllBookings()
    } else {
      ElMessage.error(res.data.message || '取消失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '取消失败，请重试')
  } finally {
    cancellingId.value = null
    cancelTarget.value = null
  }
}

// ================================================================
// 初始化
// ================================================================

onMounted(() => {
  dateList.value = generateDateList()
  loadAvailableTrainers()
  loadAllBookings() // 加载全部
  calendarSelectedDate.value = ''
  showAllFuture.value = true
})
</script>

<style scoped>
.booking-center { padding: 4px; }
.date-scroll-wrapper { display: flex; align-items: center; gap: 8px; }
.date-scroll { display: flex; gap: 8px; overflow-x: auto; flex: 1; padding: 4px 0; scroll-behavior: smooth; }
.date-scroll::-webkit-scrollbar { display: none; }
.scroll-btn { flex-shrink: 0; font-size: 20px; font-weight: bold; color: #409EFF; }
.date-item { flex-shrink: 0; width: 56px; padding: 8px 4px; text-align: center; border-radius: 10px; cursor: pointer; border: 2px solid transparent; transition: all 0.2s; background: #f5f7fa; position: relative; }
.date-item:hover { background: #ecf5ff; }
.date-item.active { border-color: #409EFF; background: #ecf5ff; color: #409EFF; }
.date-weekday { font-size: 12px; color: #999; }
.date-item.active .date-weekday { color: #409EFF; }
.date-day { font-size: 18px; font-weight: bold; line-height: 1.4; }
.date-month { font-size: 10px; color: #999; }
.trainer-grid { display: flex; flex-wrap: wrap; gap: 16px; }
.trainer-card { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 10px; flex: 1 1 200px; min-width: 180px; background: #fff; transition: box-shadow 0.2s; }
.trainer-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.trainer-avatar { flex-shrink: 0; }
.trainer-info { flex: 1; }
.trainer-name { font-weight: bold; font-size: 15px; }
.trainer-specialty { font-size: 12px; color: #999; }
.trainer-price { font-size: 13px; color: #E6A23C; font-weight: bold; }
.calendar-cell { display: flex; flex-direction: column; align-items: center; }
.dots { display: flex; gap: 2px; line-height: 1; }
.dot { font-size: 10px; }
.dot.green { color: #67C23A; }
.dot.red { color: #F56C6C; }
.slot-group { display: flex; flex-wrap: wrap; gap: 6px; }
.slot-group :deep(.el-radio-button__inner) { padding: 6px 14px; font-size: 13px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>