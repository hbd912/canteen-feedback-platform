<template>
  <div class="screen" :class="{ embedded: !screenMode }">
    <header class="topbar">
      <div class="tb-left">
        <span class="live-dot"></span>
        <span class="tb-date">{{ dateText }}</span>
        <span class="tb-week">{{ weekText }}</span>
      </div>

      <div class="tb-center">
        <div class="title-box">
          <h1>数据看板</h1>
          <p>美食地图 · 全校餐饮实时数据</p>
        </div>
      </div>

      <div class="tb-right">
        <div class="clock">
          <span class="clock-time">{{ clockText }}</span>
        </div>
        <div class="seg">
          <button v-for="d in RANGES" :key="d" :class="{ on: range === d }" @click="changeRange(d)">
            近{{ d }}天
          </button>
        </div>
        <button class="pill" :class="{ on: autoRefresh }" @click="autoRefresh = !autoRefresh">
          <i class="pill-dot"></i>自动刷新
        </button>
        <button class="pill exit" @click="toggleScreen">
          <el-icon><FullScreen v-if="screenMode" /><Aim v-else /></el-icon>
          {{ screenMode ? '退出大屏' : '大屏模式' }}
        </button>
      </div>
    </header>

    <main class="body">
      <!-- 左列 -->
      <section class="col">
        <div class="panel">
          <header class="p-head">
            <span class="p-ico"></span>
            <h2>最受欢迎档口 TOP3</h2>
            <span class="p-push"></span>
            <span class="p-unit">热度分</span>
          </header>
          <div class="p-body">
            <div class="donut-wrap"><EChart :option="topOption" height="100%" /></div>
            <div class="pop-list">
              <div v-for="(s, i) in topList" :key="s.stallId" class="pop-row">
                <span class="pop-no" :class="'n' + (i + 1)">No.{{ i + 1 }}</span>
                <span class="pop-name">{{ s.stallName }}</span>
                <span class="pop-score">{{ s.popularityScore }}<em>分</em></span>
                <div class="pop-bar"><i :class="{ first: i === 0 }" :style="{ width: pct(s.popularityScore) + '%' }"></i></div>
              </div>
              <p v-if="!topList.length" class="empty">暂无热度数据</p>
            </div>
          </div>
        </div>

        <div class="panel">
          <header class="p-head">
            <span class="p-ico"></span>
            <h2>档口口碑红黑榜</h2>
            <span class="p-push"></span>
            <span class="p-unit">学生评分 · 卫生分</span>
          </header>
          <div class="p-body split-body">
            <div class="rank-group">
              <div class="rg-title good">优质档口 TOP3</div>
              <div v-for="(s, i) in qualityTop" :key="s.stallId" class="rk-row">
                <span class="rk-no" :class="'n' + (i + 1)">{{ i + 1 }}</span>
                <div class="rk-main">
                  <div class="rk-name">{{ s.stallName }}<em>{{ s.windowNo }} 号</em></div>
                  <div class="rk-sub">学生 {{ f2(s.avgRating) }} 分 · 卫生 {{ f1(s.hygieneScore) }} 分</div>
                </div>
                <span class="rk-score good">{{ s.composite }}</span>
              </div>
              <p v-if="!qualityTop.length" class="empty">暂无评分卫生双高的档口</p>
            </div>

            <div class="rg-line"></div>

            <div class="rank-group">
              <div class="rg-title bad">劣质档口 TOP3</div>
              <div v-for="(s, i) in badTop" :key="s.stallId" class="rk-row">
                <span class="rk-no" :class="'n' + (i + 1)">{{ i + 1 }}</span>
                <div class="rk-main">
                  <div class="rk-name">
                    {{ s.stallName }}<em>{{ s.windowNo }} 号</em>
                    <i v-if="Number(s.hygieneScore) < 80" class="tag-fix">待整改</i>
                  </div>
                  <div class="rk-sub">学生 {{ f2(s.avgRating) }} 分 · 卫生 {{ f1(s.hygieneScore) }} 分</div>
                </div>
                <span class="rk-score bad">{{ s.composite }}</span>
              </div>
              <p v-if="!badTop.length" class="empty">暂无评分卫生双低的档口</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 中间主体 -->
      <section class="col center">
        <div class="panel panel-main">
          <header class="p-head">
            <span class="p-ico"></span>
            <h2>美食双龙榜</h2>
            <span class="p-push"></span>
            <span v-if="boardHint" class="p-unit">{{ boardHint }}</span>
            <div v-if="userStore.isAdmin" class="seg sm">
              <button :class="{ on: boardMode === 'dish' }" @click="boardMode = 'dish'">双龙榜</button>
              <button :class="{ on: boardMode === 'stall' }" @click="boardMode = 'stall'">档口分析</button>
            </div>
          </header>
          <div class="p-body">
            <DragonBoard v-if="boardMode === 'dish'" :data="dragonBoard" />
            <div v-else class="chart-wrap">
              <ScatterBoard mode="stall" :data="boardData" dark height="100%" />
            </div>
          </div>
        </div>

        <div class="trend-strip">
          <div class="ts-label">
            <span>近 {{ range }} 天评价趋势</span>
            <b>{{ spark.total }}<em>条</em></b>
          </div>
          <svg class="ts-spark" viewBox="0 0 300 40" preserveAspectRatio="none">
            <defs>
              <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#ff8a3d" stop-opacity="0.16" />
                <stop offset="100%" stop-color="#ff8a3d" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path :d="spark.area" fill="url(#sparkFill)" />
            <path
              :d="spark.line"
              fill="none"
              stroke="#ff9a5c"
              stroke-width="1.4"
              vector-effect="non-scaling-stroke"
              stroke-linejoin="round"
            />
          </svg>
          <div class="ts-right">平均评分 <b>{{ avgRating }}</b> 分</div>
        </div>

        <div class="kpi-row">
          <div v-for="k in kpis" :key="k.label" class="kpi">
            <div class="kpi-val"><span>{{ k.value }}</span><em>{{ k.unit }}</em></div>
            <div class="kpi-label">{{ k.label }}</div>
          </div>
        </div>
      </section>

      <!-- 右列 -->
      <section class="col">
        <div class="panel">
          <header class="p-head">
            <span class="p-ico"></span>
            <h2>菜品平均分排行</h2>
            <span class="p-push"></span>
            <div class="seg sm">
              <button :class="{ on: rankLimit === 10 }" @click="setRank(10)">TOP 10</button>
              <button :class="{ on: rankLimit === 20 }" @click="setRank(20)">TOP 20</button>
            </div>
          </header>
          <div class="p-body">
            <div class="chart-wrap"><EChart :option="rankOption" height="100%" /></div>
          </div>
        </div>

        <div class="panel">
          <header class="p-head">
            <span class="p-ico"></span>
            <h2>各档口好评占比</h2>
            <span class="p-push"></span>
            <span class="p-unit">评价数 · 悬停看详情</span>
          </header>
          <div class="p-body">
            <div class="chart-wrap"><EChart :option="goodRateOption" height="100%" /></div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, onActivated, onDeactivated, watch } from 'vue'
import EChart from '@/components/EChart.vue'
import ScatterBoard from '@/components/ScatterBoard.vue'
import DragonBoard from '@/components/DragonBoard.vue'
import { useUserStore } from '@/store/user'
import {
  getOverview, getDishRanking, getStallGoodRate, getStallTop, getRatingTrend,
  getStallScatter, getDragonBoard
} from '@/api/stats'

const RANGES = [7, 30, 90]
const PIE = ['#ff7a1a', '#7c7c82', '#c7c7cc']

const userStore = useUserStore()

const ov = ref({})
const rankList = ref([])
const goodRateList = ref([])
const topList = ref([])
const trendList = ref([])
const stallScatterList = ref([])
const dragonBoard = ref({})
const rankLimit = ref(10)
const range = ref(30)
const autoRefresh = ref(true)
const screenMode = ref(true)
const boardMode = ref('dish')
const now = ref(new Date())

let clockTimer = null
let refreshTimer = null

const pad = n => String(n).padStart(2, '0')
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const clockText = computed(() => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}:${pad(now.value.getSeconds())}`)
const dateText = computed(() => `${now.value.getFullYear()}-${pad(now.value.getMonth() + 1)}-${pad(now.value.getDate())}`)
const weekText = computed(() => WEEK[now.value.getDay()])

const f1 = v => Number(v || 0).toFixed(1)
const f2 = v => Number(v || 0).toFixed(2)

const qualityTop = computed(() => stallScatterList.value.filter(s => s.tag === 'quality').slice(0, 3))
const badTop = computed(() =>
  stallScatterList.value.filter(s => s.tag === 'bad').sort((a, b) => a.composite - b.composite).slice(0, 3))

const boardData = computed(() => stallScatterList.value)
const boardHint = computed(() => (boardMode.value === 'dish'
  ? ''
  : '横轴学生评分 · 纵轴卫生分 · 气泡大小 = 评价数'))

const kpis = computed(() => [
  { label: '食堂档口', value: ov.value.stallCount ?? 0, unit: '个' },
  { label: '在售菜品', value: ov.value.dishCount ?? 0, unit: '道' },
  { label: '累计评价', value: ov.value.reviewCount ?? 0, unit: '条' },
  { label: '今日评价', value: ov.value.todayReviewCount ?? 0, unit: '条' }
])

function pct(score) {
  const max = topList.value.length ? Number(topList.value[0].popularityScore) || 1 : 1
  return Math.min(100, Math.round((Number(score) / max) * 100))
}

const spark = computed(() => {
  const arr = trendList.value.map(i => Number(i.reviewCount) || 0)
  const total = arr.reduce((a, b) => a + b, 0)
  if (arr.length < 2) return { line: '', area: '', total }
  const W = 300
  const H = 40
  const top = 4
  const max = Math.max(1, ...arr)
  const step = W / (arr.length - 1)
  const pts = arr.map((v, i) => [i * step, H - 3 - (v / max) * (H - top - 3)])
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
  return { line, area: `${line} L${W} ${H} L0 ${H} Z`, total }
})

const avgRating = computed(() => {
  // 只统计当天有评价的日子，避免 0 值拉低均值
  const active = trendList.value.filter(i => (Number(i.reviewCount) || 0) > 0)
  if (!active.length) return '0.00'
  const sum = active.reduce((a, b) => a + (Number(b.avgRating) || 0), 0)
  return (sum / active.length).toFixed(2)
})

const tip = {
  backgroundColor: 'rgba(28,28,30,0.96)',
  borderColor: 'rgba(255,255,255,0.12)',
  borderWidth: 1,
  textStyle: { color: '#f5f5f7', fontSize: 12 },
  extraCssText: 'border-radius:10px;box-shadow:0 12px 32px rgba(0,0,0,.45);'
}
const AXIS_LABEL = { color: '#8e8e93', fontSize: 11 }
const SPLIT = { lineStyle: { color: 'rgba(255,255,255,0.06)' } }

const topOption = computed(() => ({
  tooltip: {
    ...tip,
    trigger: 'item',
    formatter: p => {
      const raw = topList.value.find(i => i.stallName === p.name) || {}
      return `${p.name}<br/><span style="color:rgba(255,255,255,0.55);font-size:11px">${raw.stallZone || '-'} · ${raw.windowNo || ''}号</span>
        <br/>热度分 <b style="color:#ffb07a">${p.value}</b>`
    }
  },
  title: {
    text: 'TOP3',
    left: 'center',
    top: '38%',
    textStyle: { color: '#8e8e93', fontSize: 13, fontWeight: 600, letterSpacing: 0.5 }
  },
  series: [
    {
      type: 'pie',
      radius: ['50%', '72%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#171719', borderWidth: 3 },
      label: { show: false },
      labelLine: { show: false },
      emphasis: { scale: true, scaleSize: 4 },
      data: topList.value.map((i, idx) => ({
        name: i.stallName,
        value: i.popularityScore,
        itemStyle: { color: PIE[idx] || '#6e6e73' }
      }))
    }
  ]
}))

const rankOption = computed(() => {
  const data = [...rankList.value].reverse()
  return {
    tooltip: {
      ...tip,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(255,255,255,0.04)' } },
      formatter: params => {
        const p = params[0]
        const raw = rankList.value.find(i => i.dishName === p.name) || {}
        return `<b>${p.name}</b><br/>
          <span style="color:rgba(255,255,255,0.55);font-size:11px">${raw.stallName || '-'} · ${raw.stallZone || '-'}</span><br/>
          平均分 <b style="color:#ffb07a">${p.value}</b> · 评价 ${raw.reviewCount || 0} 条<br/>
          好评率 ${raw.goodRate || 0}%`
      }
    },
    grid: { left: 74, right: 38, top: 6, bottom: 6 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 5,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: AXIS_LABEL,
      splitLine: SPLIT
    },
    yAxis: {
      type: 'category',
      data: data.map(i => i.dishName),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { ...AXIS_LABEL, width: 66, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        data: data.map((i, idx) => ({
          value: i.avgRating,
          itemStyle: { color: idx === data.length - 1 ? '#ff7a1a' : '#8e8e93' }
        })),
        barWidth: rankLimit.value === 10 ? 13 : 8,
        itemStyle: { borderRadius: [0, 5, 5, 0] },
        label: { show: true, position: 'right', formatter: '{c}', color: '#d2d2d7', fontSize: 11, distance: 5 }
      }
    ]
  }
})

const goodRateOption = computed(() => {
  const list = goodRateList.value
  return {
    tooltip: {
      ...tip,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(255,255,255,0.04)' } },
      formatter: params => {
        const raw = list[params[0].dataIndex] || {}
        return `<b>${raw.stallName || '-'}</b>
          <br/><span style="color:rgba(255,255,255,0.55);font-size:11px">${raw.stallZone || '-'} · ${raw.windowNo || ''}号</span><br/>` +
          params.map(p => `${p.marker}${p.seriesName}：${p.value} 条`).join('<br/>') +
          `<br/>好评率 <b style="color:#ffb07a">${raw.goodRate || 0}%</b>`
      }
    },
    legend: {
      top: 0,
      left: 'center',
      itemWidth: 9,
      itemHeight: 9,
      itemGap: 12,
      textStyle: { color: 'rgba(235,235,240,0.65)', fontSize: 11 }
    },
    grid: { left: 34, right: 26, top: 30, bottom: 54 },
    xAxis: {
      type: 'category',
      data: list.map(i => i.stallName),
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(235,235,240,0.7)',
        fontSize: 11,
        interval: 0,
        rotate: list.length > 7 ? 45 : 0,
        margin: 7,
        formatter: v => (v && v.length > 6 ? v.slice(0, 6) + '…' : v)
      }
    },
    yAxis: {
      type: 'value',
      // 轴名与面板副标题「评价数 · 悬停看详情」及图例重复，窄面板下三者叠字，去掉
      axisLine: { show: false },
      axisLabel: { color: 'rgba(235,235,240,0.6)', fontSize: 11 },
      splitLine: SPLIT
    },
    series: [
      { name: '好评(4-5星)', type: 'bar', stack: 'total', barWidth: '52%', data: list.map(i => i.goodCount), itemStyle: { color: '#7fb98a' } },
      { name: '中评(3星)', type: 'bar', stack: 'total', data: list.map(i => i.normalCount), itemStyle: { color: '#6e6e73' } },
      { name: '差评(1-2星)', type: 'bar', stack: 'total', data: list.map(i => i.badCount), itemStyle: { color: '#a06a6a' } }
    ]
  }
})

async function loadAll() {
  const [a, b, c, d, e, g, h] = await Promise.all([
    getOverview(), getDishRanking(rankLimit.value), getStallGoodRate(), getStallTop(3),
    getRatingTrend(range.value), getStallScatter(), getDragonBoard(5).catch(() => ({ data: {} }))
  ])
  ov.value = a.data || {}
  rankList.value = b.data || []
  goodRateList.value = c.data || []
  topList.value = d.data || []
  trendList.value = e.data || []
  stallScatterList.value = g.data || []
  dragonBoard.value = h.data || {}
}

// 本仓库所有页面被 Layout 的 keep-alive 缓存：切走时必须停掉时钟/轮询定时器，
// 切回来时重新拉数据并恢复定时器，否则后台空跑且显示陈旧数据。
// startTimers 先 clear 再 set，避免重复激活时定时器叠加导致请求越滚越多
function startTimers() {
  stopTimers()
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  if (autoRefresh.value) refreshTimer = setInterval(loadAll, 60000)
}

function stopTimers() {
  clearInterval(clockTimer)
  clearInterval(refreshTimer)
  refreshTimer = null
}

onActivated(() => {
  loadAll()
  startTimers()
})
onDeactivated(stopTimers)

async function loadTrend() {
  const res = await getRatingTrend(range.value)
  trendList.value = res.data || []
}

function setRank(v) {
  rankLimit.value = v
  getDishRanking(v).then(res => { rankList.value = res.data || [] })
}

function changeRange(d) {
  range.value = d
  loadTrend()
}

// 退出大屏 = 回到带侧边栏的正常页面布局，而非跳转离开
function toggleScreen() {
  if (screenMode.value && document.fullscreenElement) document.exitFullscreen().catch(() => {})
  screenMode.value = !screenMode.value
}

watch(autoRefresh, v => {
  clearInterval(refreshTimer)
  if (v) refreshTimer = setInterval(loadAll, 60000)
})

onMounted(() => {
  loadAll()
  startTimers()
})

onBeforeUnmount(stopTimers)
</script>

<style scoped>
.screen {
  --s-accent: #ff7a1a;
  --s-accent-soft: rgba(255, 122, 26, 0.16);
  --s-panel: rgba(255, 255, 255, 0.034);
  --s-edge: rgba(255, 255, 255, 0.075);
  --s-line: rgba(255, 255, 255, 0.055);
  --s-t1: #f5f5f7;
  --s-t2: rgba(235, 235, 240, 0.76);
  --s-t3: rgba(235, 235, 240, 0.48);
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 18px 16px;
  overflow: hidden;
  color: var(--s-t1);
  background:
    radial-gradient(1200px 560px at 50% -18%, rgba(255, 138, 61, 0.06), transparent 68%),
    #0b0a09;
  font-family: var(--font-sans);
  -webkit-font-smoothing: antialiased;
}

/* 退出大屏后：回到 Layout 内容区，正常滚动，不再覆盖侧边栏 */
.screen.embedded {
  position: relative;
  inset: auto;
  z-index: 1;
  height: auto;
  min-height: calc(100vh - 64px);
  padding: 16px 20px 20px;
}

/* 窄屏（嵌入模式/缩窗）下中列双龙榜两列各 ~46%，卡片内序号+缩略图+销量固定尺寸会溢出 */
@media (max-width: 1200px) {
  .body {
    grid-template-columns: minmax(220px, 24%) minmax(0, 1fr) minmax(220px, 24%);
  }
}

/* 嵌入模式有侧边栏，可用宽度比大屏模式更窄，左右列给最小保底宽度 */
.screen.embedded .body {
  grid-template-columns: minmax(230px, 26%) minmax(0, 1fr) minmax(230px, 26%);
  flex: 1 0 auto;
  min-height: 600px;
}

/* ---------- 顶部条 ---------- */
.topbar {
  position: relative;
  z-index: 1;
  flex: 0 0 56px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 0 18px;
  border: 1px solid var(--s-edge);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.028);
}

.tb-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  font-size: 12px;
  color: var(--s-t3);
  white-space: nowrap;
  overflow: hidden;
}

.tb-date {
  color: var(--s-t2);
  font-variant-numeric: tabular-nums;
}

.tb-week {
  color: var(--s-t3);
}

.live-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: #34c759;
}

.tb-center {
  display: flex;
  justify-content: center;
}

.title-box {
  text-align: center;
}

.title-box h1 {
  margin: 0;
  font-size: 21px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--s-t1);
}

.title-box p {
  margin: 4px 0 0;
  font-size: 11px;
  letter-spacing: 0.02em;
  color: var(--s-t3);
}

.tb-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.clock {
  margin-right: 4px;
}

.clock-time {
  display: block;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.1;
  color: var(--s-t1);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.seg {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.055);
  flex: 0 0 auto;
}

.seg button {
  border: 0;
  background: transparent;
  color: var(--s-t2);
  font-size: 12px;
  font-family: inherit;
  padding: 4px 11px;
  border-radius: 999px;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s var(--ease-apple), background 0.15s var(--ease-apple);
}

.seg button:hover {
  color: var(--s-t1);
}

.seg button.on {
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

.seg.sm {
  border-radius: 9px;
  padding: 2px;
  background: rgba(255, 255, 255, 0.05);
}

.seg.sm button {
  border-radius: 7px;
  padding: 2px 8px;
  font-size: 11px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  flex: 0 0 auto;
  border: 1px solid var(--s-edge);
  border-radius: 999px;
  background: transparent;
  color: var(--s-t2);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s var(--ease-apple), background 0.15s var(--ease-apple),
    border-color 0.15s var(--ease-apple);
}

.pill:hover {
  color: var(--s-t1);
  background: rgba(255, 255, 255, 0.05);
}

.pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.28);
}

.pill.on .pill-dot {
  background: #34c759;
}

.pill.exit {
  color: var(--s-t1);
  border-color: rgba(255, 255, 255, 0.14);
}

/* ---------- 主体栅格 ---------- */
.body {
  position: relative;
  z-index: 1;
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 26% minmax(0, 1fr) 26%;
  gap: 12px;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

/* ---------- 面板 ---------- */
.panel {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--s-edge);
  border-radius: 16px;
  background: var(--s-panel);
  overflow: hidden;
}

.p-head {
  flex: 0 0 36px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 14px;
  border-bottom: 1px solid var(--s-line);
}

.p-head h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--s-t1);
  white-space: nowrap;
}

.p-ico {
  width: 3px;
  height: 12px;
  flex: 0 0 3px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.28);
}

.p-push {
  flex: 1 1 auto;
}

.p-unit {
  font-size: 11.5px;
  color: var(--s-t3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60%;
}

.p-body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
}

.chart-wrap {
  flex: 1 1 0;
  min-height: 0;
}

.donut-wrap {
  flex: 0 0 55%;
  min-height: 0;
}

/* ---------- 热门档口 ---------- */
.pop-list {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  padding-top: 4px;
}

.pop-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 4px 8px;
  margin-bottom: 14px;
}

.pop-row:last-of-type {
  margin-bottom: 0;
}

.pop-no {
  min-width: 38px;
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
  color: var(--s-t3);
  background: rgba(255, 255, 255, 0.06);
  font-variant-numeric: tabular-nums;
}

.pop-no.n1 {
  color: #ffb07a;
  background: var(--s-accent-soft);
}

.pop-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--s-t1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pop-score {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--s-t1);
  font-variant-numeric: tabular-nums;
}

.pop-score em {
  font-style: normal;
  font-size: 10px;
  font-weight: 400;
  color: var(--s-t3);
  margin-left: 1px;
}

.pop-bar {
  grid-column: 1 / -1;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.07);
  overflow: hidden;
}

.pop-bar i {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: #6e6e73;
  transition: width 0.6s var(--ease-apple);
}

.pop-bar i.first {
  background: var(--s-accent);
}

/* ---------- 红黑榜列表 ---------- */
.split-body {
  gap: 14px;
  justify-content: center;
  padding-bottom: 8px;
  overflow: hidden;
}

.rank-group {
  flex: 0 0 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.rg-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  color: var(--s-t2);
  padding: 0 2px 4px;
  letter-spacing: 0.01em;
}

.rg-title::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34c759;
}

.rg-title.bad::before {
  background: #ff6961;
}

.rg-line {
  height: 1px;
  margin: 4px 0 6px;
  background: var(--s-line);
}

.rk-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 2px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.045);
}

.rk-row:last-child {
  border-bottom: 0;
}

.rk-no {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--s-t3);
  background: rgba(255, 255, 255, 0.06);
  font-variant-numeric: tabular-nums;
}

.rk-no.n1 {
  color: #ffb07a;
  background: var(--s-accent-soft);
}

.rk-main {
  min-width: 0;
  flex: 1 1 auto;
}

.rk-name {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--s-t1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rk-name em {
  font-style: normal;
  font-size: 10.5px;
  font-weight: 400;
  color: var(--s-t3);
}

.rk-sub {
  font-size: 11px;
  color: var(--s-t3);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rk-score {
  margin-left: auto;
  flex: 0 0 auto;
  font-size: 15px;
  font-weight: 650;
  color: var(--s-t1);
  font-variant-numeric: tabular-nums;
}

.rk-score.bad {
  color: #d99a9a;
}

.tag-fix {
  font-style: normal;
  font-size: 10.5px;
  padding: 1px 5px;
  border-radius: 999px;
  color: #e5a3a3;
  background: rgba(255, 99, 99, 0.12);
  border: 1px solid rgba(255, 99, 99, 0.22);
}

.empty {
  margin: 8px 0;
  text-align: center;
  font-size: 11.5px;
  color: var(--s-t3);
}

/* ---------- 趋势条 ---------- */
.trend-strip {
  flex: 0 0 54px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
  border: 1px solid var(--s-edge);
  border-radius: 14px;
  background: var(--s-panel);
}

.ts-label {
  flex: 0 0 auto;
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.ts-label span {
  font-size: 11.5px;
  color: var(--s-t3);
}

.ts-label b {
  font-size: 16px;
  font-weight: 650;
  color: var(--s-t1);
  font-variant-numeric: tabular-nums;
}

.ts-label em {
  font-style: normal;
  font-size: 10.5px;
  font-weight: 400;
  color: var(--s-t3);
  margin-left: 1px;
}

.ts-spark {
  flex: 1 1 auto;
  min-width: 0;
  height: 32px;
}

.ts-right {
  flex: 0 0 auto;
  font-size: 11.5px;
  color: var(--s-t3);
}

.ts-right b {
  font-size: 14px;
  color: var(--s-t1);
  font-variant-numeric: tabular-nums;
}

/* ---------- 指标卡 ---------- */
.kpi-row {
  flex: 0 0 84px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.kpi {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 4px;
  padding: 0 16px;
  border: 1px solid var(--s-edge);
  border-radius: 14px;
  background: var(--s-panel);
  overflow: hidden;
}

.kpi-val {
  font-size: 28px;
  font-weight: 650;
  line-height: 1.05;
  letter-spacing: -0.02em;
  color: var(--s-t1);
  font-variant-numeric: tabular-nums;
}

.kpi-val span {
  color: var(--s-t1);
  -webkit-text-fill-color: currentColor;
  background: none;
}

.kpi-val em {
  font-style: normal;
  font-size: 11.5px;
  font-weight: 400;
  color: var(--s-t3);
  margin-left: 4px;
}

.kpi-label {
  font-size: 11.5px;
  color: var(--s-t3);
  letter-spacing: 0.02em;
}

@media (max-width: 1500px) {
  .title-box p {
    display: none;
  }
}

@media (max-width: 1180px) {
  .clock {
    display: none;
  }
}

@media (max-width: 980px) {
  .tb-week {
    display: none;
  }

  .title-box h1 {
    font-size: 19px;
  }

  .pill {
    padding: 0 10px;
    font-size: 11px;
  }
}

@media (max-width: 900px) {
  .topbar {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .tb-left {
    display: none;
  }

  .tb-center {
    justify-content: flex-start;
  }
}
</style>
