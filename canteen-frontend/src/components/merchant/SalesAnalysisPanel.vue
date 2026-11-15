<template>
  <div class="ap-card sales-panel">
    <div class="sales-head">
      <div>
        <div class="ap-section-title">销售分析</div>
        <div class="ap-section-hint">选好区间后点「让 AI 分析」；图表和表格数字全部来自真实订单</div>
      </div>
      <div class="head-actions">
        <div class="ap-pill-group">
          <button
            v-for="d in rangeOptions"
            :key="d"
            class="ap-pill"
            :class="{ active: days === d }"
            @click="pickRange(d)"
          >近 {{ d }} 天</button>
        </div>
        <button class="ap-btn ap-btn--brand" :disabled="loading" @click="load">
          {{ loading ? 'AI 分析中…' : (data ? '重新分析' : '让 AI 分析') }}
        </button>
      </div>
    </div>

    <div v-if="!data && !loading" class="ap-empty sales-empty">
      <div class="ap-empty-mark"><el-icon :size="20"><TrendCharts /></el-icon></div>
      <div class="ap-empty-title">让 AI 看看你最近的生意</div>
      <div>AI 会挑选合适的统计图、写经营解读，并生成可下载的真实数据表格</div>
    </div>

    <div v-if="loading" class="sales-loading">
      <div class="loading-bar"><i></i></div>
      <div class="loading-title">AI 正在读取真实销售数据并生成图表…</div>
      <div class="loading-sub">通常需要 3–10 秒</div>
    </div>

    <div v-if="error" class="sales-error">{{ error }}</div>

    <template v-if="data && !loading">
      <div class="summary-block">
        <div class="summary-top">
          <div>
            <div class="summary-title">{{ data.title || '销售分析' }}</div>
            <div class="summary-sub">近 {{ data.days }} 天 · {{ data.model }} · {{ shortTime(data.generatedAt) }}</div>
          </div>
          <span class="ap-tag ap-tag--brand">AI</span>
        </div>
        <p class="summary-text">{{ data.summary }}</p>
        <ul v-if="data.insights?.length" class="insight-list">
          <li v-for="(t, i) in data.insights" :key="i">{{ t }}</li>
        </ul>
      </div>

      <div class="chart-grid">
        <div v-for="(c, i) in data.charts" :key="i" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">{{ c.title }}</div>
              <div class="chart-sub">{{ metricLabel(c.metric) }}<span v-if="c.unit"> · 单位：{{ c.unit }}</span></div>
            </div>
            <button class="ap-link chart-download" @click="downloadChart(c, i)">下载图片</button>
          </div>
          <EChart :ref="el => setChartRef(el, i)" :option="chartOption(c)" height="260px" />
        </div>
      </div>

      <div class="table-block">
        <div class="table-head">
          <div>
            <div class="ap-section-title">真实数据明细</div>
            <div class="table-sub">
              {{ tableTab === 'dish'
                ? '菜品销售额为原价合计，可能因优惠与订单实收略有差异'
                : '可直接下载 CSV，用 Excel 打开' }}
            </div>
          </div>
          <div class="table-actions">
            <div class="ap-pill-group">
              <button class="ap-pill" :class="{ active: tableTab === 'daily' }" @click="tableTab = 'daily'">按日</button>
              <button class="ap-pill" :class="{ active: tableTab === 'dish' }" @click="tableTab = 'dish'">按菜品</button>
            </div>
            <button class="ap-btn ap-btn--light" @click="downloadTable">
              <el-icon><Download /></el-icon>
              下载 CSV
            </button>
          </div>
        </div>

        <el-table v-if="currentTable" :data="currentTable.rows" size="small" class="sales-table" max-height="440">
          <el-table-column
            v-for="(col, ci) in currentTable.columns"
            :key="ci"
            :label="col"
            min-width="110"
            :align="ci === 0 ? 'left' : 'right'"
          >
            <template #default="{ row }"><span :class="{ 'ap-num': ci > 0 }">{{ row[ci] }}</span></template>
          </el-table-column>
        </el-table>
        <div v-if="currentTable && !currentTable.rows.length" class="ap-empty table-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><Document /></el-icon></div>
          <div class="ap-empty-title">这个区间还没有数据</div>
          <div>换个区间或先产生一些订单再来</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import EChart from '@/components/EChart.vue'
import { getAiSalesAnalysis } from '@/api/sales'

const rangeOptions = [7, 30, 90]
const days = ref(7)
const loading = ref(false)
const error = ref('')
const data = ref(null)
const tableTab = ref('daily')
const chartRefs = {}

function setChartRef(el, i) {
  chartRefs[i] = el
}

function pickRange(d) {
  days.value = d
  // 区间变了，旧结果先收起，等商家再点「让 AI 分析」
  data.value = null
  error.value = ''
}

function shortTime(t) {
  return t ? String(t).slice(5, 16) : ''
}

function metricLabel(metric) {
  return {
    dailyRevenue: '每日收入',
    dailyOrders: '每日订单数',
    topDishes: '热销菜品',
    orderStatus: '订单状态'
  }[metric] || metric
}

const currentTable = computed(() => {
  if (!data.value) return null
  return tableTab.value === 'dish' ? data.value.dishTable : data.value.table
})

const PALETTE = ['#ff7a1a', '#1d1d1f', '#8e8e93', '#d2d2d7', '#f5a623']

function chartOption(c) {
  const labels = c.labels || []
  const seriesList = c.series || []
  if (c.type === 'pie') {
    const s = seriesList[0] || { data: [] }
    return {
      color: PALETTE,
      tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ' + (c.unit || '') + '（{d}%）' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#6e6e73', fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { color: '#6e6e73', fontSize: 11, formatter: '{b}\n{d}%' },
        data: labels.map((l, i) => ({ name: l, value: Number(s.data?.[i] || 0) }))
      }]
    }
  }
  const isLine = c.type === 'line'
  return {
    color: PALETTE,
    grid: { left: 10, right: 16, top: 24, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(29,29,31,0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 },
      valueFormatter: v => `${v} ${c.unit || ''}`
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#a1a1a6', fontSize: 11, interval: 'auto' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f0f2' } },
      axisLabel: { color: '#a1a1a6', fontSize: 11 }
    },
    series: seriesList.map(s => ({
      name: s.name,
      type: c.type,
      data: (s.data || []).map(Number),
      smooth: isLine,
      showSymbol: false,
      barMaxWidth: 34,
      itemStyle: { borderRadius: isLine ? 0 : [6, 6, 0, 0] },
      lineStyle: { width: 2.5 },
      areaStyle: isLine ? {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(255,106,0,0.20)' },
            { offset: 1, color: 'rgba(255,106,0,0)' }
          ]
        }
      } : undefined
    }))
  }
}

function downloadChart(c, i) {
  const url = chartRefs[i]?.getDataURL?.()
  if (!url) return
  const a = document.createElement('a')
  a.href = url
  a.download = `${c.title || '销售图表'}.png`
  a.click()
}

function downloadTable() {
  const t = currentTable.value
  if (!t || !t.rows?.length) return
  const esc = v => `"${String(v ?? '').replace(/"/g, '""')}"`
  const csv = [t.columns.map(esc).join(','), ...t.rows.map(r => r.map(esc).join(','))].join('\r\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = t.filename || '销售数据.csv'
  a.click()
  URL.revokeObjectURL(url)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getAiSalesAnalysis(days.value)
    data.value = res.data || null
    tableTab.value = 'daily'
  } catch {
    data.value = null
    error.value = 'AI 分析失败，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.sales-panel { padding: 24px; }

.sales-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 16px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  flex-wrap: wrap;
}

.head-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.sales-empty { min-height: 360px; }

.sales-loading {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.loading-bar {
  width: 180px;
  height: 3px;
  border-radius: 999px;
  background: #ececef;
  overflow: hidden;
  margin-bottom: 6px;
}

.loading-bar i {
  display: block;
  height: 100%;
  width: 40%;
  border-radius: 999px;
  background: var(--brand);
  animation: sales-slide 1.4s var(--ease-apple) infinite;
}

@keyframes sales-slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(450%); }
}

.loading-title { font-size: 14px; font-weight: 600; color: var(--text-1); }
.loading-sub { font-size: 12px; color: var(--text-3); }

.sales-error {
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff5e6;
  color: #a96100;
  font-size: 13px;
}

/* AI 解读：纯排版区块，不再套灰底卡片 */
.summary-block { padding-bottom: 20px; margin-bottom: 20px; border-bottom: 1px solid rgba(0, 0, 0, 0.05); }

.summary-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.summary-title { font-size: 17px; font-weight: 650; letter-spacing: -0.01em; color: var(--text-1); }
.summary-sub { margin-top: 5px; font-size: 12px; color: var(--text-3); }

.summary-text { margin-top: 12px; font-size: 15px; line-height: 1.85; color: var(--text-1); max-width: 68ch; }

.insight-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  max-width: 68ch;
}
.insight-list li {
  padding: 10px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-2);
  border-top: 1px solid rgba(0, 0, 0, 0.045);
}
.insight-list li:first-child { border-top: none; }

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 24px;
  margin-bottom: 20px;
}

.chart-card { min-width: 0; }

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px 12px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.chart-title { font-size: 14px; font-weight: 650; color: var(--text-1); }
.chart-sub { margin-top: 3px; font-size: 12px; color: var(--text-3); }

.chart-download { font-size: 12.5px; flex-shrink: 0; }

.table-block { padding-top: 4px; }

.table-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 16px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  flex-wrap: wrap;
}
.table-sub { margin-top: 4px; font-size: 12px; color: var(--text-3); }
.table-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.sales-table :deep(.el-table__header th.el-table__cell) {
  background: #fafafa;
  color: var(--text-3);
  font-weight: 600;
}

.sales-table :deep(.el-table__cell) {
  border-bottom-color: rgba(0, 0, 0, 0.045);
}

.sales-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #fafafa;
}

.table-empty { padding: 32px 0; }

@media (max-width: 1100px) {
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
