<template>
  <div class="ap-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">财务中心</h1>
        <div class="ap-sub">收益口径与「经营分析 / 销售情况」一致：已支付订单金额</div>
      </div>
      <div class="ap-actions head-actions">
        <span v-if="!canWithdraw" class="head-note">暂无可提现余额</span>
        <button class="ap-btn ap-btn--light" @click="openReport">
          <el-icon><DataAnalysis /></el-icon>
          AI 经营简报
        </button>
        <button class="ap-btn ap-btn--brand" :disabled="!canWithdraw" @click="openWithdraw">提现</button>
      </div>
    </div>

    <div class="ap-grid ap-grid--4 finance-stats">
      <div class="ap-card ap-card--accent balance-card">
        <div class="ap-stat-label">可提现余额</div>
        <div class="ap-stat-value balance-value"><small>¥</small>{{ money(summary.available) }}</div>
        <div class="ap-stat-hint">累计收益 ¥{{ money(summary.totalIncome) }} · 已提现 ¥{{ money(summary.withdrawn) }}</div>
      </div>
      <div class="ap-card">
        <div class="ap-stat-label">累计收益</div>
        <div class="ap-stat-value"><small>¥</small>{{ money(summary.totalIncome) }}</div>
        <div class="ap-stat-hint">经营分析里的「总收入」与此一致</div>
      </div>
      <div class="ap-card">
        <div class="ap-stat-label">本月收益</div>
        <div class="ap-stat-value"><small>¥</small>{{ money(summary.monthIncome) }}</div>
        <div class="ap-stat-hint">今日收益 ¥{{ money(summary.todayIncome) }}</div>
      </div>
      <div class="ap-card">
        <div class="ap-stat-label">客单价</div>
        <div class="ap-stat-value"><small>¥</small>{{ money(summary.avgOrderAmount) }}</div>
        <div class="ap-stat-hint">{{ summary.paidOrderCount || 0 }} 笔已支付 · {{ summary.customerCount || 0 }} 位客户</div>
      </div>
    </div>

    <div class="ap-grid finance-main">
      <div class="ap-card">
        <div class="chart-head">
          <span class="ap-section-title">近 14 天收益</span>
          <span class="ap-muted">每日已支付订单金额</span>
        </div>
        <EChart :option="chartOption" height="250px" />
      </div>
      <div class="ap-card">
        <div class="chart-head">
          <span class="ap-section-title">收支结构</span>
          <span class="ap-section-hint">累计收益 ¥{{ money(summary.totalIncome) }} 为 100%</span>
        </div>
        <div class="mix-bar">
          <i class="withdraw" :style="{ width: withdrawPct + '%' }"></i>
          <i class="available" :style="{ width: Math.max(0, 100 - withdrawPct) + '%' }"></i>
        </div>
        <div class="mix-legend">
          <div class="mix-legend-item">
            <span class="dot withdraw"></span>
            <div>
              <div class="mix-legend-label">已提现</div>
              <div class="mix-legend-value ap-num">¥{{ money(summary.withdrawn) }}</div>
            </div>
          </div>
          <div class="mix-legend-item">
            <span class="dot available"></span>
            <div>
              <div class="mix-legend-label">可提现</div>
              <div class="mix-legend-value ap-num">¥{{ money(summary.available) }}</div>
            </div>
          </div>
        </div>
        <div class="mix-foot">
          <span>{{ summary.orderCount || 0 }} 笔订单</span>
          <span>{{ summary.paidOrderCount || 0 }} 笔已支付</span>
        </div>
      </div>
    </div>

    <div class="ap-card ledger-card">
      <div class="ledger-head">
        <div>
          <div class="ap-section-title">财务流水</div>
          <div class="ap-sub">
            订单收益与提现记录，按时间倒序
            <span class="ledger-sum">
              {{ filteredLedger.length }} 条 ·
              <b class="ap-num" :class="{ neg: ledgerTotal < 0 }">{{ ledgerTotal >= 0 ? '+' : '−' }}¥{{ money(Math.abs(ledgerTotal)) }}</b>
            </span>
          </div>
        </div>
        <div class="ap-pill-group">
          <button
            v-for="tab in ledgerTabs"
            :key="tab.value"
            class="ap-pill"
            :class="{ active: ledgerFilter === tab.value }"
            @click="ledgerFilter = tab.value"
          >{{ tab.label }}</button>
        </div>
      </div>

      <div v-loading="loading" class="ap-list">
        <div v-for="item in filteredLedger" :key="item.type + '-' + item.refId" class="ap-row">
          <div class="ap-row-icon" :class="item.type === 'INCOME' ? 'in' : 'out'">
            <el-icon :size="17"><Wallet v-if="item.type === 'INCOME'" /><Minus v-else /></el-icon>
          </div>
          <div class="ap-row-main">
            <div class="ap-row-title">{{ item.title }}</div>
            <div class="ap-row-sub">{{ item.subtitle || '—' }}</div>
          </div>
          <div class="ap-row-time ap-num">{{ shortTime(item.time) }}</div>
          <div class="ap-row-amount ap-num" :class="Number(item.amount) >= 0 ? 'pos' : 'neg'">
            {{ Number(item.amount) >= 0 ? '+' : '-' }}¥{{ money(Math.abs(Number(item.amount))) }}
          </div>
        </div>
        <div v-if="!loading && !filteredLedger.length" class="ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><Tickets /></el-icon></div>
          <div class="ap-empty-title">暂无流水</div>
          <div>换个筛选条件看看</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="withdrawVisible" title="提现" width="520px">
      <div class="wd-summary">
        <div class="ap-stat-label">可提现余额</div>
        <div class="wd-balance ap-num"><small>¥</small>{{ money(summary.available) }}</div>
      </div>
      <div class="wd-quick">
        <div class="ap-pill-group">
          <button class="ap-pill" :class="{ active: Number(withdrawForm.amount) === Number(summary.available) }" @click="withdrawForm.amount = Number(summary.available)">全部</button>
          <button class="ap-pill" :class="{ active: Number(withdrawForm.amount) === 100 }" :disabled="Number(summary.available) < 100" @click="withdrawForm.amount = 100">¥100</button>
          <button class="ap-pill" :class="{ active: Number(withdrawForm.amount) === 500 }" :disabled="Number(summary.available) < 500" @click="withdrawForm.amount = 500">¥500</button>
        </div>
      </div>
      <div class="wd-field">
        <span class="wd-label">提现金额</span>
        <el-input-number
          v-model="withdrawForm.amount"
          :min="0.01"
          :max="Number(summary.available || 0)"
          :precision="2"
          :step="10"
          controls-position="right"
          style="width: 100%"
        />
      </div>
      <div class="wd-field">
        <span class="wd-label">备注</span>
        <el-input v-model="withdrawForm.remark" maxlength="200" placeholder="选填" />
      </div>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="primary" :loading="withdrawing" @click="submitWithdraw">确认提现</el-button>
      </template>
    </el-dialog>

    <!-- AI 经营简报 -->
    <el-dialog v-model="reportVisible" title="AI 经营简报" width="560px">
      <div class="report-wrap" v-loading="reportLoading" element-loading-text="正在根据真实经营数据生成…">
        <div v-if="reportLoading && !reportText" class="report-skeleton" aria-hidden="true">
          <i></i><i></i><i></i><i></i><i></i>
        </div>
        <div v-else-if="reportText" class="ai-report">{{ reportText }}</div>
        <div v-else class="ap-empty">
          <div class="ap-empty-title">还没有生成简报</div>
          <div>点下方「重新生成」，AI 会读取本页真实经营数据</div>
        </div>
      </div>
      <div class="ai-report-tip">数据来自本页财务口径，AI 只做解读与建议，不编造数字。</div>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
        <el-button type="primary" :loading="reportLoading" @click="loadReport">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import EChart from '@/components/EChart.vue'
import { getFinanceSummary, getFinanceLedger, withdraw, getAiReport } from '@/api/finance'
import { getDailyIncome } from '@/api/sales'

const loading = ref(false)
const withdrawing = ref(false)
const summary = ref({})
const ledger = ref([])
const daily = ref([])
const ledgerFilter = ref('ALL')
const withdrawVisible = ref(false)
const withdrawForm = ref({ amount: 10, remark: '' })
const reportVisible = ref(false)
const reportLoading = ref(false)
const reportText = ref('')

const ledgerTabs = [
  { value: 'ALL', label: '全部' },
  { value: 'INCOME', label: '收益' },
  { value: 'WITHDRAW', label: '提现' }
]

const filteredLedger = computed(() => {
  if (ledgerFilter.value === 'ALL') return ledger.value
  return ledger.value.filter(i => i.type === ledgerFilter.value)
})

/** 当前筛选流水的合计（只读展示，不参与任何金额计算） */
const ledgerTotal = computed(() =>
  filteredLedger.value.reduce((sum, item) => sum + Number(item.amount || 0), 0)
)

const canWithdraw = computed(() => Number(summary.value.available || 0) >= 0.01)

const incomePct = computed(() => pct(summary.value.totalIncome))
const withdrawPct = computed(() => pct(summary.value.withdrawn))
const availablePct = computed(() => pct(summary.value.available))

function pct(v) {
  const total = Number(summary.value.totalIncome || 0)
  if (!total) return 0
  return Math.min(100, Math.max(0, Math.round((Number(v || 0) / total) * 100)))
}

function money(v) {
  return Number(v || 0).toFixed(2)
}

function shortTime(t) {
  if (!t) return ''
  const s = String(t)
  return s.length >= 16 ? s.slice(5, 16) : s
}

const chartOption = computed(() => {
  const labels = daily.value.map(d => String(d.saleDate || '').slice(5))
  const values = daily.value.map(d => Number(d.amount || 0))
  return {
    grid: { left: 8, right: 14, top: 24, bottom: 6, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(29,29,31,0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 },
      valueFormatter: v => '¥' + Number(v).toFixed(2)
    },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#a1a1a6', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f0f2' } },
      axisLabel: { color: '#a1a1a6', fontSize: 11, formatter: v => '¥' + v }
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: false,
      lineStyle: { width: 2.5, color: '#ff6a00' },
      itemStyle: { color: '#ff6a00', borderColor: '#fff', borderWidth: 2 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(255,106,0,0.22)' },
            { offset: 1, color: 'rgba(255,106,0,0)' }
          ]
        }
      }
    }]
  }
})

async function loadAll() {
  loading.value = true
  try {
    const [s, l, d] = await Promise.all([
      getFinanceSummary(),
      getFinanceLedger(),
      getDailyIncome(14)
    ])
    summary.value = s.data || {}
    ledger.value = l.data || []
    daily.value = d.data || []
  } finally {
    loading.value = false
  }
}

function openWithdraw() {
  withdrawForm.value = { amount: Math.min(Number(summary.value.available || 0), 10) || 0.01, remark: '' }
  withdrawVisible.value = true
}

function openReport() {
  reportVisible.value = true
  if (!reportText.value) loadReport()
}

async function loadReport() {
  reportLoading.value = true
  try {
    const res = await getAiReport()
    reportText.value = res.data || ''
  } catch {
    reportText.value = '简报生成失败，请稍后重试'
  } finally {
    reportLoading.value = false
  }
}

async function submitWithdraw() {
  const amount = Number(withdrawForm.value.amount || 0)
  if (!(amount > 0)) {
    ElMessage.warning('请输入提现金额')
    return
  }
  withdrawing.value = true
  try {
    const res = await withdraw({ amount, remark: withdrawForm.value.remark || undefined })
    summary.value = res.data || summary.value
    withdrawVisible.value = false
    ElMessage.success('提现成功')
    loadAll()
  } catch {
    // 请求层已提示
  } finally {
    withdrawing.value = false
  }
}

onMounted(loadAll)
onActivated(loadAll)
</script>

<style scoped>
.head-actions { flex-wrap: wrap; justify-content: flex-end; }

.head-note { font-size: 12px; color: var(--text-3); }

.finance-stats { margin-bottom: 16px; }

.balance-card { padding: 22px 24px; }

.balance-card .ap-stat-label { color: rgba(255, 255, 255, 0.68); }

.balance-card .ap-stat-hint { color: rgba(255, 255, 255, 0.52); }

.balance-card .ap-stat-value {
  color: #fff;
  font-size: clamp(32px, 3vw, 40px);
  line-height: 1;
}

.balance-card .ap-stat-value small { color: rgba(255, 255, 255, 0.62); }

.finance-main {
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  margin-bottom: 16px;
}

.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px 14px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.mix-bar {
  display: flex;
  height: 10px;
  margin-top: 24px;
  border-radius: 999px;
  background: #f2f2f4;
  overflow: hidden;
}

.mix-bar i {
  display: block;
  height: 100%;
  transition: width 0.5s var(--ease-apple);
}

.mix-bar i.withdraw {
  background: #1d1d1f;
  box-shadow: inset -2px 0 0 #fff;
}

.mix-bar i.available { background: var(--brand); }

.mix-legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.mix-legend-item { display: flex; align-items: flex-start; gap: 10px; }

.mix-legend-label { font-size: 12px; color: var(--text-2); }

.mix-legend-value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-1);
}

.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin-top: 5px; flex-shrink: 0; }
.dot.withdraw { background: #1d1d1f; }
.dot.available { background: var(--brand); }

.mix-foot {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-3);
}

.ledger-card { padding-top: 22px; }

.ledger-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}

.ledger-head .ap-sub { margin-top: 5px; }

.ledger-sum {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: 8px;
  color: var(--text-3);
}

.ledger-sum b { color: var(--text-1); font-weight: 600; }
.ledger-sum b.neg { color: #d92d20; }

.ap-row-icon.in {
  background: var(--brand-soft);
  color: var(--brand-dark);
}

.ap-row-icon.out {
  background: #f5f5f7;
  color: var(--text-2);
}

.wd-summary {
  padding: 16px 18px;
  border-radius: 14px;
  background: var(--surface-2);
}

.wd-balance {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--text-1);
}

.wd-balance small {
  font-size: 14px;
  font-weight: 550;
  margin-right: 3px;
  color: var(--text-2);
}

.wd-quick { margin: 16px 0 18px; }

.wd-quick .ap-pill:disabled { opacity: 0.35; cursor: not-allowed; }

.wd-field { margin-bottom: 14px; }

.wd-label {
  display: block;
  margin-bottom: 7px;
  font-size: 13px;
  color: var(--text-2);
}

.report-wrap { min-height: 140px; }

.ai-report {
  max-width: 62ch;
  font-size: 15px;
  line-height: 1.9;
  color: var(--text-1);
  white-space: pre-wrap;
}

.report-skeleton { display: flex; flex-direction: column; gap: 11px; padding: 4px 0; }

.report-skeleton i {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: #f2f2f4;
}

.report-skeleton i:nth-child(1) { width: 88%; }
.report-skeleton i:nth-child(2) { width: 96%; }
.report-skeleton i:nth-child(3) { width: 72%; }
.report-skeleton i:nth-child(4) { width: 92%; }
.report-skeleton i:nth-child(5) { width: 58%; }

.ai-report-tip { margin-top: 12px; font-size: 11.5px; color: var(--text-3); }

@media (max-width: 1100px) {
  .finance-main { grid-template-columns: 1fr; }
}
</style>
