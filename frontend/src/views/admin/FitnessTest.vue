<template>
  <div>
    <!-- ====== 顶部统计卡片 ====== -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6" v-for="stat in stats" :key="stat.title">
        <el-card class="stat-card" shadow="hover" :body-style="{ padding: '16px 20px' }">
          <div class="stat-item">
            <div class="stat-icon" :style="{ background: stat.color }">
              <el-icon :size="24"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 筛选与搜索 ====== -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-select
              v-model="filterMemberId"
              placeholder="搜索会员姓名或手机号"
              clearable
              filterable
              remote
              :remote-method="searchMembers"
              :loading="memberSearchLoading"
              @change="onMemberChange"
              style="width:100%"
          >
            <el-option
                v-for="m in memberList"
                :key="m.id"
                :label="m.name + ' (' + m.phone + ')'"
                :value="m.id"
            />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-date-picker
              v-model="filterStartDate"
              type="date"
              placeholder="开始日期"
              value-format="YYYY-MM-DD"
              @change="loadTests"
              style="width:100%"
          />
        </el-col>
        <el-col :span="4">
          <el-date-picker
              v-model="filterEndDate"
              type="date"
              placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="loadTests"
              style="width:100%"
          />
        </el-col>
        <el-col :span="11" style="text-align: right">
          <el-button type="success" plain @click="exportTests">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 添加记录
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- ====== 体测列表 + 趋势图 ====== -->
    <el-row :gutter="20">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>
                📊 体测记录
                <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag>
              </span>
              <div>
                <el-button size="small" text @click="refresh">
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>
            </div>
          </template>

          <el-table
              :data="tableData"
              border
              style="width:100%"
              v-loading="loading"
              row-key="id"
              @row-click="onRowClick"
              highlight-current-row
          >
            <el-table-column prop="id" label="ID" width="60" align="center" />
            <el-table-column label="会员" width="120">
              <template #default="{ row }">
                <div style="display:flex;align-items:center;gap:8px">
                  <el-avatar :size="28" :style="{ backgroundColor: getAvatarColor(row.memberName) }">
                    {{ row.memberName?.charAt(0) || '?' }}
                  </el-avatar>
                  <span>{{ row.memberName || '未知' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="testDate" label="测试日期" width="120" />
            <el-table-column prop="weightKg" label="体重(kg)" width="90" align="center">
              <template #default="{ row }">
                <span style="font-weight:bold;color:#409EFF">{{ row.weightKg || '-' }}</span>
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
                <span style="font-weight:bold;color:#67C23A">{{ row.muscleMassKg || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remarks" label="备注" min-width="120">
              <template #default="{ row }">
                <span style="color:#666;font-size:13px">{{ row.remarks || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <el-button size="small" type="info" plain @click="showDetail(row)">
                  详情
                </el-button>
                <el-button size="small" type="primary" plain @click="handleEdit(row)">
                  编辑
                </el-button>
                <el-button size="small" type="danger" plain @click="handleDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
                v-model:current-page="pageNum"
                v-model:page-size="pageSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="total"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="loadTests"
                @current-change="loadTests"
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>📈 体测趋势图</span>
              <div style="display:flex;align-items:center;gap:8px;">
                <span style="font-size:13px;color:#999;">视图：</span>
                <el-radio-group v-model="viewMode" size="small" @change="renderChart">
                  <el-radio-button label="all">📊 综合</el-radio-button>
                  <el-radio-button label="weight">⚖️ 体重</el-radio-button>
                  <el-radio-button label="fat">📉 体脂率</el-radio-button>
                  <el-radio-button label="muscle">💪 肌肉量</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>
          <div ref="chartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ====== 添加/编辑对话框 ====== -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="520px"
        @close="resetForm"
        destroy-on-close
    >
      <el-form
          :model="formData"
          :rules="formRules"
          ref="formRef"
          label-width="100px"
      >
        <el-form-item label="会员" prop="memberId" required>
          <el-select v-model="formData.memberId" placeholder="请选择会员" style="width:100%">
            <el-option
                v-for="m in memberList"
                :key="m.id"
                :label="m.name"
                :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="测试日期" prop="testDate">
          <el-date-picker
              v-model="formData.testDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width:100%"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="体重(kg)" prop="weightKg">
              <el-input-number
                  v-model="formData.weightKg"
                  :min="20"
                  :max="300"
                  :precision="1"
                  :step="0.5"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="体脂率(%)" prop="bodyFatPercent">
              <el-input-number
                  v-model="formData.bodyFatPercent"
                  :min="3"
                  :max="50"
                  :precision="1"
                  :step="0.5"
                  style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="肌肉量(kg)" prop="muscleMassKg">
          <el-input-number
              v-model="formData.muscleMassKg"
              :min="10"
              :max="100"
              :precision="1"
              :step="0.5"
              style="width:100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remarks">
          <el-input
              v-model="formData.remarks"
              type="textarea"
              rows="2"
              placeholder="如：建议加强上肢训练"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTest" :loading="saving">
          {{ isEdit ? '更新' : '添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ====== 评分引擎弹窗 ====== -->
    <el-dialog v-model="scoreVisible" title="体测评分" width="500px" destroy-on-close>
      <div v-if="scoreData">
        <el-row :gutter="16" style="text-align:center;margin-bottom:20px">
          <el-col :span="8" v-for="s in scoreData.items" :key="s.label">
            <div :style="{ background: s.color, color: '#fff', borderRadius: '12px', padding: '16px 8px' }">
              <div style="font-size:28px;font-weight:bold">{{ s.score }}</div>
              <div style="font-size:13px;margin-top:4px">{{ s.label }}</div>
              <div style="font-size:11px;opacity:0.8">{{ s.value }}</div>
            </div>
          </el-col>
        </el-row>
        <el-divider />
        <div style="text-align:center">
          <div style="font-size:36px;font-weight:bold;color:#4A6CF7">{{ scoreData.total }}</div>
          <div style="color:#666;font-size:14px">综合评分</div>
          <div style="margin-top:8px;font-size:13px;color:#999">{{ scoreData.evaluation }}</div>
        </div>
      </div>
    </el-dialog>

    <!-- ====== AI报告弹窗 ====== -->
    <el-dialog v-model="reportVisible" title="AI健康报告" width="600px" destroy-on-close>
      <div v-if="reportLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>AI正在分析体测数据，请稍候...</p>
      </div>
      <div v-else-if="reportContent" style="white-space:pre-wrap;line-height:1.8;font-size:14px">
        <div style="background:#f0f7ff;border-radius:8px;padding:16px;margin-bottom:16px">
          <div style="font-weight:bold;margin-bottom:8px">{{ reportContent.memberName }} | 体测日期：{{ reportContent.testDate }}</div>
          <div>身高：{{ reportContent.height }}cm | 体重：{{ reportContent.weight }}kg | BMI：{{ reportContent.bmi }}</div>
        </div>
        <div v-if="reportContent.report">{{ reportContent.report }}</div>
        <div v-else style="color:#999;text-align:center;padding:40px">暂无AI报告</div>
      </div>
      <div v-else style="text-align:center;padding:40px;color:#999">暂无数据</div>
    </el-dialog>

    <!-- ====== 详情抽屉 ====== -->
    <el-drawer
        v-model="detailVisible"
        :title="detailTitle"
        direction="rtl"
        size="550px"
        destroy-on-close
    >
      <div v-if="detailData">
        <!-- 会员信息头部 -->
        <div style="text-align:center;margin-bottom:16px;">
          <el-avatar :size="64" :style="{ backgroundColor: getAvatarColor(detailData.memberName), fontSize: '28px' }">
            {{ detailData.memberName?.charAt(0) || '?' }}
          </el-avatar>
          <h2 style="margin-top:8px;margin-bottom:4px;">{{ detailData.memberName || '未知会员' }}</h2>
          <div style="color:#999;font-size:13px;">共 {{ memberAllTests.length }} 条体测记录</div>
        </div>

        <!-- 该会员所有体测记录表格 -->
        <el-table :data="memberAllTests" border size="small" max-height="400">
          <el-table-column prop="testDate" label="测试日期" width="110" />
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
      </div>
    </el-drawer>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Download, Plus } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

// =============================================
// 状态变量
// =============================================

// 统计数据（针对当前选中的会员）
const stats = ref([
  { title: '请选择会员', value: '👆', icon: 'Document', color: '#909399' },
  { title: '查看统计数据', value: '', icon: 'Scale', color: '#909399' },
  { title: '', value: '', icon: 'PieChart', color: '#909399' },
  { title: '', value: '', icon: 'Star', color: '#909399' }
])

// 列表数据
const tableData = ref([])
const memberList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(5)
const filterMemberId = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')
const loading = ref(false)

// 图表
const chartRef = ref(null)
let chartInstance = null
const viewMode = ref('all')  // 'all' | 'weight' | 'fat' | 'muscle'
const trendData = ref({ dates: [], weights: [], bodyFats: [], muscles: [] })

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('添加记录')
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const formData = ref({
  id: null,
  memberId: null,
  testDate: '',
  weightKg: 70,
  bodyFatPercent: 18,
  muscleMassKg: 30,
  remarks: ''
})

// 会员搜索
const memberSearchLoading = ref(false)

// 详情抽屉
const detailVisible = ref(false)
const detailTitle = ref('体测详情')
const detailData = ref(null)
const memberAllTests = ref([])

// =============================================
// 表单校验规则
// =============================================

const formRules = {
  memberId: [{ required: true, message: '请选择会员', trigger: 'change' }]
}

// =============================================
// 工具函数
// =============================================

const getAvatarColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  if (!name) return '#909399'
  const index = name.charCodeAt(0) % colors.length
  return colors[index]
}

const getFatTagType = (fat) => {
  if (!fat) return 'info'
  if (fat < 15) return 'success'
  if (fat < 25) return 'warning'
  return 'danger'
}

// =============================================
// 加载数据
// =============================================

// 加载会员列表（下拉框用）
const loadMembers = async () => {
  try {
    const res = await axios.get('/api/members/all')
    memberList.value = res.data || []
  } catch (error) {
    console.error('加载会员列表失败', error)
  }
}

// 搜索会员（远程）
const searchMembers = async (query) => {
  if (!query || query.length < 1) {
    await loadMembers()
    return
  }
  memberSearchLoading.value = true
  try {
    const res = await axios.get('/api/members', {
      params: {
        keyword: query,
        page: 1,
        size: 20
      }
    })
    memberList.value = res.data.list || []
  } catch (error) {
    console.error('搜索会员失败', error)
  } finally {
    memberSearchLoading.value = false
  }
}

// 加载会员统计数据（顶部卡片）
const loadMemberStats = async () => {
  if (!filterMemberId.value) {
    stats.value = [
      { title: '请选择会员', value: '👆', icon: 'Document', color: '#909399' },
      { title: '查看统计数据', value: '', icon: 'Scale', color: '#909399' },
      { title: '', value: '', icon: 'PieChart', color: '#909399' },
      { title: '', value: '', icon: 'Star', color: '#909399' }
    ]
    return
  }

  try {
    const res = await axios.get(`/api/fitness-tests/member/${filterMemberId.value}/stats`)
    const data = res.data
    stats.value = [
      { title: '总记录数', value: data.total || 0, icon: 'Document', color: '#409EFF' },
      { title: '最新体重', value: data.latestWeight ? data.latestWeight + ' kg' : '-', icon: 'Scale', color: '#67C23A' },
      { title: '最新体脂率', value: data.latestBodyFat ? data.latestBodyFat + '%' : '-', icon: 'PieChart', color: '#E6A23C' },
      { title: '最新肌肉量', value: data.latestMuscle ? data.latestMuscle + ' kg' : '-', icon: 'Star', color: '#F56C6C' }
    ]
  } catch (error) {
    console.error('加载会员统计数据失败', error)
  }
}

// 加载体测列表
const loadTests = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/fitness-tests', {
      params: {
        page: pageNum.value,
        size: pageSize.value,
        memberId: filterMemberId.value || undefined,
        startDate: filterStartDate.value || undefined,
        endDate: filterEndDate.value || undefined
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
    loadTrend()
  } catch (error) {
    console.error('加载体测记录失败', error)
    ElMessage.error('加载体测记录失败')
  } finally {
    loading.value = false
  }
}

// 加载趋势数据
const loadTrend = async () => {
  if (!filterMemberId.value) {
    if (chartInstance) {
      chartInstance.clear()
      chartInstance.setOption({
        title: {
          text: '👆 请选择会员查看趋势',
          left: 'center',
          top: 'center',
          textStyle: { color: '#999', fontSize: 16 }
        },
        xAxis: { show: false },
        yAxis: { show: false },
        series: []
      })
    }
    return
  }

  try {
    const res = await axios.get('/api/fitness-tests/trend', {
      params: { memberId: filterMemberId.value }
    })
    trendData.value = res.data
    renderChart()
  } catch (error) {
    console.error('加载趋势数据失败', error)
  }
}

// 加载会员所有体测记录（详情抽屉用）
const loadMemberAllTests = async (memberId) => {
  try {
    const res = await axios.get('/api/fitness-tests', {
      params: {
        memberId: memberId,
        page: 1,
        size: 100
      }
    })
    memberAllTests.value = res.data.list || []
  } catch (error) {
    console.error('加载会员体测记录失败', error)
  }
}

// =============================================
// 选择会员后的回调
// =============================================

const onMemberChange = () => {
  loadTests()
  loadMemberStats()
  loadTrend()
}

// =============================================
// 图表渲染
// =============================================

const renderChart = () => {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const data = trendData.value

  // 空数据判断
  if (!data || !data.dates || data.dates.length === 0) {
    chartInstance.clear()
    chartInstance.setOption({
      title: {
        text: '📊 暂无体测数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#999', fontSize: 16 }
      },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    })
    chartInstance.resize()
    return
  }

  // ====== 根据视图模式构建不同的 series ======
  let series = []
  let legendData = []
  let yAxisName = '数值'

  if (viewMode.value === 'all') {
    // 综合视图：三条线同时展示
    legendData = ['体重(kg)', '体脂率(%)', '肌肉量(kg)']
    series = [
      {
        name: '体重(kg)',
        data: data.weights || [],
        type: 'line',
        smooth: true,
        lineStyle: { color: '#409EFF', width: 2 },
        itemStyle: { color: '#409EFF' },
        symbol: 'circle',
        symbolSize: 6
      },
      {
        name: '体脂率(%)',
        data: data.bodyFats || [],
        type: 'line',
        smooth: true,
        lineStyle: { color: '#E6A23C', width: 2 },
        itemStyle: { color: '#E6A23C' },
        symbol: 'diamond',
        symbolSize: 6
      },
      {
        name: '肌肉量(kg)',
        data: data.muscles || [],
        type: 'line',
        smooth: true,
        lineStyle: { color: '#67C23A', width: 2 },
        itemStyle: { color: '#67C23A' },
        symbol: 'triangle',
        symbolSize: 6
      }
    ]
    yAxisName = '数值'
  } else {
    // 单指标视图：只显示一条线
    const map = {
      weight: { key: 'weights', name: '体重(kg)', color: '#409EFF', symbol: 'circle' },
      fat: { key: 'bodyFats', name: '体脂率(%)', color: '#E6A23C', symbol: 'diamond' },
      muscle: { key: 'muscles', name: '肌肉量(kg)', color: '#67C23A', symbol: 'triangle' }
    }
    const current = map[viewMode.value]
    legendData = [current.name]
    series = [{
      name: current.name,
      data: data[current.key] || [],
      type: 'line',
      smooth: true,
      lineStyle: { color: current.color, width: 3 },
      itemStyle: { color: current.color },
      symbol: current.symbol,
      symbolSize: 8,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: current.color + '44' },
          { offset: 1, color: current.color + '11' }
        ])
      }
    }]
    yAxisName = current.name
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        let html = `<strong>${params[0].axisValue}</strong><br/>`
        params.forEach(p => {
          html += `${p.marker} ${p.seriesName}: ${p.value ?? '-'}<br/>`
        })
        return html
      }
    },
    legend: {
      data: legendData,
      top: 0,
      right: 0
    },
    grid: {
      left: '10%',
      right: '8%',
      bottom: '15%',
      top: '15%'
    },
    xAxis: {
      type: 'category',
      data: data.dates || [],
      axisLabel: {
        rotate: 30,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: yAxisName,
      nameTextStyle: { fontSize: 12 }
    },
    series: series
  }

  chartInstance.setOption(option, true)
  chartInstance.resize()
}

// =============================================
// 表格交互
// =============================================

// 点击表格行 → 自动切换趋势图
// ====== 评分引擎 ======
const showScoring = async (row) => {
  scoreVisible.value = true
  try {
    const res = await axios.post("/api/assessment/score", {
      memberId: row.memberId,
      weightKg: row.weightKg || row.weight || 70,
      bodyFatPercent: row.bodyFatPercent || row.bodyFat || 20,
      muscleMassKg: row.muscleMassKg || 30
    })
    if (res.data.success) {
      const d = res.data.data
      const total = d.totalScore || 0
      let evaluation = "体测数据良好，请保持健康饮食和规律运动。"
      if (total < 60) {
        evaluation = "体测数据偏低，建议咨询专业教练制定训练计划。"
      } else if (total < 80) {
        evaluation = "体测数据有改善空间，建议加强有氧运动和力量训练。"
      }
      scoreData.value = {
        total: total,
        evaluation: evaluation,
        items: [
          { label: "BMI", value: d.bmi || "-", score: d.bmiScore || 0, color: (d.bmiScore || 0) >= 40 ? "#67C23A" : (d.bmiScore || 0) >= 25 ? "#E6A23C" : "#F56C6C" },
          { label: "体脂率", value: (d.fatScore || 0) + "%", score: d.fatScore || 0, color: (d.fatScore || 0) >= 40 ? "#67C23A" : (d.fatScore || 0) >= 25 ? "#E6A23C" : "#F56C6C" },
          { label: "综合得分", value: total, score: total, color: total >= 80 ? "#67C23A" : total >= 60 ? "#E6A23C" : "#F56C6C" }
        ]
      }
    }
  } catch (error) {
    console.error("评分失败", error)
  }
}

// ====== AI报告 ======
const generateAIReport = async (row) => {
  reportVisible.value = true
  reportLoading.value = true
  reportContent.value = null
  try {
    const res = await axios.get("/api/fitness-tests/" + row.id + "/ai-report")
    reportContent.value = res.data
  } catch (error) {
    reportContent.value = {
      memberName: row.memberName || "未知",
      testDate: row.testDate || "",
      height: row.height || "-",
      weight: row.weight || "-",
      bmi: row.bmi || "-",
      report: "暂无AI分析报告，请确认AI服务已启动。"
    }
  } finally {
    reportLoading.value = false
  }
}

const onRowClick = (row) => {
  if (filterMemberId.value !== row.memberId) {
    filterMemberId.value = row.memberId
    loadMemberStats()
    loadTrend()
    loadTests()
  }
}

// 显示详情（该会员所有体测记录）
const showDetail = (row) => {
  detailData.value = row
  detailTitle.value = `${row.memberName || '未知会员'} 的全部体测记录`
  detailVisible.value = true
  loadMemberAllTests(row.memberId)
}

// =============================================
// 刷新 & 重置
// =============================================

const refresh = () => {
  loadMemberStats()
  loadTests()
}

const resetSearch = () => {
  filterMemberId.value = ''
  filterStartDate.value = ''
  filterEndDate.value = ''
  pageNum.value = 1
  loadTests()
}

// =============================================
// 添加/编辑/删除
// =============================================

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加体测记录'
  formData.value = {
    id: null,
    memberId: null,
    testDate: '',
    weightKg: 70,
    bodyFatPercent: 18,
    muscleMassKg: 30,
    remarks: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑体测记录'
  formData.value = { ...row }
  dialogVisible.value = true
}

const saveTest = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await axios.put(`/api/fitness-tests/${formData.value.id}`, formData.value)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/api/fitness-tests', formData.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    refresh()
  } catch (error) {
    console.error('保存失败', error)
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

const resetForm = () => {
  formData.value = {
    id: null,
    memberId: null,
    testDate: '',
    weightKg: 70,
    bodyFatPercent: 18,
    muscleMassKg: 30,
    remarks: ''
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除该体测记录吗？此操作不可恢复。`,
      '危险操作',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await axios.delete(`/api/fitness-tests/${row.id}`)
      ElMessage.success('删除成功')
      refresh()
    } catch (error) {
      console.error('删除失败', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// =============================================
// 导出
// =============================================

const exportTests = async () => {
  try {
    const res = await axios.get('/api/fitness-tests/export', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `体测记录_${new Date().toLocaleDateString()}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// =============================================
// 窗口自适应
// =============================================

const handleResize = () => {
  chartInstance?.resize()
}

// =============================================
// 生命周期
// =============================================

onMounted(() => {
  loadMemberStats()
  loadTests()
  loadMembers()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped>
.stat-card {
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}
.stat-info {
  flex: 1;
}
.stat-number {
  font-size: 24px;
  font-weight: bold;
  line-height: 1.2;
}
.stat-label {
  color: #999;
  font-size: 14px;
}
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
.chart-container {
  width: 100%;
  height: 280px;
}
</style>