<template>
  <div class="cs-container">
    <!-- 左：按学生分组的会话列表。第一层只显示学生，点开才看到该学生的多个聊天窗口 -->
    <div class="cs-side">
      <div class="cs-side-head">
        <span class="cs-side-title">学生列表</span>
        <div class="cs-side-actions">
          <span class="cs-side-total">{{ studentGroups.length }} 位学生</span>
          <button class="cs-manage-btn" @click="toggleManage">{{ manageMode ? '完成' : '管理' }}</button>
        </div>
      </div>
      <el-scrollbar class="cs-session-list">
        <div v-if="!sessions.length && !loadingSessions" class="ap-empty cs-empty">
          <div class="ap-empty-mark"><el-icon :size="19"><ChatDotRound /></el-icon></div>
          <div class="ap-empty-title">暂无学生咨询</div>
          <div>学生的消息会出现在这里</div>
        </div>
        <div v-for="g in studentGroups" :key="g.userId" class="cs-group">
          <div
            class="cs-group-head"
            :class="{ active: expandedStudentId === g.userId }"
            @click="toggleGroup(g)"
          >
            <img class="cs-session-avatar" :src="g.studentAvatar" :alt="g.studentName" />
            <div class="cs-group-info">
              <div class="cs-group-name">{{ g.studentName || '学生' }}</div>
              <div class="cs-group-sub">{{ g.sessions.length }} 个会话 · {{ shortTime(g.lastTime) }}</div>
            </div>
            <el-badge v-if="g.unread" :value="g.unread" :max="99" class="cs-badge" />
            <el-button
              v-if="!manageMode"
              class="cs-group-del"
              link
              type="danger"
              size="small"
              :icon="Delete"
              title="删除与该学生的全部会话（只影响我这边）"
              @click.stop="removeStudentGroup(g)"
            />
            <el-icon class="cs-group-arrow" :class="{ open: expandedStudentId === g.userId }"><ArrowRight /></el-icon>
          </div>
          <div v-if="expandedStudentId === g.userId" class="cs-group-sessions">
            <div
              v-for="s in g.sessions"
              :key="s.id"
              class="cs-session"
              :class="{
                active: !manageMode && activeSessionId === s.id,
                highlight: !manageMode && highlightId === s.id,
                picked: manageMode && isSelected(s.id)
              }"
              @click="manageMode ? toggleSelect(s.id) : selectSession(s)"
            >
              <div class="cs-session-top">
                <span v-if="manageMode" class="cs-sel-box" :class="{ on: isSelected(s.id) }">✓</span>
                <span class="cs-session-name">{{ s.status === 'CLOSED' ? '已结束的会话' : '进行中的会话' }}</span>
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
                <el-badge v-if="s.merchantUnread" :value="s.merchantUnread" :max="99" class="cs-badge" />
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
        <div class="cs-chat-head">
          <div class="cs-chat-title-wrap">
            <img class="cs-chat-head-avatar" :src="activeSession.studentAvatar" :alt="activeSession.studentName" />
            <div>
              <div class="cs-chat-title">{{ activeSession.studentName || '学生' }}</div>
              <div class="cs-chat-sub">与学生在线沟通</div>
            </div>
          </div>
          <div class="cs-chat-tools">
            <div class="cs-assistant">
              <img class="cs-asst-avatar" src="/ui/assistant/happy.png" alt="小助手" />
              <span class="ai-toggle-label">智能代回</span>
              <el-switch v-model="config.aiEnabled" :active-value="1" :inactive-value="0" @change="saveConfig" />
            </div>
            <button class="ap-btn ap-btn--light ap-btn--sm" @click="quickVisible = true">快捷回复</button>
            <button class="ap-btn ap-btn--light ap-btn--sm" @click="openConfig">客服设置</button>
          </div>
        </div>

        <div class="cs-chat-body" ref="chatBodyRef">
          <div v-for="(m, i) in messages" :key="m.id" class="msg-row" :class="msgClass(m)">
            <img v-if="!isSelfMsg(m)" class="msg-avatar" :src="activeSession.studentAvatar" alt="学生" />
            <div class="msg-bubble">
              <div
                v-if="i === 0 || isSelfMsg(messages[i - 1]) !== isSelfMsg(m) || messages[i - 1].senderType !== m.senderType"
                class="msg-sender"
              >
                <template v-if="isSelfMsg(m)">
                  {{ activeSession.stallName || '我' }}
                  <span v-if="m.senderType === 'AI'" class="ai-tag">AI</span>
                </template>
                <template v-else>{{ activeSession.studentName || '学生' }}</template>
              </div>
              <div v-if="m.dishId" class="dish-card" @click="goDish(m.dishId)">
                <img v-if="m.dishImage" class="dish-card-img" :src="m.dishImage" :alt="m.dishName" />
                <div class="dish-card-info">
                  <div class="dish-card-name">{{ m.dishName }}</div>
                  <div class="dish-card-price">¥{{ m.dishPrice ?? '—' }}</div>
                </div>
              </div>
              <div class="msg-content">{{ m.content }}</div>
              <ChatCouponCard v-if="m.couponOfferId" :message="m" />
              <div class="msg-time">{{ shortTime(m.createTime) }}</div>
            </div>
            <img v-if="isSelfMsg(m)" class="msg-avatar" :src="activeSession.stallAvatar" alt="档口" />
          </div>
          <div v-if="loadingMessages" class="msg-loading">加载中…</div>
        </div>

        <!-- 快捷回复 -->
        <div class="quick-reply-bar" v-if="quickReplies.length">
          <span
            v-for="q in quickReplies"
            :key="q.id"
            class="quick-reply-chip"
            @click="insertQuickReply(q)"
          >{{ q.content }}</span>
        </div>

        <!-- 已选附带菜品 -->
        <div v-if="selectedDish" class="attach-bar">
          <img class="attach-img" :src="selectedDish.image" :alt="selectedDish.name" />
          <div class="attach-info">
            <span class="attach-name">{{ selectedDish.name }}</span>
            <span class="attach-price">¥{{ selectedDish.price ?? '—' }}</span>
          </div>
          <el-icon class="attach-close" @click="selectedDish = null"><Close /></el-icon>
        </div>

        <div class="cs-chat-input">
          <div class="composer-tools">
            <button type="button" class="composer-icon" title="附菜单" @click="openDishMenu">
              <el-icon :size="16"><Menu /></el-icon>
            </button>
            <button
              v-if="config.manualGrantEnabled === 1"
              type="button"
              class="composer-icon"
              title="附优惠券"
              @click="openCoupon"
            >
              <el-icon :size="16"><Ticket /></el-icon>
            </button>
            <span class="composer-spacer"></span>
            <button type="button" class="composer-ai" :disabled="suggesting" @click="suggestAi">
              <span v-if="suggesting" class="composer-spin dark"></span>
              <el-icon v-else :size="14"><MagicStick /></el-icon>
              AI 帮我想
            </button>
          </div>
          <div class="composer-main">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="1000"
              placeholder="回复学生咨询…"
              @keydown.enter.exact.prevent="send"
            />
            <button type="button" class="send-circle" :disabled="sending" title="发送" @click="send">
              <span v-if="sending" class="composer-spin"></span>
              <el-icon v-else :size="18"><Position /></el-icon>
            </button>
          </div>
        </div>
      </template>
      <div v-else class="ap-empty cs-chat-empty">
        <div class="ap-empty-mark"><el-icon :size="19"><ChatDotRound /></el-icon></div>
        <div class="ap-empty-title">选择一个会话</div>
        <div>从左侧学生列表开始回复</div>
      </div>
    </div>

    <!-- 客服设置弹窗 -->
    <el-dialog v-model="configVisible" title="智能客服设置" width="600px">
      <el-scrollbar max-height="62vh" class="config-scroll">
        <el-form label-width="96px">
          <div class="dialog-section-title">AI 代回</div>
          <el-form-item label="智能代回">
            <el-switch v-model="config.aiEnabled" :active-value="1" :inactive-value="0" />
            <div class="form-tip">开启后，学生发消息时小助手会自动像真人一样代你回复</div>
          </el-form-item>
          <el-form-item label="评价AI托管">
            <el-switch v-model="config.reviewAiEnabled" :active-value="1" :inactive-value="0" />
            <div class="form-tip">开启后，系统会自动智能回复待处理的学生评价（可随时手动修改）</div>
          </el-form-item>
          <el-form-item label="人设话术">
            <el-input v-model="config.persona" type="textarea" :rows="2" maxlength="500" placeholder="例如：本店主打川味小炒，出餐快、分量足，欢迎同学们常来～" />
          </el-form-item>
          <el-form-item label="自动欢迎语">
            <el-input v-model="config.greeting" type="textarea" :rows="2" maxlength="500" placeholder="学生第一次进会话时自动发送的欢迎语" />
          </el-form-item>

          <div class="ap-divider dialog-divider"></div>
          <div class="dialog-section-title">优惠券发放规则</div>
          <el-form-item label="AI 可发券">
            <el-switch v-model="config.discountEnabled" :active-value="1" :inactive-value="0" />
            <div class="form-tip">学生是熟客且情绪不好时，AI 可发一张真实安抚券</div>
          </el-form-item>
          <el-form-item label="手动发券">
            <el-switch v-model="config.manualGrantEnabled" :active-value="1" :inactive-value="0" />
            <div class="form-tip">开启后，聊天输入区会出现附优惠券按钮，可随时手动发</div>
          </el-form-item>
          <el-form-item label="折扣区间">
            <el-input-number v-model="config.minRate" :min="0.1" :max="1" :step="0.05" :precision="2" controls-position="right" />
            <span class="rate-sep">~</span>
            <el-input-number v-model="config.maxRate" :min="0.1" :max="1" :step="0.05" :precision="2" controls-position="right" />
            <div class="form-tip">0.90 = 九折；AI 与手动发券都会收敛到这个区间</div>
          </el-form-item>
          <el-form-item label="允许免单">
            <el-switch v-model="config.allowFree" :active-value="1" :inactive-value="0" />
            <div class="form-tip">关闭后，AI 与手动发券都不能发 0 折免单券</div>
          </el-form-item>
          <el-form-item label="发券冷却">
            <el-input-number v-model="config.cooldownDays" :min="0" :max="365" controls-position="right" />
            <span class="rate-sep">天</span>
            <div class="form-tip">同一学生在你这最多这么久拿一次安抚券（0 = 不限制）</div>
          </el-form-item>
          <el-form-item label="老客门槛">
            <el-input-number v-model="config.veteranMinOrders" :min="1" :max="999" controls-position="right" />
            <span class="rate-sep">单</span>
            <div class="form-tip">本档口完成满这么多单才算老客，AI 才会给安抚券</div>
          </el-form-item>
        </el-form>
      </el-scrollbar>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 快捷回复管理弹窗 -->
    <el-dialog v-model="quickVisible" title="快捷回复管理" width="560px">
      <div class="quick-add">
        <el-input v-model="quickInput" maxlength="500" placeholder="新增快捷回复，例如：好的，稍等给您出餐～" @keydown.enter.prevent="addQuickReply" />
        <el-button type="primary" @click="addQuickReply">添加</el-button>
      </div>
      <el-scrollbar max-height="320px">
        <div v-for="q in quickReplies" :key="q.id" class="quick-item">
          <span class="quick-item-text">{{ q.content }}</span>
          <el-button link type="danger" size="small" @click="deleteQuickReply(q)">删除</el-button>
        </div>
        <div v-if="!quickReplies.length" class="ap-empty quick-empty">
          <div class="ap-empty-mark"><el-icon :size="18"><ChatLineRound /></el-icon></div>
          <div class="ap-empty-title">还没有快捷回复</div>
          <div>把常用话术存起来，回复更快</div>
        </div>
      </el-scrollbar>
    </el-dialog>

    <!-- 附菜单弹窗 -->
    <el-dialog v-model="dishMenuVisible" title="选择要附上的菜品" width="560px">
      <el-scrollbar max-height="420px">
        <div v-if="dishLoading" class="dish-loading">加载中…</div>
        <div v-else-if="!menuDishes.length" class="ap-empty">
          <div class="ap-empty-mark"><el-icon :size="18"><Goods /></el-icon></div>
          <div class="ap-empty-title">该档口暂无菜品</div>
          <div>上架菜品后就可以附给学生了</div>
        </div>
        <div v-else class="menu-grid">
          <div
            v-for="d in menuDishes"
            :key="d.id"
            class="menu-item"
            :class="{ selected: selectedDish?.id === d.id }"
            @click="chooseDish(d)"
          >
            <img v-if="d.image" class="menu-item-img" :src="d.image" :alt="d.name" />
            <span v-else class="menu-item-img fallback">{{ (d.name || '菜').slice(0, 1) }}</span>
            <div class="menu-item-name">{{ d.name }}</div>
            <div class="menu-item-price">¥{{ d.price }}</div>
          </div>
        </div>
      </el-scrollbar>
      <template #footer>
        <el-button @click="dishMenuVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedDish" @click="confirmDish">附上这道菜</el-button>
      </template>
    </el-dialog>

    <!-- 手动发优惠券弹窗 -->
    <el-dialog v-model="couponVisible" title="给学生发优惠券" width="520px">
      <div class="coupon-tip">
        券会以卡片形式发到会话里，学生点「领取」后才真正到账。
      </div>

      <div class="coupon-field">
        <span class="coupon-field-label">适用范围</span>
        <div class="ap-pill-group">
          <button class="ap-pill" :class="{ active: couponScope === 'ORDER' }" @click="couponScope = 'ORDER'">整单</button>
          <button class="ap-pill" :class="{ active: couponScope === 'ITEM' }" @click="couponScope = 'ITEM'">单份菜</button>
        </div>
      </div>
      <div v-if="couponScope === 'ITEM'" class="coupon-field">
        <span class="coupon-field-label">限定菜品</span>
        <el-select v-model="couponDishId" clearable placeholder="不限（任意一道菜）" style="width: 100%">
          <el-option v-for="d in couponDishOptions" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <div class="coupon-field-tip">单份菜券只抵扣一道菜的一份，不与其它菜叠加</div>
      </div>

      <div class="coupon-field">
        <span class="coupon-field-label">折扣档位</span>
        <div class="ap-pill-group coupon-pills">
          <button
            v-for="opt in couponOptions"
            :key="opt.value"
            type="button"
            class="ap-pill"
            :class="{ active: couponRate === opt.value }"
            @click="couponRate = opt.value"
          >{{ opt.label }}</button>
        </div>
      </div>

      <template #footer>
        <el-button @click="couponVisible = false">取消</el-button>
        <el-button type="primary" :loading="couponSending" :disabled="couponRate === null" @click="confirmGrantCoupon">
          发送券卡片
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Delete, ArrowRight } from '@element-plus/icons-vue'
import {
  listSessions, getMessages, sendMessage, suggestAiReply,
  getCsConfig, updateCsConfig, listQuickReplies,
  addQuickReply as apiAddQuickReply, deleteQuickReply as apiDeleteQuickReply,
  streamMessages, deleteSession, readSessionMessages, grantCouponToSession
} from '@/api/customerService'
import { listAllDishes } from '@/api/dish'
import ChatCouponCard from '@/components/coupon/ChatCouponCard.vue'

const router = useRouter()
const route = useRoute()

const sessions = ref([])
const activeSessionId = ref(null)
const activeSession = ref(null)
const messages = ref([])
const inputText = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const suggesting = ref(false)
const chatBodyRef = ref(null)
const highlightId = ref(null)

const DEFAULT_CONFIG = {
  aiEnabled: 0,
  reviewAiEnabled: 0,
  persona: '',
  greeting: '',
  discountEnabled: 1,
  manualGrantEnabled: 1,
  minRate: 0.5,
  maxRate: 0.95,
  allowFree: 1,
  cooldownDays: 30,
  veteranMinOrders: 10
}
const config = ref({ ...DEFAULT_CONFIG })
const configVisible = ref(false)
const quickReplies = ref([])
const quickVisible = ref(false)
const quickInput = ref('')

/** 当前展开的学生分组 */
const expandedStudentId = ref(null)
/** 批量管理（多选删除） */
const manageMode = ref(false)
const selectedIds = ref([])
const allSelected = computed(() =>
  sessions.value.length > 0 && selectedIds.value.length === sessions.value.length
)

/** 手动发券弹窗 */
const couponVisible = ref(false)
const couponRate = ref(null)
const couponSending = ref(false)
const couponScope = ref('ORDER')
const couponDishId = ref(null)

/** 按学生把会话归组：第一层只显示学生账号，点进去再看该学生的多个聊天窗口 */
const studentGroups = computed(() => {
  const map = new Map()
  for (const s of sessions.value) {
    const key = s.userId ?? 'unknown'
    let g = map.get(key)
    if (!g) {
      g = {
        userId: s.userId,
        studentName: s.studentName,
        studentAvatar: s.studentAvatar,
        unread: 0,
        lastTime: null,
        sessions: []
      }
      map.set(key, g)
    }
    g.sessions.push(s)
    g.unread += s.merchantUnread || 0
    if (!g.lastTime || String(s.lastMessageTime || '') > String(g.lastTime || '')) {
      g.lastTime = s.lastMessageTime
    }
  }
  return [...map.values()].sort((a, b) => String(b.lastTime || '').localeCompare(String(a.lastTime || '')))
})

/** 发券可选菜品：只列在售菜，且每次打开弹窗都会重拉，保证新上架菜品自动出现 */
const couponDishOptions = computed(() => menuDishes.value.filter(d => d.status === 1))

/** 手动发券可选档位：落在商家配置的折扣区间内，允许免单时多一个「免单」 */
const couponOptions = computed(() => {
  const min = Number(config.value.minRate ?? 0.5)
  const max = Number(config.value.maxRate ?? 0.95)
  const opts = [0.5, 0.6, 0.7, 0.8, 0.85, 0.9, 0.95]
    .filter(r => r >= min && r <= max)
    .map(r => ({ value: r, label: `${Math.round(r * 100) / 10} 折` }))
  if (config.value.allowFree === 1) {
    opts.unshift({ value: 0, label: '免单' })
  }
  return opts
})

const dishMenuVisible = ref(false)
const dishLoading = ref(false)
const menuDishes = ref([])
const selectedDish = ref(null)

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

function isSelfMsg(m) {
  return m.self || m.senderType === 'AI'
}

function msgClass(m) {
  return isSelfMsg(m) ? 'msg-self' : 'msg-other'
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    const res = await listSessions()
    sessions.value = res.data || []
    // 待办跳转定位：?highlight=会话id，自动选中并高亮该会话
    const hid = route.query.highlight ? Number(route.query.highlight) : null
    if (hid) {
      highlightId.value = hid
      const target = sessions.value.find(s => s.id === hid)
      if (target && activeSessionId.value !== hid) {
        selectSession(target)
      }
    }
  } catch {
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

function selectSession(s) {
  activeSessionId.value = s.id
  activeSession.value = s
  expandedStudentId.value = s.userId
  selectedDish.value = null
  // 进入会话即视为已读：先清本地红点，等后端 getMessages 清零后再通知铃铛（避免竞态）
  markSessionReadLocal(s)
  loadMessages(s.id).then(() => window.dispatchEvent(new CustomEvent('cs:unread-changed')))
  startStream(s.id)
}

function markSessionReadLocal(s) {
  if (!s) return
  s.merchantUnread = 0
}

/** 展开/收起某个学生；展开时自动进入他最近的会话 */
function toggleGroup(g) {
  if (expandedStudentId.value === g.userId) {
    expandedStudentId.value = null
    return
  }
  expandedStudentId.value = g.userId
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

/** 批量删除：逐条隐藏我这边 */
async function batchDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      `删除选中的 ${ids.length} 个会话？只会在你的列表里隐藏，学生仍保留记录。`,
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

/** 删除一个会话：只隐藏我这边，学生仍保留记录 */
async function removeSession(s) {
  try {
    await ElMessageBox.confirm(
      '删除后这个会话只在你的列表里隐藏（学生仍保留记录），确定删除吗？',
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
    window.dispatchEvent(new CustomEvent('cs:unread-changed'))
    ElMessage.success('已删除该会话')
    loadSessions()
  } catch {
    // 请求层已提示
  }
}

/** 删除与某个学生的全部会话（逐条隐藏） */
async function removeStudentGroup(g) {
  try {
    await ElMessageBox.confirm(
      `将删除与「${g.studentName || '该学生'}」的 ${g.sessions.length} 个会话（只在你的列表隐藏，学生仍保留记录），确定吗？`,
      '删除全部会话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    for (const s of g.sessions) {
      await deleteSession(s.id)
    }
    if (activeSession.value && activeSession.value.userId === g.userId) {
      closeStream()
      activeSessionId.value = null
      activeSession.value = null
      messages.value = []
    }
    sessions.value = sessions.value.filter(x => x.userId !== g.userId)
    window.dispatchEvent(new CustomEvent('cs:unread-changed'))
    ElMessage.success('已删除该学生的全部会话')
    loadSessions()
  } catch {
    // 请求层已提示
  }
}

/** 打开手动发券弹窗 */
async function openCoupon() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  // 默认选非免单的第一个档位，避免误发免单券
  const firstPaid = couponOptions.value.find(o => o.value > 0)
  couponRate.value = (firstPaid || couponOptions.value[0])?.value ?? null
  couponScope.value = 'ORDER'
  couponDishId.value = null
  couponVisible.value = true
  // 每次打开都重新拉菜单：后续新上架的菜品能立刻出现在「限定菜品」里
  try {
    menuDishes.value = await listAllDishes(activeSession.value.stallId)
  } catch {
    menuDishes.value = []
  }
}

/** 确认发券：后端生成可领取券卡片，学生点领取后才到账 */
async function confirmGrantCoupon() {
  if (!activeSessionId.value || couponRate.value === null) return
  couponSending.value = true
  try {
    const res = await grantCouponToSession(
      activeSessionId.value, couponRate.value, couponScope.value,
      couponScope.value === 'ITEM' ? couponDishId.value : null
    )
    ElMessage.success('券卡片已发送，等待学生领取')
    couponVisible.value = false
    if (!sseAlive) await loadMessages(activeSessionId.value)
    loadSessions()
  } catch {
    // 请求层已提示
  } finally {
    couponSending.value = false
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
      m.self = m.senderType !== 'STUDENT'
      upsertMessage(m)
      if (m.senderType === 'STUDENT') {
        // 正开着这个会话：立刻清掉服务端未读，清完再通知铃铛，避免「人在窗口里红点还在」
        readSessionMessages(sessionId)
          .then(() => {
            markSessionReadLocal(sessions.value.find(x => x.id === sessionId))
            window.dispatchEvent(new CustomEvent('cs:unread-changed'))
          })
          .catch(() => {})
        scheduleSessionsRefresh()
      }
      scrollToBottom()
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

async function send() {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  sending.value = true
  try {
    const res = await sendMessage(activeSessionId.value, text, selectedDish.value?.id || null)
    if (!sseAlive) {
      const list = res.data || []
      for (const m of list) {
        m.self = m.senderType !== 'STUDENT'
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

async function suggestAi() {
  if (!activeSessionId.value) return
  suggesting.value = true
  try {
    const res = await suggestAiReply(activeSessionId.value)
    inputText.value = res.data?.reply || ''
  } catch {
    // 请求层已统一提示
  } finally {
    suggesting.value = false
  }
}

async function loadConfig() {
  try {
    const res = await getCsConfig()
    config.value = { ...DEFAULT_CONFIG, ...(res.data || {}) }
  } catch {
    // 忽略
  }
}

async function saveConfig() {
  try {
    const res = await updateCsConfig({
      aiEnabled: config.value.aiEnabled,
      reviewAiEnabled: config.value.reviewAiEnabled,
      persona: config.value.persona,
      greeting: config.value.greeting,
      discountEnabled: config.value.discountEnabled,
      manualGrantEnabled: config.value.manualGrantEnabled,
      minRate: config.value.minRate,
      maxRate: config.value.maxRate,
      allowFree: config.value.allowFree,
      cooldownDays: config.value.cooldownDays,
      veteranMinOrders: config.value.veteranMinOrders
    })
    config.value = { ...config.value, ...res.data }
    ElMessage.success('已保存')
    configVisible.value = false
  } catch {
    // 请求层已统一提示
  }
}

function openConfig() {
  configVisible.value = true
}

async function loadQuickReplies() {
  try {
    const res = await listQuickReplies()
    quickReplies.value = res.data || []
  } catch {
    quickReplies.value = []
  }
}

async function addQuickReply() {
  const text = quickInput.value.trim()
  if (!text) return
  try {
    await apiAddQuickReply({ content: text })
    quickInput.value = ''
    loadQuickReplies()
  } catch {
    // 请求层已统一提示
  }
}

async function deleteQuickReply(q) {
  try {
    await apiDeleteQuickReply(q.id)
    loadQuickReplies()
  } catch {
    // 请求层已统一提示
  }
}

function insertQuickReply(q) {
  inputText.value = q.content
}

async function openDishMenu() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  dishMenuVisible.value = true
  dishLoading.value = true
  try {
    // 商家可见本档口全部菜品（含下架），用于向学生说明；自动翻页拉全量
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
  dishMenuVisible.value = false
  ElMessage.success('已附上菜品，可输入说明后发送')
}

function goDish(dishId) {
  if (dishId) router.push(`/dishes/${dishId}`)
}

function startPolling() {
  stopPolling()
  // 兜底：SSE 断开时才每 20 秒补拉一次，正常情况下完全靠推送
  pollTimer = setInterval(() => {
    if (!sseAlive && activeSessionId.value) {
      getMessages(activeSessionId.value).then(res => {
        const list = res.data || []
        for (const m of list) {
          m.self = m.senderType !== 'STUDENT'
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

onMounted(() => {
  loadSessions()
  loadConfig()
  loadQuickReplies()
})
onActivated(() => {
  active = true
  loadSessions()
  loadConfig()
  loadQuickReplies()
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
.cs-container {
  display: flex;
  gap: 16px;
  height: calc(100vh - 64px);
  padding: 24px 30px 30px;
  background: var(--bg);
  box-sizing: border-box;
  overflow: hidden;
}

.cs-side {
  width: 292px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.cs-side-head {
  padding: 16px 18px 12px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cs-side-title {
  font-size: 14px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.cs-side-total { font-size: 11.5px; font-weight: 500; color: var(--text-3); }

.cs-side-actions { display: flex; align-items: center; gap: 10px; }

.cs-manage-btn {
  border: none;
  background: none;
  padding: 0;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--text-2);
  cursor: pointer;
  transition: color 0.16s var(--ease-apple);
}
.cs-manage-btn:hover { color: var(--text-1); }

.cs-sel-box {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid #d2d2d7;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: transparent;
  flex-shrink: 0;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.cs-sel-box.on {
  background: var(--text-1);
  border-color: var(--text-1);
  color: #fff;
}

.cs-session.picked { background: #f0f0f2; }

.cs-batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
  background: #fff;
}

.cs-batch-all {
  border: none;
  background: none;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--text-2);
  cursor: pointer;
}

.cs-batch-del {
  margin-left: auto;
  border: 1px solid rgba(201, 52, 43, 0.16);
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: #fff;
  color: #c9342b;
  font-size: 12.5px;
  font-weight: 550;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.cs-batch-del:hover:not(:disabled) { background: #fff5f4; }

.cs-batch-del:disabled { opacity: 0.35; cursor: not-allowed; }

.cs-session-list { flex: 1; min-height: 0; }

.cs-empty { padding: 44px 20px; }

/* 第一层：学生分组 */
.cs-group { border-bottom: 1px solid rgba(0, 0, 0, 0.04); }
.cs-group:last-child { border-bottom: none; }

.cs-group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 16px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.cs-group-head:hover { background: #f5f5f7; }
.cs-group-head.active { background: #f0f0f2; }

.cs-group-info { flex: 1; min-width: 0; }

.cs-group-name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-group-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-3);
}

.cs-group-arrow {
  font-size: 12px;
  color: var(--text-3);
  transition: transform 0.2s var(--ease-apple);
}

.cs-group-arrow.open { transform: rotate(90deg); }

.cs-group-del {
  margin-left: 2px;
  color: var(--text-3) !important;
  opacity: 0.55;
  transition: opacity 0.15s ease;
}
.cs-group-head:hover .cs-group-del { opacity: 1; }
.cs-group-del:hover { color: #ff3b30 !important; }

/* 第二层：该学生下的会话窗口 */
.cs-group-sessions {
  background: #fafafa;
  border-top: 1px solid var(--border);
}

.cs-session-del {
  margin-left: 2px;
  color: var(--text-3) !important;
  opacity: 0.55;
  transition: opacity 0.15s ease;
}
.cs-session:hover .cs-session-del { opacity: 1; }
.cs-session-del:hover { color: #ff3b30 !important; }

.coupon-tip {
  margin-bottom: 14px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-2);
}

.coupon-field { margin-bottom: 16px; }

.coupon-field-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-1);
}

.coupon-field-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--text-3);
}

.coupon-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.rate-sep {
  margin: 0 6px;
  color: var(--text-3);
  font-size: 13px;
}

.cs-session {
  padding: 10px 16px 10px 20px;
  cursor: pointer;
  border-bottom: 1px solid rgba(0, 0, 0, 0.035);
  transition: background-color 0.15s ease;
}
.cs-session:last-child { border-bottom: none; }

.cs-session:hover { background: #f5f5f7; }
.cs-session.active { background: #f0f0f2; box-shadow: inset 2px 0 0 var(--brand); }
.cs-session.highlight { animation: cs-flash 2.4s ease; }
@keyframes cs-flash {
  0% { background: #fff4e0; }
  60% { background: #f5f5f7; }
  100% { background: transparent; }
}

.cs-session-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cs-session-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: #ececee;
}

.cs-session-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.cs-session-time { font-size: 11px; color: var(--text-3); flex-shrink: 0; }

.cs-session-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.cs-session-last {
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.cs-badge { flex-shrink: 0; }
.cs-badge :deep(.el-badge__content) {
  background: var(--brand);
  border: none;
  height: 16px;
  min-width: 16px;
  padding: 0 5px;
  font-size: 10px;
  font-weight: 650;
  line-height: 16px;
}

.cs-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.cs-chat-head {
  min-height: 64px;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.cs-chat-title-wrap {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.cs-chat-head-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: #ececee;
}

.cs-chat-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}
.cs-chat-sub { font-size: 12px; color: var(--text-3); margin-top: 2px; }

.cs-chat-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ai-toggle-label { font-size: 12.5px; color: var(--text-2); white-space: nowrap; }

/* 小助手代回开关：头像 + 标签 + 开关 */
.cs-assistant {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 11px 4px 5px;
  background: #f5f5f7;
  border-radius: 999px;
}
.cs-asst-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.cs-assistant :deep(.el-switch) { margin-left: 2px; }

.cs-chat-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fafafa;
}

.msg-row { display: flex; align-items: flex-start; gap: 9px; }
.msg-self { justify-content: flex-end; }
.msg-other { justify-content: flex-start; }

.msg-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: #ececee;
}

.msg-bubble {
  max-width: 64%;
  padding: 9px 13px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.6;
}

.msg-self .msg-bubble {
  background: #f0f0f2;
  border: none;
  color: var(--text-1);
  border-bottom-right-radius: 5px;
}

.msg-other .msg-bubble {
  background: #fff;
  border: 1px solid var(--border);
  color: var(--text-1);
  border-bottom-left-radius: 5px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.msg-sender {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 3px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-tag {
  font-size: 9.5px;
  font-weight: 600;
  color: var(--brand-dark);
  background: var(--brand-soft);
  padding: 1px 6px;
  border-radius: 999px;
}

.dish-card {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 6px 8px;
  margin-bottom: 8px;
  cursor: pointer;
  max-width: 220px;
}

.dish-card-img {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.dish-card-name { font-size: 12.5px; color: var(--text-1); font-weight: 600; }
.dish-card-price { font-size: 11.5px; color: var(--brand-dark); margin-top: 2px; }

.msg-content { white-space: pre-wrap; word-break: break-word; }

.msg-time {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 4px;
  text-align: right;
}

.msg-loading { text-align: center; color: var(--text-3); font-size: 12px; padding: 8px; }

.quick-reply-bar {
  display: flex;
  gap: 8px;
  padding: 10px 20px;
  background: #fff;
  border-top: 1px solid var(--border);
  flex-wrap: wrap;
  max-height: 84px;
  overflow-y: auto;
}

.quick-reply-chip {
  font-size: 12px;
  color: var(--text-2);
  background: #f5f5f7;
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.quick-reply-chip:hover { background: #ececee; color: var(--text-1); }

.attach-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 20px 8px;
  padding: 7px 10px;
  background: #f5f5f7;
  border-radius: 12px;
}

.attach-img { width: 30px; height: 30px; border-radius: 8px; object-fit: cover; }
.attach-info { flex: 1; min-width: 0; }
.attach-name { font-size: 12.5px; color: var(--text-1); font-weight: 600; }
.attach-price { font-size: 12px; color: var(--brand-dark); margin-left: 6px; }
.attach-close { cursor: pointer; color: var(--text-3); }
.attach-close:hover { color: var(--text-1); }

.cs-chat-input {
  padding: 10px 16px 14px;
  background: #fff;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.composer-spacer { flex: 1; }

.composer-icon {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.composer-icon:hover { background: #f5f5f7; color: var(--text-1); }

.composer-ai {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 11px;
  border: none;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.composer-ai:hover:not(:disabled) { background: #ececee; color: var(--text-1); }
.composer-ai:disabled { opacity: 0.6; cursor: not-allowed; }

.composer-main {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.composer-main .el-textarea { flex: 1; }

.cs-chat-input :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
  padding: 10px 14px;
  border-radius: 14px;
  background: #f5f5f7;
  box-shadow: none !important;
}
.cs-chat-input :deep(.el-textarea__inner:hover) { background: #f0f0f2; }
.cs-chat-input :deep(.el-textarea__inner:focus) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--brand) inset, 0 0 0 4px rgba(255, 122, 26, 0.1) !important;
}

.send-circle {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: var(--text-1);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background-color 0.15s ease, transform 0.12s var(--ease-apple);
}
.send-circle:hover:not(:disabled) { background: #333336; }
.send-circle:active:not(:disabled) { transform: scale(0.96); }
.send-circle:disabled { opacity: 0.4; cursor: not-allowed; }

.composer-spin {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: composer-spin 0.8s linear infinite;
}
.composer-spin.dark {
  border-color: rgba(0, 0, 0, 0.15);
  border-top-color: var(--text-2);
  animation: composer-spin 0.8s linear infinite;
}
@keyframes composer-spin { to { transform: rotate(360deg); } }

.cs-chat-empty { margin: auto; }

.form-tip {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 6px;
  width: 100%;
  line-height: 1.5;
}

.config-scroll :deep(.el-scrollbar__view) { padding-right: 4px; }

.dialog-section-title {
  font-size: 14px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
  margin: 2px 0 14px;
}

.dialog-divider { margin: 2px 0 18px; }

.quick-add {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.quick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 11px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}
.quick-item:last-child { border-bottom: none; }

.quick-item-text {
  font-size: 13px;
  color: var(--text-1);
  flex: 1;
  min-width: 0;
}

.quick-empty { padding: 32px 16px; }

.menu-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.menu-item {
  border: 1px solid transparent;
  background: #fafafa;
  border-radius: 14px;
  padding: 8px;
  cursor: pointer;
  text-align: center;
  transition: background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.menu-item:hover { background: #f5f5f7; }
.menu-item.selected {
  background: #fff;
  border-color: var(--text-1);
  box-shadow: 0 0 0 1px var(--text-1);
}

.menu-item-img {
  width: 100%;
  height: 72px;
  border-radius: 10px;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-item-img.fallback {
  background: #f0f0f2;
  color: var(--text-3);
  font-size: 22px;
  font-weight: 650;
}

.menu-item-name {
  font-size: 12.5px;
  color: var(--text-1);
  margin-top: 7px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-item-price { font-size: 11.5px; color: var(--brand-dark); margin-top: 2px; }
.dish-loading { text-align: center; color: var(--text-3); padding: 30px 0; font-size: 12.5px; }

@media (max-width: 1100px) {
  .cs-side { width: 260px; }
  .cs-container { padding: 18px 20px 22px; }
}
</style>
