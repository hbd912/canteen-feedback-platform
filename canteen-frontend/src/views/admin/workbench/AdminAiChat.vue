<template>
  <section class="wb-chat" :class="{ collapsed: !expanded, fullscreen }">
    <!-- 折叠态：小卡片，只显示一句话 + 开始整理按钮 -->
    <template v-if="!expanded">
      <div class="pad-card">
        <img class="pad-avatar" src="/ui/assistant/happy.png" alt="" />
        <div class="pad-copy">
          <div class="pad-title">值班助理</div>
          <div class="pad-sub">{{ hasRan ? '已整理过，点开可查看动态与待人工处理项。' : `今天有 ${pendingTotal} 项待处理，我帮你处理吧。` }}</div>
        </div>
        <button class="ap-btn pad-btn" type="button" @click="expand">开始整理</button>
      </div>
    </template>

    <!-- 展开态：对话窗口（双击标题栏放大/还原） -->
    <template v-else>
      <header class="chat-head" @dblclick="toggleFullscreen">
        <div class="chat-head-left">
          <img class="chat-avatar" src="/ui/assistant/happy.png" alt="" />
          <div>
            <div class="chat-title">值班助理</div>
            <div class="chat-sub">我帮你处理吧，敏感的才留给你拍板</div>
          </div>
        </div>
        <div class="chat-head-actions">
          <button class="ap-btn ap-btn--light ap-btn--sm badge-hist" type="button" @click="historyOpen = !historyOpen" :disabled="loading">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
            <span>历史会话</span>
          </button>
          <button class="ap-btn ap-btn--light ap-btn--sm badge-new" type="button" @click="newChat" :disabled="loading">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
            <span>新建对话</span>
          </button>
          <button class="ap-btn ap-btn--light ap-btn--sm badge-fs" type="button" @click="toggleFullscreen" :title="fullscreen ? '还原窗口（Esc）' : '放大窗口（双击标题栏也可）'">
            <svg v-if="!fullscreen" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg>
            <svg v-else viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"/></svg>
            <span>{{ fullscreen ? '还原' : '放大' }}</span>
          </button>
          <button class="ap-btn ap-btn--light ap-btn--sm badge-close" type="button" @click="collapse" title="收起">
            <span>收起</span>
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>
          </button>
        </div>
      </header>

      <!-- 历史会话面板（服务端持久化：换浏览器/清缓存不丢） -->
      <transition name="fade">
        <div v-if="historyOpen" class="hist-panel">
          <div class="hist-head">
            <span>历史会话</span>
            <button class="hist-close" type="button" @click="historyOpen = false">✕</button>
          </div>
          <div class="hist-list">
            <div
              v-for="s in sessions"
              :key="s.id"
              class="hist-item-wrap"
              :class="{ active: s.id === activeId }"
            >
              <button type="button" class="hist-item" @click="switchSession(s.id)">
                <span class="hist-title">{{ s.title || '新会话' }}</span>
                <span class="hist-time">{{ s.time }}</span>
              </button>
              <button class="hist-del" type="button" title="删除会话" @click.stop="removeSession(s.id)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/></svg>
              </button>
            </div>
            <div v-if="!sessions.length" class="hist-empty">还没有历史会话</div>
          </div>
        </div>
      </transition>

      <div v-if="autoDone.count" class="auto-strip">
        <span class="auto-dot"></span>
        <span>已自动回复并标记处理 <b>{{ autoDone.count }}</b> 条</span>
        <button class="auto-toggle" type="button" @click="autoOpen = !autoOpen">
          {{ autoOpen ? '收起' : '查看明细' }} ›
        </button>
      </div>
      <div v-if="autoOpen && autoDone.list.length" class="auto-list">
        <div class="auto-item" v-for="item in autoDone.list" :key="item.id + '-auto'">
          <span class="auto-check"><el-icon :size="12"><Check /></el-icon></span>
          <span class="auto-text">{{ item.title }} · {{ item.suggestedReply ? '已回复' : '已处理' }}</span>
        </div>
      </div>

      <div ref="bodyRef" class="chat-body">
        <div
          v-for="(message, index) in messages"
          :key="index"
          class="chat-row"
          :class="message.role === 'user' ? 'is-user' : 'is-ai'"
        >
          <div
            class="chat-bubble"
            :class="{
              'user-bubble': message.role === 'user',
              'insight-bubble': message.kind === 'insight',
              'report-bubble': message.kind === 'report'
            }"
          >
            <template v-if="message.kind === 'welcome'">
              <p class="chat-text">{{ message.content || '正在看看今天的待办…' }}</p>
              <button class="chat-primary" type="button" @click="startAnalysis">
                开始整理当前待处理
              </button>
            </template>

            <template v-else-if="message.kind === 'divider'">
              <div class="chat-divider"><span>—— 新会话 ——</span></div>
            </template>

            <template v-else-if="message.kind === 'insight'">
              <p class="chat-text insight-summary">{{ message.insight.summary }}</p>

              <div class="insight-meta">
                <span class="insight-window">当前待处理 · {{ message.insight.totalItems }} 条</span>
                <span v-if="message.insight.degraded" class="chat-badge inline">规则版</span>
              </div>
              <div v-if="message.insight.aiNote" class="insight-note">{{ message.insight.aiNote }}</div>

              <!-- 三格结论：需你手动 / 可自动处理 / 已处理完 -->
              <div v-if="message.insight.needActionCount || message.insight.autoHandleableCount || message.insight.autoHandledCount" class="insight-split">
                <div class="split-cell">
                  <div class="split-num need">{{ message.insight.needActionCount }}</div>
                  <div class="split-label">需你手动</div>
                </div>
                <div class="split-cell">
                  <div class="split-num auto">{{ message.insight.autoHandleableCount || 0 }}</div>
                  <div class="split-label">可自动处理</div>
                </div>
                <div class="split-cell">
                  <div class="split-num done">{{ message.insight.autoHandledCount || 0 }}</div>
                  <div class="split-label">已处理完</div>
                </div>
              </div>

              <!-- 需你手动：点击跳转 + 高亮闪烁定位 -->
              <div v-if="message.insight.needAction?.length" class="insight-block">
                <div class="block-head">
                  <span class="need-label">需要你手动</span>
                  <span>敏感 / 高金额 / 安全类，点击即可定位</span>
                </div>
                <button
                  v-for="item in message.insight.needAction.slice(0, 8)"
                  :key="item.id"
                  class="priority-row"
                  type="button"
                  @click="go(item)"
                >
                  <span class="severity" :class="item.severity"><i></i>{{ item.severityLabel }}</span>
                  <span class="priority-main">
                    <span class="priority-title">{{ item.title }}</span>
                    <span class="priority-sub">{{ item.stallName || '平台' }} · {{ item.count }} 条 · {{ item.users }} 人 · 点击定位</span>
                  </span>
                  <span class="priority-action">定位处理 ›</span>
                </button>
              </div>

              <!-- 优先级排名：默认折叠 -->
              <details v-if="chartRows(message.insight).length" class="insight-fold">
                <summary>
                  <span class="fold-title">优先级排名</span>
                  <span class="fold-hint">越靠前越需要你处理</span>
                  <span class="fold-arrow" aria-hidden="true"></span>
                </summary>
                <div class="fold-body">
                  <div class="insight-chart">
                    <EChart :option="chartOption(message.insight)" height="200px" />
                  </div>
                </div>
              </details>

              <!-- 可自动处理：助手能代劳，默认折叠 -->
              <details v-if="message.insight.autoHandleable?.length" class="insight-fold">
                <summary>
                  <span class="fold-title">可以自动处理</span>
                  <span class="fold-hint">{{ message.insight.autoHandleable.length }} 项 · 无需你审核</span>
                  <span class="fold-arrow" aria-hidden="true"></span>
                </summary>
                <div class="fold-body">
                  <div v-for="item in message.insight.autoHandleable.slice(0, 6)" :key="item.id" class="auto-row">
                    <span class="auto-check"><el-icon :size="12"><Check /></el-icon></span>
                    <span class="auto-row-title">{{ item.title }}</span>
                    <span class="auto-row-sub">{{ item.stallName || '平台' }} · {{ item.count }} 条</span>
                  </div>
                </div>
              </details>

              <!-- 处理明细：处理了什么、怎么回的，默认折叠 -->
              <details v-if="message.insight.handledDetails?.length" class="insight-fold">
                <summary>
                  <span class="fold-title">处理明细</span>
                  <span class="fold-hint">共 {{ message.insight.handledDetails.length }} 条</span>
                  <span class="fold-arrow" aria-hidden="true"></span>
                </summary>
                <div class="fold-body">
                  <div class="detail-table">
                    <div class="detail-row detail-head">
                      <span>类型</span><span>对象</span><span>处理动作</span>
                    </div>
                    <div v-for="(d, i) in message.insight.handledDetails" :key="i" class="detail-row">
                      <span class="detail-type">{{ d.sourceLabel }}</span>
                      <span class="detail-title">{{ d.title }}</span>
                      <span class="detail-action">{{ d.action }}<template v-if="d.count > 1">（{{ d.count }} 条）</template></span>
                      <div v-if="d.reply" class="detail-reply">回复：{{ d.reply }}</div>
                    </div>
                  </div>
                </div>
              </details>

              <div class="insight-foot">
                <p class="chat-guard">处罚、上架审核等不代执行，会留给你人工确认；退款由商家端审核，后台只记录。</p>
                <button type="button" class="text-btn refresh-btn" :disabled="loading" @click="startAnalysis">
                  重新整理
                </button>
              </div>
            </template>

            <!-- 统计后的选择提问：单独成一条 AI 消息，紧跟着统计气泡 -->
            <template v-else-if="message.kind === 'ask-handle'">
              <p class="chat-text">{{ message.content }}</p>
              <div v-if="!message.answered" class="agent-actions ask-actions">
                <button class="agent-btn primary" type="button" :disabled="loading" @click="chooseHandleAll(message)">
                  全部处理
                </button>
                <button class="agent-btn" type="button" :disabled="loading" @click="chooseHandleAuto(message)">
                  只处理免审核的
                </button>
              </div>
              <p v-else class="ask-answered">已选择：{{ message.answered }}</p>
            </template>

            <!-- 处理完之后的公告授权提问：单独成一条 AI 消息 -->
            <template v-else-if="message.kind === 'ask-announce'">
              <div class="announce-card">
                <div class="announce-head">这个问题比较严肃，建议发布公告提醒同学</div>
                <div class="announce-title">{{ message.announcementTitle }}</div>
                <div class="announce-content">{{ message.announcementContent }}</div>
                <div v-if="message.announceResult" class="announce-done">
                  {{ message.announceResult === 'published' ? '已发布公告' : '已选择暂不发布' }}
                </div>
                <div v-else class="agent-actions ask-actions">
                  <button class="agent-btn primary" type="button" :disabled="loading" @click="confirmAnnounce(message)">
                    授权发布
                  </button>
                  <button class="agent-btn" type="button" :disabled="loading" @click="declineAnnounce(message)">
                    暂不发布
                  </button>
                </div>
              </div>
            </template>

            <!-- 全平台销售分析报告：图表 + 表格，均可下载 -->
            <template v-else-if="message.kind === 'report'">
              <div v-if="message.report" class="report-card">
                <div class="report-head">
                  <span class="report-title">{{ message.report.title }}</span>
                  <span class="report-days">近 {{ message.report.days }} 天 · 全平台</span>
                </div>
                <p class="report-summary">{{ message.report.summary }}</p>
                <ul v-if="message.report.insights?.length" class="report-insights">
                  <li v-for="(line, i) in message.report.insights" :key="i">{{ line }}</li>
                </ul>
                <AgentDataCard
                  :charts="message.report.charts"
                  :tables="reportTables(message.report)"
                  :days="message.report.days"
                />
              </div>
              <p v-else class="chat-text">报告数据缺失，请重新生成。</p>
              <button type="button" class="text-btn refresh-btn" :disabled="loading" @click="runSalesReport(message.report?.days || periodDays(activePeriod))">
                再生成一份
              </button>
            </template>

            <template v-else-if="message.kind === 'error'">
              <p class="chat-text">{{ message.content }}</p>
              <button class="chat-primary" type="button" @click="retryLast">重试</button>
            </template>

            <template v-else>
              <p class="chat-text">{{ message.content }}</p>
            </template>
          </div>
        </div>

        <div v-if="loading" class="chat-row is-ai">
          <div class="typing">
            <img class="typing-avatar" src="/ui/assistant/think.png" alt="" />
            <span>{{ loadingText }}</span>
          </div>
        </div>
      </div>

      <div class="chat-quick">
        <div class="ap-pill-group">
          <button
            v-for="chip in quickChips"
            :key="chip.key"
            type="button"
            class="ap-pill"
            :class="{ active: activeSkill === chip.key }"
            :disabled="loading"
            @click="pickSkill(chip)"
          >{{ chip.label.replace(/^●\s*/, '') }}</button>
          <template v-if="activeSkill === 'sales'">
            <span class="quick-sep"></span>
            <button
              v-for="p in PERIODS"
              :key="p.key"
              type="button"
              class="ap-pill period-chip"
              :class="{ active: activePeriod === p.key }"
              :disabled="loading"
              @click="pickPeriod(p)"
            >{{ p.label }}</button>
          </template>
        </div>
      </div>

      <div class="chat-dock">
        <div class="chat-strip">
          <span>{{ skillHint }}</span>
        </div>
        <div class="chat-input-row">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="300"
            :placeholder="inputPlaceholder"
            @keydown.enter.exact.prevent="send"
          />
          <button class="send-btn" type="button" :disabled="loading || !input.trim()" @click="send">
            <el-icon :size="18"><Position /></el-icon>
          </button>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Position } from '@element-plus/icons-vue'
import EChart from '@/components/EChart.vue'
import AgentDataCard from '@/components/merchant/AgentDataCard.vue'
import {
  refreshAdminAiInsight, askAdminAiInsight, handleAllAdminAiInsight, handleAutoAdminAiInsight,
  getAdminAiGreeting, listAdminAiSessions, createAdminAiSession, deleteAdminAiSession,
  getAdminAiSessionMessages, syncAdminAiSessionMessages, getAdminSalesAnalysis
} from '@/api/adminCenter'
import { createNotice } from '@/api/notice'

const props = defineProps({
  pendingTotal: { type: Number, default: 0 }
})

const router = useRouter()
const expanded = ref(false)
// 全屏放大：覆盖整页看大图；会话状态不丢，还原即回原位
const fullscreen = ref(false)
const hasRan = ref(false)
const autoOpen = ref(false)
const messages = ref([])
const loading = ref(false)
const loadingText = ref('正在读取当前的留言和评价…')
const input = ref('')
const bodyRef = ref(null)
const autoDone = ref({ count: 0, list: [] })
const historyOpen = ref(false)
const sessions = ref([])
const activeId = ref(null)
const lastUserAsk = ref('')
// AI 研判建议发布公告时先挂起，等「全部处理 / 只处理免审核的」执行完再弹公告授权提问
const pendingAnnounce = ref(null)

// 快捷功能（参考商家经营助理的技能按钮）：待办整理 / 销售分析报告
const skills = [
  { key: 'todo', label: '待办事项' },
  { key: 'sales', label: '销售分析报告' }
]
// 销售分析报告的周期档位：填入对应话术（可改再发）
const PERIODS = [
  { key: '7d', label: '近7天', days: 7 },
  { key: '1m', label: '近1个月', days: 30 },
  { key: '1q', label: '近1个季度', days: 90 }
]
const activePeriod = ref('7d')
// 点技能按钮只把默认话术填进输入栏（可改再发），不自动发送
const SKILL_PROMPTS = {
  todo: '帮我整理当前的待处理，能处理的直接处理。',
  sales: '生成全平台近7天销售分析报告'
}
function salesPrompt(days) {
  const label = days === 30 ? '近1个月' : days === 90 ? '近1个季度' : `近${days}天`
  return `生成全平台${label}销售分析报告`
}
const activeSkill = ref('todo')
const quickChips = computed(() => skills.map(s => ({
  key: s.key,
  label: activeSkill.value === s.key ? `● ${s.label}` : s.label
})))
const skillHint = computed(() => activeSkill.value === 'sales'
  ? '将汇总全平台所有档口的订单：收入/退款/净收入对比、档口排行、菜品销量 TOP10，图表和表格都能下载。'
  : '可以问我：先处理哪些、哪些需要人工；或直接点上方「全部处理 / 只处理免审核的」。')
const inputPlaceholder = computed(() => activeSkill.value === 'sales'
  ? '例如：最近30天全平台卖得怎么样？'
  : '问问值班助理…')

function pickSkill(chip) {
  activeSkill.value = chip.key
  input.value = chip.key === 'sales' ? salesPrompt(periodDays(activePeriod.value)) : SKILL_PROMPTS[chip.key]
  nextTick(() => document.querySelector('.chat-input-row textarea')?.focus())
}

function periodDays(key) {
  return PERIODS.find(p => p.key === key)?.days || 7
}

function pickPeriod(p) {
  activePeriod.value = p.key
  input.value = salesPrompt(p.days)
  nextTick(() => document.querySelector('.chat-input-row textarea')?.focus())
}

function reportTables(report) {
  const tables = []
  if (report.table?.rows?.length) tables.push(report.table)
  if (report.monthTable?.rows?.length) tables.push(report.monthTable)
  if (report.dishTable?.rows?.length) tables.push(report.dishTable)
  if (report.stallTable?.rows?.length) tables.push(report.stallTable)
  return tables
}

function nowLabel() {
  const d = new Date()
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// ---- 服务端会话持久化 ----

async function loadSessions() {
  try {
    const res = await listAdminAiSessions()
    sessions.value = (res.data || []).map(s => ({
      id: s.id,
      title: s.title,
      time: fmtTime(s.lastMessageTime || s.createTime)
    }))
  } catch {
    sessions.value = []
  }
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  return nowLabelOf(d)
}
function nowLabelOf(d) {
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 把当前消息全量同步到服务端（防抖：一轮回答落库一次） */
let syncTimer = null
function scheduleSync() {
  if (!activeId.value) return
  clearTimeout(syncTimer)
  syncTimer = setTimeout(syncNow, 600)
}
async function syncNow() {
  const id = activeId.value
  if (!id || !messages.value.length) return
  try {
    await syncAdminAiSessionMessages(id, messages.value)
  } catch {
    // 同步失败不阻断使用；下一条消息会再尝试全量覆盖
  }
}

async function startSession(withGreeting = true) {
  let created = null
  try {
    const res = await createAdminAiSession()
    created = res.data
  } catch {
    ElMessage.error('会话创建失败，请稍后再试')
    return
  }
  const welcome = { role: 'assistant', kind: 'welcome', content: '' }
  sessions.value.unshift({ id: created.id, title: created.title || '新会话', time: nowLabel() })
  activeId.value = created.id
  messages.value = withGreeting ? [welcome] : []
  if (withGreeting) loadGreeting(welcome)
}

async function switchSession(id) {
  if (loading.value || id === activeId.value) { historyOpen.value = false; return }
  historyOpen.value = false
  loading.value = true
  loadingText.value = '正在载入会话…'
  try {
    const res = await getAdminAiSessionMessages(id)
    // content 是前端消息对象 JSON 快照，原样还原
    messages.value = (res.data || []).map(m => {
      try { return JSON.parse(m.content) } catch { return { role: 'assistant', kind: 'text', content: m.content } }
    })
    activeId.value = id
    historyOpen.value = false
    nextTick(scrollBottom)
  } catch {
    ElMessage.error('会话载入失败')
  } finally {
    loading.value = false
  }
}

async function removeSession(id) {
  try {
    await ElMessageBox.confirm('删除这条会话？历史消息将一并删除。', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteAdminAiSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (activeId.value === id) {
      activeId.value = null
      messages.value = []
      if (sessions.value.length) {
        switchSession(sessions.value[0].id)
      } else {
        startSession(true)
      }
    }
  } catch {
    ElMessage.error('删除失败，请稍后再试')
  }
}

async function loadGreeting(msg) {
  try {
    const res = await getAdminAiGreeting()
    if (res.data?.text) msg.content = res.data.text
  } catch {
    msg.content = '今天有 ' + props.pendingTotal + ' 项待处理，我帮你处理吧。'
  }
  scheduleSync()
}

function expand() {
  expanded.value = true
  if (!activeId.value && !sessions.value.length) {
    loadSessions().then(() => {
      if (sessions.value.length) {
        // 有历史会话：打开最近一条（服务端载入）
        switchSession(sessions.value[0].id)
      } else {
        startSession(true)
      }
      // 欢迎语未生成时补拉
      const welcome = messages.value.find(m => m.kind === 'welcome')
      if (welcome && !welcome.content) loadGreeting(welcome)
    })
  }
  nextTick(scrollBottom)
}
function collapse() {
  expanded.value = false
  historyOpen.value = false
  fullscreen.value = false
}
/** 放大/还原：全屏覆盖层，会话原样保留 */
function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
  nextTick(scrollBottom)
}
function onEscKey(e) {
  if (e.key === 'Escape' && fullscreen.value) fullscreen.value = false
}
import { onMounted, onBeforeUnmount } from 'vue'
onMounted(() => window.addEventListener('keydown', onEscKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onEscKey))
function newChat() {
  if (loading.value) return
  autoOpen.value = false
  input.value = ''
  startSession(true)
  historyOpen.value = false
  nextTick(scrollBottom)
}

function scrollBottom() {
  nextTick(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  })
}

// 任何新消息都防抖同步到服务端（标题取第一条用户消息，服务端负责生成）
watch(messages, () => {
  scheduleSync()
}, { deep: true })

function syncAuto(insight) {
  autoDone.value = {
    count: insight.autoHandledCount || 0,
    list: insight.autoHandledItems || []
  }
}

async function startAnalysis() {
  if (loading.value) return
  activeSkill.value = 'todo'
  messages.value.push({ role: 'user', kind: 'text', content: '帮我整理当前的待处理，能处理的直接处理。' })
  loading.value = true
  loadingText.value = '正在读取并自动处理可安抚内容…'
  scrollBottom()
  try {
    const res = await refreshAdminAiInsight()
    const data = res.data
    hasRan.value = true
    syncAuto(data)
    if (!data || data.status === 'EMPTY' || data.totalItems === 0) {
      messages.value.push({
        role: 'assistant',
        kind: 'text',
        content: data?.summary || '当前没有需要整理的留言和退款。'
      })
    } else {
      // 顺序：① 统计汇报气泡（纯汇报，不带任何提问按钮）
      messages.value.push({ role: 'assistant', kind: 'insight', insight: data })
      // ② 统计完，再单独问怎么处理（全部处理 / 只处理免审核的）
      if (data.needActionCount || data.autoHandleableCount) {
        messages.value.push({
          role: 'assistant',
          kind: 'ask-handle',
          content: `统计完了：有 ${data.needActionCount} 条需要你拍板，${data.autoHandleableCount || 0} 条我可以直接代处理。要我怎么处理？`
        })
        // ③ 公告授权不在此刻问：先等你选「全部处理 / 只处理免审核的」并处理完，再接着问
        pendingAnnounce.value = data.announcementSuggested
          ? { title: data.announcementTitle, content: data.announcementContent }
          : null
      }
    }
  } catch {
    messages.value.push({ role: 'assistant', kind: 'error', content: '整理失败，请稍后再试。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

/** 选择「全部处理」：标记提问已答 → 执行 → 完成后汇报 */
async function chooseHandleAll(message) {
  if (loading.value) return
  message.answered = '全部处理'
  messages.value.push({ role: 'user', kind: 'text', content: '全部处理' })
  await runHandle(message, 'handleAllAdminAiInsight', '好的，我这就全部处理…')
}

/** 选择「只处理免审核的」 */
async function chooseHandleAuto(message) {
  if (loading.value) return
  message.answered = '只处理免审核的'
  messages.value.push({ role: 'user', kind: 'text', content: '只处理免审核的' })
  await runHandle(message, 'handleAutoAdminAiInsight', '好的，我先把免审核的处理掉，剩下的列给你…')
}

async function runHandle(message, apiFn, text) {
  loading.value = true
  loadingText.value = text
  scrollBottom()
  try {
    const res = await (apiFn === 'handleAllAdminAiInsight' ? handleAllAdminAiInsight() : handleAutoAdminAiInsight())
    const data = res.data
    hasRan.value = true
    syncAuto(data)
    // 处理结果汇报气泡
    messages.value.push({ role: 'assistant', kind: 'insight', insight: data })
    // 处理完了，若此前 AI 研判建议发布公告，这时才接着问公告授权
    if (pendingAnnounce.value) {
      messages.value.push({
        role: 'assistant',
        kind: 'ask-announce',
        announcementTitle: pendingAnnounce.value.title,
        announcementContent: pendingAnnounce.value.content
      })
      pendingAnnounce.value = null
    }
  } catch {
    messages.value.push({ role: 'assistant', kind: 'error', content: '处理失败，请稍后再试。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

async function askQuestion(question) {
  const text = (question || '').trim()
  if (!text || loading.value) return
  lastUserAsk.value = text
  // 销售分析意图：当前技能是「销售分析报告」，或问题里明确带分析意图（含自定义天数）
  if (activeSkill.value === 'sales' || /销售分析|销售报告|卖得怎么样|营业额|利润|销量排行|净收入/.test(text)) {
    const days = parseDays(text)
    messages.value.push({ role: 'user', kind: 'text', content: text })
    input.value = ''
    runSalesReport(days)
    return
  }
  messages.value.push({ role: 'user', kind: 'text', content: text })
  input.value = ''
  loading.value = true
  loadingText.value = '正在根据当前研判回答…'
  scrollBottom()
  try {
    // 「不用确认，直接处理」走真实处理链路，而不是只回答
    if (/不用确认|直接帮我处理|全部直接处理/.test(text)) {
      await runHandle(null, 'handleAllAdminAiInsight', '好的，我这就把能代处理的都处理掉…')
      return
    }
    const res = await askAdminAiInsight(text)
    messages.value.push({
      role: 'assistant',
      kind: 'text',
      content: res.data?.answer || '暂时没有可回答的内容。'
    })
  } catch {
    messages.value.push({ role: 'assistant', kind: 'error', content: '处理失败，请稍后再试。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function parseDays(text) {
  // 口语化周期：季度/月优先于数字匹配
  if (/季度|三个月|3个月/.test(text)) return 90
  if (/一个月|1个月|近一月|本月/.test(text)) return 30
  const m = (text || '').match(/(\d{1,3})\s*天/)
  if (m) {
    const n = parseInt(m[1], 10)
    if (n >= 1 && n <= 90) return n
  }
  return periodDays(activePeriod.value)
}

async function runSalesReport(days) {
  if (loading.value) return
  activePeriod.value = days === 30 ? '1m' : days === 90 ? '1q' : '7d'
  activeSkill.value = 'sales'
  loading.value = true
  loadingText.value = `正在汇总全平台近 ${days} 天订单，生成分析报告…`
  scrollBottom()
  try {
    const res = await getAdminSalesAnalysis(days)
    const data = res.data
    if (!data) {
      messages.value.push({ role: 'assistant', kind: 'error', content: '报告生成失败，请稍后再试。' })
    } else {
      messages.value.push({ role: 'assistant', kind: 'report', report: data })
    }
  } catch {
    messages.value.push({ role: 'assistant', kind: 'error', content: '报告生成失败，请稍后再试。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function retryLast() {
  if (loading.value) return
  const text = lastUserAsk.value
  if (/整理当前的待处理/.test(text)) {
    startAnalysis()
  } else if (activeSkill.value === 'sales' || /销售分析|销售报告/.test(text)) {
    runSalesReport(parseDays(text))
  } else {
    askQuestion(text)
  }
}

async function doHandleAll() {
  if (loading.value) return
  messages.value.push({ role: 'user', kind: 'text', content: '全部处理' })
  await runHandle(null, 'handleAllAdminAiInsight', '好的，我这就全部处理…')
}

async function doHandleAuto() {
  if (loading.value) return
  messages.value.push({ role: 'user', kind: 'text', content: '只处理免审核的' })
  await runHandle(null, 'handleAutoAdminAiInsight', '好的，我先把免审核的处理掉，剩下的列给你…')
}

function send() {
  askQuestion(input.value)
}

/** 授权发布公告（管理员确认后才真正发布） */
async function confirmAnnounce(message) {
  if (loading.value) return
  loading.value = true
  loadingText.value = '正在发布公告…'
  try {
    await createNotice({
      title: message.announcementTitle || '食品安全提醒公告',
      content: message.announcementContent || '',
      status: 1
    })
    message.announceResult = 'published'
    ElMessage.success('公告已发布')
  } catch {
    ElMessage.error('发布失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

/** 暂不发布公告 */
function declineAnnounce(message) {
  message.announceResult = 'declined'
}

function chartRows(insight) {
  // 优先级排名：按敏感度排序的「需你拍板」项，越靠前越紧急
  const rank = { P0: 0, P1: 1, P2: 2 }
  return (insight.needAction || [])
    .slice()
    .sort((a, b) => (rank[a.severity] ?? 9) - (rank[b.severity] ?? 9) || (b.count || 0) - (a.count || 0))
    .slice(0, 6)
    .map(item => ({
      name: item.title,
      value: item.count || 0,
      severity: item.severity || 'P2'
    }))
}

function chartOption(insight) {
  const rows = chartRows(insight).reverse()
  const colorBy = { P0: '#ff3b30', P1: '#ff9500', P2: '#c7c7cc' }
  return {
    grid: { left: 8, right: 26, top: 8, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      borderWidth: 0,
      textStyle: { fontSize: 12 }
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f0f0f2' } },
      axisLabel: { color: '#8e8e93', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: rows.map(row => row.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#6e6e73', fontSize: 11, width: 140, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      data: rows.map(row => ({ value: row.value, itemStyle: { color: colorBy[row.severity] || '#c7c7cc' } })),
      barMaxWidth: 14,
      itemStyle: { borderRadius: [0, 5, 5, 0] },
      label: {
        show: true,
        position: 'right',
        color: '#8e8e93',
        fontSize: 11,
        formatter: ({ dataIndex }) => {
          const s = rows[dataIndex]?.severity
          return s === 'P0' ? '紧急' : s === 'P1' ? '优先' : ''
        }
      }
    }]
  }
}

// 点击敏感项：跳转目标页 + 高亮闪烁定位
function go(item) {
  if (!item?.targetUrl) return
  // 目标 URL 自带 ?tab=xxx，需合并进 query，否则 Vue Router 会丢弃 path 里的查询串
  const [path, qs] = item.targetUrl.split('?')
  const query = {}
  if (qs) {
    new URLSearchParams(qs).forEach((v, k) => { query[k] = v })
  }
  if (item.sourceId && item.sourceType) {
    query.refer = item.sourceType
    query.id = item.sourceId
  }
  // 商家留言/评价类：附带档口 id，目标页据此打开对应档口/筛选
  if (item.stallId) {
    query.stallId = item.stallId
  }
  router.push({ path, query })
}
</script>

<style scoped>
.wb-chat {
  margin-top: 28px;
  display: flex;
  flex-direction: column;
  /* 固定高度：消息再多也只在内部滚动，输入框始终固定在底部 */
  height: clamp(480px, 62vh, 720px);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-xs);
  overflow: hidden;
  position: relative;
}

/* ---- 折叠态：独立助手卡片 ---- */
.wb-chat.collapsed {
  height: auto;
  min-height: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  overflow: visible;
}

/* ---- 全屏放大：覆盖视口，聊天区吃满剩余高度，字体与卡片同步放大 ---- */
.wb-chat.fullscreen {
  position: fixed;
  inset: 16px;
  z-index: 2000;
  height: auto;
  max-width: none;
  margin: 0;
  border-radius: 24px;
  /* 第二层是 100vmax 扩散环：只在面板外部压暗页面，面板内部保持原色。
     不能用 inset 的 ::before 压暗——父级 z-index 形成层叠上下文后，负 z-index 子层会画在面板背景之上，整块发灰。 */
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.28), 0 0 0 100vmax rgba(0, 0, 0, 0.34);
  background: var(--surface);
  animation: chat-zoom-in 220ms cubic-bezier(0.2, 0.9, 0.3, 1);
}
.wb-chat.fullscreen .chat-body { padding: 16px clamp(20px, 6vw, 72px) 20px; }
.wb-chat.fullscreen .chat-bubble { max-width: 92ch; }
.wb-chat.fullscreen .chat-text { font-size: 14.5px; }
.wb-chat.fullscreen .insight-summary { font-size: 15px; }
.wb-chat.fullscreen .chat-head { padding: 20px clamp(20px, 6vw, 72px) 14px; }
.wb-chat.fullscreen .chat-quick,
.wb-chat.fullscreen .chat-dock { padding-left: clamp(20px, 6vw, 72px); padding-right: clamp(20px, 6vw, 72px); }
.wb-chat.fullscreen .hist-panel { right: clamp(20px, 6vw, 72px); }
@keyframes chat-zoom-in {
  from { opacity: 0; transform: scale(0.965) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
/* 全屏背后的页面加暗改用 box-shadow 扩散环实现（见 .wb-chat.fullscreen 的注释） */
@media (prefers-reduced-motion: reduce) {
  .wb-chat.fullscreen { animation: none; }
}
.pad-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: #f5f5f7;
  border: 1px solid transparent;
  border-radius: 22px;
}
.pad-avatar {
  width: 44px;
  height: 44px;
  object-fit: contain;
  flex-shrink: 0;
}
.pad-copy { flex: 1; min-width: 0; }
.pad-title { font-size: 17px; font-weight: 650; color: var(--text-1); letter-spacing: -0.02em; }
.pad-sub { margin-top: 4px; font-size: 13px; color: var(--text-2); line-height: 1.5; }
.pad-btn {
  flex-shrink: 0;
  height: 38px;
  padding: 0 20px;
  font-size: 13.5px;
}

/* ---- 展开态 ---- */
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 24px 14px;
  flex-shrink: 0;
}
.chat-head-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.chat-avatar {
  width: 40px;
  height: 40px;
  object-fit: contain;
  flex-shrink: 0;
}
.chat-title { font-size: 17px; font-weight: 650; color: var(--text-1); letter-spacing: -0.02em; }
.chat-sub { margin-top: 3px; font-size: 12px; color: var(--text-3); }
.chat-head-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.chat-head-actions .ap-btn {
  background: #f5f5f7;
  box-shadow: none;
  color: var(--text-2);
  font-weight: 500;
}
.chat-head-actions .ap-btn:hover { background: #ececf0; color: var(--text-1); }
.chat-head-actions .ap-btn:disabled { opacity: 0.4; cursor: default; }

.hist-panel {
  position: absolute;
  top: 70px;
  right: 24px;
  z-index: 20;
  width: 300px;
  max-height: 380px;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
.hist-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  border-bottom: 1px solid var(--border);
}
.hist-close { appearance: none; border: 0; background: transparent; color: var(--text-3); cursor: pointer; font-size: 12px; }
.hist-list { overflow-y: auto; padding: 6px; }
.hist-item-wrap {
  display: flex;
  align-items: center;
  border-radius: 10px;
  transition: background 0.15s var(--ease-apple);
}
.hist-item-wrap:hover { background: #f5f5f7; }
.hist-item-wrap.active { background: #f5f5f7; }
.hist-item-wrap.active .hist-title { font-weight: 600; }
.hist-item {
  appearance: none;
  border: 0;
  background: transparent;
  flex: 1;
  min-width: 0;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 9px 6px 9px 10px;
  border-radius: 10px 0 0 10px;
  cursor: pointer;
}
.hist-del {
  appearance: none;
  border: 0;
  background: transparent;
  color: var(--text-3);
  padding: 6px 9px;
  cursor: pointer;
  flex-shrink: 0;
  border-radius: 0 10px 10px 0;
  transition: color 0.15s;
}
.hist-del:hover { color: #ff3b30; }
.hist-title { font-size: 13px; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hist-time { font-size: 11px; color: var(--text-3); }
.hist-empty { padding: 18px; text-align: center; font-size: 12px; color: var(--text-3); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.16s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.chat-badge {
  flex-shrink: 0;
  padding: 3px 9px;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 11px;
  font-weight: 500;
}
.chat-badge.inline { margin-left: 0; }

.auto-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 24px 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f2faf5;
  color: #1d8a45;
  font-size: 12.5px;
}
.auto-dot { width: 6px; height: 6px; border-radius: 50%; background: #34c759; flex-shrink: 0; }
.auto-strip b { font-weight: 650; }
.auto-toggle {
  appearance: none;
  border: 0;
  background: transparent;
  color: #1d8a45;
  font-size: 12px;
  margin-left: auto;
  cursor: pointer;
  flex-shrink: 0;
}
.auto-list {
  margin: 0 24px 6px;
  max-height: 160px;
  overflow-y: auto;
}
.auto-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.028);
  font-size: 12.5px;
  color: var(--text-2);
}
.auto-item + .auto-item { margin-top: 4px; }
.auto-check { display: inline-flex; align-items: center; color: #1d9d55; flex-shrink: 0; }
.auto-text { overflow-wrap: anywhere; }

.chat-body {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 24px 20px;
  scroll-behavior: smooth;
}
.chat-body::-webkit-scrollbar { width: 6px; }
.chat-body::-webkit-scrollbar-thumb { background: rgba(0, 0, 0, 0.14); border-radius: 3px; }
.chat-row { display: flex; margin-bottom: 18px; }
.chat-row.is-user { justify-content: flex-end; }
.chat-row.is-ai { justify-content: flex-start; }
.chat-bubble { max-width: 75ch; }
.chat-bubble.insight-bubble { max-width: 100%; width: 100%; }
.chat-bubble.report-bubble { max-width: 100%; width: 100%; }
.chat-bubble.user-bubble {
  max-width: 76%;
  padding: 9px 15px;
  border-radius: 18px;
  border-bottom-right-radius: 6px;
  background: var(--text-1);
}
.chat-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-1);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-width: 68ch;
}
.user-bubble .chat-text { color: #fff; max-width: none; }
.chat-divider {
  text-align: center;
  padding: 8px 0;
}
.chat-divider span {
  font-size: 11px;
  color: var(--text-3);
  letter-spacing: 0.3px;
}
.chat-primary {
  appearance: none;
  border: 0;
  margin-top: 14px;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  background: #ff6a00;
  color: #fff;
  font-size: 13px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}
.chat-primary:hover { background: #f26100; }
.chat-primary:active { transform: scale(0.97); }

/* ---- 销售分析报告卡 ---- */
.report-card {
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 18px 20px;
  background: var(--surface);
}
.report-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.report-title { font-size: 16px; font-weight: 650; color: var(--text-1); letter-spacing: -0.01em; }
.report-days { font-size: 12px; color: var(--text-3); white-space: nowrap; }
.report-summary { margin: 8px 0 0; font-size: 13.5px; line-height: 1.75; color: var(--text-2); }
.report-insights {
  margin: 12px 0 0;
  padding: 12px 14px 12px 30px;
  border-radius: 12px;
  background: #f5f5f7;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.7;
}
.report-insights li + li { margin-top: 4px; }
.report-card :deep(.agent-data) { margin-top: 14px; border: 0; padding: 0; }

.insight-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-3);
}
.insight-window { font-variant-numeric: tabular-nums; }
.insight-note {
  margin-top: 12px;
  padding: 11px 14px;
  border-radius: 12px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.7;
}
.insight-chart {
  padding: 12px 14px 6px;
  border-radius: 14px;
  background: #fafafa;
}
.insight-split {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: #fafafa;
  overflow: hidden;
}
.split-cell {
  padding: 16px 12px;
  text-align: center;
}
.split-cell + .split-cell { border-left: 1px solid var(--border); }
.split-num {
  font-size: 26px;
  font-weight: 650;
  line-height: 1.1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.split-num.need { color: var(--brand-dark); }
.split-num.auto { color: var(--text-1); }
.split-num.done { color: #1d9d55; }
.split-label { margin-top: 5px; font-size: 12px; color: var(--text-2); }

.insight-block { margin-top: 16px; }
.block-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}
.block-head span { font-weight: 400; color: var(--text-3); font-size: 12px; }
.block-head .need-label { color: var(--text-1); font-weight: 600; }

.priority-row {
  appearance: none;
  width: 100%;
  border: 0;
  border-top: 1px solid rgba(0, 0, 0, 0.045);
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 4px;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple);
}
.priority-row:hover { background: #fafafa; }
.severity {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--text-2);
  white-space: nowrap;
  flex-shrink: 0;
}
.severity i { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.severity.P0 { color: #c9342b; }
.severity.P0 i { background: #ff3b30; }
.severity.P1 { color: #a96100; }
.severity.P1 i { background: #ff9500; }
.priority-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.priority-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.priority-sub {
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.priority-action { flex-shrink: 0; font-size: 12px; color: var(--text-2); }
.priority-row:hover .priority-action { color: var(--text-1); }

/* ---- 渐进披露：折叠分组 ---- */
.insight-fold { margin-top: 16px; border-top: 1px solid rgba(0, 0, 0, 0.05); }
.insight-fold--warm > summary .fold-title { color: #a96100; }
.insight-fold > summary {
  list-style: none;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 12px 2px;
  cursor: pointer;
  user-select: none;
}
.insight-fold > summary::-webkit-details-marker { display: none; }
.fold-title { font-size: 13px; font-weight: 600; color: var(--text-1); }
.fold-hint { font-size: 12px; color: var(--text-3); }
.fold-arrow {
  margin-left: auto;
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-right: 1.5px solid var(--text-3);
  border-bottom: 1.5px solid var(--text-3);
  transform: rotate(45deg) translateY(-1px);
  transition: transform 0.2s var(--ease-apple);
}
.insight-fold[open] .fold-arrow { transform: rotate(225deg) translateY(-1px); }
.fold-body { padding: 0 0 14px; }

.auto-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 2px;
  border-top: 1px solid rgba(0, 0, 0, 0.045);
}
.auto-row:first-child { border-top: 0; }
.auto-row-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.auto-row-sub { margin-left: auto; font-size: 12px; color: var(--text-3); white-space: nowrap; }

.detail-table {
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
}
.detail-row {
  display: grid;
  grid-template-columns: 80px 1fr 1.1fr;
  gap: 8px;
  padding: 9px 12px;
  font-size: 12px;
  color: var(--text-1);
  border-top: 1px solid rgba(0, 0, 0, 0.045);
}
.detail-row:first-child { border-top: 0; }
.detail-head {
  background: #fafafa;
  font-size: 11px;
  color: var(--text-3);
  font-weight: 600;
}
.detail-type { color: var(--text-2); }
.detail-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-action { color: var(--text-1); }
.detail-reply {
  grid-column: 1 / -1;
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.6;
}

.agent-ask {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: 16px;
  background: #f5f5f7;
}
.agent-ask-text { margin: 0 0 12px; font-size: 13.5px; font-weight: 600; color: var(--text-1); }
.agent-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.ask-actions { margin-top: 12px; }
.ask-answered { margin: 10px 0 0; font-size: 12.5px; color: var(--text-3); }
.agent-btn {
  appearance: none;
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: #fff;
  color: var(--text-1);
  height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), border-color 0.16s var(--ease-apple),
    transform 0.12s var(--ease-apple);
}
.agent-btn:hover { background: #fafafa; }
.agent-btn:active { transform: scale(0.97); }
.agent-btn.primary { background: #ff6a00; border-color: #ff6a00; color: #fff; }
.agent-btn.primary:hover { background: #f26100; }
.agent-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.announce-card {
  padding: 14px 16px;
  border-radius: 14px;
  background: #fff8f0;
  border: 1px solid #f5d9b8;
}
.announce-head { font-size: 12.5px; font-weight: 600; color: #8a5a1e; }
.announce-title { margin-top: 8px; font-size: 13.5px; font-weight: 600; color: var(--text-1); }
.announce-content { margin-top: 5px; font-size: 13px; color: var(--text-2); line-height: 1.7; }
.announce-done { margin-top: 10px; font-size: 12.5px; color: #1d9d55; font-weight: 600; }
.announce-card .agent-actions { margin-top: 12px; }

.insight-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}
.chat-guard { margin: 0; font-size: 12px; color: var(--text-3); line-height: 1.6; }
.text-btn {
  appearance: none;
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--text-2);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.16s var(--ease-apple);
}
.text-btn:hover { color: var(--text-1); }
.text-btn:disabled { color: var(--text-3); cursor: default; }
.refresh-btn {
  flex-shrink: 0;
  margin-top: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f5f5f7;
}
.insight-foot .refresh-btn { margin-top: 0; }
.refresh-btn:hover { background: #ececf0; }

.typing { display: flex; align-items: center; gap: 8px; padding: 4px 2px; font-size: 13px; color: var(--text-3); }
.typing-avatar { width: 22px; height: 22px; object-fit: contain; }

.chat-quick { flex-shrink: 0; padding: 12px 24px 0; }
.chat-quick .ap-pill-group { max-width: 100%; }
.chat-quick .ap-pill { font-size: 12px; padding: 5px 11px; }
.chat-quick .ap-pill:disabled { opacity: 0.45; cursor: not-allowed; }
.quick-sep {
  width: 1px;
  height: 16px;
  flex: 0 0 1px;
  align-self: center;
  margin: 0 3px;
  background: rgba(0, 0, 0, 0.1);
}

.chat-dock {
  flex-shrink: 0;
  padding: 10px 24px 20px;
  border-top: 1px solid var(--border);
  margin-top: 12px;
}
.chat-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px 10px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.55;
}
.chat-input-row { display: flex; align-items: flex-end; gap: 12px; }
.chat-input-row :deep(.el-textarea) { flex: 1; }
.chat-input-row :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
  padding: 11px 14px;
  border-radius: 12px;
  border: 1px solid var(--border-strong);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  background: #fff;
}
.chat-input-row :deep(.el-textarea__inner:focus) {
  border-color: var(--brand);
  box-shadow: 0 0 0 4px rgba(255, 122, 26, 0.12);
}
.send-btn {
  appearance: none;
  border: 0;
  background: var(--text-1);
  color: #fff;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  cursor: pointer;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}
.send-btn:hover:not(:disabled) { background: #333336; }
.send-btn:active:not(:disabled) { transform: scale(0.96); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; }

@media (max-width: 760px) {
  .wb-chat { height: clamp(440px, 68vh, 660px); border-radius: 20px; }
  .wb-chat.collapsed { height: auto; }
  .pad-card { padding: 18px; gap: 12px; border-radius: 20px; }
  .pad-avatar { width: 40px; height: 40px; }
  .pad-title { font-size: 16px; }
  .pad-sub { font-size: 12px; }
  .chat-head { padding: 16px 18px 12px; flex-wrap: wrap; }
  .chat-head-actions { gap: 6px; }
  .chat-head-actions .ap-btn span { display: none; }
  .hist-panel { right: 18px; width: calc(100% - 36px); }
  .chat-body { padding: 8px 18px 18px; }
  .chat-bubble { max-width: 88%; }
  .chat-bubble.insight-bubble, .chat-bubble.report-bubble { max-width: 100%; }
  .chat-quick { padding: 10px 18px 0; }
  .chat-dock { padding: 8px 18px 16px; }
  .chat-title { font-size: 16px; }
  .detail-row { grid-template-columns: 64px 1fr 1.1fr; }
  .insight-foot { align-items: flex-start; flex-direction: column; gap: 8px; }
}
</style>
