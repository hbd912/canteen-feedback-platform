<template>
  <div class="assistant-wrap">
    <!-- 左：对话历史侧栏（仿 Claude/图6 的聊天存档） -->
    <aside class="conv-side" :class="{ collapsed: !convOpen }">
      <div class="conv-side-head">
        <span class="conv-side-title">对话历史</span>
        <span class="conv-side-count">{{ conversations.length }}</span>
      </div>
      <el-scrollbar class="conv-list" v-show="convOpen && conversations.length">
        <div
          v-for="c in conversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: c.id === activeConvId }"
          @click="selectConv(c)"
        >
          <span class="conv-dot" :class="{ on: c.id === activeConvId }"></span>
          <div class="conv-body">
            <div class="conv-title">{{ c.title || '新对话' }}</div>
            <div class="conv-time">{{ convTime(c.updatedAt) }}</div>
          </div>
          <button class="conv-del" title="删除" @click.stop="deleteConv(c)">
            <el-icon :size="12"><Close /></el-icon>
          </button>
        </div>
      </el-scrollbar>
      <div v-if="!conversations.length" class="conv-empty">还没有对话</div>
    </aside>

    <!-- 右：聊天卡片 -->
    <div class="assistant-card">
      <!-- 头部：小助手 emoji + 标题 + 状态 + 操作 -->
      <div class="assistant-head">
        <div class="head-left">
          <img class="head-avatar" src="/ui/assistant/happy.png" alt="小助手" />
          <div class="head-text">
            <div class="assistant-title">经营助理</div>
            <div class="head-sub">你的经营小助手 · 随叫随到</div>
          </div>
        </div>
        <div class="head-right">
          <span class="head-note">真实数据 · 省 token</span>
          <button class="icon-btn hide-btn" :title="convOpen ? '收起历史' : '展开历史'" @click="convOpen = !convOpen">
            <el-icon :size="14"><component :is="convOpen ? 'Fold' : 'Expand'" /></el-icon>
          </button>
          <button class="ap-btn ap-btn--sm" @click="newConv">
            <el-icon :size="14"><Plus /></el-icon>
            新建对话
          </button>
        </div>
      </div>

      <!-- 消息区 -->
      <div class="assistant-body" ref="bodyRef">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="msg-row"
          :class="m.role === 'user' ? 'msg-user' : 'msg-ai'"
        >
          <div class="msg-row-inner" :class="{ 'msg-row-inner--wide': m.kind !== 'user' && m.charts?.length }">
            <template v-if="m.kind === 'user'">
              <el-image v-if="m.image" :src="m.image" class="msg-image" fit="cover" />
              <div class="msg-text">{{ m.content }}</div>
            </template>

            <template v-else-if="m.kind === 'draft' && m.draft">
              <div class="msg-text" v-if="m.content">{{ m.content }}</div>
              <AgentDishDraftCard
                :draft="m.draft"
                :image="m.draftImage || ''"
                :stall-id="userStore.userInfo?.stallId"
                @uploaded="onDishCreated"
                @superseded="appendDraftCard"
                @discarded="discardDraftCard(m)"
              />
            </template>

            <template v-else>
              <div class="msg-text" v-html="renderRich(m.content)"></div>
              <AgentDataCard
                v-if="m.charts?.length"
                :charts="m.charts"
                :tables="m.tables"
                :days="m.dataDays"
              />
              <div v-if="m.model" class="msg-meta">
                {{ m.model }} · {{ m.tokens }} tokens · {{ formatMs(m.costMs) }}
                <span v-if="m.visionUsed"> · 已识别图片</span>
              </div>
              <button
                v-if="m.question && !m.helpfulSent"
                class="help-btn"
                @click="markHelpful(m)"
              >这条有用</button>
              <span v-else-if="m.helpfulSent" class="help-done">已记入经验库</span>
            </template>
          </div>
        </div>

        <!-- 转态占位 -->
        <div v-if="loading || pendingDraft" class="msg-row msg-ai">
          <div class="msg-row-inner">
            <div class="typing">{{ loading ? '正在为你整理…' : '正在识别菜品、生成草稿…' }}</div>
          </div>
        </div>
      </div>

      <!-- 输入区：技能分段器 + 快问，下面一行是图片、输入框与发送 -->
      <div class="input-dock">
        <div class="dock-top">
          <div class="ap-pill-group skill-chips" role="tablist">
            <button
              v-for="s in skills"
              :key="s.key"
              class="ap-pill"
              :class="{ active: activeSkill === s.key }"
              role="tab"
              :aria-selected="activeSkill === s.key"
              @click="selectSkill(s)"
            >{{ s.label }}</button>
          </div>
          <div class="quick-row">
            <button
              v-for="q in quickQuestions"
              :key="q"
              class="quick-chip"
              @click="quickAsk(q)"
            >{{ q }}</button>
          </div>
        </div>
        <div v-if="attachImage" class="attach-bar">
          <el-image :src="attachImage" fit="cover" class="attach-thumb" />
          <span class="attach-hint">已添加图片，回车发送</span>
          <el-icon class="attach-close" @click="attachImage = ''"><Close /></el-icon>
        </div>
        <div class="input-row">
          <el-upload
            :show-file-list="false"
            :http-request="doUploadImage"
            accept="image/*"
            class="img-btn"
          >
            <button class="round-btn" :class="{ active: attachImage }" :title="imgTitle" type="button">
              <el-icon :size="18"><Picture /></el-icon>
            </button>
          </el-upload>
          <el-input
            v-model="input"
            :rows="2"
            type="textarea"
            resize="none"
            maxlength="500"
            :placeholder="placeholder"
            @keydown.enter.exact.prevent="send"
          />
          <button class="send-btn" :disabled="(!input.trim() && !attachImage) || loading" @click="send">
            <el-icon v-if="!loading" :size="18"><Position /></el-icon>
            <span v-else class="send-loading"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Picture, Position, Plus, Fold, Expand } from '@element-plus/icons-vue'
import { askAssistant, assistantFeedback } from '@/api/merchantAssistant'
import { uploadImage } from '@/api/sales'
import { useUserStore } from '@/store/user'
import { useDishDraftStore } from '@/store/dishDraft'
import { prepareImageFile } from '@/utils/imageUpload'
import AgentDataCard from '@/components/merchant/AgentDataCard.vue'
import AgentDishDraftCard from '@/components/merchant/AgentDishDraftCard.vue'

const userStore = useUserStore()
const draftStore = useDishDraftStore()

// 对话历史持久化：localStorage 存多套对话，仿图6左侧聊天存档
const CONV_KEY = 'merchant_ai_convs_v1'
const convOpen = ref(true)
const conversations = ref([])
const activeConvId = ref(null)

function genId() {
  return 'c' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)
}

/** AI 气泡轻排版：先转义 HTML 防注入，再把 **加粗** 转成 <strong>、列表行转成圆点，段落留白 */
function renderRich(text) {
  if (!text) return ''
  const esc = String(text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|\n)\s*(\d{1,2})[.、)]\s*/g, '$1<span class="li-num">$2.</span> ')
    .replace(/(^|\n)\s*[-·•]\s+/g, '$1<span class="li-dot">·</span> ')
}

/** 默认欢迎消息：每次新对话一套 */
function welcomeMsg() {
  return {
    role: 'assistant', kind: 'text',
    content: '你好，我是你的经营小助手🙌 在输入框上方选功能：上传菜品就传图，经营问答就直接聊，要销售数据就说「生成销售分析」。要数据时我给你真实表格和统计图，都能下载。',
    model: '', tokens: 0, costMs: 0
  }
}

function newConvObj() {
  return { id: genId(), title: '新对话', updatedAt: Date.now(), activeSkill: 'chat', messages: [welcomeMsg()] }
}

function persist() {
  try {
    const plain = conversations.value.map((c) => {
      const msgs = c.messages.map((m) => ({
        role: m.role,
        kind: m.kind,
        content: m.content,
        image: m.image || '',
        draftImage: m.draftImage || '',
        draft: m.draft ? { ...m.draft } : null,
        charts: m.charts || [],
        tables: m.tables || [],
        dataDays: m.dataDays || 0,
        model: m.model || '',
        tokens: m.tokens || 0,
        costMs: m.costMs || 0,
        visionUsed: m.visionUsed || false,
        question: m.question || '',
        helpfulSent: m.helpfulSent || false
      }))
      return { id: c.id, title: c.title, updatedAt: c.updatedAt, activeSkill: c.activeSkill, messages: msgs }
    })
    localStorage.setItem(CONV_KEY, JSON.stringify(plain))
  } catch {
    // 存储失败忽略
  }
}

function loadConvs() {
  let saved = []
  try {
    saved = JSON.parse(localStorage.getItem(CONV_KEY) || '[]') || []
  } catch {
    saved = []
  }
  if (!Array.isArray(saved)) saved = []
  if (!saved.length) {
    const c = newConvObj()
    saved = [c]
  }
  conversations.value = saved
  // 恢复上次活跃的对话，否则回到第一个
  const last = localStorage.getItem(CONV_KEY + ':' + userStore.userId + ':active')
  const target = saved.find(c => c.id === last) || saved[0]
  selectConv(target, false)
}

function applyConv(c) {
  activeConvId.value = c.id
  messages.value = (c.messages && c.messages.length ? c.messages : [welcomeMsg()])
  activeSkill.value = c.activeSkill || 'chat'
  nextTick(scrollBottom)
}

function selectConv(c, doPersist = true) {
  applyConv(c)
  localStorage.setItem(CONV_KEY + ':' + userStore.userId + ':active', c.id)
  if (doPersist) persist()
}

function newConv() {
  const c = newConvObj()
  conversations.value.unshift(c)
  applyConv(c)
  localStorage.setItem(CONV_KEY + ':' + userStore.userId + ':active', c.id)
  persist()
}

function deleteConv(c) {
  conversations.value = conversations.value.filter(x => x.id !== c.id)
  if (activeConvId.value === c.id) {
    // 删掉了正在看的对话：跳到剩下的第一个；若一个都不剩，则新建一个并真正入列，
    // 避免产生「侧栏为空但聊天还活着的幽灵会话」（否则新消息无处保存、刷新即丢）
    let next = conversations.value[0]
    if (!next) {
      next = newConvObj()
      conversations.value.push(next)
    }
    applyConv(next)
    localStorage.setItem(CONV_KEY + ':' + userStore.userId + ':active', next.id)
  }
  persist()
}

function convTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = Date.now()
  if (now - t < 60000) return '刚刚'
  if (new Date(d).toDateString() === new Date().toDateString()) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 更新指定会话的标题/内容/时间戳并持久化。消息数组取该会话自己在 list 里存的数组，
 *  而不是当前视图的 messages.value —— 请求跨会话返回时视图可能已切走，绝不能把别的会话内容盖上去。 */
function touchConv(convId) {
  const idx = conversations.value.findIndex(c => c.id === convId)
  if (idx < 0) return
  const conv = conversations.value[idx]
  const arr = Array.isArray(conv.messages) ? conv.messages : messages.value
  const firstUser = arr.find(m => m.kind === 'user')
  if (firstUser) {
    const t = String(firstUser.content || '').slice(0, 18)
    conv.title = t || '新对话'
  }
  conv.updatedAt = Date.now()
  conv.messages = arr
  conv.activeSkill = activeSkill.value
  persist()
}
/** 更新当前视图所在会话并持久化 */
function touchTitle() {
  touchConv(activeConvId.value)
}

const messages = ref([])
const activeSkill = ref('chat')
const input = ref('')
const loading = ref(false)
const pendingDraft = ref(false)
const bodyRef = ref(null)
const snapshot = ref('')
const snapshotOpen = ref(false)
const attachImage = ref('')
const uploading = ref(false)

const skills = [
  { key: 'upload', label: '上传菜品' },
  { key: 'chat', label: '经营问答' },
  { key: 'sales', label: '销售分析' }
]

const placeholder = computed(() =>
  activeSkill.value === 'upload'
    ? '传一张图或写一句描述，AI 生成上架草稿'
    : activeSkill.value === 'sales'
      ? '直接说要什么数据，例如：近7天销售分析'
      : '问点经营上的事，例如：这个月卖得怎么样？')
const imgTitle = computed(() =>
  activeSkill.value === 'upload' ? '上传菜品图片' : '添加图片，让 AI 识别')

/** 小助手右侧提示：随选中功能切换，点选即给出该功能的提示词（图3风格） */
const skillHint = computed(() => {
  if (activeSkill.value === 'upload') return '上传菜品：发一张菜图或一句描述，我帮你生成上架草稿～'
  if (activeSkill.value === 'sales') return '销售分析：直接说要什么数据，例如「近7天销售分析」～'
  return '我是你的经营小助手，上传菜品 / 问生意 / 要数据都可以找我～'
})

const quickQuestions = computed(() =>
  activeSkill.value === 'upload'
    ? ['帮我上架：番茄牛腩，微辣，分量足']
    : activeSkill.value === 'sales'
      ? ['生成近7天销售分析表格', '本月营收和客户数', '最热销的菜']
      : ['这个月卖得怎么样？', '哪些菜该下架？', '怎么提升差评？'])

function selectSkill(s) {
  activeSkill.value = s.key
}

function scrollBottom() {
  nextTick(() => { if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight })
}
function formatMs(ms) { return ms ? ms + 'ms' : '' }

async function doUploadImage(option) {
  uploading.value = true
  try {
    const file = await prepareImageFile(option.file)
    if (!file) { ElMessage.error('这个图片格式浏览器无法处理，请转成 JPG/PNG 再上传'); return }
    const res = await uploadImage(file)
    attachImage.value = res.data
    ElMessage.success(activeSkill.value === 'upload' ? '图片已添加，回车生成草稿' : '图片已添加，回车发送')
  } catch { ElMessage.error('上传失败') }
  finally { uploading.value = false }
}

function pushReplyToConv(convId, msg) {
  const conv = conversations.value.find(c => c.id === convId)
  if (!conv) return false
  if (!Array.isArray(conv.messages)) conv.messages = []
  // 正在看的会话：conv.messages 与 messages.value 是同一引用（applyConv 赋值过），
  // 只 push 一次；切走后再由 send 把回复补进去。
  if (conv.messages !== messages.value) conv.messages.push(msg)
  return true
}

async function send() {
  const text = input.value.trim()
  if ((!text && !attachImage.value && !pendingDraft.value) || loading.value) return
  // 记录发送时归属的会话；请求回来后即使切了会话，回复仍落到原会话，避免串桌
  const fromConvId = activeConvId.value
  if (text || attachImage.value) {
    const userMsg = { role: 'user', kind: 'user', content: text, image: attachImage.value || '' }
    messages.value.push(userMsg)
    pushReplyToConv(fromConvId, userMsg)
  }
  const question = text || (activeSkill.value === 'upload' ? '请生成上架草稿' : '')
  const image = attachImage.value || ''
  input.value = ''
  attachImage.value = ''
  loading.value = true
  scrollBottom()
  try {
    const res = await askAssistant(question, image, activeSkill.value)
    const data = res.data || {}
    let msg
    if (data.draft) {
      const draftMsg = {
        role: 'assistant', kind: 'draft',
        draft: { ...data.draft, localId: 'draft-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7) },
        draftImage: image || '', content: data.draft.note || '', model: data.draft.model, tokens: data.draft.tokens, costMs: 0,
        question: '', helpfulSent: false
      }
      // 同步进草稿箱：档口预览/菜品管理读的是这份数据
      draftStore.upsert({ ...draftMsg.draft, image: image || '', createdAt: new Date().toISOString() })
      if (pushReplyToConv(fromConvId, draftMsg)) {
        if (activeConvId.value === fromConvId) {
          messages.value.push(draftMsg)
          scrollBottom()
        }
      }
    } else {
      msg = data.charts?.length
        ? {
            role: 'assistant', kind: 'text',
            content: data.answer || '',
            model: data.model, tokens: data.tokens, costMs: data.costMs,
            visionUsed: data.visionUsed, experienceUsed: data.experienceUsed,
            question, helpfulSent: false,
            charts: data.charts, tables: data.tables, dataDays: data.dataDays
          }
        : {
            role: 'assistant', kind: 'text',
            content: data.answer || '（没有返回内容）',
            model: data.model, tokens: data.tokens, costMs: data.costMs,
            visionUsed: data.visionUsed, experienceUsed: data.experienceUsed,
            question, helpfulSent: false
          }
      if (pushReplyToConv(fromConvId, msg)) {
        if (activeConvId.value === fromConvId) {
          messages.value.push(msg)
          scrollBottom()
        }
      }
      snapshot.value = data.dataSnapshot || ''; snapshotOpen.value = false
    }
    // 回复已 pushReplyToConv 进 fromConv，这里把它落盘并刷新标题/时间戳
    touchConv(fromConvId)
  } catch {
    const errMsg = { role: 'assistant', kind: 'text', content: '这次请求失败了，稍后再试一次。', model: '', tokens: 0, costMs: 0 }
    if (pushReplyToConv(fromConvId, errMsg)) {
      touchConv(fromConvId)
      if (activeConvId.value === fromConvId) {
        messages.value.push(errMsg)
        scrollBottom()
      }
    }
  } finally {
    loading.value = false
    pendingDraft.value = false
  }
}

function appendDraftCard(draft, image) {
  const msg = {
    role: 'assistant', kind: 'draft',
    draft: { ...draft, localId: draft.localId || ('draft-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7)) },
    draftImage: image || '', content: draft.note || '', model: draft.model, tokens: draft.tokens, costMs: 0,
    question: '', helpfulSent: false
  }
  // 重做后的新草稿也要同步进草稿箱（档口预览读这份）
  draftStore.upsert({ ...msg.draft, image: image || '', createdAt: new Date().toISOString() })
  messages.value.push(msg)
  scrollBottom()
}

function onDishCreated() {}

/** 商家放弃草稿：把这张卡片从当前对话移除（会话里的持久化消息一并删掉） */
function discardDraftCard(msg) {
  const i = messages.value.indexOf(msg)
  if (i >= 0) messages.value.splice(i, 1)
  // 跨会话引用：也在归属会话里删一遍
  const conv = conversations.value.find(c => c.id === activeConvId.value)
  if (conv && conv.messages !== messages.value) {
    const j = conv.messages.indexOf(msg)
    if (j >= 0) conv.messages.splice(j, 1)
  }
  touchTitle()
}

function quickAsk(q) { input.value = q; send() }

async function markHelpful(m) {
  try {
    await assistantFeedback({ question: m.question, answer: m.content, helpful: true })
    m.helpfulSent = true
    touchTitle()
    ElMessage.success('已记入经验库')
  } catch {}
}

onMounted(loadConvs)
onActivated(() => {
  if (!conversations.value.length) loadConvs()
  scrollBottom()
})
watch([messages, activeSkill], touchTitle, { deep: true })
</script>

<style scoped>
/* ============ 布局：历史侧栏 + 卡片，卡片高度锁死，仅消息区滚动 ============ */
.assistant-wrap {
  display: flex;
  gap: 16px;
  width: 100%;
  height: calc(100vh - 64px);
  min-height: 520px;
  padding: 24px 30px 30px;
  background: var(--bg);
  box-sizing: border-box;
  overflow: hidden;
}

/* 左历史侧栏 */
.conv-side {
  width: 236px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border: 1px solid var(--border);
  border-radius: 22px;
  overflow: hidden;
  transition: width 0.24s var(--ease-apple), opacity 0.2s ease;
}
.conv-side.collapsed { width: 0; border: none; opacity: 0; }
.conv-side-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 18px 8px;
}
.conv-side-title { font-size: 12px; font-weight: 600; color: var(--text-3); letter-spacing: 0.04em; }
.conv-side-count { font-size: 11px; color: var(--text-3); }
.icon-btn {
  appearance: none; border: none; background: transparent; color: var(--text-2);
  width: 28px; height: 28px; border-radius: 8px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.icon-btn:hover { background: #f0f0f2; color: var(--text-1); }
.conv-list { flex: 1; padding: 4px 8px 10px; }
.conv-item {
  display: flex; align-items: center; gap: 8px;
  padding: 9px 10px; border-radius: 10px; cursor: pointer;
  transition: background-color 0.15s ease;
}
.conv-item:hover { background: #f5f5f7; }
.conv-item.active { background: #ececee; }
.conv-dot { width: 6px; height: 6px; border-radius: 50%; background: transparent; flex-shrink: 0; transition: background-color 0.15s ease; }
.conv-dot.on { background: var(--brand); }
.conv-body { flex: 1; min-width: 0; }
.conv-title { font-size: 13px; color: var(--text-1); font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-time { font-size: 11px; color: var(--text-3); margin-top: 2px; }
.conv-del { appearance: none; border: none; background: transparent; color: #c7c7cc;
  width: 22px; height: 22px; border-radius: 6px; cursor: pointer; flex-shrink: 0;
  display: none; align-items: center; justify-content: center; }
.conv-item:hover .conv-del { display: inline-flex; }
.conv-del:hover { background: #e8e8ea; color: var(--text-1); }
.conv-empty { flex: 1; display: flex; align-items: center; justify-content: center; font-size: 12px; color: var(--text-3); }

/* 右卡片 */
.assistant-card {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

/* ============ 头部 ============ */
.assistant-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px;
  padding: 14px 22px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}
.head-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.head-avatar { width: 38px; height: 38px; border-radius: 12px; object-fit: cover; flex-shrink: 0; }
.head-text { display: flex; flex-direction: column; min-width: 0; }
.assistant-title { font-size: 20px; font-weight: 650; letter-spacing: -0.02em; color: var(--text-1); line-height: 1.15; }
.head-sub { font-size: 12px; color: var(--text-3); margin-top: 2px; }
.head-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.head-note { font-size: 11.5px; color: var(--text-3); white-space: nowrap; }
.hide-btn { width: 30px; height: 30px; }

/* ============ 消息区 ============ */
.assistant-body { flex: 1; min-height: 0; overflow-y: auto; padding: 24px 30px; scroll-behavior: smooth; }
.assistant-body::-webkit-scrollbar { width: 6px; }
.assistant-body::-webkit-scrollbar-thumb { background: #e0e0e0; border-radius: 3px; }

.msg-row { display: flex; margin-bottom: 18px; }
.msg-row-inner { max-width: 86%; }
.msg-row-inner--wide { max-width: 100%; width: 100%; }
.msg-user { justify-content: flex-end; }
.msg-ai { justify-content: flex-start; }
.msg-user .msg-row-inner { display: flex; flex-direction: column; align-items: flex-end; max-width: 76%; }

.msg-text {
  font-size: 15px; line-height: 1.75; color: var(--text-1);
  white-space: pre-wrap; word-break: break-word;
}
.msg-ai .msg-text :deep(strong) { font-weight: 600; }
.msg-ai .msg-text :deep(.li-num),
.msg-ai .msg-text :deep(.li-dot) {
  color: var(--brand-dark); font-weight: 600; margin-right: 2px;
}
.msg-user .msg-text {
  padding: 9px 15px; border-radius: 18px; border-bottom-right-radius: 6px;
  background: #f0f0f2; color: var(--text-1); max-width: 100%;
}
.msg-user .msg-image { border-radius: 14px; margin-bottom: 6px; max-width: 220px; }

.msg-meta {
  margin-top: 6px; font-size: 10.5px; color: #c7c7cc;
  opacity: 0; transition: opacity 0.18s ease;
}
.msg-row:hover .msg-meta { opacity: 1; }
.msg-ai .msg-text + .msg-meta { padding-left: 2px; }
.help-btn {
  appearance: none; border: none; background: transparent; padding: 2px;
  font-size: 12px; color: var(--brand-dark); cursor: pointer; margin-top: 4px;
  opacity: 0; transition: opacity 0.18s ease;
}
.msg-row:hover .help-btn,
.help-btn:focus-visible { opacity: 1; }
.help-done {
  font-size: 12px; color: #c7c7cc; margin-top: 4px; display: inline-block;
  opacity: 0; transition: opacity 0.18s ease;
}
.msg-row:hover .help-done { opacity: 1; }
.typing { font-size: 14px; color: var(--text-3); padding: 6px 2px; }

/* ============ 输入坞：技能 + 快问一行，输入一行 ============ */
.input-dock {
  flex-shrink: 0;
  padding: 12px 22px 18px;
  border-top: 1px solid var(--border);
  background: #fff;
}

.dock-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-bottom: 10px;
}

.skill-chips { flex-shrink: 0; }

.quick-row { display: flex; flex-wrap: wrap; align-items: center; gap: 2px; }
.quick-chip {
  appearance: none; border: none; background: transparent; color: var(--text-3);
  font-family: inherit; font-size: 12px; padding: 5px 9px; border-radius: 999px; cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.quick-chip:hover { background: #f5f5f7; color: var(--text-1); }

.attach-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; margin-bottom: 10px;
  background: #f5f5f7; border-radius: 12px;
}
.attach-thumb { width: 44px; height: 44px; border-radius: 8px; flex-shrink: 0; }
.attach-hint { font-size: 12.5px; color: var(--text-2); flex: 1; }
.attach-close { cursor: pointer; color: var(--text-3); }
.attach-close:hover { color: var(--text-1); }

.input-row { display: flex; align-items: flex-end; gap: 10px; }
.input-row .el-textarea { flex: 1; }
.input-row :deep(.el-textarea__inner) {
  font-size: 14px; line-height: 1.6; padding: 10px 14px;
  border-radius: 12px; border: 1px solid #e4e4e8;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
}
.input-row :deep(.el-textarea__inner:focus) { border-color: var(--text-1); box-shadow: 0 0 0 3px rgba(0,0,0,0.04); }

.img-btn { flex-shrink: 0; }
.round-btn {
  appearance: none; border: 1px solid var(--border-strong); background: #fff; color: var(--text-2);
  width: 40px; height: 40px; border-radius: 999px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: border-color 0.18s ease, color 0.18s ease;
}
.round-btn:hover, .round-btn.active { border-color: var(--text-1); color: var(--text-1); }

.send-btn {
  appearance: none; border: none; background: var(--text-1); color: #fff;
  width: 40px; height: 40px; border-radius: 999px; cursor: pointer; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  transition: opacity 0.18s ease, background-color 0.18s ease;
}
.send-btn:hover:not(:disabled) { background: #333336; }
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.send-loading {
  width: 16px; height: 16px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.msg-image { width: 140px; height: 100px; border-radius: 10px; margin-bottom: 6px; display: block; }

@media (max-width: 1000px) {
  .assistant-wrap { padding: 18px 20px 22px; }
  .assistant-body { padding: 20px 22px; }
}

@media (max-width: 900px) {
  .conv-side, .conv-side.collapsed { display: none; }
  .hide-btn { display: none; }
}
</style>