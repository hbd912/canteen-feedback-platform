<template>
  <div class="wmc-wrap">
    <!-- 左：档口列表 -->
    <aside class="wmc-list">
      <div class="wmc-list-head">
        <span class="wmc-list-title">档口</span>
        <span class="wmc-list-count">{{ stalls.length }} 个 · 未读 {{ unreadTotal }}</span>
        <button class="wmc-refresh" type="button" aria-label="刷新档口列表" :disabled="loading" @click="load">
          <el-icon :size="14"><Refresh /></el-icon>
        </button>
      </div>

      <el-input v-model="stallSearch" class="wmc-search" placeholder="搜索档口" clearable>
        <template #prefix>
          <el-icon :size="14"><Search /></el-icon>
        </template>
      </el-input>

      <div class="ap-pill-group wmc-filter" role="tablist" aria-label="档口筛选">
        <button
          type="button"
          class="ap-pill"
          :class="{ active: stallFilter === 'all' }"
          @click="stallFilter = 'all'"
        >
          全部
        </button>
        <button
          type="button"
          class="ap-pill"
          :class="{ active: stallFilter === 'unread' }"
          @click="stallFilter = 'unread'"
        >
          未读
        </button>
        <button
          type="button"
          class="ap-pill"
          :class="{ active: stallFilter === 'high' }"
          @click="stallFilter = 'high'"
        >
          重点
        </button>
      </div>

      <div class="wmc-stalls">
        <button
          v-for="s in visibleStalls"
          :key="s.id"
          class="wmc-stall"
          :class="{ active: current?.id === s.id }"
          type="button"
          @click="openStall(s)"
          :data-recid="s.id"
        >
          <span class="wmc-avatar">
            <img
              v-if="s.merchantAvatar || s.coverImage"
              :src="s.merchantAvatar || s.coverImage"
              alt=""
              @error="$event.target.style.display = 'none'"
            />
            <span v-else class="wmc-af">{{ (s.name || '商').slice(0, 1) }}</span>
          </span>
          <span class="wmc-stall-main">
            <span class="wmc-stall-name">
              <b>{{ s.name }}</b>
              <i v-if="s.unreadFromMerchant" class="wmc-uread">{{ s.unreadFromMerchant }}</i>
            </span>
            <span class="wmc-stall-sub">
              {{ s.windowNo ? '窗口 ' + s.windowNo : '' }}{{ s.zone ? ' · ' + s.zone : '' }}
            </span>
            <span v-if="s.lastContent" class="wmc-last">
              <em v-if="s.lastDirection === 'OUT'">我›</em>
              <em v-else-if="s.lastAuthor === 'AI'">AI›</em>
              {{ s.lastContent }}
            </span>
          </span>
          <span v-if="s.weight === 'HIGH'" class="wmc-weight">重点</span>
        </button>

        <div v-if="!visibleStalls.length" class="wmc-list-empty">没有符合条件的档口</div>
      </div>
    </aside>

    <!-- 右：双向线程 + 发送 -->
    <section class="wmc-thread">
      <template v-if="current">
        <div class="wmc-thread-head">
          <div class="wmc-thread-copy">
            <div class="wmc-thread-name">{{ current.name }}</div>
            <div class="wmc-thread-meta">
              <span v-if="current.windowNo">窗口 {{ current.windowNo }}</span>
              <span v-if="current.zone">{{ current.zone }}</span>
              <span v-if="current.avgRating">评分 {{ current.avgRating }}</span>
              <span v-if="current.salesCount">销量 {{ current.salesCount }}</span>
            </div>
            <div v-if="current.weight === 'HIGH'" class="wmc-high-hint">
              <i class="wmc-high-dot"></i>
              重点档口，建议主动发送反馈
            </div>
          </div>
          <button class="ap-btn ap-btn--light ap-btn--sm" type="button" @click="openCompose">发反馈</button>
        </div>

        <div ref="threadBodyRef" class="wmc-thread-body">
          <div v-if="!messages.length" class="ap-empty wmc-thread-empty">
            <div class="ap-empty-mark">
              <el-icon :size="20"><ChatDotRound /></el-icon>
            </div>
            <div class="ap-empty-title">暂无往来记录</div>
            <div>给这个档口发送一条反馈，开始对话</div>
          </div>
          <template v-for="(m, idx) in messages" :key="m.id">
            <div
              v-if="idx === 0 || String(m.createTime).slice(0, 10) !== String(messages[idx - 1].createTime).slice(0, 10)"
              class="wmc-day"
            >
              {{ String(m.createTime).slice(0, 10) }}
            </div>
            <div
              class="wmc-msg"
              :data-msgid="m.id"
              :class="m.direction === 'OUT' ? 'from-admin' : 'from-merchant'"
            >
              <div v-if="m.title" class="wmc-msg-title">{{ m.title }}</div>
              <div class="wmc-msg-body">{{ m.content }}</div>
              <div class="wmc-msg-time">{{ authorLabel(m) }} · {{ m.createTime }}</div>
            </div>
          </template>
        </div>

        <div class="wmc-input">
          <div class="wmc-input-box">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              maxlength="500"
              placeholder="给商家写反馈或通知…"
            />
            <button
              class="wmc-send"
              type="button"
              aria-label="发送"
              :disabled="!draft.trim() || loading"
              @click="sendDraft"
            >
              <el-icon :size="16"><Promotion /></el-icon>
            </button>
          </div>
        </div>
      </template>

      <div v-else class="ap-empty wmc-empty">
        <div class="ap-empty-mark">
          <el-icon :size="20"><ChatLineSquare /></el-icon>
        </div>
        <div class="ap-empty-title">选择左侧档口开始沟通</div>
        <div>查看平台与该档口的往来记录，或发送反馈与通知</div>
      </div>
    </section>
  </div>

  <!-- 发反馈弹窗：AI 编辑文案 -->
  <el-dialog v-model="composeVisible" :title="'发反馈 · ' + (current?.name || '')" width="540px">
    <div class="compose-label">主题（可选）</div>
    <el-input v-model="composeTopic" maxlength="60" placeholder="如：学生反馈出餐太慢" class="compose-field" />

    <div class="compose-label-row">
      <span class="compose-label">内容要点</span>
      <button
        type="button"
        class="ap-btn ap-btn--light ap-btn--sm"
        :disabled="aiLoading"
        @click="generateAi"
      >
        {{ aiLoading ? '编辑中…' : 'AI 编辑' }}
      </button>
    </div>
    <el-input
      v-model="composeContent"
      type="textarea"
      :rows="4"
      maxlength="300"
      placeholder="例：请整改出餐速度，减少排队"
    />

    <div v-if="aiResult" class="compose-ai">
      <div class="compose-ai-label">AI 已编辑，可直接修改后发送</div>
      <el-input v-model="aiResult" type="textarea" :rows="4" maxlength="500" />
    </div>

    <template #footer>
      <button type="button" class="ap-btn ap-btn--light" @click="composeVisible = false">取消</button>
      <button type="button" class="ap-btn" :disabled="sending || aiLoading" @click="sendNotice">
        {{ sending ? '发送中…' : '发送通知' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantCommStalls, merchantCommThread, merchantCommSend, noticeGenerate } from '@/api/admin'

const route = useRoute()
const stalls = ref([])
const current = ref(null)
const messages = ref([])
const loading = ref(false)
const threadBodyRef = ref(null)
const draft = ref('')

const stallSearch = ref('')
const stallFilter = ref('all')
const visibleStalls = computed(() => {
  const key = stallSearch.value.trim().toLowerCase()
  return stalls.value.filter((s) => {
    if (stallFilter.value === 'unread' && !s.unreadFromMerchant) return false
    if (stallFilter.value === 'high' && s.weight !== 'HIGH') return false
    if (!key) return true
    return [s.name, s.windowNo, s.zone, s.lastContent]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(key)
  })
})
const unreadTotal = computed(() => stalls.value.filter((s) => s.unreadFromMerchant).length)

const composeVisible = ref(false)
const composeTopic = ref('')
const composeContent = ref('')
const aiResult = ref('')
const aiLoading = ref(false)
const sending = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await merchantCommStalls()
    stalls.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function openStall(s, { keepScroll = false } = {}) {
  current.value = s
  loading.value = true
  try {
    const res = await merchantCommThread(s.id).catch(() => ({ data: [] }))
    messages.value = res.data || []
  } finally {
    loading.value = false
    if (!keepScroll) {
      nextTick(scrollBottom)
    }
  }
}

function scrollBottom() {
  if (threadBodyRef.value) threadBodyRef.value.scrollTop = threadBodyRef.value.scrollHeight
}

function authorLabel(m) {
  if (m.direction === 'OUT') return m.authorRole === 'AI' ? '值班助理' : '管理员'
  return '商家'
}

function openCompose() {
  composeTopic.value = ''
  composeContent.value = ''
  aiResult.value = ''
  composeVisible.value = true
}

async function generateAi() {
  const topic = composeTopic.value.trim() || composeContent.value.trim()
  if (!topic) {
    ElMessage.warning('请先填写主题或要点')
    return
  }
  aiLoading.value = true
  try {
    const res = await noticeGenerate(topic)
    const data = res.data || {}
    aiResult.value = data.content || ''
  } finally {
    aiLoading.value = false
  }
}

async function sendNotice() {
  const content = aiResult.value.trim() || composeContent.value.trim()
  if (!content) {
    ElMessage.warning('请填写反馈内容，或点击「AI 编辑」生成')
    return
  }
  sending.value = true
  try {
    await merchantCommSend(current.value.id, { title: composeTopic.value || '', content })
    ElMessage.success('已发送给商家')
    composeVisible.value = false
    openStall(current.value)
  } finally {
    sending.value = false
  }
}

async function sendDraft() {
  if (!draft.value.trim() || !current.value) return
  sending.value = true
  try {
    await merchantCommSend(current.value.id, { title: '', content: draft.value.trim() })
    draft.value = ''
    ElMessage.success('已发送')
    openStall(current.value)
  } finally {
    sending.value = false
  }
}

// 工作台敏感项跳转：/admin/feedback-center?tab=merchant&refer=MERCHANT_MESSAGE&id=消息id&stallId=档口id
// 打开对应档口线程后，滚动并高亮那条具体留言。
async function locateMessage() {
  if (route.query.refer !== 'MERCHANT_MESSAGE') return
  const msgId = Number(route.query.id)
  const stallId = Number(route.query.stallId)
  if (!msgId) return
  await nextTick()
  let stall = stalls.value.find((s) => s.id === stallId)
  if (!stall) {
    // 兜底：没有 stallId 时按未读/最近逐个尝试前 20 个档口
    for (const s of stalls.value.slice(0, 20)) {
      await openStall(s)
      await nextTick()
      if (document.querySelector(`.wmc-msg[data-msgid="${msgId}"]`)) { stall = s; break }
    }
  } else if (current.value?.id !== stallId) {
    await openStall(stall)
  }
  await nextTick()
  await wait(150)
  const el = document.querySelector(`.wmc-msg[data-msgid="${msgId}"]`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('flash-target')
    setTimeout(() => el.classList.remove('flash-target'), 2400)
  }
}

function wait(ms) { return new Promise(r => setTimeout(r, ms)) }

onMounted(async () => {
  await load()
  nextTick(locateMessage)
})
onActivated(() => {
  load()
})
</script>

<style scoped>
.wmc-wrap {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 460px;
}

/* ---------- 左栏 ---------- */
.wmc-list {
  width: 292px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 14px 12px 8px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.wmc-list-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px 10px;
}

.wmc-list-title {
  font-size: 14px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.wmc-list-count {
  flex: 1;
  font-size: 11.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.wmc-refresh {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: #f5f5f7;
  color: var(--text-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.wmc-refresh:hover {
  background: #ececf0;
  color: var(--text-1);
}

.wmc-refresh:active {
  transform: scale(0.94);
}

.wmc-refresh:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.wmc-search {
  margin-bottom: 8px;
}

.wmc-search :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.wmc-filter {
  display: flex;
  width: 100%;
  margin-bottom: 4px;
}

.wmc-filter .ap-pill {
  flex: 1;
  padding: 5px 0;
  font-size: 12px;
  text-align: center;
}

.wmc-stalls {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 0 2px;
}

.wmc-stall {
  appearance: none;
  position: relative;
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 10px;
  margin-bottom: 2px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple);
}

.wmc-stall:hover {
  background: #fafafa;
}

.wmc-stall.active {
  background: #f5f5f7;
  box-shadow: inset 2px 0 0 var(--brand);
}

.wmc-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #f5f5f7;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
}

.wmc-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.wmc-af {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
}

.wmc-stall-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wmc-stall-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.wmc-stall-name b {
  font-size: 13.5px;
  color: var(--text-1);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wmc-uread {
  font-style: normal;
  color: #fff;
  background: var(--brand);
  font-size: 10.5px;
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.wmc-stall-sub {
  font-size: 11px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wmc-last {
  font-size: 11.5px;
  color: #86868b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wmc-last em {
  font-style: normal;
  color: var(--text-3);
}

.wmc-weight {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--text-2);
  background: #f0f0f2;
  padding: 2px 7px;
  border-radius: 999px;
  line-height: 1.5;
}

.wmc-list-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-3);
}

/* ---------- 右栏 ---------- */
.wmc-thread {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.wmc-thread-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  flex-shrink: 0;
}

.wmc-thread-copy {
  min-width: 0;
}

.wmc-thread-name {
  font-size: 17px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.015em;
}

.wmc-thread-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.wmc-high-hint {
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: #a96100;
}

.wmc-high-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff9500;
  flex-shrink: 0;
}

.wmc-thread-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px;
}

.wmc-thread-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.wmc-day {
  margin: 6px 0 14px;
  text-align: center;
  font-size: 11px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.wmc-msg {
  margin-bottom: 14px;
  max-width: 76%;
}

.wmc-msg.from-merchant {
  margin-right: auto;
}

.wmc-msg.from-admin {
  margin-left: auto;
}

.wmc-msg-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 4px;
}

.wmc-msg-body {
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--text-1);
  padding: 10px 13px;
  border-radius: 14px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.wmc-msg.from-merchant .wmc-msg-body {
  background: #fff;
  border: 1px solid var(--border);
  border-top-left-radius: 6px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.wmc-msg.from-admin .wmc-msg-body {
  background: #f5f5f7;
  border-top-right-radius: 6px;
}

.wmc-msg.from-admin .wmc-msg-title,
.wmc-msg.from-admin .wmc-msg-time {
  text-align: right;
}

.wmc-msg-time {
  font-size: 11px;
  color: #86868b;
  margin-top: 5px;
  font-variant-numeric: tabular-nums;
}

.wmc-input {
  padding: 12px 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.045);
  flex-shrink: 0;
}

.wmc-input-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 8px 8px 8px 14px;
  border: 1px solid var(--border-strong);
  border-radius: 16px;
  background: #fff;
  transition: box-shadow 0.18s var(--ease-apple);
}

.wmc-input-box:focus-within {
  box-shadow: 0 0 0 3px rgba(255, 122, 26, 0.1);
}

.wmc-input-box :deep(.el-textarea) {
  flex: 1;
}

.wmc-input-box :deep(.el-textarea__inner) {
  box-shadow: none !important;
  background: transparent;
  padding: 4px 0;
  resize: none;
}

.wmc-send {
  appearance: none;
  border: 0;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #1d1d1f;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.wmc-send:hover {
  background: #333336;
}

.wmc-send:active {
  transform: scale(0.94);
}

.wmc-send:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.wmc-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

/* ---------- 弹窗 ---------- */
.compose-label {
  font-size: 12.5px;
  color: var(--text-2);
  margin-bottom: 6px;
}

.compose-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  margin-bottom: 6px;
}

.compose-label-row .compose-label {
  margin-bottom: 0;
}

.compose-field {
  margin-bottom: 4px;
}

.compose-ai {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f5f5f7;
}

.compose-ai-label {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 550;
  color: var(--text-2);
}

.compose-ai :deep(.el-textarea__inner) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--border) inset !important;
}

@media (max-width: 860px) {
  .wmc-wrap {
    flex-direction: column;
    height: auto;
  }

  .wmc-list {
    width: 100%;
    max-height: 320px;
  }

  .wmc-thread {
    min-height: 420px;
  }
}
</style>
