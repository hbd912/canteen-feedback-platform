<template>
  <div class="ap-page wb-page">
    <header class="ap-head wb-head">
      <div>
        <div class="wb-eyebrow">{{ dateText }}</div>
        <h1 class="ap-title">{{ greeting }}，{{ adminName }}</h1>
        <div class="ap-sub">
          {{ loading && !loaded ? '正在加载今天的经营概况…' : (todos.length ? `今天有 ${totalCount} 项待处理` : '今天没有待办，一切正常') }}
        </div>
      </div>
    </header>

    <section class="wb-stats ap-grid ap-grid--4">
      <div class="wb-stat ap-kpi">
        <div class="ap-stat-label">今日订单</div>
        <div class="ap-stat-value wb-stat-value">{{ stats.todayOrders ?? 0 }}<span class="ap-stat-unit">单</span></div>
        <div class="ap-stat-hint">已支付</div>
      </div>
      <div class="wb-stat ap-kpi">
        <div class="ap-stat-label">今日营业额</div>
        <div class="ap-stat-value wb-stat-value"><span class="wb-yen">¥</span>{{ revenueText }}</div>
        <div class="ap-stat-hint">今日累计</div>
      </div>
      <div class="wb-stat ap-kpi">
        <div class="ap-stat-label">待上架审核</div>
        <div class="ap-stat-value wb-stat-value">{{ stats.pendingDishes ?? 0 }}<span class="ap-stat-unit">道</span></div>
        <div class="ap-stat-hint">商家提交</div>
      </div>
      <div class="wb-stat ap-kpi">
        <div class="ap-stat-label">待处理留言</div>
        <div class="ap-stat-value wb-stat-value">{{ pendingNotices }}<span class="ap-stat-unit">条</span></div>
        <div class="ap-stat-hint">学生 + 商家</div>
      </div>
    </section>

    <section class="wb-section">
      <div class="wb-section-head">
        <div class="wb-section-heading">
          <h2 class="wb-section-title">待处理</h2>
          <span v-if="totalCount" class="wb-count-pill">{{ totalCount }}</span>
        </div>
        <div class="wb-section-actions">
          <button class="ap-btn ap-btn--light ap-btn--sm" type="button" @click="goRefunds">
            退款记录 {{ stats.refundRecords ?? 0 }} 笔
          </button>
          <button class="wb-refresh" :class="{ spinning: loading }" title="刷新" @click="load">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 1 1-2.64-6.36"/><polyline points="21 3 21 9 15 9"/>
            </svg>
          </button>
        </div>
      </div>

      <div v-if="todos.length" class="ap-card wb-list">
        <button v-for="t in todos" :key="t.type" class="wb-row" type="button" @click="go(t)">
          <span class="wb-dot" :class="{ 'is-urgent': t.type === 'DISH_AUDIT' }"></span>
          <span class="wb-row-main">
            <span class="wb-row-title">{{ t.title }}</span>
            <span class="wb-row-sub">{{ t.content }}</span>
          </span>
          <span class="wb-row-count">{{ t.count }}</span>
          <span class="wb-chevron" aria-hidden="true">›</span>
        </button>
      </div>

      <div v-else-if="loaded" class="ap-card wb-empty-card">
        <div class="ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><CircleCheck /></el-icon></div>
          <div class="ap-empty-title">暂无待办</div>
          <div>菜品审核、评价和留言都处理完了</div>
        </div>
      </div>

      <div v-else class="ap-card wb-empty-card">
        <div class="ap-empty">
          <div class="ap-empty-title">正在加载待办…</div>
        </div>
      </div>
    </section>

    <AdminAiChat :pending-total="totalCount" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getAdminWorkbench } from '@/api/adminCenter'
import AdminAiChat from './workbench/AdminAiChat.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const loaded = ref(false)
const todos = ref([])
const stats = ref({})

const totalCount = computed(() => todos.value.reduce((sum, item) => sum + (item.count || 0), 0))
const pendingNotices = computed(() =>
  Number(stats.value.pendingFeedbacks || 0) + Number(stats.value.pendingMessages || 0)
)
const adminName = computed(() => userStore.userInfo?.realName || userStore.nickname || '管理员')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const dateText = computed(() => {
  const date = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][date.getDay()]
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日 · 星期${week}`
})

const revenueText = computed(() => {
  const value = Number(stats.value.todayRevenue || 0)
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})

function dotColor(type) {
  return {
    DISH_AUDIT: '#ff9500',
    REVIEW: '#af52de',
    FEEDBACK: '#0071e3',
    MESSAGE: '#30b0c7'
  }[type] || '#8e8e93'
}

async function load() {
  loading.value = true
  try {
    const res = await getAdminWorkbench()
    const data = res.data || {}
    todos.value = data.todos || []
    stats.value = data
  } catch {
    todos.value = []
    stats.value = {}
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function go(todo) {
  if (todo.targetUrl) router.push(todo.targetUrl)
}

function goRefunds() {
  router.push('/admin/refunds')
}

onMounted(load)
onActivated(load)
</script>

<style scoped>
.wb-page {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
}

.wb-head { margin-bottom: 22px; }
.wb-eyebrow {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.01em;
  color: var(--text-3);
}

.wb-stats { margin-bottom: 28px; }

.wb-stat {
  border-radius: 22px;
  padding: 22px 24px 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03), 0 10px 30px rgba(0, 0, 0, 0.045);
  transition: none;
}
.wb-stat:hover {
  border-color: var(--border);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03), 0 10px 30px rgba(0, 0, 0, 0.045);
}
.wb-stat .ap-stat-label { font-size: 12px; font-weight: 500; color: var(--text-2); }
.wb-stat .ap-stat-hint { margin-top: 9px; font-size: 12px; color: #8e8e93; }
.wb-stat .ap-stat-unit { font-size: 13px; color: #8e8e93; }
.wb-stat-value {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 650;
  line-height: 1;
  gap: 3px;
}
.wb-yen {
  margin-right: 1px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-2);
  letter-spacing: 0;
}

.wb-section { margin-top: 0; }
.wb-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 2px 12px;
}
.wb-section-heading { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.wb-section-title {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}
.wb-count-pill {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-dark);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.wb-section-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.wb-refresh {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text-3);
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}
.wb-refresh:hover { background: rgba(0, 0, 0, 0.05); color: var(--text-1); }
.wb-refresh.spinning svg { animation: wb-spin 0.9s linear infinite; }
@keyframes wb-spin { to { transform: rotate(360deg); } }

.wb-list { padding: 4px 22px; overflow: hidden; }
.wb-row {
  appearance: none;
  border: none;
  background: transparent;
  width: calc(100% + 20px);
  margin: 0 -10px;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 10px;
  border-radius: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple);
}
.wb-row + .wb-row { border-top: 1px solid rgba(0, 0, 0, 0.045); }
.wb-row:hover { background: #fafafa; }
.wb-dot { width: 7px; height: 7px; border-radius: 50%; background: #d2d2d7; flex-shrink: 0; }
.wb-dot.is-urgent { background: var(--brand); }
.wb-row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.wb-row-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
}
.wb-row-sub {
  font-size: 12px;
  color: #8e8e93;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb-row-count {
  flex-shrink: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}
.wb-chevron {
  flex-shrink: 0;
  font-size: 19px;
  line-height: 1;
  color: var(--text-3);
  transition: transform 0.15s var(--ease-apple), color 0.15s var(--ease-apple);
}
.wb-row:hover .wb-chevron { transform: translateX(2px); color: var(--text-2); }

.wb-empty-card { padding: 0; }
.wb-empty-card .ap-empty { padding: 52px 24px; }

@media (max-width: 760px) {
  .wb-stat { border-radius: 20px; padding: 18px 18px 16px; }
  .wb-stat-value { font-size: 30px; }
  .wb-section-head { align-items: flex-end; }
  .wb-section-actions { gap: 4px; }
  .wb-list { padding: 2px 16px; }
  .wb-row { padding: 14px 8px; width: calc(100% + 16px); margin: 0 -8px; }
}
</style>
