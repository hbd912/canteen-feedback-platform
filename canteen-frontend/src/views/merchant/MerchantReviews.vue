<template>
  <div class="page-container adaptive-page review-page">
    <!-- 顶部：页头 + AI 托管开关 + 状态分段器 -->
    <div class="ap-head review-head">
      <div>
        <h1 class="ap-title">学生评价</h1>
        <div class="ap-sub">
          共 {{ total }} 条<template v-if="pendingCount"> · 待回复 {{ pendingCount }} 条</template>
        </div>
      </div>
      <div class="ap-actions">
        <div class="ai-toggle">
          <span class="ai-toggle-label">AI 评价托管</span>
          <el-switch
            v-model="reviewAiEnabled"
            :active-value="1"
            :inactive-value="0"
            @change="saveAiToggle"
          />
        </div>
      </div>
    </div>

    <el-card shadow="never" class="page-card tab-card">
      <div class="filter-bar review-filter">
        <div class="ap-pill-group status-pills">
          <button
            v-for="t in statusTabs"
            :key="t.key"
            type="button"
            class="ap-pill"
            :class="{ active: activeTab === t.key }"
            @click="switchTab(t)"
          >{{ t.label }}</button>
        </div>
        <span class="filter-spacer"></span>
        <el-select v-model="query.sort" placeholder="排序方式" style="width: 150px" @change="handleFilterChange">
          <el-option label="最新优先" value="time_desc" />
          <el-option label="好评优先" value="rating_desc" />
          <el-option label="差评优先" value="rating_asc" />
          <el-option label="最高点赞" value="like_desc" />
          <el-option label="最高浏览" value="view_desc" />
        </el-select>
        <el-select v-model="query.handled" placeholder="全部状态" clearable style="width: 140px" @change="handleFilterChange">
          <el-option label="待处理" :value="0" />
          <el-option label="已处理" :value="1" />
        </el-select>
      </div>
    </el-card>

    <div class="adaptive-table">
    <div v-if="!records.length && !loading" class="ap-empty review-empty">
      <div class="ap-empty-mark"><el-icon :size="19"><ChatLineSquare /></el-icon></div>
      <div class="ap-empty-title">{{ emptyText }}</div>
      <div>换个筛选条件或稍后再来看看</div>
    </div>

    <template v-else>
      <!-- 精选：带菜品图的评价，横向滚动紧凑卡 -->
      <div v-if="featuredReviews.length" class="featured-section">
        <div class="ap-section-head">
          <span class="ap-section-title">精选评价</span>
          <span class="ap-section-hint">评分较高的带图评价</span>
        </div>
        <div class="featured-scroll">
          <div v-for="r in featuredReviews" :key="'f' + r.id" class="featured-card" :class="{ 'todo-highlight': highlightId === r.id }" @click="openReply(r)">
            <div class="featured-cover" :style="r.dishImage ? '' : { background: '#f5f5f7' }">
              <img v-if="r.dishImage" :src="r.dishImage" :alt="r.dishName" loading="lazy">
              <span v-else class="featured-initial">{{ (r.dishName || '菜').slice(0, 1) }}</span>
              <span class="featured-score">{{ r.rating }}<span class="score-star">★</span></span>
            </div>
            <div class="featured-body">
              <div class="featured-dish">{{ r.dishName }}</div>
              <div class="featured-content">{{ r.content || '（未填写文字评价）' }}</div>
              <div class="featured-foot">
                <div class="featured-user">
                  <img v-if="r.avatar" :src="r.avatar" class="user-avatar">
                  <span v-else class="user-avatar fallback">{{ (r.userName || '学').slice(0, 1) }}</span>
                  <span class="user-name">{{ r.userName || '学生' }}</span>
                </div>
                <span class="featured-state" :class="r.merchantReply ? 'done' : 'todo'">
                  {{ r.merchantReply ? (r.merchantReplySource === 'AI' ? 'AI 已回复' : '已回复') : '待处理' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 下方：纯文字评论列表 -->
      <div class="list-section">
        <div class="ap-section-head">
          <span class="ap-section-title">其他评论</span>
        </div>
        <div class="ap-card list-card">
          <div v-for="r in listReviews" :key="r.id" class="review-item" :class="{ 'todo-highlight': highlightId === r.id }">
            <div class="review-main">
              <img v-if="r.avatar" :src="r.avatar" class="review-avatar">
              <span v-else class="review-avatar fallback">{{ (r.userName || '学').slice(0, 1) }}</span>
              <div class="review-body">
                <div class="review-topline">
                  <span class="review-name">{{ r.userName || '学生' }}</span>
                  <span class="review-dish">{{ r.dishName }}</span>
                  <span class="review-time">{{ r.createTime }}</span>
                </div>
                <div class="review-content">{{ r.content || '（未填写文字评价）' }}</div>
                <div class="review-meta">
                  <span class="meta-item"><el-icon :size="13"><Pointer /></el-icon>{{ r.likeCount || 0 }}</span>
                  <span class="meta-item"><el-icon :size="13"><View /></el-icon>{{ r.viewCount || 0 }}</span>
                  <span class="meta-item"><el-icon :size="13"><ChatDotRound /></el-icon>{{ (r.replyCount || 0) + (r.merchantReply ? 1 : 0) }}</span>
                </div>
                <div v-if="r.merchantReply" class="review-reply">
                  <span class="reply-label">
                    商家回复（{{ r.merchantReplyTime }}）<span v-if="r.merchantReplySource === 'AI'" class="reply-ai-tag">AI</span>
                  </span>
                  <div class="reply-text">{{ r.merchantReply }}</div>
                </div>
              </div>
            </div>
            <div class="review-side">
              <div class="review-rating">{{ r.rating }}<span class="rating-star"><el-icon :size="12"><StarFilled /></el-icon></span></div>
              <div class="review-btns">
                <button class="review-ai-btn" :disabled="aiGeneratingId === r.id" @click="aiQuickReply(r)">
                  <span v-if="aiGeneratingId === r.id" class="ai-spin"></span>
                  AI 回复
                </button>
                <button class="review-reply-btn" @click="openReply(r, 'manual')">
                  {{ r.merchantReply ? '修改回复' : '回复' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
    </div>

    <div v-if="total > 0" class="adaptive-pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <el-dialog v-model="replyVisible" title="回复学生评价" width="560px">
      <div v-if="currentReview" class="dialog-review">
        <img v-if="currentReview.dishImage" :src="currentReview.dishImage" class="dialog-img">
        <span v-else class="dialog-img fallback">{{ (currentReview.dishName || '菜').slice(0, 1) }}</span>
        <div>
          <div class="dialog-dish">{{ currentReview.dishName }}</div>
          <div class="dialog-content">{{ currentReview.content || '（未填写文字评价）' }}</div>
        </div>
      </div>

      <div class="ap-pill-group reply-mode">
        <button type="button" class="ap-pill" :class="{ active: replyMode === 'manual' }" @click="replyMode = 'manual'">手动回复</button>
        <button type="button" class="ap-pill" :class="{ active: replyMode === 'ai' }" @click="replyMode = 'ai'">AI 智能回复</button>
      </div>

      <div v-if="replyMode === 'ai' && currentReview" class="ai-hint">
        <div class="ai-hint-title">AI 将按「{{ ratingLabel(currentReview.rating) }} + 评价正文」生成个性化回复</div>
        <div class="ai-hint-tip">可生成后微调再发送，语气会根据评分和具体问题自动调整</div>
      </div>

      <el-input
        v-model="replyText"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="回复学生，例如：感谢反馈，我们会马上改进～"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button v-if="replyMode === 'ai'" :loading="aiGenerating" @click="generateAiReply">重新生成</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMerchantReviews, replyReview, aiReplyReview } from '@/api/review'
import { getCsConfig, updateCsConfig } from '@/api/customerService'

const route = useRoute()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const activeTab = ref('all')
const highlightId = ref(null)

const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'good', label: '好评' },
  { key: 'mid', label: '中评' },
  { key: 'bad', label: '差评' },
  { key: 'pending', label: '待回复' }
]

const query = reactive({
  current: 1,
  size: 10,
  rating: undefined,
  ratingMin: undefined,
  ratingMax: undefined,
  handled: undefined,
  sort: 'time_desc'
})

const reviewAiEnabled = ref(0)
const replyVisible = ref(false)
const replying = ref(false)
const aiGenerating = ref(false)
const aiGeneratingId = ref(null)
const replyText = ref('')
const replyMode = ref('manual')
const currentReview = ref(null)

// 精选区：只在全部/好评 tab 展示评分≥4 的带图评价，避免重复
const featuredReviews = computed(() => {
  if (activeTab.value === 'mid' || activeTab.value === 'bad' || activeTab.value === 'pending') return []
  return records.value.filter(r => r.rating >= 4).slice(0, 3)
})

// 待回复数量：供页头副标题展示
const pendingCount = computed(() => records.value.filter(r => !r.merchantReply).length)

// 列表区：排除已在精选区展示的评价，避免同一评价出现两次
const listReviews = computed(() => {
  const featuredIds = new Set(featuredReviews.value.map(r => r.id))
  return records.value.filter(r => !featuredIds.has(r.id))
})

const emptyText = computed(() => {
  const hasFilter = query.ratingMin !== undefined || query.handled !== undefined || activeTab.value !== 'all'
  return hasFilter ? '没有符合筛选条件的评价' : '还没有学生评价，继续保持好口碑吧～'
})

function ratingLabel(rating) {
  if (rating >= 5) return '五星好评'
  if (rating === 4) return '四星好评'
  if (rating === 3) return '三星中评'
  return '差评'
}

function switchTab(t) {
  activeTab.value = t.key
  query.rating = undefined
  query.ratingMin = undefined
  query.ratingMax = undefined
  query.handled = undefined
  if (t.key === 'pending') {
    query.handled = 0
  } else if (t.key === 'good') {
    query.ratingMin = 4
    query.ratingMax = 5
  } else if (t.key === 'mid') {
    query.ratingMin = 3
    query.ratingMax = 3
  } else if (t.key === 'bad') {
    query.ratingMin = 1
    query.ratingMax = 2
  }
  handleFilterChange()
}

/** 待办跳转定位：?highlight=评价id，切到「待回复」tab 并高亮该评价 */
function applyHighlight() {
  const hid = route.query.highlight ? Number(route.query.highlight) : null
  highlightId.value = hid
  if (hid) {
    activeTab.value = 'pending'
    query.rating = undefined
    query.ratingMin = undefined
    query.ratingMax = undefined
    query.handled = 0
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantReviews(query)
    records.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  query.current = 1
  loadData()
}

function openReply(row, mode = 'manual') {
  currentReview.value = row
  replyText.value = row.merchantReply || ''
  replyMode.value = mode
  replyVisible.value = true
  if (mode === 'ai' && !row.merchantReply) {
    generateAiReply()
  }
}

async function aiQuickReply(row) {
  aiGeneratingId.value = row.id
  try {
    const res = await aiReplyReview(row.id)
    const reply = res.data?.reply
    if (reply) {
      currentReview.value = row
      replyText.value = reply
      replyMode.value = 'ai'
      replyVisible.value = true
      ElMessage.success('已生成回复，可微调后提交')
    }
  } catch {
    // 请求层已统一提示
  } finally {
    aiGeneratingId.value = null
  }
}

async function submitReply() {
  const content = replyText.value.trim()
  if (!content) {
    ElMessage.warning('请填写回复内容')
    return
  }
  replying.value = true
  try {
    await replyReview(currentReview.value.id, {
      reply: content,
      source: replyMode.value === 'ai' ? 'AI' : 'MANUAL'
    })
    ElMessage.success('回复成功')
    replyVisible.value = false
    loadData()
  } finally {
    replying.value = false
  }
}

async function generateAiReply() {
  if (!currentReview.value) return
  aiGenerating.value = true
  try {
    const res = await aiReplyReview(currentReview.value.id)
    const reply = res.data?.reply
    if (reply) {
      replyText.value = reply
      ElMessage.success('已生成回复，可微调后提交')
    }
  } catch {
    // 请求层已统一提示
  } finally {
    aiGenerating.value = false
  }
}

async function loadAiConfig() {
  try {
    const res = await getCsConfig()
    reviewAiEnabled.value = res.data?.reviewAiEnabled || 0
  } catch {
    // 忽略
  }
}

async function saveAiToggle(val) {
  try {
    await updateCsConfig({ reviewAiEnabled: val })
    ElMessage.success(val === 1 ? '已开启 AI 评价托管' : '已关闭 AI 评价托管')
  } catch {
    loadAiConfig()
  }
}

onMounted(() => {
  applyHighlight()
  loadData()
  loadAiConfig()
})
onActivated(() => {
  applyHighlight()
  loadData()
  loadAiConfig()
})
</script>

<style scoped>
/* 铺满高度：页头/筛选卡固定，评价列表滚动，翻页固定在底部 */
.review-page > .review-head { flex: 0 0 auto; }
.review-page > .tab-card { flex: 0 0 auto; }
.review-page > .adaptive-table { flex: 1; min-height: 0; }

.review-page {
  width: 100%;
}

.review-head { margin-bottom: 16px; }

.review-head .ap-title,
.review-head .ap-sub { margin: 0; }
.review-head .ap-sub { margin-top: 7px; }

.ai-toggle {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 15px;
  background: #f5f5f7;
  border-radius: 999px;
}
.ai-toggle-label { font-size: 12.5px; font-weight: 550; color: var(--text-2); white-space: nowrap; }

.tab-card { margin-bottom: 16px; }

.review-filter { margin-bottom: 0; }
.filter-spacer { flex: 1; }

.review-empty { padding: 64px 24px; }

.featured-section { margin-bottom: 22px; }

.featured-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 6px;
}
.featured-scroll::-webkit-scrollbar { height: 6px; }
.featured-scroll::-webkit-scrollbar-thumb { background: #e0e0e0; border-radius: 3px; }

.featured-card {
  flex: 0 0 240px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.18s var(--ease-apple);
}
.featured-card:hover { box-shadow: var(--shadow-sm); }

.featured-cover {
  position: relative;
  height: 88px;
  overflow: hidden;
}

.featured-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.featured-initial {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 650;
  color: var(--text-3);
}

.featured-score {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.62);
  color: #fff;
  font-size: 12.5px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  padding: 2px 8px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.score-star { display: inline-flex; color: var(--brand); }

.featured-body { padding: 11px 13px 12px; }
.featured-dish {
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.featured-content {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 5px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 38px;
}
.featured-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
}
.featured-user { display: flex; align-items: center; gap: 6px; min-width: 0; }
.user-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.user-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ececee;
  color: var(--text-2);
  font-size: 10.5px;
  font-weight: 600;
}
.user-name { font-size: 12px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.featured-state {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 550;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-2);
}
.featured-state.todo { background: var(--brand-soft); color: var(--brand-dark); }
.featured-state.done { background: #e9f8ee; color: #1d8a45; }

.list-section { margin-top: 4px; }

.list-card { padding: 4px 20px; }

.review-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}
.review-item:last-child { border-bottom: none; }

.review-main { display: flex; gap: 12px; flex: 1; min-width: 0; }
.review-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.review-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ececee;
  color: var(--text-2);
  font-size: 15px;
  font-weight: 600;
}

.review-body { flex: 1; min-width: 0; }
.review-topline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.review-name { font-size: 14px; font-weight: 600; letter-spacing: -0.005em; color: var(--text-1); }
.review-dish {
  font-size: 12px;
  color: var(--text-2);
  background: #f5f5f7;
  padding: 2px 9px;
  border-radius: 999px;
}
.review-time { font-size: 12px; color: var(--text-3); margin-left: auto; }
.review-content {
  margin-top: 7px;
  font-size: 14px;
  color: var(--text-1);
  white-space: pre-wrap;
  line-height: 1.7;
}

.review-reply {
  margin-top: 10px;
  padding: 10px 12px;
  background: #f5f5f7;
  border-radius: 12px;
}
.reply-label { font-size: 12px; color: var(--text-3); display: flex; align-items: center; gap: 6px; }
.reply-ai-tag {
  font-size: 9.5px;
  font-weight: 650;
  color: var(--brand-dark);
  background: var(--brand-soft);
  padding: 1px 6px;
  border-radius: 999px;
}
.reply-text { margin-top: 4px; font-size: 13.5px; color: var(--text-1); white-space: pre-wrap; }

.review-meta {
  display: flex;
  gap: 14px;
  margin-top: 8px;
}

.meta-item {
  font-size: 12px;
  color: var(--text-3);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}

.review-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  flex-shrink: 0;
}
.review-rating {
  font-size: 14px;
  font-weight: 650;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.rating-star { display: inline-flex; color: var(--brand); }

.review-btns { display: flex; align-items: center; gap: 10px; }

.review-ai-btn {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border-radius: 999px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.review-ai-btn:hover:not(:disabled) { background: #f5f5f7; color: var(--text-1); }
.review-ai-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ai-spin {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.12);
  border-top-color: var(--text-2);
  animation: ai-spin 0.8s linear infinite;
}
@keyframes ai-spin { to { transform: rotate(360deg); } }

.review-reply-btn {
  appearance: none;
  border: none;
  background: var(--text-1);
  color: #fff;
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.15s ease, transform 0.12s var(--ease-apple);
}
.review-reply-btn:hover { background: #333336; }
.review-reply-btn:active { transform: scale(0.97); }

.dialog-review {
  display: flex;
  gap: 11px;
  margin-bottom: 14px;
  padding: 12px 13px;
  background: #f5f5f7;
  border-radius: 14px;
  align-items: flex-start;
}
.dialog-img {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}
.dialog-img.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ececee;
  color: var(--text-2);
  font-size: 20px;
  font-weight: 650;
}
.dialog-dish { font-size: 13px; font-weight: 600; color: var(--text-1); }
.dialog-content { font-size: 13px; color: var(--text-2); margin-top: 4px; line-height: 1.6; }

.reply-mode { margin-bottom: 14px; }

.ai-hint {
  margin-bottom: 14px;
  padding: 11px 13px;
  background: #f5f5f7;
  border-radius: 12px;
}
.ai-hint-title { font-size: 12.5px; color: var(--text-1); font-weight: 600; }
.ai-hint-tip { font-size: 12px; color: var(--text-3); margin-top: 4px; }

@media (max-width: 900px) {
  .review-side { flex-direction: row; align-items: center; }
}

.review-item.todo-highlight,
.featured-card.todo-highlight {
  animation: review-flash 2s ease;
}
@keyframes review-flash {
  0% { background: #fff4e0; }
  100% { background: transparent; }
}
</style>
