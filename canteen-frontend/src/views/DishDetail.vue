<template>
  <div class="page-container" v-loading="loading">
    <button class="back-link" type="button" @click="goBack">
      <el-icon><ArrowLeft /></el-icon>
      {{ fromOrders ? '返回我的订单' : '返回菜品列表' }}
    </button>

    <el-empty v-if="!detail && !loading" description="菜品不存在或已下架">
      <el-button type="primary" @click="goBack">返回菜品列表</el-button>
    </el-empty>

    <!-- 主视觉：图片 + 名称/价格 + 关键事实 + 操作 -->
    <section v-if="detail" class="dish-hero">
      <div class="hero-media" :style="{ background: coverGradient(detail.dish.category) }">
        <img
          v-if="detail.dish.image && !broken"
          class="hero-photo"
          :src="detail.dish.image"
          :alt="detail.dish.name"
          @error="broken = true"
        >
        <span v-else class="hero-initial">{{ detail.dish.name.slice(0, 1) }}</span>
      </div>

      <div class="hero-body">
        <div class="hero-head">
          <div class="hero-copy">
            <div class="hero-eyebrow">
              {{ detail.dish.category || '未分类' }} · {{ detail.dish.stallName }} {{ detail.dish.windowNo }} 号窗口
            </div>
            <h1 class="hero-title">{{ detail.dish.name }}</h1>
            <p class="hero-desc">{{ detail.dish.description || '暂无简介' }}</p>
          </div>
          <div class="hero-price">
            <span class="hero-price-sym">¥</span>{{ detail.dish.price }}
          </div>
        </div>

        <div class="hero-stats">
          <div class="hero-stat">
            <div class="stat-label">综合评分</div>
            <div class="stat-line">
              <span class="stat-value">{{ detail.dish.avgRating || '—' }}</span>
              <StarRating :value="Number(detail.dish.avgRating)" readonly />
            </div>
          </div>
          <div class="hero-stat">
            <div class="stat-label">好评率</div>
            <div class="stat-line">
              <span class="stat-value">{{ detail.dish.goodRate ?? '—' }}<small>%</small></span>
            </div>
          </div>
          <div class="hero-stat">
            <div class="stat-label">累计评价</div>
            <div class="stat-line">
              <span class="stat-value">{{ detail.dish.reviewCount }}<small>条</small></span>
            </div>
          </div>

          <div class="hero-actions">
            <button
              type="button"
              class="ap-btn ap-btn--light hero-fav"
              :class="{ 'is-on': detail.dish.favorited }"
              @click="toggleFavorite"
            >
              <el-icon><Star /></el-icon>
              {{ detail.dish.favorited ? '已收藏' : '收藏' }}
            </button>
            <button type="button" class="ap-btn ap-btn--brand" :disabled="adding" @click="handleAddCart">
              <el-icon><ShoppingCart /></el-icon>
              {{ adding ? '添加中…' : '加入购物车' }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- 评分分布 + 我要评价 -->
    <div class="detail-grid" v-if="detail">
      <section class="ap-card chart-card">
        <div class="card-title">评分分布</div>
        <EChart :option="distributionOption" height="220px" />
      </section>

      <section class="ap-card form-card">
        <div class="card-title">{{ detail.canReview ? '我要评价' : '评价这道菜' }}</div>
        <el-alert
          v-if="!detail.canReview"
          type="info"
          :closable="false"
          show-icon
          title="购买过该菜品后才能评价"
          description="先去下单品尝一下，再来分享你的真实感受吧～"
        />
        <el-form v-else ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-position="top">
          <el-form-item label="评分" prop="rating">
            <StarRating v-model:value="reviewForm.rating" />
          </el-form-item>
          <el-form-item label="评价内容" prop="content">
            <el-input
              v-model="reviewForm.content"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="说说这道菜的口味、分量、性价比……"
            />
          </el-form-item>
          <div class="form-foot">
            <el-checkbox v-model="reviewForm.anonymous">匿名评价</el-checkbox>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">提交评价</el-button>
          </div>
        </el-form>
      </section>
    </div>

    <!-- 全部评价：单独一排 -->
    <section class="ap-card reviews-card" v-if="detail">
      <div class="reviews-head">
        <div class="card-title">全部评价</div>
        <span class="ap-tag">{{ detail.latestReviews?.length || 0 }} 条</span>
      </div>
      <div v-if="!detail.latestReviews?.length" class="ap-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><ChatDotRound /></el-icon></div>
        <div class="ap-empty-title">还没有人评价</div>
        <div>来做第一个分享感受的人吧</div>
      </div>
      <div v-if="blockedNotice" id="blocked-notice" class="blocked-notice">
        <span class="blocked-notice-icon"><el-icon><WarningFilled /></el-icon></span>
        <span>你有一条评价因包含敏感内容被系统屏蔽，请注意文明用语。</span>
      </div>
      <div v-for="r in detail.latestReviews || []" :key="r.id" :id="'review-' + r.id" class="review-item">
        <div class="review-head">
          <img
            v-if="r.avatar"
            class="review-avatar"
            :src="r.avatar"
            :alt="r.userName"
            @error="$event.target.style.display = 'none'"
          >
          <div v-else class="review-avatar review-avatar--fallback">{{ (r.userName || '客').slice(0, 1) }}</div>
          <div class="review-head-info">
            <div class="review-name">
              {{ r.userName }}
              <span v-if="r.mine" class="mine-tag">我的评价</span>
            </div>
            <div class="review-time">{{ r.createTime }}</div>
          </div>
          <div class="review-stars">
            <StarRating :value="r.rating" readonly />
          </div>
        </div>

        <div class="review-content">{{ r.content || '（该用户未填写文字评价）' }}</div>

        <div class="review-actions">
          <button
            class="review-action-btn"
            :class="{ active: r.liked }"
            @click="toggleLike(r)"
          >
            <el-icon><Pointer /></el-icon>
            {{ r.liked ? '已赞' : '点赞' }}<span v-if="r.likeCount"> {{ r.likeCount }}</span>
          </button>
          <span class="review-action-meta">
            <el-icon><View /></el-icon>
            {{ r.viewCount || 0 }}
          </span>
          <button class="review-action-btn" @click="toggleReplies(r)">
            <el-icon><ChatDotRound /></el-icon>
            回复 {{ (r.replyCount || 0) + (r.merchantReply ? 1 : 0) }}
          </button>
        </div>

        <!-- 回复列表 -->
        <div v-if="r.showReplies" class="reply-list">
          <div v-if="r.merchantReply" class="reply-item merchant-reply-item">
            <img
              v-if="r.merchantAvatar"
              class="reply-avatar"
              :src="r.merchantAvatar"
              @error="$event.target.style.display = 'none'"
            >
            <div v-else class="reply-avatar reply-avatar--fallback">{{ (r.stallName || '商').slice(0, 1) }}</div>
            <div class="reply-body">
              <span class="reply-name merchant-name">商家 · {{ r.stallName || '档口' }}</span>
              <span class="reply-content">{{ r.merchantReply }}</span>
            </div>
          </div>
          <div v-if="!r.replies || !r.replies.length" class="muted" style="font-size: 12px; padding: 6px 0">
            还没有回复，来问问他吧～
          </div>
          <div v-for="rp in r.replies" :key="rp.id" :id="'reply-' + rp.id" class="reply-item">
            <img
              v-if="rp.avatar"
              class="reply-avatar"
              :src="rp.avatar"
              @error="$event.target.style.display = 'none'"
            >
            <div v-else class="reply-avatar reply-avatar--fallback">{{ (rp.userName || '客').slice(0, 1) }}</div>
            <div class="reply-body">
              <span class="reply-name">{{ rp.userName }}<span v-if="rp.mine" class="mine-tag">我</span></span>
              <span class="reply-content">{{ rp.content }}</span>
            </div>
          </div>
          <div class="reply-input-row">
            <el-input
              v-model="r.replyDraft"
              size="small"
              maxlength="500"
              placeholder="写下你的追问或回答…"
              @keyup.enter="doReply(r)"
            />
            <el-button size="small" type="primary" :loading="r.replying" @click="doReply(r)">回复</el-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EChart from '@/components/EChart.vue'
import StarRating from '@/components/StarRating.vue'
import { getDishDetail } from '@/api/dish'
import { submitReview as submitReviewApi, likeReview, studentReply, getReplies } from '@/api/review'
import { addFavorite, removeFavorite } from '@/api/favorite'
import { addToCart } from '@/api/cart'
import { coverGradient } from '@/utils/dishImage'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const blockedNotice = ref(false)
const submitting = ref(false)
const detail = ref(null)
const broken = ref(false)

const reviewForm = reactive({
  dishId: Number(route.params.id),
  rating: 5,
  content: '',
  anonymous: false
})

const reviewFormRef = ref()
const reviewRules = {
  rating: [{ type: 'number', required: true, min: 1, max: 5, message: '评分范围为1-5星', trigger: 'change' }],
  content: [{ max: 500, message: '评价内容不能超过500字', trigger: 'blur' }]
}

const distributionOption = computed(() => {
  const d = detail.value?.distribution || {}
  const rows = [
    { name: '5星', value: d.star5 || 0, color: '#34c759' },
    { name: '4星', value: d.star4 || 0, color: '#8ddc6b' },
    { name: '3星', value: d.star3 || 0, color: '#ffa94d' },
    { name: '2星', value: d.star2 || 0, color: '#ff8a80' },
    { name: '1星', value: d.star1 || 0, color: '#ef4444' }
  ]
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: rows.map(r => r.name) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f1f3' } } },
    series: [
      {
        type: 'bar',
        barWidth: 22,
        data: rows.map(r => ({ value: r.value, itemStyle: { color: r.color, borderRadius: [4, 4, 0, 0] } })),
        label: { show: true, position: 'top', fontSize: 11 }
      }
    ]
  }
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getDishDetail(route.params.id)
    detail.value = res.data
    // 给每条评价挂上交互用的本地状态
    ;(detail.value.latestReviews || []).forEach(r => {
      r.showReplies = false
      r.replies = []
      r.replyDraft = ''
      r.replying = false
    })
    highlightTarget()
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

// 从通知跳进来时，自动展开并高亮定位到指定评价/回复（?review=评价ID&reply=回复ID，或 ?highlight=评价ID）
async function highlightTarget() {
  const reviewId = Number(route.query.review || route.query.highlight)
  const replyId = Number(route.query.reply)
  if (!reviewId) return
  await nextTick()
  const r = (detail.value?.latestReviews || []).find(x => x.id === reviewId)
  if (!r) {
    // 评价被系统屏蔽（敏感词）时不在公开列表里，给出提示而非静默
    if (route.query.highlight) {
      blockedNotice.value = true
      await nextTick()
      const n = document.getElementById('blocked-notice')
      if (n) {
        n.scrollIntoView({ behavior: 'smooth', block: 'center' })
        n.classList.add('flash-highlight')
        setTimeout(() => n.classList.remove('flash-highlight'), 2400)
      }
    }
    return
  }
  r.showReplies = true
  try {
    const res = await getReplies(r.id)
    r.replies = res.data || []
  } catch {
    r.replies = []
  }
  await nextTick()
  // 优先定位具体回复，否则定位整条评价
  const el = document.getElementById(replyId ? `reply-${replyId}` : `review-${reviewId}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('flash-highlight')
    setTimeout(() => el.classList.remove('flash-highlight'), 2400)
  }
}

const adding = ref(false)

async function handleAddCart() {
  adding.value = true
  try {
    await addToCart(detail.value.dish.id, 1)
    window.dispatchEvent(new CustomEvent('canteen:cart-changed'))
    ElMessage.success('已加入购物车')
  } finally {
    adding.value = false
  }
}

async function toggleFavorite() {
  const dish = detail.value.dish
  if (dish.favorited) {
    await removeFavorite(dish.id)
    dish.favorited = false
    ElMessage.success('已取消收藏')
  } else {
    await addFavorite(dish.id)
    dish.favorited = true
    ElMessage.success('收藏成功')
  }
}

async function handleSubmit() {
  try {
    await reviewFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await submitReviewApi({ ...reviewForm, anonymous: reviewForm.anonymous ? 1 : 0 })
    ElMessage.success('评价提交成功')
    reviewForm.content = ''
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function toggleLike(r) {
  try {
    const res = await likeReview(r.id)
    const data = res.data || {}
    r.liked = data.liked
    r.likeCount = data.likeCount
  } catch {
    // 请求层已统一提示
  }
}

async function toggleReplies(r) {
  r.showReplies = !r.showReplies
  if (r.showReplies) {
    try {
      const res = await getReplies(r.id)
      r.replies = res.data || []
    } catch {
      r.replies = []
    }
  }
}

async function doReply(r) {
  const content = (r.replyDraft || '').trim()
  if (!content) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  r.replying = true
  try {
    await studentReply(r.id, { content })
    r.replyDraft = ''
    const res = await getReplies(r.id)
    r.replies = res.data || []
    r.replyCount = (r.replyCount || 0) + 1
  } finally {
    r.replying = false
  }
}

onMounted(loadDetail)

watch(
  () => (route.name === 'DishDetail' ? route.params.id : null),
  (id) => {
    if (!id) return
    reviewForm.dishId = Number(id)
    reviewForm.content = ''
    broken.value = false
    loadDetail()
  }
)

// 已在详情页内时，从通知再次跳转到同一菜品（仅 query 变化）也要重新定位高亮
watch(() => [route.query.review, route.query.reply], () => {
  highlightTarget()
})

const fromOrders = computed(() => route.query.from === 'orders')

function goBack() {
  if (fromOrders.value) {
    router.push('/my/orders')
    return
  }
  const stallId = detail.value?.dish?.stallId
  router.push(stallId ? `/dishes?stallId=${stallId}` : '/stalls/south')
}
</script>

<style scoped>
/* ============ 返回 ============ */
.page-container {
  padding-bottom: 116px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  padding: 6px 2px;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  font-weight: 550;
  cursor: pointer;
  transition: color 0.16s var(--ease-apple);
}

.back-link:hover {
  color: var(--text-1);
}

.back-link .el-icon {
  font-size: 14px;
}

/* ============ 主视觉 ============ */
.dish-hero {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 28px;
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xs);
}

.hero-media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 18px;
  background: #f2f2f5;
}

.hero-photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-initial {
  font-size: 64px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: rgba(255, 255, 255, 0.92);
}

.hero-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow {
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 0.01em;
}

.hero-title {
  margin: 8px 0 0;
  font-size: 27px;
  font-weight: 650;
  letter-spacing: -0.024em;
  line-height: 1.15;
  color: var(--text-1);
}

.hero-desc {
  margin: 10px 0 0;
  max-width: 56ch;
  font-size: 13.5px;
  line-height: 1.65;
  color: var(--text-2);
}

.hero-price {
  flex-shrink: 0;
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -0.03em;
  line-height: 1;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.hero-price-sym {
  margin-right: 2px;
  font-size: 16px;
  font-weight: 600;
}

.hero-stats {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 34px;
  margin-top: auto;
  padding-top: 24px;
}

.hero-stat .stat-label {
  font-size: 11.5px;
  font-weight: 550;
  letter-spacing: 0.02em;
  color: var(--text-3);
}

.stat-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.stat-value {
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.stat-value small {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-3);
}

.hero-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
  padding-bottom: 2px;
}

.hero-actions .ap-btn {
  height: 38px;
  font-size: 13.5px;
}

.hero-fav.is-on {
  color: var(--brand-dark);
  background: var(--brand-soft);
  box-shadow: 0 0 0 1px var(--brand-line);
}

.hero-fav.is-on .el-icon {
  color: #f5a623;
}

/* ============ 评分分布 + 评价表单 ============ */
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 16px;
  margin-top: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}

.chart-card .card-title {
  margin-bottom: 10px;
}

.form-card .card-title {
  margin-bottom: 14px;
}

.form-card :deep(.el-form-item) {
  margin-bottom: 14px;
}

.form-card :deep(.el-alert) {
  border-radius: 14px;
}

.form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* ============ 评价列表 ============ */
.reviews-card {
  margin-top: 16px;
}

.reviews-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.review-item {
  padding: 18px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.review-item:last-child {
  padding-bottom: 4px;
  border-bottom: none;
}

/* 从通知定位跳转时的高亮闪烁（类名由 JS 添加，样式可改） */
.flash-highlight {
  animation: review-flash 1.6s ease;
  border-radius: 12px;
}

@keyframes review-flash {
  0%, 55% {
    background: rgba(255, 122, 26, 0.07);
    box-shadow: 0 0 0 3px rgba(255, 122, 26, 0.18);
  }
  100% {
    background: transparent;
    box-shadow: none;
  }
}

.blocked-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff8ef;
  color: #8a5a1e;
  font-size: 12.5px;
  line-height: 1.6;
}

.blocked-notice-icon {
  display: inline-flex;
  flex-shrink: 0;
  font-size: 14px;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  object-fit: cover;
  background: #f2f2f5;
}

.review-avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-2);
  background: #e9e9ee;
}

.review-head-info {
  flex: 1;
  min-width: 0;
}

.review-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
}

.review-time {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-3);
}

.mine-tag {
  margin-left: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.055);
  color: var(--text-2);
  font-size: 10.5px;
  font-weight: 550;
}

.review-content {
  margin: 10px 0 6px;
  font-size: 13.5px;
  line-height: 1.65;
  color: var(--text-1);
}

.review-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.review-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-3);
  font-family: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: background-color 0.15s var(--ease-apple), color 0.15s var(--ease-apple);
}

.review-action-btn .el-icon,
.review-action-meta .el-icon {
  font-size: 12px;
}

.review-action-btn:hover {
  background: rgba(0, 0, 0, 0.045);
  color: var(--text-1);
}

.review-action-btn.active {
  background: var(--brand-soft);
  color: var(--brand-dark);
  font-weight: 600;
}

.review-action-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12.5px;
  color: var(--text-3);
}

/* 回复 */
.reply-list {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f7f7f9;
}

.reply-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
}

.reply-avatar {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 50%;
  object-fit: cover;
  background: #f2f2f5;
}

.reply-avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-2);
  background: #e9e9ee;
}

.reply-body {
  flex: 1;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--text-2);
}

.reply-name {
  margin-right: 6px;
  font-weight: 600;
  color: var(--text-1);
}

.merchant-reply-item {
  margin-bottom: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--border);
}

.merchant-name {
  color: var(--brand-dark);
}

.reply-input-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 900px) {
  .dish-hero {
    grid-template-columns: 1fr;
    gap: 20px;
    padding: 18px;
  }

  .hero-media {
    aspect-ratio: 16 / 9;
  }

  .hero-head {
    gap: 12px;
  }

  .hero-title {
    font-size: 23px;
  }

  .hero-price {
    font-size: 24px;
  }

  .hero-stats {
    gap: 24px;
    padding-top: 20px;
  }

  .hero-actions {
    width: 100%;
    margin-left: 0;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .flash-highlight {
    animation: none;
    box-shadow: 0 0 0 2px rgba(255, 122, 26, 0.35);
  }
}
</style>
