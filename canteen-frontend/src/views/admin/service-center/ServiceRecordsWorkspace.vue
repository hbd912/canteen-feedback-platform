<template>
  <div class="cs-panel records-workspace">
    <div class="cs-panel-head">
      <div class="cs-panel-title-wrap">
        <span class="cs-panel-title">{{ kind === 'CHAT' ? '服务记录 · 平台问答' : kind === 'SESSION' ? '服务记录 · 商家会话' : '服务记录' }}</span>
        <span class="cs-panel-count">共 {{ total }} 条记录，查看详情不会改变消息未读状态</span>
      </div>
      <div class="cs-panel-actions">
        <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="openMiss">待补充问题</button>
        <button type="button" class="ap-btn ap-btn--ghost ap-btn--sm" @click="loadData">刷新</button>
      </div>
    </div>

    <div class="cs-toolbar">
      <el-input v-model="keyword" class="cs-search" placeholder="搜索提问、学生或档口" aria-label="搜索服务记录" clearable @keyup.enter="reload" @clear="reload" />
      <div class="ap-pill-group kind-pills" aria-label="记录类型">
        <button type="button" class="ap-pill" :class="{ active: kind === '' }" @click="emit('change-kind', '')">全部</button>
        <button type="button" class="ap-pill" :class="{ active: kind === 'CHAT' }" @click="emit('change-kind', 'CHAT')">平台问答</button>
        <button type="button" class="ap-pill" :class="{ active: kind === 'SESSION' }" @click="emit('change-kind', 'SESSION')">商家会话</button>
      </div>
      <button
        type="button"
        class="ap-pill attention-pill"
        :class="{ active: state === 'ATTENTION' }"
        :aria-pressed="state === 'ATTENTION'"
        @click="toggleAttention"
      >
        需要关注
      </button>
      <span class="toolbar-spacer"></span>
      <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="reload">搜索</button>
    </div>

    <div ref="wrapRef" class="cs-table-wrap adaptive-table">
      <el-table :data="records" :row-key="row => `${row.kind}:${row.id}`" v-loading="loading">
        <el-table-column label="提问 / 最近消息" min-width="270" show-overflow-tooltip>
          <template #default="{ row }"><span class="cs-primary-text">{{ row.question || '暂无消息' }}</span></template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="record-kind" :class="row.kind === 'SESSION' ? 'is-session' : 'is-chat'">
              <i class="record-dot"></i>
              {{ row.kind === 'SESSION' ? '商家会话' : '平台问答' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="person" label="学生" width="110" show-overflow-tooltip />
        <el-table-column label="档口" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.stallName || '平台客服' }}</template></el-table-column>
        <el-table-column label="状态" min-width="130">
          <template #default="{ row }">
            <span class="record-kind" :class="recordStatusClass(row) === 'warning' ? 'is-warn' : recordStatusClass(row) === 'good' ? 'is-good' : 'is-idle'">
              <i class="record-dot"></i>
              {{ recordStatus(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="152"><template #default="{ row }"><span class="cs-secondary-text">{{ shortTime(row.createTime) }}</span></template></el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <button type="button" class="cs-row-link" @click="openDetail(row)">查看</button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="cs-empty">
            <span>{{ loadError ? '暂时无法读取服务记录' : '没有符合条件的记录' }}</span>
            <button v-if="loadError" type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="loadData">重新加载</button>
            <span v-else class="cs-footer-note">有新的提问或客服会话时，会显示在这里。</span>
          </div>
        </template>
      </el-table>
    </div>
    <footer class="cs-footer">
      <span class="cs-footer-note">共 {{ total }} 条记录，查看详情不会改变消息未读状态</span>
      <el-pagination :current-page="page.current" :page-size="page.size" :total="total" layout="prev, pager, next" @current-change="changePage" />
    </footer>

    <el-drawer v-model="detailVisible" :title="detail?.kind === 'SESSION' ? '会话详情' : '问答详情'" size="min(640px, 100vw)" append-to-body @closed="clearDetail">
      <template v-if="detail">
        <div class="detail-meta">
          <div class="meta-field">
            <span class="meta-label">学生</span>
            <span class="meta-value">{{ detail.person || '未知学生' }}</span>
          </div>
          <div class="meta-field">
            <span class="meta-label">档口</span>
            <span class="meta-value">{{ detail.stallName || '平台客服' }}</span>
          </div>
          <div class="meta-field">
            <span class="meta-label">时间</span>
            <span class="meta-value ap-num">{{ shortTime(detail.createTime) }}</span>
          </div>
          <div class="meta-field">
            <span class="meta-label">状态</span>
            <span class="meta-value">{{ recordStatus(detail) }}</span>
          </div>
          <div v-if="detail.orderId" class="meta-field">
            <span class="meta-label">关联订单</span>
            <span class="meta-value ap-num">#{{ detail.orderId }}</span>
          </div>
        </div>
        <template v-if="detail.kind === 'CHAT'">
          <div class="cs-detail-label">学生提问</div><div class="cs-detail-content">{{ detail.question }}</div>
          <div class="cs-detail-label">命中情况</div><div class="cs-detail-content">{{ detail.intentLabel || (detail.hit === 1 ? '已回答' : '未匹配到答案') }}</div>
          <div class="cs-detail-label">用户评价</div><div class="cs-detail-content">{{ detail.helpful === 1 ? '有帮助' : detail.helpful === 0 ? '没有帮助' : '未评价' }}</div>
          <div class="cs-detail-label">响应耗时</div><div class="cs-detail-content">{{ detail.costMs == null ? '未记录' : `${detail.costMs} ms` }}</div>
          <p class="cs-form-tip" style="margin-top: 28px">此类历史记录未保存回答正文，可补充已确认的答案后收录为知识。</p>
          <div class="cs-detail-actions"><button type="button" class="ap-btn" @click="collect({ kind: 'FAQ', question: detail.question })">收录为知识</button></div>
        </template>
        <template v-else>
          <div v-if="messagesLoading" v-loading="true" style="min-height: 160px" />
          <div v-else-if="messagesError" class="cs-empty"><span>会话消息加载失败</span><button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="loadMessages(detail.id)">重新加载</button></div>
          <div v-else-if="!messages.length" class="cs-empty">这段会话还没有消息。</div>
          <div v-else class="thread-body">
            <template v-for="(message, idx) in messages" :key="message.id">
              <div
                v-if="idx === 0 || dayOf(message.createTime) !== dayOf(messages[idx - 1].createTime)"
                class="thread-day"
              >
                <span class="thread-day-text">{{ dayOf(message.createTime) }}</span>
              </div>
              <div class="thread-message" :class="isMine(message) ? 'is-mine' : 'is-theirs'">
                <div class="bubble-meta">
                  <span class="bubble-role">{{ senderLabel(message.senderType) }}</span>
                  <span class="bubble-time ap-num">{{ shortTime(message.createTime) }}</span>
                </div>
                <div class="bubble">{{ message.content }}</div>
                <div v-if="message.dishId" class="cs-reference">推荐菜品：{{ message.dishName || `菜品 #${message.dishId}` }}<span v-if="message.dishPrice != null"> · ¥{{ message.dishPrice }}</span></div>
                <div v-if="message.couponOfferId" class="cs-reference">赠券：{{ message.couponName || '优惠券' }} · {{ message.couponOfferStatus === 'CLAIMED' ? '已领取' : '未领取' }}</div>
                <div v-if="message.senderType === 'MERCHANT' && message.content && hasQuestionBefore(message)" class="collect-row">
                  <button type="button" class="collect-link" @click="collectReply(message)">收录这条回复</button>
                </div>
              </div>
            </template>
          </div>
        </template>
      </template>
    </el-drawer>

    <el-drawer v-model="missVisible" title="待补充问题" size="min(640px, 100vw)" append-to-body>
      <p class="cs-drawer-intro">以下为平台尚未匹配答案、提问次数最多的 10 个问题。补充确认过的答案后，可直接收录。</p>
      <div v-if="missLoading" v-loading="true" style="min-height: 180px" />
      <div v-else-if="missError" class="cs-empty"><span>问题加载失败</span><button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="openMiss">重新加载</button></div>
      <div v-else-if="!missList.length" class="cs-empty">暂无需要补充的问题。</div>
      <div v-else class="ap-list miss-list">
        <div v-for="row in missList" :key="row.question" class="ap-row">
          <div class="ap-row-main">
            <div class="ap-row-title">{{ row.question }}</div>
            <div class="ap-row-sub ap-num">{{ row.count }} 次提问 · {{ shortTime(row.lastTime) }}</div>
          </div>
          <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="collect({ kind: 'FAQ', question: row.question })">补充答案</button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { getServiceRecords, getAdminSessionMessages } from '@/api/serviceCenter'
import { getChatMissTop } from '@/api/chat'
import { useServicePaging } from './useServicePaging'
import { shortTime, senderLabel, recordStatus, recordStatusClass } from './labels'

const props = defineProps({ kind: { type: String, default: '' } })
const emit = defineEmits(['change-kind', 'save-knowledge'])
const keyword = ref('')
const state = ref('')
const records = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref(false)
const wrapRef = ref(null)
const page = reactive({ current: 1, size: 10 })
const { bind } = useServicePaging(page, loadData)
let requestId = 0
let messageRequest = 0
let disposed = false
function reload() { page.current = 1; loadData() }
function changePage(value) { page.current = value; loadData() }
async function loadData() {
  const id = ++requestId
  loading.value = true
  loadError.value = false
  try {
    const result = await getServiceRecords({ ...page, kind: props.kind || undefined, keyword: keyword.value.trim() || undefined, state: state.value || undefined })
    if (disposed || id !== requestId) return
    records.value = result.data?.records || []
    total.value = Number(result.data?.total || 0)
  } catch { if (id === requestId) { records.value = []; total.value = 0; loadError.value = true } } finally { if (id === requestId) loading.value = false }
}
function toggleAttention() {
  state.value = state.value === 'ATTENTION' ? '' : 'ATTENTION'
  reload()
}
watch(() => props.kind, reload)
onMounted(async () => { loadData(); await nextTick(); bind(wrapRef.value) })
onBeforeUnmount(() => { disposed = true; requestId++; messageRequest++ })

const detailVisible = ref(false)
const detail = ref(null)
const messages = ref([])
const messagesLoading = ref(false)
const messagesError = ref(false)
function openDetail(row) {
  messageRequest++
  detail.value = row
  messages.value = []
  messagesError.value = false
  detailVisible.value = true
  if (row.kind === 'SESSION') loadMessages(row.id)
}
function clearDetail() { messageRequest++; detail.value = null; messages.value = [] }
async function loadMessages(sessionId) {
  const id = ++messageRequest
  messagesLoading.value = true
  messagesError.value = false
  try {
    const result = await getAdminSessionMessages(sessionId)
    if (disposed || id !== messageRequest) return
    messages.value = result.data || []
  } catch { if (id === messageRequest) messagesError.value = true } finally { if (id === messageRequest) messagesLoading.value = false }
}
function dayOf(value) {
  return value ? String(value).replace('T', ' ').slice(0, 10) : ''
}
function isMine(message) {
  return message.senderType === 'AI' || message.senderType === 'ADMIN'
}
function questionBefore(message) {
  const index = messages.value.findIndex(item => item.id === message.id)
  return messages.value.slice(0, index).reverse().find(item => item.senderType === 'STUDENT' && item.content)?.content || ''
}
function hasQuestionBefore(message) { return !!questionBefore(message) }
function collectReply(message) { collect({ kind: 'EXPERIENCE', scene: 'CUSTOMER_SERVICE', question: questionBefore(message), answer: message.content }) }
function collect(preset) {
  detailVisible.value = false
  missVisible.value = false
  emit('save-knowledge', preset)
}

const missVisible = ref(false)
const missLoading = ref(false)
const missError = ref(false)
const missList = ref([])
async function openMiss() {
  missVisible.value = true
  missLoading.value = true
  missError.value = false
  try { const result = await getChatMissTop(10); missList.value = result.data || [] } catch { missList.value = []; missError.value = true } finally { missLoading.value = false }
}
</script>

<style scoped>
/* ---------- 面板标题行 ---------- */
.cs-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px 0;
  flex-shrink: 0;
}

.cs-panel-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.cs-panel-title {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.cs-panel-count {
  font-size: 12px;
  color: var(--text-3);
}

.cs-panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* ---------- 工具条：胶囊类型切换 + 关注开关 ---------- */
.cs-toolbar {
  padding-top: 12px;
}

.kind-pills {
  background: transparent;
  padding: 0;
  gap: 6px;
}

.kind-pills .ap-pill {
  padding: 6px 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-2);
  background: transparent;
  border: 1px solid var(--border);
  box-shadow: none;
}

.kind-pills .ap-pill:hover {
  color: var(--text-1);
  background: rgba(0, 0, 0, 0.03);
}

.kind-pills .ap-pill.active {
  background: var(--text-1);
  border-color: var(--text-1);
  color: #fff;
  box-shadow: none;
}

.attention-pill {
  appearance: none;
  font-family: inherit;
  padding: 6px 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-2);
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s var(--ease-apple);
}

.attention-pill:hover {
  color: var(--text-1);
  background: rgba(0, 0, 0, 0.03);
}

.attention-pill.active {
  background: var(--brand-soft);
  border-color: rgba(255, 122, 26, 0.35);
  color: var(--brand-dark);
}

.toolbar-spacer {
  flex: 1;
}

/* ---------- 表格：类型与状态统一圆点 ---------- */
.record-kind {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}

.record-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a1a1a6;
  flex-shrink: 0;
}

.record-kind.is-chat .record-dot {
  background: #1d1d1f;
}

.record-kind.is-session .record-dot {
  background: var(--brand);
}

.record-kind.is-good .record-dot {
  background: #34c759;
}

.record-kind.is-warn .record-dot {
  background: #ff9500;
}

.record-kind.is-idle {
  color: var(--text-3);
}

/* ---------- 行内文字按钮 ---------- */
.cs-row-link {
  border: none;
  background: transparent;
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--brand-dark);
  font-family: inherit;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple);
}

.cs-row-link:hover {
  background: rgba(0, 0, 0, 0.04);
}

/* ---------- 详情抽屉 ---------- */
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 28px;
  padding-bottom: 18px;
  border-bottom: 1px solid #ececf0;
  margin-bottom: 6px;
}

.meta-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.meta-label {
  font-size: 12px;
  color: var(--text-3);
}

.meta-value {
  font-size: 14px;
  color: var(--text-1);
  font-weight: 500;
}

/* ---------- 会话气泡 ---------- */
.thread-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 10px;
}

.thread-day {
  display: flex;
  justify-content: center;
  margin: 12px 0 6px;
}

.thread-day-text {
  font-size: 11px;
  color: var(--text-3);
  background: #f5f5f7;
  border-radius: 999px;
  padding: 3px 12px;
  font-variant-numeric: tabular-nums;
}

.thread-message {
  max-width: 78%;
  display: flex;
  flex-direction: column;
}

.thread-message.is-theirs {
  align-self: flex-start;
  align-items: flex-start;
}

.thread-message.is-mine {
  align-self: flex-end;
  align-items: flex-end;
}

.bubble-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 8px 4px 4px;
  font-size: 11px;
  color: #86868b;
}

.bubble-role {
  font-weight: 600;
  color: #45454d;
}

.thread-message.is-mine .bubble-role {
  color: var(--text-2);
}

.bubble {
  padding: 9px 13px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--text-1);
}

.thread-message.is-theirs .bubble {
  background: #f5f5f7;
  border-top-left-radius: 4px;
}

.thread-message.is-mine .bubble {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.07);
  border-top-right-radius: 4px;
}

/* 引用块（推荐菜品/赠券）：跟随气泡方向缩进 */
.cs-reference {
  padding: 8px 12px;
  background: #f5f5f7;
  border-radius: 10px;
  color: #5e5e67;
  font-size: 12px;
  line-height: 1.6;
}

.thread-message .cs-reference {
  max-width: 100%;
  margin: 6px 4px 0;
}

/* 正文内引用与收录按钮沿用缩进，位于气泡之下 */

.collect-row {
  display: flex;
  margin: 4px 4px 0;
  opacity: 0;
  transition: opacity 0.16s var(--ease-apple);
}

.thread-message:hover .collect-row {
  opacity: 1;
}

.collect-link {
  border: none;
  background: transparent;
  padding: 2px 4px;
  font-size: 12px;
  font-weight: 550;
  color: var(--brand-dark);
  font-family: inherit;
  cursor: pointer;
  border-radius: 6px;
}

.collect-link:hover {
  background: rgba(255, 122, 26, 0.08);
}

/* 键盘可达性：focus 时也显示收录按钮 */
.collect-row:focus-within {
  opacity: 1;
}

/* ---------- 待补充问题抽屉 ---------- */
.miss-list {
  margin-top: 4px;
}

.miss-list .ap-row {
  padding: 12px 2px;
}

@media (max-width: 760px) {
  .thread-message {
    max-width: 92%;
  }
}
</style>
