<template>
  <EChart :option="option" :height="height" />
</template>

<script setup>
import { computed } from 'vue'
import EChart from './EChart.vue'

const props = defineProps({
  // dish-美食红黑榜（x=单价 y=评分）；stall-综合档口评价（x=学生评分 y=卫生分）
  mode: { type: String, default: 'dish' },
  data: { type: Array, default: () => [] },
  height: { type: String, default: '400px' },
  dark: { type: Boolean, default: false }
})

const LIGHT = {
  text: '#606266',
  muted: '#909399',
  grid: '#f0f1f3',
  markLine: '#c0c4cc',
  pointBorder: '#fff',
  areaOpacity: 0.05,
  tipBg: '#fff',
  tipBorder: '#eee',
  accent: '#ff6a00',
  dish: {
    value: { name: '红榜 · 便宜又好吃', color: '#f5b301', ripple: true },
    avoid: { name: '黑榜 · 贵又难吃', color: '#ef4444', ripple: true },
    new: { name: '新菜 · 样本少', color: '#6f8fb5', ripple: false },
    normal: { name: '一般', color: '#c9ccd4', ripple: false }
  },
  stall: {
    quality: { name: '红榜 · 口碑卫生双高', color: '#ef4444', ripple: true },
    bad: { name: '黑榜 · 口碑卫生双低', color: '#1f2937', ripple: true },
    normal: { name: '一般档口', color: '#f59e0b', ripple: false }
  },
  zone: { good: '#b58a00', bad: '#dc2626', quality: '#15803d' }
}

const DARK = {
  text: 'rgba(255, 238, 216, 0.86)',
  muted: 'rgba(255, 230, 205, 0.62)',
  grid: 'rgba(255, 225, 190, 0.1)',
  markLine: 'rgba(255, 225, 190, 0.28)',
  pointBorder: '#2b1404',
  areaOpacity: 0.055,
  tipBg: 'rgba(38, 20, 6, 0.96)',
  tipBorder: 'rgba(255, 200, 150, 0.35)',
  accent: '#ffd48e',
  dish: {
    value: { name: '红榜 · 便宜又好吃', color: '#ffd15c', ripple: true },
    avoid: { name: '黑榜 · 贵又难吃', color: '#f87171', ripple: true },
    new: { name: '新菜 · 样本少', color: '#7f9cc0', ripple: false },
    normal: { name: '一般', color: '#a98f78', ripple: false }
  },
  stall: {
    quality: { name: '红榜 · 口碑卫生双高', color: '#f87171', ripple: true },
    bad: { name: '黑榜 · 口碑卫生双低', color: '#cbb6a0', ripple: true },
    normal: { name: '一般档口', color: '#e08b3c', ripple: false }
  },
  zone: { good: '#ffd48e', bad: '#ff9b9b', quality: '#8fd9a8' }
}

const T = computed(() => (props.dark ? DARK : LIGHT))

const RIPPLE_ORDER = {
  dish: ['value', 'avoid', 'new', 'normal'],
  stall: ['quality', 'bad', 'normal']
}

const option = computed(() => (props.mode === 'dish' ? dishOption() : stallOption()))

function symbolSize(count, max) {
  if (!count || !max) return 9
  return 9 + Math.sqrt(count / max) * 21
}

// 标出红榜/黑榜：评分最高 3 个、最低 3 个，直接在图上报菜名，省得逐点去点
function pickHeroes(list, keyOf, minCount = 2) {
  const rated = (list || []).filter(d => Number(d.reviewCount) >= minCount)
  const sorted = [...rated].sort((a, b) => (Number(b.avgRating) || 0) - (Number(a.avgRating) || 0))
  const n = sorted.length >= 8 ? 3 : sorted.length >= 4 ? 2 : 0
  const map = new Map()
  if (!n) return map
  sorted.slice(0, n).forEach((d, i) => map.set(keyOf(d), { kind: 'top', rank: i + 1 }))
  sorted.slice(-n).reverse().forEach((d, i) => map.set(keyOf(d), { kind: 'bottom', rank: i + 1 }))
  return map
}

function heroLabel(name, rating, hero, relX = 0.5) {
  const good = hero.kind === 'top'
  // 贴边的点朝内标注，避免标签被画布裁掉；同簇三个标签按名次散开方向
  let side
  if (relX > 0.72) side = 'left'
  else if (relX < 0.2) side = 'right'
  else side = hero.rank === 1 ? (good ? 'top' : 'bottom') : (relX > 0.5 ? 'left' : 'right')
  return {
    show: true,
    position: side,
    distance: 8,
    formatter: `${name}  ${Number(rating || 0).toFixed(1)}`,
    color: good ? '#3a1e07' : '#fff1ec',
    backgroundColor: good ? 'rgba(255, 214, 146, 0.96)' : 'rgba(168, 52, 36, 0.96)',
    borderColor: good ? 'rgba(255, 240, 210, 0.7)' : 'rgba(255, 180, 165, 0.55)',
    borderWidth: 1,
    padding: [3, 7],
    borderRadius: 5,
    fontSize: 11,
    fontWeight: 600,
    shadowBlur: 8,
    shadowColor: 'rgba(0, 0, 0, 0.4)'
  }
}

function group(list) {
  const map = new Map()
  for (const item of list) {
    const key = item.tag || 'normal'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(item)
  }
  return map
}

function buildSeries(list, styles, order, toPoint, maxCount, t, heroes, extent) {
  const grouped = group(list)
  return order
    .filter(key => grouped.has(key))
    .map(key => {
      const style = styles[key]
      return {
        name: style.name,
        type: style.ripple ? 'effectScatter' : 'scatter',
        rippleEffect: style.ripple ? { brushType: 'stroke', scale: 3.2, period: 3.5 } : undefined,
        symbolSize: val => val[2],
        itemStyle: {
          color: style.color,
          opacity: style.ripple ? 0.92 : 0.5,
          borderColor: t.pointBorder,
          borderWidth: 1
        },
        emphasis: { focus: 'series', scale: 1.15 },
        labelLayout: { hideOverlap: false },
        z: style.ripple ? 10 : 3,
        data: grouped.get(key).map(item => {
          const pt = { ...toPoint(item, maxCount), raw: item }
          const hero = heroes && heroes.get(item.dishId ?? item.stallId)
          if (hero) {
            const relX = extent ? (pt.value[0] - extent.min) / (extent.range || 1) : 0.5
            pt.label = heroLabel(pt.name, item.avgRating, hero, relX)
            // 上榜点描一圈亮边，与普通点拉开层次
            pt.itemStyle = hero.kind === 'top'
              ? { borderColor: '#fff6e2', borderWidth: 2, shadowBlur: 14, shadowColor: 'rgba(255, 208, 138, 0.55)' }
              : { borderColor: '#ffe2da', borderWidth: 2, shadowBlur: 14, shadowColor: 'rgba(240, 110, 90, 0.55)' }
          }
          return pt
        })
      }
    })
}

function tooltipBase(t) {
  return {
    trigger: 'item',
    backgroundColor: t.tipBg,
    borderColor: t.tipBorder,
    borderWidth: 1,
    padding: [10, 14],
    textStyle: { color: props.dark ? '#e9e3da' : '#1a1d24', fontSize: 12 },
    extraCssText: 'border-radius:10px;box-shadow:0 10px 30px rgba(0,0,0,.35);'
  }
}

// 象限结论钉在图表两个角落（像素定位），不随数据范围漂移，也不会被坐标系裁掉
function quadrantText(t, good, bad) {
  return {
    elements: [
      {
        type: 'text', left: 66, top: 56, silent: true,
        style: {
          text: good, fill: t.zone.good, font: '600 12px "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif',
          // 描边让文字压到数据点上时依然可读
          stroke: 'rgba(36, 18, 4, 0.85)', lineWidth: 2
        }
      },
      {
        type: 'text', right: 22, bottom: 92, silent: true,
        style: {
          text: bad, fill: t.zone.bad, font: '600 12px "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif',
          stroke: 'rgba(36, 18, 4, 0.85)', lineWidth: 2
        }
      }
    ]
  }
}

function legendBase(t) {
  return {
    bottom: 0,
    itemWidth: 12,
    itemHeight: 12,
    itemGap: 18,
    textStyle: { fontSize: 12, color: t.text }
  }
}

function dishOption() {
  const t = T.value
  const list = props.data || []
  const prices = list.map(d => Number(d.price) || 0)
  const ratings = list.map(d => Number(d.avgRating) || 0)
  const counts = list.map(d => Number(d.reviewCount) || 0)
  const avgPrice = prices.length ? prices.reduce((a, b) => a + b, 0) / prices.length : 0
  const maxCount = Math.max(1, ...counts)
  const xMin = prices.length ? Math.min(...prices) : 0
  const xMax = prices.length ? Math.max(...prices) : 1
  const xRange = Math.max(1, xMax - xMin)
  const yMin = ratings.length ? Math.max(0, Math.floor(Math.min(...ratings) * 10) / 10 - 0.2) : 0

  const heroes = pickHeroes(list, d => d.dishId)
  const series = buildSeries(
    list,
    t.dish,
    RIPPLE_ORDER.dish,
    item => ({
      value: [Number(item.price) || 0, Number(item.avgRating) || 0, symbolSize(item.reviewCount, maxCount)],
      name: item.dishName
    }),
    maxCount,
    t,
    heroes,
    { min: xMin, range: xRange }
  )

  // 参考线：均价竖线 + 3.5 分横线，切出四个象限
  if (series.length) {
    series[0].markLine = {
      silent: true,
      symbol: 'none',
      lineStyle: { type: 'dashed', color: t.markLine, width: 1 },
      label: { fontSize: 11, color: t.muted },
      data: [
        { xAxis: Number(avgPrice.toFixed(1)), label: { formatter: `均价 ¥${avgPrice.toFixed(1)}`, position: 'end' } },
        { yAxis: 3.5, label: { formatter: '3.5 分', position: 'insideEndTop' } }
      ]
    }
    series[0].markArea = {
      silent: true,
      itemStyle: { opacity: t.areaOpacity },
      data: [
        [
          { xAxis: 'min', yAxis: 3.5, itemStyle: { color: t.zone.good, opacity: 0.1 } },
          { xAxis: avgPrice, yAxis: 'max' }
        ],
        [
          { xAxis: avgPrice, yAxis: 'min', itemStyle: { color: t.zone.bad, opacity: 0.15 } },
          { xAxis: 'max', yAxis: 3.5 }
        ]
      ]
    }
  }

  return {
    tooltip: {
      ...tooltipBase(t),
      formatter: p => {
        const d = p.data.raw || {}
        return `<div style="font-weight:600;margin-bottom:4px">${d.dishName || ''}</div>
          <div style="color:${t.muted};font-size:12px;margin-bottom:6px">${d.stallZone || ''} · ${d.windowNo || ''}号 ${d.stallName || ''}</div>
          <div>单价：<b style="color:${t.accent}">¥${Number(d.price || 0).toFixed(2)}</b></div>
          <div>评分：<b>${Number(d.avgRating || 0).toFixed(2)}</b> 分</div>
          <div>评价：<b>${d.reviewCount || 0}</b> 条 · 好评率 ${d.goodRate || 0}%</div>
          <div style="margin-top:6px"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};margin-right:6px"></span>${d.tagLabel || ''}</div>`
      }
    },
    legend: legendBase(t),
    graphic: quadrantText(t, '红榜区 · 便宜又好吃', '黑榜区 · 贵又难吃'),
    grid: { left: 58, right: 68, top: 40, bottom: 78 },
    xAxis: {
      type: 'value',
      name: '菜品单价（元）',
      nameLocation: 'middle',
      nameGap: 30,
      nameTextStyle: { fontSize: 12, color: t.muted },
      scale: true,
      axisLine: { lineStyle: { color: t.markLine } },
      axisLabel: { color: t.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: t.grid } }
    },
    yAxis: {
      type: 'value',
      name: '平均评分',
      nameTextStyle: { fontSize: 12, color: t.muted },
      min: v => Math.max(0, Math.floor(v.min * 10) / 10 - 0.2),
      max: 5,
      axisLine: { show: false },
      axisLabel: { color: t.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: t.grid } }
    },
    series
  }
}

function stallOption() {
  const t = T.value
  const list = props.data || []
  const ratingXs = list.map(s => Number(s.avgRating) || 0)
  const counts = list.map(s => Number(s.reviewCount) || 0)
  const maxCount = Math.max(1, ...counts)
  const xMin = ratingXs.length ? Math.min(...ratingXs) : 0
  const xMax = ratingXs.length ? Math.max(...ratingXs) : 1
  const xRange = Math.max(0.5, xMax - xMin)

  const heroes = pickHeroes(list, s => s.stallId)
  const series = buildSeries(
    list,
    t.stall,
    RIPPLE_ORDER.stall,
    item => ({
      value: [Number(item.avgRating) || 0, Number(item.hygieneScore) || 0, symbolSize(item.reviewCount, maxCount)],
      name: item.stallName
    }),
    maxCount,
    t,
    heroes,
    { min: xMin, range: xRange }
  )

  // 参考线：x=3.5 分口碑线、y=80 分卫生合格线，右上为优质区、左下为劣质区
  if (series.length) {
    series[0].markLine = {
      silent: true,
      symbol: 'none',
      lineStyle: { type: 'dashed', color: t.markLine, width: 1 },
      label: { fontSize: 11, color: t.muted },
      data: [
        { xAxis: 3.5, label: { formatter: '口碑线 3.5', position: 'end' } },
        { yAxis: 80, label: { formatter: '卫生合格线 80', position: 'insideEndTop' } }
      ]
    }
    series[0].markArea = {
      silent: true,
      itemStyle: { opacity: t.areaOpacity },
      data: [
        [
          { xAxis: 3.5, yAxis: 80, itemStyle: { color: t.zone.quality, opacity: 0.1 } },
          { xAxis: 'max', yAxis: 'max' }
        ],
        [
          { xAxis: 'min', yAxis: 'min', itemStyle: { color: t.zone.bad, opacity: 0.15 } },
          { xAxis: 3.5, yAxis: 80 }
        ]
      ]
    }
  }

  return {
    tooltip: {
      ...tooltipBase(t),
      formatter: p => {
        const d = p.data.raw || {}
        return `<div style="font-weight:600;margin-bottom:4px">${d.stallName || ''}</div>
          <div style="color:${t.muted};font-size:12px;margin-bottom:6px">${d.stallZone || ''} · ${d.windowNo || ''}号窗口 · ${d.location || ''} · 负责人 ${d.managerName || '待分配'}</div>
          <div>学生评分：<b style="color:${t.accent}">${Number(d.avgRating || 0).toFixed(2)}</b> 分</div>
          <div>卫生评分：<b>${Number(d.hygieneScore || 0).toFixed(1)}</b> 分</div>
          <div>评价数：<b>${d.reviewCount || 0}</b> 条 · 好评率 ${d.goodRate || 0}%</div>
          <div style="margin-top:6px"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};margin-right:6px"></span>${d.tagLabel || ''}</div>`
      }
    },
    legend: legendBase(t),
    graphic: quadrantText(t, '红榜区 · 口碑卫生双高', '黑榜区 · 口碑卫生双低'),
    grid: { left: 62, right: 68, top: 40, bottom: 78 },
    xAxis: {
      type: 'value',
      name: '学生评分',
      nameLocation: 'middle',
      nameGap: 30,
      nameTextStyle: { fontSize: 12, color: t.muted },
      min: v => Math.max(0, Math.floor(v.min * 10) / 10 - 0.2),
      max: 5,
      axisLine: { lineStyle: { color: t.markLine } },
      axisLabel: { color: t.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: t.grid } }
    },
    yAxis: {
      type: 'value',
      name: '学校评定卫生分',
      nameTextStyle: { fontSize: 12, color: t.muted },
      min: 60,
      max: 100,
      axisLine: { show: false },
      axisLabel: { color: t.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: t.grid } }
    },
    series
  }
}
</script>
