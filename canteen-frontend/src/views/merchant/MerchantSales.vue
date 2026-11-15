<template>
  <div class="ap-page sales-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">销售情况</h1>
        <div class="ap-sub">菜单、收入与客户为累计概览；订单与收入趋势按所选区间统计，收入口径为已支付订单金额</div>
      </div>
      <div class="ap-actions">
        <div class="ap-pill-group">
          <button
            v-for="opt in rangeOptions"
            :key="opt.value"
            class="ap-pill"
            :class="{ active: orderRange === opt.value }"
            @click="orderRange = opt.value; loadOrderAnalysis()"
          >{{ opt.label }}</button>
        </div>
      </div>
    </div>

    <!-- 顶部指标 -->
    <div class="ap-grid ap-grid--4 kpi-row">
      <div class="ap-kpi">
        <div class="ap-stat-label">菜单总数</div>
        <div class="ap-stat-value">{{ summary.dishCount ?? 0 }}<span class="ap-stat-unit">道</span></div>
        <div class="ap-stat-hint">本档口菜品</div>
      </div>
      <div class="ap-kpi">
        <div class="ap-stat-label">总收入</div>
        <div class="ap-stat-value"><small>¥</small>{{ fmt(summary.totalIncome) }}</div>
        <div class="ap-stat-hint">累计已支付订单金额</div>
      </div>
      <div class="ap-kpi">
        <div class="ap-stat-label">订单数</div>
        <div class="ap-stat-value">{{ summary.orderCount ?? 0 }}<span class="ap-stat-unit">单</span></div>
        <div class="ap-stat-hint">累计订单</div>
      </div>
      <div class="ap-kpi">
        <div class="ap-stat-label">客户数</div>
        <div class="ap-stat-value">{{ summary.customerCount ?? 0 }}<span class="ap-stat-unit">人</span></div>
        <div class="ap-stat-hint">下过单的同学</div>
      </div>
    </div>

    <!-- 订单总览 -->
    <div class="ap-card overview-card">
      <div class="ap-section-head">
        <div>
          <div class="ap-section-title">订单总览</div>
          <div class="ap-section-hint">所选区间内的订单状态 · 今日收入始终为当天</div>
        </div>
      </div>
      <div class="order-overview">
        <div class="order-stats">
          <div class="order-stat-item">
            <div class="order-stat-num pending ap-num">{{ orderAnalysis.pendingCount ?? 0 }}</div>
            <div class="order-stat-label">进行中</div>
          </div>
          <div class="order-stat-item">
            <div class="order-stat-num done ap-num">{{ orderAnalysis.doneCount ?? 0 }}</div>
            <div class="order-stat-label">已完成</div>
          </div>
          <div class="order-stat-item">
            <div class="order-stat-num canceled ap-num">{{ orderAnalysis.canceledCount ?? 0 }}</div>
            <div class="order-stat-label">已取消</div>
          </div>
          <div class="order-stat-item">
            <div class="order-stat-num income ap-num">¥{{ fmt(orderAnalysis.todayIncome) }}</div>
            <div class="order-stat-label">今日收入</div>
          </div>
        </div>
        <div class="order-pie">
          <EChart :option="orderPieOption" height="220px" />
        </div>
      </div>
    </div>

    <!-- 订单分析 + 收入分析 -->
    <div class="ap-grid ap-grid--2 chart-row">
      <div class="ap-card chart-card">
        <div class="chart-head">
          <div>
            <div class="ap-section-title">订单分析</div>
            <div class="ap-section-hint">所选区间内的订单量趋势</div>
          </div>
        </div>
        <EChart :option="orderTrendOption" height="300px" />
      </div>

      <div class="ap-card chart-card">
        <div class="chart-head">
          <div>
            <div class="ap-section-title">收入分析</div>
            <div class="ap-section-hint">所选区间内的已支付金额趋势</div>
          </div>
        </div>
        <EChart :option="incomeTrendOption" height="300px" />
      </div>
    </div>

    <!-- 每日收入 + 近 30 天菜品销量占比 -->
    <div class="ap-grid ap-grid--2 chart-row">
      <div class="ap-card chart-card">
        <div class="chart-head">
          <div>
            <div class="ap-section-title">每日收入</div>
            <div class="ap-section-hint">已支付订单金额</div>
          </div>
          <div class="ap-pill-group">
            <button
              v-for="d in [7, 30, 180]"
              :key="d"
              class="ap-pill"
              :class="{ active: dailyDays === d }"
              @click="dailyDays = d; loadDaily()"
            >近{{ d === 7 ? ' 7 天' : d === 30 ? '一月' : '半年' }}</button>
          </div>
        </div>
        <EChart :option="dailyOption" height="300px" />
      </div>

      <div class="ap-card chart-card">
        <div class="chart-head">
          <div>
            <div class="ap-section-title">近 30 天菜品销量占比</div>
            <div class="ap-section-hint">按销量份额排序</div>
          </div>
        </div>
        <EChart :option="monthlyOption" height="300px" />
      </div>
    </div>

    <!-- 热销 TOP3 / BOTTOM3 -->
    <div class="ap-grid ap-grid--2 chart-row">
      <div class="ap-card chart-card">
        <div class="ap-section-head">
          <div class="ap-section-title">卖得好的菜</div>
          <div class="ap-section-hint">TOP 3 · 近 30 天</div>
        </div>
        <EChart :option="bestOption" height="280px" />
      </div>
      <div class="ap-card chart-card">
        <div class="ap-section-head">
          <div class="ap-section-title">卖得较少的菜</div>
          <div class="ap-section-hint">BOTTOM 3 · 近 30 天</div>
        </div>
        <EChart :option="worstOption" height="280px" />
      </div>
    </div>

    <!-- 近 7 天各菜销量 -->
    <div class="ap-card chart-card">
      <div class="ap-section-head">
        <div class="ap-section-title">近 7 天各菜销量</div>
        <div class="ap-section-hint">单位：份</div>
      </div>
      <EChart :option="weeklyOption" height="300px" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted , onActivated } from 'vue'
import EChart from '@/components/EChart.vue'
import { getSalesOverview, getSalesSummary, getDailyIncome, getOrderAnalysis } from '@/api/sales'

const weekly = ref([])
const monthly = ref([])
const daily = ref([])
const dailyDays = ref(7)
const orderRange = ref('day')
const summary = reactive({ dishCount: 0, totalIncome: 0, orderCount: 0, customerCount: 0 })
const orderAnalysis = reactive({ pendingCount: 0, doneCount: 0, canceledCount: 0, todayOrderCount: 0, todayIncome: 0, orderTrend: [], incomeTrend: [] })

const ORANGE = '#ff7a1a'
const GRAPHITE = '#1d1d1f'
const PIE_COLORS = ['#ff7a1a', '#1d1d1f', '#c7c7cc', '#e5e5ea', '#8e8e93', '#f5a623']

const rangeOptions = [
  { label: '每天', value: 'day' },
  { label: '每周', value: 'week' },
  { label: '每月', value: 'month' }
]

function fmt(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

const weeklyOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 20, bottom: 60 },
  xAxis: { type: 'category', data: weekly.value.map(d => d.dishName), axisLabel: { rotate: 30 } },
  yAxis: { type: 'value', name: '份' },
  series: [{
    type: 'bar',
    data: weekly.value.map(d => d.quantity),
    itemStyle: { color: ORANGE, borderRadius: [4, 4, 0, 0] },
    barMaxWidth: 32
  }]
}))

const monthlyOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 份（{d}%）' },
  legend: { orient: 'vertical', right: 10, top: 'center' },
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    center: ['38%', '50%'],
    data: monthly.value.map((d, i) => ({ name: d.dishName, value: d.quantity, itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] } })),
    itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    label: { formatter: '{b}\n{c}份' }
  }]
}))

const dailyOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: daily.value.map(d => d.saleDate), boundaryGap: false },
  yAxis: { type: 'value', name: '元' },
  series: [{
    type: 'line',
    smooth: true,
    data: daily.value.map(d => Number(d.amount)),
    itemStyle: { color: ORANGE },
    lineStyle: { width: 2.5 },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: 'rgba(255,122,26,0.18)' },
          { offset: 1, color: 'rgba(255,122,26,0.01)' }
        ]
      }
    }
  }]
}))

// 订单总览环形图
const orderPieOption = computed(() => {
  const data = [
    { name: '进行中', value: orderAnalysis.pendingCount ?? 0, itemStyle: { color: '#f5a623' } },
    { name: '已完成', value: orderAnalysis.doneCount ?? 0, itemStyle: { color: '#4c9a63' } },
    { name: '已取消', value: orderAnalysis.canceledCount ?? 0, itemStyle: { color: '#d2d2d7' } }
  ]
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 单（{d}%）' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['50%', '44%'],
      data,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}单' }
    }]
  }
})

const orderTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: (orderAnalysis.orderTrend || []).map(d => d.date), boundaryGap: false },
  yAxis: { type: 'value', name: '单' },
  series: [{
    type: 'line',
    smooth: true,
    data: (orderAnalysis.orderTrend || []).map(d => d.count),
    itemStyle: { color: GRAPHITE },
    lineStyle: { width: 2 },
    symbolSize: 6,
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: 'rgba(29,29,31,0.10)' },
          { offset: 1, color: 'rgba(29,29,31,0.01)' }
        ]
      }
    }
  }]
}))

const incomeTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: (orderAnalysis.incomeTrend || []).map(d => d.date), boundaryGap: false },
  yAxis: { type: 'value', name: '元' },
  series: [{
    type: 'bar',
    data: (orderAnalysis.incomeTrend || []).map(d => Number(d.amount)),
    itemStyle: { color: ORANGE, borderRadius: [4, 4, 0, 0] },
    barMaxWidth: 26
  }]
}))

function rankOption(list, reverse) {
  const data = reverse ? [...list].reverse() : list
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 90, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'value', name: '份' },
    yAxis: { type: 'category', data: data.map(d => d.dishName) },
    series: [{
      type: 'bar',
      data: data.map(d => d.quantity),
      itemStyle: { color: reverse ? '#c7c7cc' : ORANGE, borderRadius: [0, 4, 4, 0] },
      barMaxWidth: 20
    }]
  }
}

const bestOption = computed(() => rankOption(monthly.value.slice(0, 3), false))
const worstOption = computed(() => rankOption(monthly.value.slice(-3), true))

async function loadData() {
  const [ov, sm] = await Promise.all([getSalesOverview(), getSalesSummary()])
  weekly.value = ov.data.weekly || []
  monthly.value = ov.data.monthly || []
  Object.assign(summary, sm.data || {})
  loadDaily()
  loadOrderAnalysis()
}

async function loadDaily() {
  const res = await getDailyIncome(dailyDays.value)
  daily.value = res.data || []
}

async function loadOrderAnalysis() {
  try {
    const daysMap = { day: 7, week: 30, month: 180 }
    const res = await getOrderAnalysis(daysMap[orderRange.value] || 7)
    Object.assign(orderAnalysis, res.data || {})
  } catch {
    /* 忽略 */
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.sales-page { width: 100%; }

.kpi-row { margin-bottom: 16px; }

.overview-card { margin-bottom: 16px; }

.order-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(260px, 0.9fr);
  gap: 24px;
  align-items: center;
}

.order-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.order-stat-item {
  padding: 16px 18px;
  background: var(--surface-2);
  border-radius: 14px;
}

.order-stat-num {
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--text-1);
}

.order-stat-num.pending { color: var(--brand-dark); }
.order-stat-num.done { color: #1d9d55; }
.order-stat-num.canceled { color: var(--text-3); }
.order-stat-num.income { font-size: 22px; }

.order-stat-label { font-size: 12px; color: var(--text-3); margin-top: 6px; }

.chart-row { margin-bottom: 16px; }

.chart-card { min-width: 0; }

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px 16px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.chart-card .ap-section-head { margin-bottom: 10px; }

@media (max-width: 1100px) {
  .order-overview { grid-template-columns: 1fr; }
  .order-pie { max-width: 420px; width: 100%; margin: 0 auto; }
}

@media (max-width: 960px) {
  .chart-row { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .kpi-row { grid-template-columns: 1fr; }
}
</style>
