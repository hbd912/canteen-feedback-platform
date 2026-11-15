<template>
  <div class="ap-page reviews-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">我的评价</h1>
        <div class="ap-sub">
          共 {{ total }} 条评价<span v-if="records.length"> · 本页 {{ records.filter(r => r.status === 0).length }} 条待审核</span>
        </div>
      </div>
      <div v-if="records.length" class="ap-actions">
        <button class="ap-btn ap-btn--light" @click="toggleEditing">{{ editing ? '完成' : '管理' }}</button>
      </div>
    </div>

    <div v-loading="loading" class="ap-card review-card">
      <div v-if="editing" class="manage-bar">
        <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选</el-checkbox>
        <span class="selected-hint">已选 {{ selectedIds.length }} 项</span>
        <span class="manage-spacer"></span>
        <el-button
          class="batch-cancel"
          :disabled="!selectedIds.length"
          :loading="batchDeleting"
          @click="handleBatchDelete"
        >
          删除所选
        </el-button>
      </div>

      <div v-if="!records.length && !loading" class="ap-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><ChatLineSquare /></el-icon></div>
        <div class="ap-empty-title">你还没有评价过菜品</div>
        <div>点餐后去菜品详情写下感受吧</div>
      </div>

      <article v-for="r in records" :key="r.id" class="review-item">
        <el-checkbox
          v-if="editing"
          v-model="selectedIds"
          :value="r.id"
          class="item-check"
          @click.stop
        />

        <div class="review-thumb" @click="goDish(r)">
          <img
            v-if="r.dishImage"
            :src="r.dishImage"
            :alt="r.dishName"
            loading="lazy"
            @error="onImgError($event, r)"
          />
          <span v-show="!r.dishImage" class="review-initial">{{ (r.dishName || '菜').slice(0, 1) }}</span>
        </div>

        <div class="review-main">
          <div class="review-title-row">
            <button class="review-dish-name" @click="goDish(r)">{{ r.dishName }}</button>
            <span class="review-stall">{{ r.stallName }}</span>
          </div>

          <div class="review-rating">
            <StarRating :value="r.rating" readonly />
          </div>

          <div class="review-content-wrap">
            <div class="review-content" :class="{ expanded: expandedIds.includes(r.id) }">
              {{ r.content || '（未填写文字评价）' }}
            </div>
            <button
              v-if="r.content && r.content.length > 70"
              class="review-expand"
              @click="toggleExpand(r.id)"
            >
              {{ expandedIds.includes(r.id) ? '收起' : '展开' }}
            </button>
          </div>

          <div class="review-foot">
            <span class="review-date">{{ r.createTime }}</span>
            <span class="review-status">
              <i
                class="status-dot"
                :class="r.status === 2 ? 'is-blocked' : r.status === 0 ? 'is-pending' : 'is-published'"
              ></i>
              {{ r.status === 2 ? '已被屏蔽' : r.status === 0 ? '待审核' : '已发布' }}
            </span>
          </div>

          <el-dropdown v-if="!editing" trigger="click" class="review-more" @command="handleDelete(r)">
            <button class="more-btn" aria-label="更多操作">
              <el-icon :size="16"><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="delete">删除评价</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </article>
    </div>

    <div v-if="total > query.size" class="reviews-pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StarRating from '@/components/StarRating.vue'
import { pageMyReviews, deleteReview } from '@/api/review'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const selectedIds = ref([])
const batchDeleting = ref(false)
// 仅本页使用的展示态：编辑模式与长文展开
const editing = ref(false)
const expandedIds = ref([])

const allSelected = computed({
  get: () => records.value.length > 0 && selectedIds.value.length === records.value.length,
  set: (val) => {
    selectedIds.value = val ? records.value.map(r => r.id) : []
  }
})
const isIndeterminate = computed(() => selectedIds.value.length > 0 && selectedIds.value.length < records.value.length)

const query = reactive({ current: 1, size: 10 })

function toggleEditing() {
  editing.value = !editing.value
  if (!editing.value) selectedIds.value = []
}

function toggleExpand(id) {
  const index = expandedIds.value.indexOf(id)
  if (index >= 0) expandedIds.value.splice(index, 1)
  else expandedIds.value.push(id)
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageMyReviews(query)
    records.value = res.data.records
    total.value = res.data.total
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

function onImgError(e, r) {
  e.target.style.display = 'none'
  e.target.nextElementSibling && (e.target.nextElementSibling.style.display = 'flex')
}

function goDish(r) {
  if (r?.dishId) {
    router.push(`/dishes/${r.dishId}`)
  }
}

function handleDelete(row) {
  ElMessageBox.confirm('删除后不可恢复，确定删除这条评价吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteReview(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

async function handleBatchDelete() {
  const ids = selectedIds.value
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 项吗？删除后不可恢复`, '提示', { type: 'warning' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map(id => deleteReview(id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.reviews-page {
  max-width: 1080px;
}

.review-card {
  padding-top: 12px;
  padding-bottom: 12px;
  min-height: 240px;
}

.manage-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 6px 0 14px;
  border-bottom: 1px solid #f0f0f2;
}

.manage-spacer {
  flex: 1;
  min-width: 8px;
}

.selected-hint {
  font-size: 12px;
  color: var(--text-3);
}

.manage-bar .batch-cancel {
  color: #d92d20;
  background: #fff;
  border-color: var(--border-strong);
}

.manage-bar .batch-cancel:hover:not(.is-disabled) {
  color: #b42318;
  border-color: rgba(217, 45, 32, 0.32);
  background: #fff7f6;
}

.review-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 12px;
  margin: 0 -12px;
  border-bottom: 1px solid #f0f0f2;
  border-radius: 12px;
  transition: background 0.16s var(--ease-apple);
}

.review-item:last-of-type {
  border-bottom: none;
}

.review-item:hover {
  background: #f7f7f9;
}

.item-check {
  flex-shrink: 0;
  margin-top: 18px;
}

.item-check :deep(.el-checkbox__inner) {
  width: 18px;
  height: 18px;
  border-radius: 50%;
}

.item-check :deep(.el-checkbox__inner::after) {
  left: 5px;
  top: 2px;
}

.review-thumb {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f7;
  cursor: pointer;
}

.review-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.review-initial {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-3);
}

.review-main {
  flex: 1;
  min-width: 0;
  padding-right: 28px;
}

.review-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.review-dish-name {
  border: none;
  background: transparent;
  padding: 0;
  font-family: inherit;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
  cursor: pointer;
  text-align: left;
}

.review-dish-name:hover {
  color: var(--brand-dark);
}

.review-stall {
  font-size: 12px;
  color: var(--text-3);
}

.review-rating {
  margin-top: 1px;
}

.review-rating :deep(.el-rate) {
  height: 18px;
  line-height: 18px;
}

.review-rating :deep(.el-rate__item) {
  margin-right: 2px;
}

.review-rating :deep(.el-rate__icon) {
  font-size: 15px;
  margin-right: 0;
}

.review-content-wrap {
  margin-top: 6px;
}

.review-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.65;
  color: var(--text-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-content.expanded {
  display: block;
  overflow: visible;
}

.review-expand {
  margin-top: 2px;
  padding: 0;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--brand-dark);
  cursor: pointer;
}

.review-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
}

.review-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-3);
  flex-shrink: 0;
}

.status-dot.is-published {
  background: #34c759;
}

.status-dot.is-pending {
  background: #ff9500;
}

.status-dot.is-blocked {
  background: #8e8e93;
}

.review-more {
  position: absolute;
  top: 12px;
  right: 0;
  opacity: 0;
  transition: opacity 0.16s var(--ease-apple);
}

.review-item:hover .review-more,
.review-item:focus-within .review-more {
  opacity: 1;
}

.more-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s, color 0.16s;
}

.more-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: var(--text-1);
}

.reviews-pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (hover: none) {
  .review-more {
    opacity: 0.6;
  }
}
</style>
