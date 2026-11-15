<template>
  <div class="page-container adaptive-page audit-page">
    <div class="ap-head audit-head">
      <div>
        <h1 v-if="!embedded" class="ap-title">处理记录</h1>
        <div v-if="!embedded" class="ap-sub">共 {{ total }} 条记录 · 只读审计流，数据来自 AI 事件、命中记录与退款表</div>
        <div v-else class="ap-section-title">处理记录</div>
      </div>
      <div class="ap-actions audit-actions">
        <span
          class="live-state"
          :class="{ off: !autoRefresh }"
          role="switch"
          :aria-checked="autoRefresh"
          :title="autoRefresh ? '点击暂停自动刷新' : '点击开启自动刷新'"
          @click="autoRefresh = !autoRefresh"
        >
          <i class="live-dot"></i>{{ autoRefresh ? '实时更新中' : '已暂停' }}
        </span>
        <el-button circle text title="刷新" @click="loadData">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <div class="page-card audit-card">
      <div class="stat-row ap-grid ap-grid--4 audit-stats">
        <div class="ap-kpi audit-stat">
          <div class="stat-label"><i class="stat-dot ai"></i>AI 事件</div>
          <div class="stat-num ap-num">{{ stats.ai }}</div>
        </div>
        <div class="ap-kpi audit-stat">
          <div class="stat-label"><i class="stat-dot exp"></i>经验命中</div>
          <div class="stat-num ap-num">{{ stats.experienceHits }}</div>
        </div>
        <div class="ap-kpi audit-stat">
          <div class="stat-label"><i class="stat-dot refund"></i>退款记录</div>
          <div class="stat-num ap-num">{{ stats.refunds }}</div>
        </div>
        <div class="ap-kpi audit-stat">
          <div class="stat-label"><i class="stat-dot fail"></i>退款失败</div>
          <div class="stat-num ap-num">{{ stats.refundFailures }}</div>
        </div>
      </div>

      <div class="ap-toolbar audit-toolbar">
        <div class="ap-pill-group" role="tablist" aria-label="记录类型">
          <button type="button" class="ap-pill" :class="{ active: !type }" @click="setType('')">全部</button>
          <button type="button" class="ap-pill" :class="{ active: type === 'AI' }" @click="setType('AI')">AI 事件</button>
          <button type="button" class="ap-pill" :class="{ active: type === 'EXPERIENCE' }" @click="setType('EXPERIENCE')">经验命中</button>
          <button type="button" class="ap-pill" :class="{ active: type === 'REFUND' }" @click="setType('REFUND')">退款</button>
        </div>
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 150px" @change="reload">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="days" style="width: 110px" @change="reload">
          <el-option label="近 1 天" :value="1" />
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 30 天" :value="30" />
        </el-select>
        <span class="ap-toolbar-spacer"></span>
        <span class="toolbar-hint">只读审计流</span>
      </div>

      <div class="adaptive-table">
        <el-table :data="records" v-loading="loading">
          <el-table-column label="时间" width="140">
            <template #default="{ row }"><span class="cell-time">{{ short(row.time) }}</span></template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }"><span class="ap-tag">{{ row.typeLabel }}</span></template>
          </el-table-column>
          <el-table-column label="事件" min-width="180" show-overflow-tooltip>
            <template #default="{ row }"><span class="cell-title">{{ row.title }}</span></template>
          </el-table-column>
          <el-table-column label="详情" min-width="260">
            <template #default="{ row }"><span class="detail-cell" :title="row.detail">{{ row.detail }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <span
                class="ap-tag"
                :class="{
                  'ap-tag--green': statusTag(row.status) === 'success',
                  'ap-tag--amber': statusTag(row.status) === 'warning',
                  'ap-tag--red': statusTag(row.status) === 'danger'
                }"
              >
                {{ row.statusLabel }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="actor" label="操作者/去向" width="120" />
          <el-table-column label="操作" width="72" align="right">
            <template #default="{ row }">
              <el-button v-if="row.target" link class="row-action" @click="go(row.target)">查看</el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark"><el-icon :size="20"><Tickets /></el-icon></div>
              <div class="ap-empty-title">暂无处理记录</div>
              <div>换个类型或时间范围试试</div>
            </div>
          </template>
        </el-table>
      </div>

      <div class="adaptive-pager audit-pager">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, onDeactivated, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { auditFeed, auditStats } from '@/api/audit'

defineProps({ embedded: { type: Boolean, default: false } })
const emit = defineEmits(['navigate'])
const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const type = ref('')
const status = ref('')
const days = ref(7)
const autoRefresh = ref(true)
const stats = reactive({ ai: 0, experienceHits: 0, refunds: 0, refundFailures: 0, total: 0 })
const page = reactive({ current: 1, size: 20 })

// 状态选项随类型联动：退款只有已退款/失败，AI 事件只有已处理/升级
const statusOptions = computed(() => {
  if (type.value === 'REFUND') {
    return [{ label: '已退款', value: 'REFUNDED' }, { label: '退款失败', value: 'FAILED' }]
  }
  if (type.value === 'AI') {
    return [{ label: '已处理', value: 'DONE' }, { label: '已升级人工', value: 'ESCALATED' }]
  }
  if (type.value === 'EXPERIENCE') {
    return [{ label: '命中', value: 'HIT' }]
  }
  return [
    { label: '已处理', value: 'DONE' },
    { label: '已升级人工', value: 'ESCALATED' },
    { label: '命中', value: 'HIT' },
    { label: '已退款', value: 'REFUNDED' },
    { label: '退款失败', value: 'FAILED' }
  ]
})

let timer = null
let active = true

function short(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

function typeTag(t) {
  return { AI: 'primary', EXPERIENCE: 'success', REFUND: 'danger' }[t] || 'info'
}

function statusTag(s) {
  return {
    DONE: 'success',
    ESCALATED: 'warning',
    HIT: 'success',
    PENDING: 'warning',
    REJECTED: 'info',
    REFUNDED: 'success',
    FAILED: 'danger'
  }[s] || 'info'
}

async function loadStats() {
  try {
    const res = await auditStats(days.value)
    Object.assign(stats, res.data || {})
  } catch {
    // 忽略
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await auditFeed({
      type: type.value || undefined,
      status: status.value || undefined,
      current: page.current,
      size: page.size,
      days: days.value
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
    loadStats()
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

function setType(value) {
  if (type.value === value) return
  type.value = value
  // 切换类型后旧状态可能不在新选项里，清掉避免无效筛选
  if (status.value && !statusOptions.value.some(o => o.value === status.value)) {
    status.value = ''
  }
  reload()
}

function reload() {
  page.current = 1
  loadData()
}

function go(target) {
  emit('navigate')
  router.push(target)
}

function startTimer() {
  stopTimer()
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    if (active && !loading.value) loadData()
  }, 15000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, () => {
  if (autoRefresh.value) startTimer()
  else stopTimer()
})

onMounted(() => {
  loadData()
  startTimer()
})
onActivated(() => {
  active = true
  loadData()
  startTimer()
})
onDeactivated(() => {
  active = false
  stopTimer()
})
onBeforeUnmount(() => {
  active = false
  stopTimer()
})
</script>

<style scoped>
.audit-page {
  width: 100%;
}

.audit-head {
  margin-bottom: 14px;
}

.audit-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.audit-actions {
  gap: 8px;
}

.live-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-2);
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34c759;
}

.live-state.off .live-dot {
  background: var(--text-3);
}

.audit-stats {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.audit-stat {
  padding: 15px 18px 14px;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 0;
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.stat-dot.ai { background: #8e8e93; }
.stat-dot.exp { background: #34c759; }
.stat-dot.refund { background: #ff9500; }
.stat-dot.fail { background: #ff3b30; }

.stat-num {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--text-1);
}

.audit-toolbar {
  flex-shrink: 0;
}

.toolbar-hint {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.cell-time {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.cell-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

.detail-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-2);
}

.row-action {
  color: var(--text-2);
  font-size: 13px;
}

.row-action:hover {
  color: var(--text-1) !important;
  background: rgba(0, 0, 0, 0.04);
}

.audit-pager {
  justify-content: flex-end;
  flex-shrink: 0;
}

:deep(.el-table__row:hover > td.el-table__cell) {
  background: #f5f5f7 !important;
}
</style>
