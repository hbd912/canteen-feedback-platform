<template>
  <div class="cs-page">
    <!-- 页头：标题与新建会话 -->
    <div class="ap-head cs-head">
      <div>
        <h1 class="ap-title cs-title">客服会话</h1>
        <div class="ap-sub">
          {{ stallGroups.length }} 个档口 · {{ sessions.length }} 个会话
          <span v-if="totalUnread" class="cs-head-unread">· {{ totalUnread }} 条未读</span>
        </div>
      </div>
      <div class="ap-actions">
        <button class="ap-btn cs-new-session" @click="openNewSession">
          <el-icon :size="14"><Plus /></el-icon>
          新建会话
        </button>
      </div>
    </div>
    <div class="cs-shell">
      <!-- 左：按档口分组的会话列表 -->
      <div class="cs-side">
        <div class="cs-side-head">
          <span class="cs-side-title">档口列表</span>
          <div class="cs-side-actions">
            <span class="cs-side-total">{{ stallGroups.length }} 个档口</span>
            <button class="cs-manage-btn" :class="{ on: manageMode }" @click="toggleManage">{{ manageMode ? '完成' : '管理' }}</button>
          </div>
        </div>
        <el-scrollbar class="cs-session-list">
          <div v-if="!sessions.length && !loadingSessions" class="ap-empty cs-side-empty">
            <div class="ap-empty-mark"><el-icon :size="18"><ChatDotRound /></el-icon></div>
            <div class="ap-empty-title">暂无会话</div>
            <div>去订单页联系客服吧</div>
          </div>
          <div v-for="g in stallGroups" :key="g.stallId" class="cs-group">
            <div
              class="cs-group-head"
              :class="{ active: expandedStallId === g.stallId }"
              @click="toggleGroup(g)"
            >
              <img class="cs-session-avatar" :src="g.stallAvatar" :alt="g.stallName" />
              <div class="cs-group-info">
                <div class="cs-group-name">{{ g.stallName || '档口' }}</div>
                <div class="cs-group-sub">{{ g.sessions.length }} 个会话 · {{ shortTime(g.lastTime) }}</div>
              </div>
              <el-badge v-if="g.unread" :value="g.unread" :max="99" class="cs-badge" />
              <el-icon class="cs-group-arrow" :class="{ open: expandedStallId === g.stallId }"><ArrowRight /></el-icon>
            </div>
            <div v-if="expandedStallId === g.stallId" class="cs-group-sessions">
              <div
                v-for="s in g.sessions"
                :key="s.id"
                class="cs-session"
                :class="{
                  active: !manageMode && activeSessionId === s.id,
                  closed: s.status === 'CLOSED',
                  picked: manageMode && isSelected(s.id)
                }"
                @click="manageMode ? toggleSelect(s.id) : selectSession(s)"
              >
                <div class="cs-session-top">
                  <span v-if="manageMode" class="cs-sel-box" :class="{ on: isSelected(s.id) }">✓</span>
                  <span class="cs-session-name">{{ s.orderId ? `订单 #${s.orderId}` : '咨询会话' }}</span>
                  <span v-if="s.status === 'CLOSED'" class="cs-session-state">已结束</span>
                  <span class="cs-session-time">{{ shortTime(s.lastMessageTime) }}</span>
                  <el-button
                    v-if="!manageMode"
                    class="cs-session-del"
                    link
                    type="danger"
                    size="small"
                    :icon="Delete"
                    title="删除会话（只影响我这边）"
                    @click.stop="removeSession(s)"
                  />
                </div>
                <div class="cs-session-bottom">
                  <span class="cs-session-last">{{ s.lastMessage || '暂无消息' }}</span>
                  <el-badge v-if="s.studentUnread" :value="s.studentUnread" :max="99" class="cs-badge" />
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
        <div v-if="manageMode" class="cs-batch-bar">
          <button class="cs-batch-all" @click="toggleSelectAll">
            {{ allSelected ? '取消全选' : '全选' }}
          </button>
          <button class="cs-batch-del" :disabled="!selectedIds.length" @click="batchDelete">
            删除选中 ({{ selectedIds.length }})
          </button>
        </div>
      </div>

    <!-- 右：聊天面板 -->
    <div class="cs-chat">
      <template v-if="activeSession">
        <div class="cs-chat-head" @click="goStall(activeSession)">
          <img class="cs-chat-head-avatar" :src="activeSession.stallAvatar" :alt="activeSession.stallName" />
          <div class="cs-chat-head-info">
            <div class="cs-chat-title">{{ activeSession.stallName || '档口' }}</div>
            <div class="cs-chat-sub">与商家在线沟通中</div>
          </div>
          <span class="cs-chat-enter">进入店铺 ›</span>
        </div>
        <div class="cs-chat-body" ref="chatBodyRef">
          <div v-for="m in messages" :key="m.id" class="msg-row" :class="msgClass(m)">
            <img
              v-if="!m.self"
              class="msg-avatar"
              :src="activeSession.stallAvatar"
              :alt="activeSession.stallName"
            />
            <div
              class="msg-stack"
              :class="{ 'msg-stack--self': m.self, 'msg-stack--wide': m.couponOfferId }"
            >
              <div class="msg-bubble">
                <div v-if="!m.self" class="msg-sender">
                  {{ activeSession.stallName || '商家' }}
                  <span v-if="m.senderType === 'AI'" class="ai-tag">AI</span>
                </div>
                <!-- 咨询菜品卡片（AI 推荐菜也可一键加购） -->
                <div v-if="m.dishId" class="dish-card" @click="goDish(m.dishId)">
                  <img v-if="m.dishImage" class="dish-card-img" :src="m.dishImage" :alt="m.dishName" />
                  <div class="dish-card-info">
                    <div class="dish-card-name">{{ m.dishName }}</div>
                    <div class="dish-card-price">¥{{ m.dishPrice ?? '—' }}</div>
                  </div>
                  <span class="dish-card-add" title="加入购物车" @click.stop="addCart(m)">
                    <el-icon><Plus /></el-icon>
                  </span>
                </div>
                <div class="msg-content">{{ m.content }}</div>
                <ChatCouponCard
                  v-if="m.couponOfferId"
                  :message="m"
                  can-claim
                  :claiming="claimingOfferId === m.couponOfferId"
                  @claim="claimOffer"
                />
              </div>
              <div class="msg-time">{{ shortTime(m.createTime) }}</div>
            </div>
            <img
              v-if="m.self"
              class="msg-avatar"
              :src="activeSession.studentAvatar"
              alt="我"
            />
          </div>
          <div v-if="loadingMessages" class="msg-loading">加载中…</div>
        </div>

        <!-- 已选咨询菜品 -->
        <div v-if="selectedDish" class="attach-bar">
          <img class="attach-img" :src="selectedDish.image" :alt="selectedDish.name" />
          <div class="attach-info">
            <span class="attach-name">{{ selectedDish.name }}</span>
            <span class="attach-price">¥{{ selectedDish.price ?? '—' }}</span>
          </div>
          <button class="attach-close" title="取消附带菜品" @click="clearDish">
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <div class="cs-chat-input">
          <button class="ap-btn ap-btn--light menu-btn" @click="openDishMenu">
            <el-icon :size="14"><Dish /></el-icon>
            菜单咨询
          </button>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="1000"
            placeholder="输入想咨询的问题，例如：今天的菜多久能出餐？"
            @keydown.enter.exact.prevent="send"
          />
          <el-button
            type="primary"
            circle
            class="send-btn"
            :icon="Promotion"
            :loading="sending"
            title="发送"
            aria-label="发送"
            @click="send"
          />
        </div>
      </template>
      <div v-else class="ap-empty cs-chat-empty">
        <div class="cs-chat-empty-mark"><el-icon :size="22"><ChatDotRound /></el-icon></div>
        <div class="ap-empty-title">选择一个会话开始沟通</div>
        <div>从左侧档口列表进入会话，或新建一个咨询</div>
      </div>
    </div>
    </div>

    <!-- 菜单咨询弹窗 -->
    <el-dialog v-model="dishMenuVisible" :title="`${activeSession?.stallName || ''} 的菜单`" width="540px" class="dish-menu-dialog">
      <el-scrollbar max-height="420px" class="menu-scroll">
        <div v-if="dishLoading" class="dish-loading">加载中…</div>
        <div v-else-if="!menuDishes.length" class="ap-empty menu-empty">
          <div class="ap-empty-mark"><el-icon :size="18"><Dish /></el-icon></div>
          <div class="ap-empty-title">该档口暂无在售菜品</div>
          <div>换个档口或稍后再来看看</div>
        </div>
        <div v-else class="menu-grid">
          <div
            v-for="d in menuDishes"
            :key="d.id"
            class="menu-item"
            :class="{ selected: selectedDish?.id === d.id }"
            @click="chooseDish(d)"
          >
            <div class="menu-item-img-wrap">
              <img v-if="d.image" class="menu-item-img" :src="d.image" :alt="d.name" />
              <span v-else class="menu-item-img fallback">{{ (d.name || '菜').slice(0, 1) }}</span>
              <span v-if="selectedDish?.id === d.id" class="menu-item-check"><el-icon><Select /></el-icon></span>
            </div>
            <div class="menu-item-name">{{ d.name }}</div>
            <div class="menu-item-price">¥{{ d.price }}</div>
          </div>
        </div>
      </el-scrollbar>
      <template #footer>
        <span class="menu-tip">{{ selectedDish ? `已选：${selectedDish.name}` : '点一道菜，可附带一起咨询' }}</span>
        <el-button @click="dishMenuVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedDish" @click="confirmDish">附上这道菜去咨询</el-button>
      </template>
    </el-dialog>

    <!-- 新建会话：选择档口 -->
    <el-dialog v-model="newSessionVisible" title="新建会话" width="420px">
      <div class="new-session-tip">选择要咨询的档口，会为你开一个干净的新会话：</div>
      <el-radio-group v-model="newSessionStallId" class="new-session-options">
        <el-radio v-for="g in stallGroups" :key="g.stallId" :value="g.stallId" class="new-session-option">
          {{ g.stallName || '档口' }}
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="newSessionVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!newSessionStallId" @click="confirmNewSession">新建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Select, Plus, Delete, ArrowRight, Promotion } from '@element-plus/icons-vue'
import {
  listSessions, createSession, getMessages, sendMessage, streamMessages, deleteSession,
  readSessionMessages
} from '@/api/customerService'
import { listAllDishes } from '@/api/dish'
import { addToCart } from '@/api/cart'
import { claimCouponOffer } from '@/api/coupon'
import ChatCouponCard from '@/components/coupon/ChatCouponCard.vue'
import { useCartStore } from '@/store/cart'

const cartStore = useCartStore()

const route = useRoute()
const router = useRouter()

const sessions = ref([])
const activeSessionId = ref(null)
const activeSession = ref(null)
const messages = ref([])
const inputText = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const claimingOfferId = ref(null)
const chatBodyRef = ref(null)

const dishMenuVisible = ref(false)
const dishLoading = ref(false)
const menuDishes = ref([])
const selectedDish = ref(null)

/** 当前展开的档口分组 */
const expandedStallId = ref(null)
/** 批量管理（多选删除） */
const manageMode = ref(false)
const selectedIds = ref([])
const allSelected = computed(() =>
  sessions.value.length > 0 && selectedIds.value.length === sessions.value.length
)
/** 新建会话：选档口弹窗 */
const newSessionVisible = ref(false)
const newSessionStallId = ref(null)

/** 按档口把会话归组：第一层只显示档口，点进去再看该档口的多个会话 */
const stallGroups = computed(() => {
  const map = new Map()
  for (const s of sessions.value) {
    const key = s.stallId ?? 'unknown'
    let g = map.get(key)
    if (!g) {
      g = {
        stallId: s.stallId,
        stallName: s.stallName,
        stallAvatar: s.stallAvatar,
        unread: 0,
        lastTime: null,
        sessions: []
      }
      map.set(key, g)
    }
    g.sessions.push(s)
    g.unread += s.studentUnread || 0
    if (!g.lastTime || String(s.lastMessageTime || '') > String(g.lastTime || '')) {
      g.lastTime = s.lastMessageTime
    }
  }
  return [...map.values()].sort((a, b) => String(b.lastTime || '').localeCompare(String(a.lastTime || '')))
})

/** 未读合计：仅用于页头副标题展示 */
const totalUnread = computed(() => stallGroups.value.reduce((sum, g) => sum + (g.unread || 0), 0))

let pollTimer = null
let stream = null
let reconnectTimer = null
let sessionsTimer = null
let sseAlive = false
let active = true

function shortTime(t) {
  if (!t) return ''
  const s = String(t)
  return s.length >= 16 ? s.slice(5, 16) : s
}

function msgClass(m) {
  return m.self ? 'msg-self' : 'msg-other'
}

function avatarOf(m) {
  return m.self ? (activeSession.value?.studentAvatar || '') : (activeSession.value?.stallAvatar || '')
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    const res = await listSessions()
    sessions.value = res.data || []
  } catch {
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

function selectSession(s) {
  activeSessionId.value = s.id
  activeSession.value = s
  expandedStallId.value = s.stallId
  selectedDish.value = null
  // 进入会话即视为已读：先清本地红点，等后端 getMessages 清零后再通知铃铛刷新（避免竞态取到旧值）
  markSessionReadLocal(s)
  loadMessages(s.id).then(() => window.dispatchEvent(new CustomEvent('cs:unread-changed')))
  startStream(s.id)
}

/** 只清前端本侧未读，不发通知（通知要等服务端未读真正清零后再发） */
function markSessionReadLocal(s) {
  if (!s) return
  s.studentUnread = 0
}

/** 展开/收起某个档口；展开时自动进入它最近的会话 */
function toggleGroup(g) {
  if (expandedStallId.value === g.stallId) {
    expandedStallId.value = null
    return
  }
  expandedStallId.value = g.stallId
  const first = g.sessions[0]
  if (first && activeSessionId.value !== first.id) {
    selectSession(first)
  }
}

/** 切换批量管理模式 */
function toggleManage() {
  manageMode.value = !manageMode.value
  selectedIds.value = []
}

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function toggleSelect(id) {
  selectedIds.value = isSelected(id)
    ? selectedIds.value.filter(x => x !== id)
    : [...selectedIds.value, id]
}

function toggleSelectAll() {
  selectedIds.value = allSelected.value ? [] : sessions.value.map(s => s.id)
}

/** 批量删除：逐条隐藏自己这边的会话 */
async function batchDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      `删除选中的 ${ids.length} 个会话？只会在你这边隐藏，商家仍保留记录。`,
      '批量删除会话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    for (const id of ids) {
      await deleteSession(id)
    }
    sessions.value = sessions.value.filter(s => !ids.includes(s.id))
    if (ids.includes(activeSessionId.value)) {
      closeStream()
      activeSessionId.value = null
      activeSession.value = null
      messages.value = []
    }
    selectedIds.value = []
    manageMode.value = false
    window.dispatchEvent(new CustomEvent('cs:unread-changed'))
    ElMessage.success(`已删除 ${ids.length} 个会话`)
    loadSessions()
  } catch {
    // 请求层已提示
  }
}

/** 删除会话：只隐藏自己这边的记录，不影响商家 */
async function removeSession(s) {
  try {
    await ElMessageBox.confirm(
      `删除后这个会话只在你这边隐藏（商家仍保留记录），确定删除吗？`,
      '删除会话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteSession(s.id)
    sessions.value = sessions.value.filter(x => x.id !== s.id)
    if (activeSessionId.value === s.id) {
      closeStream()
      activeSessionId.value = null
      activeSession.value = null
      messages.value = []
    }
    if (expandedStallId.value != null && !stallGroups.value.some(g => g.stallId === expandedStallId.value)) {
      expandedStallId.value = null
    }
    window.dispatchEvent(new CustomEvent('cs:unread-changed'))
    ElMessage.success('已删除该会话')
    loadSessions()
  } catch {
    // 请求层已提示
  }
}

/** 按消息 id 去重合并：SSE 推送与接口返回可能同时到达 */
function upsertMessage(m) {
  const idx = messages.value.findIndex(x => x.id === m.id)
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], ...m }
  } else {
    messages.value.push(m)
  }
}

function startStream(sessionId) {
  closeStream()
  sseAlive = false
  stream = streamMessages(sessionId, {
    onOpen: () => { sseAlive = true },
    onMessage: (m) => {
      m.self = m.senderType === 'STUDENT'
      upsertMessage(m)
      if (m.senderType === 'STUDENT') {
        sending.value = false
      } else {
        // 正开着这个会话：立刻把服务端未读也清掉，清完再通知铃铛，避免「人在窗口里红点还在」
        readSessionMessages(sessionId)
          .then(() => {
            markSessionReadLocal(sessions.value.find(x => x.id === sessionId))
            window.dispatchEvent(new CustomEvent('cs:unread-changed'))
          })
          .catch(() => {})
      }
      scrollToBottom()
      scheduleSessionsRefresh()
    },
    onError: () => {
      sseAlive = false
      scheduleReconnect(sessionId)
    }
  })
}

function closeStream() {
  if (stream) {
    stream.close()
    stream = null
  }
  sseAlive = false
}

function scheduleReconnect(sessionId) {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
  reconnectTimer = setTimeout(() => {
    if (active && activeSessionId.value === sessionId) {
      startStream(sessionId)
    }
  }, 3000)
}

function scheduleSessionsRefresh() {
  if (sessionsTimer) {
    return
  }
  sessionsTimer = setTimeout(() => {
    sessionsTimer = null
    loadSessions()
  }, 800)
}

async function loadMessages(sessionId) {
  loadingMessages.value = true
  try {
    const res = await getMessages(sessionId)
    messages.value = res.data || []
    scrollToBottom()
  } catch {
    messages.value = []
  } finally {
    loadingMessages.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  })
}

/** 领取客服发的券卡片 */
async function claimOffer(m) {
  if (!m.couponOfferId || claimingOfferId.value) return
  claimingOfferId.value = m.couponOfferId
  try {
    await claimCouponOffer(m.couponOfferId)
    m.couponOfferStatus = 'CLAIMED'
    ElMessage.success('已领取，可在「我的优惠券」里查看')
  } catch {
    // 请求层已提示
  } finally {
    claimingOfferId.value = null
  }
}

async function send() {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择一个会话')
    return
  }
  sending.value = true
  try {
    const res = await sendMessage(activeSessionId.value, text, selectedDish.value?.id || null)
    if (!sseAlive) {
      const list = res.data || []
      for (const m of list) {
        m.self = m.senderType === 'STUDENT'
      }
      messages.value = list
    }
    inputText.value = ''
    selectedDish.value = null
    scrollToBottom()
    loadSessions()
  } catch {
    // 请求层已统一提示
  } finally {
    sending.value = false
  }
}

/** 新建会话：有当前会话就用它的档口；否则弹窗选档口（只有一个档口时直接建） */
async function openNewSession() {
  const activeStall = activeSession.value?.stallId
  if (activeStall) {
    await createNewSession(activeStall)
    return
  }
  if (!stallGroups.value.length) {
    ElMessage.warning('暂无档口会话，请先从订单页联系客服')
    return
  }
  if (stallGroups.value.length === 1) {
    await createNewSession(stallGroups.value[0].stallId)
    return
  }
  newSessionStallId.value = stallGroups.value[0].stallId
  newSessionVisible.value = true
}

async function confirmNewSession() {
  if (!newSessionStallId.value) return
  newSessionVisible.value = false
  await createNewSession(newSessionStallId.value)
}

/** 以某个档口开一个干净的新会话，旧会话自动归档 */
async function createNewSession(stallId) {
  try {
    const res = await createSession({ stallId, newSession: true })
    const session = res.data
    if (session && session.id) {
      await loadSessions()
      selectSession(sessions.value.find(x => x.id === session.id) || session)
      ElMessage.success('已新建会话')
    }
  } catch {
    // 请求层已统一提示
  }
}

async function openDishMenu() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择一个会话')
    return
  }
  dishMenuVisible.value = true
  dishLoading.value = true
  try {
    menuDishes.value = await listAllDishes(activeSession.value.stallId)
  } catch {
    menuDishes.value = []
  } finally {
    dishLoading.value = false
  }
}

function chooseDish(d) {
  selectedDish.value = d
}

function confirmDish() {
  if (!selectedDish.value) return
  inputText.value = `我想咨询「${selectedDish.value.name}」：`
  dishMenuVisible.value = false
  ElMessage.success('已附上菜品，可直接补充问题后发送')
}

function clearDish() {
  selectedDish.value = null
}

function goDish(dishId) {
  if (dishId) router.push(`/dishes/${dishId}`)
}

/** 从聊天里的菜品卡片直接加入购物车 + 打开购物车抽屉 */
async function addCart(m) {
  if (!m?.dishId) return
  try {
    await addToCart(m.dishId, 1)
    window.dispatchEvent(new CustomEvent('canteen:cart-changed'))
    ElMessage.success(`已加入购物车：${m.dishName || '菜品'}`)
    cartStore.openDrawer()
  } catch {
    // 请求层已统一弹出错误提示，这里保持静默
  }
}

function goStall(session) {
  if (session?.stallId) router.push(`/stalls/${session.stallId}`)
}

async function handleEntryQuery() {
  const stall = Number(route.query.stall)
  if (!stall) return
  const orderId = route.query.order ? Number(route.query.order) : undefined
  router.replace({ path: '/customer-service' })
  try {
    const res = await createSession({ stallId: stall, orderId })
    const session = res.data
    if (session && session.id) {
      await loadSessions()
      selectSession(session)
    }
  } catch {
    loadSessions()
  }
}

function startPolling() {
  stopPolling()
  // 兜底：SSE 断开时才每 20 秒补拉一次，正常情况下完全靠推送
  pollTimer = setInterval(() => {
    if (!sseAlive && activeSessionId.value) {
      getMessages(activeSessionId.value).then(res => {
        const list = res.data || []
        for (const m of list) {
          m.self = m.senderType === 'STUDENT'
        }
        messages.value = list
      }).catch(() => {})
    }
  }, 20000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (sessionsTimer) {
    clearTimeout(sessionsTimer)
    sessionsTimer = null
  }
}

function init() {
  loadSessions()
  handleEntryQuery()
}

onMounted(init)
onActivated(() => {
  active = true
  loadSessions()
  handleEntryQuery()
  startPolling()
  if (activeSessionId.value) {
    startStream(activeSessionId.value)
  }
})
onDeactivated(() => {
  active = false
  stopPolling()
  closeStream()
})
onBeforeUnmount(() => {
  active = false
  stopPolling()
  closeStream()
})
</script>

<style scoped>
.cs-page {
  height: calc(100vh - 64px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: 20px 24px 24px;
  background: var(--bg);
  color: var(--text-1);
  font-family: var(--font-sans);
  -webkit-font-smoothing: antialiased;
}

.cs-head {
  flex-shrink: 0;
  margin-bottom: 18px;
}

.cs-title {
  font-size: 26px;
}

.cs-head .ap-sub {
  margin-top: 5px;
}

.cs-head-unread {
  color: var(--brand-dark);
}

.cs-new-session {
  height: 36px;
  padding: 0 16px;
  font-size: 13px;
}

/* 白色大卡外壳，双栏都用发丝线分层 */
.cs-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

/* ---------- 左栏：档口 / 会话 ---------- */
.cs-side {
  width: 280px;
  flex-shrink: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-right: 1px solid var(--border);
}

.cs-side-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px 10px;
}

.cs-side-title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
}

.cs-side-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cs-side-total {
  font-size: 11.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.cs-manage-btn {
  border: none;
  background: none;
  padding: 2px 4px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  color: var(--text-2);
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), color 0.15s var(--ease-apple);
}

.cs-manage-btn:hover {
  background: var(--bg);
  color: var(--text-1);
}

.cs-manage-btn.on {
  color: var(--text-1);
  font-weight: 600;
}

.cs-session-list {
  flex: 1;
  min-height: 0;
}

.cs-session-list :deep(.el-scrollbar__thumb) {
  background: rgba(0, 0, 0, 0.16);
  opacity: 1;
}

.cs-side-empty {
  padding: 40px 16px;
  font-size: 12px;
}

.cs-side-empty .ap-empty-mark {
  width: 40px;
  height: 40px;
  font-size: 16px;
}

.cs-side-empty .ap-empty-title {
  font-size: 13px;
}

/* 档口分组 */
.cs-group {
  margin: 2px 8px;
}

.cs-group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple);
}

.cs-group-head:hover {
  background: var(--bg);
}

.cs-group-head.active {
  background: #f0f0f2;
}

.cs-session-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: #eee;
}

.cs-group-info {
  flex: 1;
  min-width: 0;
}

.cs-group-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-group-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-group-arrow {
  font-size: 12px;
  color: var(--text-3);
  transition: transform 0.2s var(--ease-apple);
}

.cs-group-arrow.open {
  transform: rotate(90deg);
}

.cs-group-sessions {
  padding: 2px 0 6px;
}

.cs-badge {
  flex-shrink: 0;
}

/* 会话行：选中用中性灰块 + 黑色短指示条 */
.cs-session {
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple);
}

.cs-session + .cs-session {
  margin-top: 2px;
}

.cs-session:hover {
  background: var(--bg);
}

.cs-session.active {
  background: #f0f0f2;
  box-shadow: inset 3px 0 0 #1d1d1f;
}

.cs-session.picked {
  background: #f0f0f2;
}

.cs-session.closed {
  opacity: 0.6;
}

.cs-session.closed:hover {
  opacity: 0.8;
}

.cs-session.closed.active {
  opacity: 1;
}

.cs-session-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.cs-session-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-session-state {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-3);
  font-size: 10.5px;
  line-height: 16px;
  white-space: nowrap;
}

.cs-session-time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.cs-session-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.cs-session-last {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-session-del {
  margin-left: 0;
}

.cs-session-del :deep(.el-icon) {
  color: var(--text-3);
}

.cs-session-del:hover :deep(.el-icon) {
  color: #c9342b;
}

.cs-sel-box {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid #d2d2d7;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  line-height: 1;
  color: transparent;
  flex-shrink: 0;
  transition: background 0.15s var(--ease-apple), border-color 0.15s var(--ease-apple),
    color 0.15s var(--ease-apple);
}

.cs-sel-box.on {
  background: #1d1d1f;
  border-color: #1d1d1f;
  color: #fff;
}

/* 批量删除条 */
.cs-batch-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--surface);
  border-top: 1px solid var(--border);
}

.cs-batch-all {
  border: none;
  background: none;
  padding: 0;
  font-size: 12.5px;
  font-weight: 500;
  font-family: inherit;
  color: var(--text-2);
  cursor: pointer;
}

.cs-batch-all:hover {
  color: var(--text-1);
}

.cs-batch-del {
  margin-left: auto;
  border: none;
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: #d92d20;
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.cs-batch-del:hover:not(:disabled) {
  background: #c6281c;
}

.cs-batch-del:active:not(:disabled) {
  transform: scale(0.97);
}

.cs-batch-del:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* ---------- 右栏：聊天 ---------- */
.cs-chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

.cs-chat-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s var(--ease-apple);
}

.cs-chat-head:hover {
  background: #fafafa;
}

.cs-chat-head-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.cs-chat-head-info {
  flex: 1;
  min-width: 0;
}

.cs-chat-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.cs-chat-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-3);
}

.cs-chat-enter {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-2);
  transition: color 0.15s var(--ease-apple);
}

.cs-chat-head:hover .cs-chat-enter {
  color: var(--text-1);
}

/* 消息滚动区：细腻滚动条 */
.cs-chat-body {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 24px;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.16) transparent;
}

.cs-chat-body::-webkit-scrollbar {
  width: 8px;
}

.cs-chat-body::-webkit-scrollbar-track {
  background: transparent;
}

.cs-chat-body::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.12);
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: content-box;
}

.cs-chat-body::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.22);
  background-clip: content-box;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.msg-self {
  justify-content: flex-end;
}

.msg-other {
  justify-content: flex-start;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: #eee;
}

.msg-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  max-width: 68%;
  min-width: 0;
}

.msg-stack--self {
  align-items: flex-end;
}

/* 含券消息给卡片留出更宽的空间 */
.msg-stack--wide {
  max-width: 82%;
}

.msg-bubble {
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.6;
}

.msg-other .msg-bubble {
  background: var(--surface);
  color: var(--text-1);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 0 0 1px rgba(0, 0, 0, 0.03);
}

.msg-self .msg-bubble {
  background: #1d1d1f;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-sender {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
}

.ai-tag {
  padding: 0 6px;
  border-radius: 999px;
  background: #eaf3ff;
  color: #1f6fd1;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

.msg-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-self .msg-content {
  color: #fff;
}

.msg-time {
  padding: 0 4px;
  font-size: 11px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.msg-loading {
  text-align: center;
  color: var(--text-3);
  font-size: 12px;
}

/* 菜品引用卡 */
.dish-card {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 240px;
  margin-bottom: 8px;
  padding: 8px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), border-color 0.15s var(--ease-apple);
}

.dish-card:hover {
  border-color: var(--border-strong);
}

.msg-self .dish-card {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.12);
}

.msg-self .dish-card:hover {
  background: rgba(255, 255, 255, 0.14);
}

.dish-card-img {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.dish-card-info {
  flex: 1;
  min-width: 0;
}

.dish-card-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-self .dish-card-name {
  color: #fff;
}

.dish-card-price {
  margin-top: 2px;
  font-size: 12px;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.msg-self .dish-card-price {
  color: #ffb27a;
}

.dish-card-add {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple);
}

.dish-card-add:hover {
  background: var(--brand-dark);
}

/* 已选咨询菜品 */
.attach-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 16px 8px;
  padding: 6px 10px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
}

.attach-img {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
}

.attach-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.attach-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-price {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.attach-close {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), color 0.15s var(--ease-apple);
}

.attach-close:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-1);
}

/* 输入区 */
.cs-chat-input {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px 14px;
  background: var(--surface);
  border-top: 1px solid var(--border);
}

.cs-chat-input :deep(.el-textarea) {
  flex: 1;
  min-width: 0;
}

.cs-chat-input :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--bg);
  box-shadow: 0 0 0 1px var(--border-strong) inset;
  font-size: 13.5px;
  line-height: 1.5;
  font-family: var(--font-sans);
  transition: background 0.16s var(--ease-apple), box-shadow 0.16s var(--ease-apple);
}

.cs-chat-input :deep(.el-textarea__inner:focus) {
  background: var(--surface);
  box-shadow: 0 0 0 1px var(--brand) inset, 0 0 0 3px rgba(255, 122, 26, 0.12);
}

.menu-btn {
  flex-shrink: 0;
  height: 36px;
  padding: 0 12px;
  font-size: 12.5px;
}

.send-btn.el-button.is-circle {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  background: var(--brand);
  border-color: var(--brand);
  box-shadow: none;
}

.send-btn.el-button.is-circle:hover {
  background: var(--brand-dark);
  border-color: var(--brand-dark);
}

.send-btn.el-button.is-circle:active {
  transform: scale(0.96);
}

.send-btn :deep(.el-icon) {
  font-size: 17px;
}

/* 聊天区空态 */
.cs-chat-empty {
  margin: auto;
  padding: 40px 24px;
}

.cs-chat-empty-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface);
  box-shadow: var(--shadow-xs);
  color: var(--text-3);
}

.cs-chat-empty .ap-empty-title {
  font-size: 14px;
}

/* 弹窗 */
.new-session-tip {
  margin-bottom: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}

.new-session-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.new-session-options :deep(.el-radio) {
  margin-right: 0;
  height: 40px;
  padding: 0 12px;
  border-radius: 10px;
}

.new-session-options :deep(.el-radio:hover) {
  background: var(--bg);
}

.menu-empty {
  padding: 36px 16px;
}

.menu-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.menu-item {
  padding: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.16s var(--ease-apple), box-shadow 0.16s var(--ease-apple);
}

.menu-item:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.menu-item.selected {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px rgba(255, 122, 26, 0.16);
}

.menu-item-img-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 10px;
  overflow: hidden;
  background: #f2f2f5;
}

.menu-item-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-item-img.fallback {
  background: #f2f2f5;
  color: var(--text-3);
  font-size: 24px;
  font-weight: 600;
}

.menu-item-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

.menu-item-name {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 550;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-item-price {
  margin-top: 2px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.dish-loading {
  padding: 30px 0;
  text-align: center;
  color: var(--text-3);
  font-size: 12px;
}

.menu-tip {
  margin-right: auto;
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 1180px) {
  .cs-side {
    width: 260px;
  }
}

@media (max-width: 960px) {
  .cs-page {
    padding: 16px;
  }

  .msg-stack {
    max-width: 76%;
  }

  .msg-stack--wide {
    max-width: 88%;
  }
}

</style>
