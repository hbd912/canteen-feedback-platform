<template>
  <div class="agent-data" v-if="charts?.length || tables?.length">
    <div class="ad-head">
      <div class="ad-title">销售数据<span v-if="days" class="ad-meta">近 {{ days }} 天 · 来自真实订单</span></div>
    </div>

    <div v-if="charts?.length" class="ad-charts">
      <div v-for="(c, i) in charts" :key="'c' + i" class="ad-chart">
        <div class="ad-chart-head">
          <div>
            <div class="ad-chart-title">{{ c.title }}</div>
            <div class="ad-chart-sub">{{ metricLabel(c.metric) }}<span v-if="c.unit"> · {{ c.unit }}</span></div>
          </div>
        </div>
        <EChart :ref="el => setChartRef(el, i)" :option="chartOption(c)" height="200px" />
      </div>
    </div>

    <div v-if="tables?.length" class="ad-tables">
      <div v-for="(t, ti) in tables" :key="'t' + ti" class="ad-table">
        <div class="ad-table-head">
          <div class="ad-table-name">{{ tableTitle(t) }}</div>
        </div>
        <el-table v-if="t.rows?.length" :data="t.rows" size="small" max-height="320" class="ad-table-el">
          <el-table-column
            v-for="col in t.columns"
            :key="col"
            :label="col"
            min-width="110"
          >
            <template #default="{ row }">{{ row[arrayIndex(t.columns, col)] }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="ad-empty">这个区间还没有数据</div>
      </div>
    </div>

    <div class="ad-foot">
      <el-dropdown trigger="click" @command="handleDownload">
        <button class="ad-dl" type="button">
          <el-icon :size="13"><Download /></el-icon>
          下载
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="(c, i) in charts"
              :key="'dc' + i"
              :command="'chart:' + i"
            >{{ c.title || '图表' }}（PNG）</el-dropdown-item>
            <el-dropdown-item
              v-for="(t, ti) in tables"
              :key="'dt' + ti"
              :command="'table:' + ti"
            >{{ tableTitle(t) }}（CSV）</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import EChart from '@/components/EChart.vue'

const props = defineProps({
  charts: { type: Array, default: () => [] },
  tables: { type: Array, default: () => [] },
  days: { type: Number, default: 0 }
})

const chartRefs = {}
function setChartRef(el, i) { chartRefs[i] = el }

function arrayIndex(arr, v) {
  const i = arr.indexOf(v)
  return i < 0 ? 0 : i
}

function metricLabel(metric) {
  return {
    dailyRevenue: '每日收入',
    dailyOrders: '每日订单数',
    topDishes: '热销菜品',
    orderStatus: '订单状态'
  }[metric] || metric
}

/** 把 sales_7d.csv 这类技术文件名换成可读标题 */
function tableTitle(t) {
  const raw = String(t?.filename || '')
  const noExt = raw.replace(/\.(csv|xlsx?)$/i, '')
  const m = noExt.match(/^(.+?)[_-](\d+d)$/)
  if (m) return `${m[1]} · 近 ${m[2].replace('d', ' 天')}`
  return noExt || '销售数据'
}

const PALETTE = ['#ff7a1a', '#1d1d1f', '#86868b', '#d2d2d7', '#b8b8bd']

function chartOption(c) {
  const labels = c.labels || []
  const seriesList = c.series || []
  if (c.type === 'pie') {
    const s = seriesList[0] || { data: [] }
    return {
      color: PALETTE,
      tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ' + (c.unit || '') + '（{d}%）' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#6e6e73', fontSize: 11 } },
      series: [{
        type: 'pie',
        radius: ['42%', '66%'],
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
    tooltip: { trigger: 'axis', borderWidth: 0, textStyle: { color: '#fff', fontSize: 12 },
      valueFormatter: v => `${v} ${c.unit || ''}` },
    xAxis: { type: 'category', data: labels, axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#a1a1a6', fontSize: 11, interval: 'auto' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f0f2' } },
      axisLabel: { color: '#a1a1a6', fontSize: 11 } },
    series: seriesList.map(s => ({
      name: s.name,
      type: c.type,
      data: (s.data || []).map(Number),
      smooth: isLine,
      showSymbol: false,
      barMaxWidth: 30,
      itemStyle: { borderRadius: isLine ? 0 : [6, 6, 0, 0] },
      lineStyle: { width: 2.5 },
      areaStyle: isLine ? {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(255,122,26,0.08)' }, { offset: 1, color: 'rgba(255,122,26,0)' }] }
      } : undefined
    }))
  }
}

function dlChart(c, i) {
  const url = chartRefs[i]?.getDataURL?.()
  if (!url) return
  const a = document.createElement('a')
  a.href = url
  a.download = `${c.title || '销售图表'}.png`
  a.click()
}

function dlTable(t) {
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

function handleDownload(cmd) {
  const [kind, idx] = String(cmd).split(':')
  if (kind === 'chart') {
    dlChart(props.charts[Number(idx)], Number(idx))
  } else if (kind === 'table') {
    dlTable(props.tables[Number(idx)])
  }
}
</script>

<style scoped>
.agent-data { margin-top: 10px; }

.ad-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 4px;
}
.ad-title {
  font-size: 13px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.ad-meta { font-size: 11px; font-weight: 400; color: var(--text-3); }

.ad-charts {
  display: grid;
  grid-template-columns: 1fr;
  gap: 4px;
}
.ad-chart { padding-top: 4px; }
.ad-chart-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 2px; }
.ad-chart-title { font-size: 12.5px; font-weight: 650; color: var(--text-1); }
.ad-chart-sub { margin-top: 2px; font-size: 11px; color: var(--text-3); }

.ad-tables { display: flex; flex-direction: column; gap: 4px; }
.ad-table { padding-top: 6px; border-top: 1px solid rgba(0, 0, 0, 0.05); }
.ad-table-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.ad-table-name { font-size: 12.5px; font-weight: 650; letter-spacing: -0.01em; color: var(--text-1); word-break: break-all; }

.ad-table-el { font-size: 12.5px; }
.ad-empty { font-size: 12px; color: var(--text-3); padding: 8px 0; }

.ad-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  margin-top: 8px;
}
.ad-dl {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: #f5f5f7;
  color: var(--text-2);
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.ad-dl:hover { background: #ececee; color: var(--text-1); }
</style>
