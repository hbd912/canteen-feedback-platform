<template>
  <div class="page-container adaptive-page chatlog-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">问答记录</h1>
        <p class="ap-sub">统计客服问答与命中情况，未命中的高频问题可补录进知识库。</p>
      </div>
    </div>

    <div class="ap-card stats-band">
      <div class="stat-item">
        <span class="ap-stat-label">累计提问</span>
        <span class="ap-stat-value ap-num">{{ stats.total || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="ap-stat-label">命中率</span>
        <span class="ap-stat-value ap-num">
          {{ stats.hitRate ?? 0 }}<small>%</small>
        </span>
      </div>
      <div class="stat-item">
        <span class="ap-stat-label">
          <i class="stat-dot"></i>
          未命中
        </span>
        <span class="ap-stat-value ap-num">{{ stats.missCount || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="ap-stat-label">回答满意度</span>
        <span class="ap-stat-value ap-num">
          {{ stats.satisfaction ?? '—' }}<small v-if="stats.satisfaction">%</small>
        </span>
      </div>
    </div>

    <div class="page-card miss-card">
      <div class="card-toolbar">
        <div class="toolbar-main">
          <span class="section-title">未命中问题 TOP10</span>
          <span class="section-hint">可补录进知识库</span>
        </div>
        <button
          v-if="missList.length > 5"
          type="button"
          class="ap-btn ap-btn--ghost ap-btn--sm"
          @click="showAllMiss = !showAllMiss"
        >
          {{ showAllMiss ? '收起' : '查看全部' }}
        </button>
      </div>

      <div v-loading="missLoading" class="miss-body">
        <div v-if="missList.length" class="ap-list">
          <div v-for="row in (showAllMiss ? missList : missList.slice(0, 5))" :key="row.question" class="ap-row">
            <div class="ap-row-icon">
              <el-icon :size="17"><QuestionFilled /></el-icon>
            </div>
            <div class="ap-row-main">
              <div class="ap-row-title miss-question">{{ row.question }}</div>
              <div class="ap-row-sub">{{ row.count }} 次提问 · 最近 {{ row.lastTime || '—' }}</div>
            </div>
            <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="toFaq(row)">
              补录为问答
            </button>
          </div>
        </div>
        <div v-else-if="!missLoading" class="ap-empty">
          <div class="ap-empty-mark">
            <el-icon :size="20"><CircleCheck /></el-icon>
          </div>
          <div class="ap-empty-title">暂无未命中问题</div>
          <div>客服问答目前都有匹配答案</div>
        </div>
      </div>
    </div>

    <div class="page-card records-card">
      <div class="card-toolbar">
        <div class="toolbar-main">
          <span class="section-title">问答记录</span>
          <span class="section-hint">共 {{ total }} 条</span>
        </div>
      </div>

      <div class="adaptive-table">
        <el-table :data="list" v-loading="loading" class="chat-table">
          <el-table-column prop="question" label="提问" min-width="220">
            <template #default="{ row }">
              <span class="question-text">{{ row.question }}</span>
            </template>
          </el-table-column>
          <el-table-column label="提问人" width="130">
            <template #default="{ row }">
              <span class="cell-muted">{{ row.realName || row.username || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="命中意图" width="130">
            <template #default="{ row }">
              <span class="hit-cell" :class="row.hit === 1 ? 'is-hit' : 'is-miss'">
                <i class="hit-dot"></i>
                {{ row.intentLabel || (row.hit === 1 ? '已回答' : '未命中') }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="用户评价" width="110">
            <template #default="{ row }">
              <span v-if="row.helpful === 1" class="helpful is-good">有帮助</span>
              <span v-else-if="row.helpful === 0" class="helpful is-bad">没帮助</span>
              <span v-else class="helpful is-none">未评价</span>
            </template>
          </el-table-column>
          <el-table-column prop="costMs" label="耗时(ms)" width="100" align="right">
            <template #default="{ row }">
              <span class="cell-muted ap-num">{{ row.costMs ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提问时间" width="170">
            <template #default="{ row }">
              <span class="cell-muted ap-num">{{ row.createTime || '—' }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark">
                <el-icon :size="20"><ChatLineSquare /></el-icon>
              </div>
              <div class="ap-empty-title">暂无问答记录</div>
              <div>学生向客服提问后会显示在这里</div>
            </div>
          </template>
        </el-table>
      </div>

      <div class="adaptive-pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :current-page="current"
          :page-size="size"
          @current-change="changePage"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onActivated, ref } from 'vue'
import { getChatLogs, getChatMissTop, getChatStats } from '@/api/chat'

const stats = ref({})
const missList = ref([])
const list = ref([])
const total = ref(0)
const current = ref(1)
const size = 10
const loading = ref(false)
const missLoading = ref(false)
const showAllMiss = ref(false)

async function loadStats() {
  const res = await getChatStats()
  stats.value = res.data || {}
}

async function loadMiss() {
  missLoading.value = true
  try {
    const res = await getChatMissTop(10)
    missList.value = res.data || []
  } finally {
    missLoading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await getChatLogs({ current: current.value, size })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function changePage(page) {
  current.value = page
  loadLogs()
}

function toFaq(row) {
  // 通过全局事件通知「客服中心」切换到知识库 tab 并预填问题
  window.dispatchEvent(new CustomEvent('canteen:faq-preset', { detail: { question: row.question } }))
}

onMounted(() => {
  loadStats()
  loadMiss()
  loadLogs()
})

// keep-alive 缓存后回访刷新问答记录
onActivated(() => {
  loadStats()
  loadMiss()
  loadLogs()
})
</script>

<style scoped>
/* 铺满高度：页头、指标、未命中列表保持自然高度，问答记录表格占满剩余空间 */
.chatlog-page > .ap-head,
.chatlog-page > .ap-card,
.chatlog-page > .page-card:not(:last-child) {
  flex-shrink: 0;
}

.chatlog-page > .page-card:last-child {
  flex: 1;
  min-height: 0;
}

.chatlog-page > .ap-card {
  margin-bottom: 16px;
}

.chatlog-page > .page-card:not(:last-child) {
  margin-bottom: 16px;
}

/* ---------- 统计带 ---------- */
.stats-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 18px 8px;
}

.stat-item {
  padding: 2px 20px;
  border-left: 1px solid rgba(0, 0, 0, 0.05);
  min-width: 0;
}

.stat-item:first-child {
  border-left: none;
}

.stats-band .ap-stat-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.stats-band .ap-stat-value {
  margin-top: 6px;
  font-size: 30px;
  font-weight: 650;
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  flex-shrink: 0;
}

/* ---------- 卡片头 ---------- */
.card-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.toolbar-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.section-title {
  font-size: 15px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.section-hint {
  font-size: 12px;
  color: var(--text-3);
}

/* ---------- 未命中列表 ---------- */
.miss-card {
  padding: 18px 22px 8px;
}

.miss-body {
  min-height: 60px;
}

.miss-body .ap-row {
  padding: 13px 2px;
}

.miss-body :deep(.ap-row-icon) {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 15px;
  color: var(--text-2);
}

.miss-question {
  font-weight: 500;
}

/* ---------- 记录卡片 ---------- */
.records-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px 22px 8px;
}

.records-card .card-toolbar {
  margin-bottom: 4px;
}

.chat-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.chat-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.question-text {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-1);
}

.cell-muted {
  font-size: 12.5px;
  color: var(--text-2);
}

.hit-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-2);
  white-space: nowrap;
}

.hit-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff9500;
}

.hit-cell.is-hit .hit-dot {
  background: #34c759;
}

.helpful {
  font-size: 12.5px;
}

.helpful.is-good {
  color: #1d8a45;
}

.helpful.is-bad {
  color: #c9342b;
}

.helpful.is-none {
  color: var(--text-3);
}

.adaptive-pager {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

@media (max-width: 900px) {
  .stats-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 18px;
  }

  .stat-item:nth-child(3) {
    border-left: none;
  }
}
</style>
