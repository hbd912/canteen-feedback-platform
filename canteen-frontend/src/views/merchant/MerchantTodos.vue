<template>
  <div class="ap-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">待办</h1>
        <div class="ap-sub">
          {{ todos.length ? `共 ${totalCount} 件待处理` : '今天没有待办，一切正常' }}
          <template v-if="todos.length && todos[0].latestTime"> · 最晚一条 {{ todos[0].latestTime }}</template>
        </div>
      </div>
      <div class="ap-actions">
        <button class="ap-btn ap-btn--light ap-btn--icon" title="刷新" :disabled="loading" @click="loadData">
          <el-icon :size="16"><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div class="ap-card todo-card">
      <!-- 小助手横幅：常驻，以助手口吻对话；有待办时提出帮忙，点击才开始处理 -->
      <div class="assistant-banner" :class="{ idle: !todos.length }">
        <img class="asst-avatar" src="/ui/assistant/happy.png" alt="小助手" />
        <div class="asst-text">
          <span class="asst-speak">
            {{ todos.length
              ? `看你还有 ${totalCount} 件待办没处理，要我帮你处理吗？出餐、回消息、回评价我都行～`
              : '目前没有待办，我都帮你盯着，一有新的就提醒你～' }}
          </span>
          <span class="asst-appended">
            {{ todos.length ? '退款单我会留给你人工确认，不替你乱来。' : '有别的需要也可以去「经营助理」直接找我。' }}
          </span>
        </div>
        <button
          v-if="todos.length"
          class="ap-btn ap-btn--sm asst-run"
          :disabled="aiLoading"
          @click="handleAiProcess"
        >
          <span v-if="aiLoading" class="asst-spin"></span>
          <el-icon v-else :size="14" class="asst-spark"><MagicStick /></el-icon>
          {{ aiLoading ? '处理中' : '好呀，帮我去弄' }}
        </button>
      </div>

      <div v-if="!todos.length && !loading" class="ap-empty">
        <div class="ap-empty-mark"><el-icon :size="19"><CircleCheckFilled /></el-icon></div>
        <div class="ap-empty-title">今天没有待办</div>
        <div>新订单、退款和消息会在这里提醒你</div>
      </div>

      <div v-else class="todo-list">
        <div v-for="t in todos" :key="t.type" class="todo-item" @click="go(t)">
          <div class="todo-badge">
            <span class="todo-dot" :style="{ background: badgeColor(t.type) }"></span>
            <span class="todo-badge-text">{{ badgeText(t.type) }}</span>
          </div>
          <div class="todo-main">
            <div class="todo-title">
              {{ t.title }}
              <span v-if="t.count > 1" class="todo-count">{{ t.count }} 件</span>
            </div>
            <div v-if="t.content" class="todo-content">{{ t.content }}</div>
            <div v-if="t.latestTime" class="todo-time">{{ t.latestTime }}</div>
          </div>
          <div class="todo-actions" @click.stop>
            <el-button v-if="t.doneId" size="small" @click="handleDone(t)">已处理</el-button>
            <button v-else-if="t.targetUrl" class="todo-go" @click="go(t)">
              去处理
              <el-icon :size="12"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="reportVisible" title="AI 一键处理完成" width="480px" :close-on-click-modal="false">
      <div class="report-summary">{{ report?.summary }}</div>
      <div class="report-stats">
        <div class="report-stat"><b>{{ report?.orderDone || 0 }}</b><span>出餐完成</span></div>
        <div class="report-stat"><b>{{ report?.csReplied || 0 }}</b><span>客服回复</span></div>
        <div class="report-stat"><b>{{ report?.reviewReplied || 0 }}</b><span>评价回复</span></div>
        <div class="report-stat warn"><b>{{ report?.refundSkipped || 0 }}</b><span>退款待人工</span></div>
      </div>
      <template #footer>
        <el-button type="primary" @click="reportVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listTodoSummary, doneTodo, aiProcessTodos, getAiProcessStatus } from '@/api/merchant'

const router = useRouter()
const loading = ref(false)
const todos = ref([])
const aiLoading = ref(false)
const reportVisible = ref(false)
const report = ref(null)

const totalCount = computed(() => todos.value.reduce((s, t) => s + (t.count || 0), 0))

function badgeColor(type) {
  return {
    ORDER: '#0a84ff',
    REFUND: '#ff9f0a',
    MESSAGE: '#30d158',
    REVIEW: '#bf5af2',
    BAD_REVIEW: '#ff453a',
    INSPECT: '#ff9f0a'
  }[type] || '#8e8e93'
}

function badgeText(type) {
  return {
    ORDER: '订单',
    REFUND: '退款',
    MESSAGE: '消息',
    REVIEW: '评价',
    BAD_REVIEW: '差评',
    INSPECT: '整改'
  }[type] || '待办'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listTodoSummary()
    todos.value = res.data || []
  } finally {
    loading.value = false
  }
}

function go(t) {
  if (!t.targetUrl) return
  const query = {}
  if (t.targetId) query.highlight = t.targetId
  // 新订单待办：跳转订单处理时直接筛到「待取餐」，聚焦待出餐订单
  if (t.type === 'ORDER') query.status = 'PENDING'
  router.push({ path: t.targetUrl, query })
}

async function handleDone(t) {
  await doneTodo(t.doneId)
  ElMessage.success('已标记处理')
  loadData()
}

let aiTimer = null

// 触发后接口立即返回，后台异步跑；这里轮询状态直到完成
async function handleAiProcess() {
  aiLoading.value = true
  try {
    await aiProcessTodos()
    ElMessage.info('AI 正在自动处理，完成后会通知你')
    pollAiStatus()
  } catch {
    aiLoading.value = false
  }
}

async function pollAiStatus() {
  if (aiTimer) {
    clearInterval(aiTimer)
    aiTimer = null
  }
  // 最多轮询 5 分钟：后端若一直不返回 running=false（缺字段/卡死），也不能让按钮永远转圈、轮询永不终止
  let waited = 0
  const MAX_WAIT = 150 // 2s * 150 = 300s
  aiTimer = setInterval(async () => {
    let st
    try {
      st = (await getAiProcessStatus()).data || {}
    } catch {
      // 状态接口异常不中断轮询
    }
    waited += 1
    const done = st && st.running === false
    if (done || waited >= MAX_WAIT) {
      clearInterval(aiTimer)
      aiTimer = null
      aiLoading.value = false
      if (st && st.error) {
        ElMessage.error(st.error)
      } else if (done && st.report) {
        report.value = st.report
        reportVisible.value = true
        ElMessage.success('AI 已自动处理完成')
      } else if (waited >= MAX_WAIT) {
        ElMessage.warning('AI 处理超时，可再试一次或手动处理')
      }
      loadData()
    }
  }, 2000)
}

function onCsUnreadChanged() {
  // 铃铛里「全部已读」或客服页读了消息后，待办里的「N 条客服消息待回复」也要跟着刷新
  loadData()
}

onBeforeUnmount(() => {
  if (aiTimer) {
    clearInterval(aiTimer)
    aiTimer = null
  }
  window.removeEventListener('cs:unread-changed', onCsUnreadChanged)
})

onMounted(() => {
  loadData()
  window.addEventListener('cs:unread-changed', onCsUnreadChanged)
})
onActivated(loadData)
</script>

<style scoped>
.todo-card { padding: 8px 24px 6px; }

.todo-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 15px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.todo-item:last-child { border-bottom: none; }
.todo-item:hover { background: #fafafa; }

.todo-badge {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 64px;
}

.todo-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.todo-badge-text {
  font-size: 11.5px;
  font-weight: 550;
  color: var(--text-3);
  letter-spacing: 0.02em;
}

.todo-main {
  flex: 1;
  min-width: 0;
}

.todo-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-count {
  flex-shrink: 0;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-2);
  background: #f5f5f7;
  padding: 1px 8px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

.todo-content {
  font-size: 12.5px;
  color: var(--text-2);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-time {
  font-size: 11px;
  color: var(--text-3);
  margin-top: 3px;
}

.todo-actions { flex-shrink: 0; display: flex; align-items: center; }

.todo-go {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 999px;
  transition: color 0.15s ease, background-color 0.15s ease;
}
.todo-go:hover { color: var(--text-1); background: #f5f5f7; }

/* ===== 小助手横幅：中性提示条，唯一橙色给 AI 操作 ===== */
.assistant-banner {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 14px 16px;
  margin: 8px 0 4px;
  background: #f5f5f7;
  border-radius: 16px;
}
.asst-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
}
.assistant-banner.idle { background: #f5f5f7; }
.asst-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.asst-speak { font-size: 13.5px; color: var(--text-1); line-height: 1.6; }
.asst-appended { font-size: 12px; color: var(--text-3); }
.asst-run { flex-shrink: 0; }
.asst-spark { color: rgba(255, 255, 255, 0.92); }
.asst-run:disabled { opacity: 0.65; cursor: not-allowed; }
.asst-spin {
  width: 13px; height: 13px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.35); border-top-color: #fff;
  animation: asst-spin 0.8s linear infinite;
}
@keyframes asst-spin { to { transform: rotate(360deg); } }

.report-summary {
  font-size: 14px;
  color: var(--text-1);
  line-height: 1.6;
  background: #f5f5f7;
  border-radius: 12px;
  padding: 13px 15px;
  margin-bottom: 16px;
}

.report-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.report-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 6px 13px;
  background: #fafafa;
  border-radius: 14px;
}

.report-stat b {
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.report-stat span {
  font-size: 11.5px;
  color: var(--text-3);
}

.report-stat.warn b { color: var(--text-1); }
.report-stat.warn span::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  margin-right: 5px;
  vertical-align: 1px;
}
</style>
