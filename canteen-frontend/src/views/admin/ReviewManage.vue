<template>
  <div class="page-container adaptive-page review-manage" :class="{ 'is-embedded': embedded }">
    <div v-if="!embedded" class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">评价审核</h1>
        <div class="ap-sub">共 {{ total }} 条评价 · 审核、屏蔽与恢复学生评价</div>
      </div>
    </div>

    <div class="page-card review-card">
      <div class="ap-toolbar review-toolbar">
        <el-input v-model="query.keyword" placeholder="搜索内容/菜品" clearable style="width: 190px" @keyup.enter="loadData" />
        <el-select v-model="query.rating" placeholder="全部星级" clearable style="width: 110px" @change="loadData">
          <el-option :label="`${n} 星`" :value="n" v-for="n in 5" :key="n" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="loadData">
          <el-option label="待审核" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已屏蔽" :value="2" />
        </el-select>
        <el-select v-model="query.sort" placeholder="排序方式" clearable style="width: 130px" @change="loadData">
          <el-option label="最新发布" value="time_desc" />
          <el-option label="最高点赞" value="like_desc" />
          <el-option label="最高浏览" value="view_desc" />
          <el-option label="好评优先" value="rating_desc" />
          <el-option label="差评优先" value="rating_asc" />
        </el-select>
        <span class="ap-toolbar-spacer"></span>
        <div class="auto-block">
          <el-switch v-model="autoBlock" size="small" :loading="autoBlockLoading" @change="onAutoBlockChange" />
          <span class="auto-block-label">自动屏蔽敏感词</span>
        </div>
      </div>

      <div class="review-selectbar">
        <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选</el-checkbox>
        <span class="selected-hint">已选 {{ selectedRows.length }} 项</span>
        <transition name="fade">
          <el-button
            v-if="selectedRows.length"
            type="danger"
            plain
            size="small"
            :loading="batchDeleting"
            @click="handleBatchDelete"
          >
            批量删除
          </el-button>
        </transition>
        <span class="ap-toolbar-spacer"></span>
        <span class="toolbar-count">共 {{ total }} 条</span>
      </div>

      <div v-if="records.length || loading" v-loading="loading" class="adaptive-table review-list">
        <div v-for="r in records" :key="r.id" class="review-item" :data-recid="r.id">
          <el-checkbox v-model="selectedIds" :value="r.id" class="item-check" />
          <div class="review-main">
            <img v-if="r.avatar" :src="r.avatar" class="review-avatar" @error="onAvatarError($event)">
            <span v-else class="review-avatar fallback">{{ (r.userName || '学').slice(0, 1) }}</span>
            <div class="review-body">
              <div class="review-head">
                <span class="review-name">{{ r.userName || '学生' }}</span>
                <span class="review-dish">{{ r.dishName }}</span>
                <span v-if="r.stallName" class="ap-tag">{{ r.stallName }}</span>
                <span
                  class="ap-tag"
                  :class="{
                    'ap-tag--amber': r.status === 0,
                    'ap-tag--green': r.status === 1,
                    'ap-tag--red': r.status === 2
                  }"
                >
                  {{ statusLabel(r.status) }}
                </span>
                <span class="review-time">{{ r.createTime }}</span>
              </div>
              <div class="review-content">{{ r.content || '（未填写文字评价）' }}</div>
              <div class="review-meta">
                <span class="meta-item"><el-icon><Pointer /></el-icon>{{ r.likeCount || 0 }}</span>
                <span class="meta-item"><el-icon><View /></el-icon>{{ r.viewCount || 0 }}</span>
                <span class="meta-item"><el-icon><ChatDotRound /></el-icon>{{ replyTotal(r) }}</span>
                <el-button link size="small" class="reply-toggle" @click="toggleReplies(r)">
                  {{ r.showReplies ? '收起回复' : '查看回复' }}
                </el-button>
              </div>
              <div v-if="r.showReplies" class="reply-list">
                <div v-if="r.merchantReply" class="reply-item merchant-reply-item">
                  <img v-if="r.merchantAvatar" :src="r.merchantAvatar" class="reply-avatar" @error="$event.target.style.display='none'">
                  <span v-else class="reply-avatar fallback">{{ (r.stallName || '商').slice(0, 1) }}</span>
                  <div class="reply-body">
                    <span class="reply-name">商家 · {{ r.stallName || '档口' }}</span>
                    <span class="reply-content">{{ r.merchantReply }}</span>
                    <span v-if="r.merchantReplyTime" class="reply-time">{{ r.merchantReplyTime }}</span>
                  </div>
                </div>
                <div v-if="!r.replies || !r.replies.length" class="reply-empty">还没有学生回复</div>
                <div v-for="rp in r.replies" :key="rp.id" class="reply-item">
                  <img v-if="rp.avatar" :src="rp.avatar" class="reply-avatar" @error="$event.target.style.display='none'">
                  <span v-else class="reply-avatar fallback">{{ (rp.userName || '学').slice(0, 1) }}</span>
                  <div class="reply-body">
                    <span class="reply-name">{{ rp.userName }}</span>
                    <span class="reply-content">{{ rp.content }}</span>
                    <span class="reply-time">{{ rp.createTime }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="review-side">
            <StarRating :value="r.rating" readonly />
            <div class="review-actions">
              <el-button v-if="r.status !== 1" link class="row-action" @click="updateStatus(r, 1)">恢复</el-button>
              <el-button v-else link class="row-action" @click="updateStatus(r, 2)">屏蔽</el-button>
              <el-dropdown trigger="click" @command="handleDelete(r)">
                <el-button link class="row-action row-action--icon" aria-label="更多操作">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="delete">删除评价</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="ap-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><ChatDotRound /></el-icon></div>
        <div class="ap-empty-title">暂无评价</div>
        <div>换个筛选条件试试</div>
      </div>

      <div v-if="total" class="adaptive-pager review-pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted , onActivated, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StarRating from '@/components/StarRating.vue'
import { pageReviews, deleteReview, updateReviewStatus, getReplies } from '@/api/review'
import { useFlashLocate } from '@/composables/useFlashLocate'
import { moderationAutoBlock, setModerationAutoBlock } from '@/api/admin'

defineProps({ embedded: { type: Boolean, default: false } })

const route = useRoute()
const loading = ref(false)
const autoBlock = ref(true)
const autoBlockLoading = ref(false)
const records = ref([])
const total = ref(0)
const selectedIds = ref([])
const batchDeleting = ref(false)

const allSelected = computed({
  get: () => records.value.length > 0 && selectedIds.value.length === records.value.length,
  set: (val) => {
    selectedIds.value = val ? records.value.map(r => r.id) : []
  }
})
const isIndeterminate = computed(() => selectedIds.value.length > 0 && selectedIds.value.length < records.value.length)

const selectedRows = computed(() => records.value.filter(r => selectedIds.value.includes(r.id)))

const query = reactive({ current: 1, size: 10, keyword: '', rating: undefined, status: undefined, sort: undefined })

async function loadData() {
  loading.value = true
  try {
    const res = await pageReviews(query)
    records.value = (res.data.records || []).map(r => ({ ...r, showReplies: false, replies: [] }))
    total.value = res.data.total
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

async function loadAutoBlock() {
  try {
    const res = await moderationAutoBlock()
    autoBlock.value = !!res.data?.enabled
  } catch {
    autoBlock.value = true
  }
}

async function onAutoBlockChange(val) {
  autoBlockLoading.value = true
  try {
    await setModerationAutoBlock(val)
    ElMessage.success(val ? '已开启自动屏蔽敏感词' : '已关闭自动屏蔽敏感词')
  } catch {
    autoBlock.value = !val
    ElMessage.error('设置失败，请稍后再试')
  } finally {
    autoBlockLoading.value = false
  }
}

// 回复总数 = 学生回复数 + 商家回复（若有）
function replyTotal(r) {
  return (r.replyCount || 0) + (r.merchantReply ? 1 : 0)
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

function statusLabel(status) {
  return { 0: '待审核', 1: '已发布', 2: '已屏蔽' }[status] || '未知'
}

function statusTag(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info'
}

function onAvatarError(e) {
  e.target.style.display = 'none'
  const parent = e.target.parentElement
  if (!parent.querySelector('.avatar-fallback')) {
    const span = document.createElement('span')
    span.className = 'avatar-fallback'
    span.textContent = '学'
    parent.appendChild(span)
  }
}

async function updateStatus(row, status) {
  await updateReviewStatus(row.id, status)
  ElMessage.success(status === 1 ? '已恢复显示' : '已屏蔽该评价')
  loadData()
}

function handleDelete(row) {
  ElMessageBox.confirm('删除后不可恢复，确定删除吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteReview(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

async function handleBatchDelete() {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 项吗？删除后不可恢复`, '提示', { type: 'warning' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(rows.map(row => deleteReview(row.id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

// 工作台低分评价敏感项跳转定位：/admin/feedback-center?tab=review&refer=REVIEW&id=xx
const { locate } = useFlashLocate(route, (rid) => document.querySelector(`.review-item[data-recid="${rid}"]`), {
  refer: route.query.refer,
  id: route.query.id,
  getRecId: (item) => item.id,
  onFound: async (rid, found) => {
    if (found || route.query.refer !== 'REVIEW') return
    // 第 1 页未见：逐页翻查直到找到该条评价
    const totalPages = Math.max(1, Math.ceil(total.value / query.size))
    for (let page = 1; page <= totalPages; page++) {
      query.current = page
      await loadData()
      await nextTick()
      const el = document.querySelector(`.review-item[data-recid="${rid}"]`)
      if (el) {
        nextTick(() => {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' })
          el.classList.add('flash-target')
          setTimeout(() => el.classList.remove('flash-target'), 2400)
        })
        return
      }
    }
    query.current = 1
    loadData()
  }
})

onMounted(async () => {
  await loadData()
  loadAutoBlock()
  nextTick(locate)
})
onActivated(loadData)
</script>

<style scoped>
.review-manage {
  width: 100%;
}

/* 作为「反馈与评价」的审核 tab 渲染时，由外层壳负责留白 */
.review-manage.is-embedded {
  padding: 0;
}

.review-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.review-toolbar {
  flex-shrink: 0;
  margin-bottom: 10px;
}

.auto-block {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.auto-block-label {
  font-size: 12.5px;
  color: var(--text-2);
  white-space: nowrap;
}

.review-selectbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 2px;
  margin-bottom: 2px;
  flex-shrink: 0;
}

.selected-hint,
.toolbar-count {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s var(--ease-apple);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.review-list {
  padding: 0 2px;
}

/* 评价行：卡内用发丝线分隔，而不是每行独立描边卡 */
.review-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.review-item:last-child {
  border-bottom: none;
}

.item-check {
  flex-shrink: 0;
  margin-top: 14px;
}

.review-main {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.review-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.review-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f2;
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
}

.review-avatar .avatar-fallback {
  font-size: 14px;
  font-weight: 600;
}

/* 头像图片加载失败时由 onAvatarError 追加的首字兜底 */
.review-main > .avatar-fallback {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #f0f0f2;
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
}

.review-body {
  flex: 1;
  min-width: 0;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.review-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

.review-dish {
  font-size: 11.5px;
  color: var(--text-2);
  background: #f5f5f7;
  padding: 2px 8px;
  border-radius: 999px;
}

.review-time {
  font-size: 12px;
  color: var(--text-3);
  margin-left: auto;
  white-space: nowrap;
}

.review-content {
  margin-top: 7px;
  font-size: 14px;
  color: var(--text-1);
  white-space: pre-wrap;
  line-height: 1.7;
}

.review-meta {
  display: flex;
  gap: 14px;
  margin-top: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.meta-item .el-icon {
  font-size: 13px;
}

.reply-toggle {
  color: var(--text-2);
  font-size: 12.5px;
}

.reply-toggle:hover {
  color: var(--text-1) !important;
}

.reply-list {
  margin-top: 10px;
  padding: 8px 14px;
  border-radius: 12px;
  background: #fafafa;
}

.reply-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
}

.merchant-reply-item {
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  padding-bottom: 8px;
  margin-bottom: 2px;
}

.reply-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.reply-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f2;
  color: var(--text-2);
  font-size: 11px;
  font-weight: 600;
}

.reply-body {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.6;
}

.reply-name {
  font-weight: 600;
  color: var(--text-1);
  margin-right: 6px;
}

.merchant-reply-item .reply-name {
  color: var(--text-2);
}

.reply-content {
  color: var(--text-2);
}

.reply-time {
  margin-left: 8px;
  font-size: 11px;
  color: var(--text-3);
}

.reply-empty {
  font-size: 12px;
  color: var(--text-3);
  padding: 4px 0;
}

.review-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
  width: 120px;
}

.review-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.row-action {
  color: var(--text-2);
  font-size: 13px;
}

.row-action:hover {
  color: var(--text-1) !important;
  background: rgba(0, 0, 0, 0.04);
}

.row-action--icon {
  padding: 4px 6px;
}

.review-pager {
  justify-content: flex-end;
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .review-side {
    width: auto;
  }
  .review-time {
    margin-left: 0;
  }
}
</style>
