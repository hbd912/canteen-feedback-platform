<template>
  <div class="ap-page dish-audit-page">
    <header class="ap-head audit-head">
      <div class="head-copy">
        <h1 class="ap-title">菜品审核</h1>
        <p class="ap-sub">商家提交的菜品，审核通过后才会出现在学生端；审核结果会通知对应商家。</p>
      </div>
      <div class="head-tools">
        <div class="ap-pill-group" role="tablist" aria-label="审核状态筛选">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            type="button"
            role="tab"
            class="ap-pill"
            :class="{ active: status === tab.value }"
            :aria-selected="status === tab.value"
            @click="switchTab(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
        <button
          type="button"
          class="ap-btn ap-btn--light ap-btn--sm"
          :disabled="loading"
          @click="loadData"
        >
          <el-icon :size="14"><Refresh /></el-icon>
          刷新
        </button>
      </div>
    </header>

    <section class="ap-card audit-card">
      <div class="card-toolbar">
        <div class="toolbar-main">
          <span class="toolbar-title">{{ currentTabLabel }}</span>
          <span class="toolbar-count">{{ total }} 条</span>
        </div>
      </div>

      <div class="table-wrap">
        <el-table
          v-loading="loading"
          :data="records"
          row-key="id"
          class="audit-table"
          table-layout="fixed"
        >
          <el-table-column label="图片" width="76">
            <template #default="{ row }">
              <el-image
                v-if="row.image"
                :src="row.image"
                fit="cover"
                class="dish-thumb"
                :preview-src-list="[row.image]"
                preview-teleported
              />
              <span v-else class="dish-thumb dish-thumb-fallback">{{ initial(row.name) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="菜品名" min-width="170">
            <template #default="{ row }">
              <div class="dish-name">{{ row.name }}</div>
              <div class="dish-desc">{{ row.description || '暂无描述' }}</div>
            </template>
          </el-table-column>

          <el-table-column label="档口" min-width="130">
            <template #default="{ row }">
              <span class="stall-name">{{ row.stallName || '未关联档口' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="价格" width="100" align="right">
            <template #default="{ row }">
              <span class="price">{{ priceText(row.price) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="分类" width="100">
            <template #default="{ row }">
              <span class="category">{{ row.category || '未分类' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="150">
            <template #default="{ row }">
              <span class="time-text">{{ formatTime(row.updateTime || row.createTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="审核" width="200">
            <template #default="{ row }">
              <div class="audit-state">
                <span class="audit-badge" :class="badgeClass(row.auditStatus)">
                  {{ auditLabel(row.auditStatus) }}
                </span>
                <span class="audit-time">
                  {{
                    row.auditStatus === 'PENDING'
                      ? '等待审核'
                      : formatTime(row.auditTime) || '—'
                  }}
                </span>
              </div>
              <div v-if="row.auditStatus === 'REJECTED'" class="audit-note">
                {{ row.auditRemark || '未填写驳回原因' }}
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <div v-if="row.auditStatus === 'PENDING'" class="row-actions">
                <button
                  type="button"
                  class="ap-btn ap-btn--sm"
                  :disabled="actingId === row.id"
                  @click="approve(row)"
                >
                  {{ actingId === row.id && actingType === 'approve' ? '处理中…' : '通过' }}
                </button>
                <button
                  type="button"
                  class="ap-btn ap-btn--ghost ap-btn--sm reject-text"
                  :disabled="actingId === row.id"
                  @click="openReject(row)"
                >
                  驳回
                </button>
              </div>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark">
                <el-icon :size="20"><Document /></el-icon>
              </div>
              <div class="ap-empty-title">{{ loadError ? '加载失败' : emptyTitle }}</div>
              <div>{{ loadError ? '网络异常或服务暂不可用，请稍后重试' : emptyHint }}</div>
              <button
                v-if="loadError"
                type="button"
                class="ap-btn ap-btn--light ap-btn--sm empty-action"
                @click="loadData"
              >
                重新加载
              </button>
            </div>
          </template>
        </el-table>
      </div>

      <div v-if="total > 0" class="pager">
        <el-pagination
          v-model:current-page="current"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadData"
        />
      </div>
    </section>

    <el-dialog
      v-model="rejectVisible"
      title="驳回菜品"
      width="480px"
      align-center
      append-to-body
      :close-on-click-modal="!rejectLoading"
      :close-on-press-escape="!rejectLoading"
      :show-close="!rejectLoading"
    >
      <div class="reject-body">
        <div class="reject-target">
          <span class="reject-thumb">
            <el-image
              v-if="rejectTarget?.image"
              :src="rejectTarget.image"
              fit="cover"
              class="reject-thumb-img"
            />
            <span v-else class="reject-thumb-initial">{{ initial(rejectTarget?.name) }}</span>
          </span>
          <div class="reject-meta">
            <div class="reject-name">{{ rejectTarget?.name }}</div>
            <div class="reject-sub">
              {{ rejectTarget?.stallName || '未关联档口' }} · {{ priceText(rejectTarget?.price) }}
            </div>
          </div>
        </div>
        <div class="reject-field">
          <div class="reject-field-label">驳回原因</div>
          <el-input
            ref="reasonInputRef"
            v-model="rejectReason"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="请说明驳回原因，例如：图片不清晰、描述与实际不符"
          />
        </div>
        <p class="reject-tip">驳回后菜品保持下架，原因会通知商家。</p>
      </div>
      <template #footer>
        <button
          type="button"
          class="ap-btn ap-btn--light"
          :disabled="rejectLoading"
          @click="rejectVisible = false"
        >
          取消
        </button>
        <button
          type="button"
          class="ap-btn"
          :disabled="rejectLoading"
          @click="submitReject"
        >
          {{ rejectLoading ? '提交中…' : '确认驳回' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDishAudits, approveDishAudit, rejectDishAudit } from '@/api/dishAudit'
import { useFlashLocate } from '@/composables/useFlashLocate'

const route = useRoute()

const tabs = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' }
]

const status = ref('PENDING')
const records = ref([])
const total = ref(0)
const current = ref(1)
const size = 10
const loading = ref(false)
const loadError = ref(false)
const actingId = ref(null)
const actingType = ref('')
const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')
const reasonInputRef = ref()
let skipNextActivation = true

const currentTabLabel = computed(
  () => tabs.find(tab => tab.value === status.value)?.label || '全部'
)
const emptyTitle = computed(() => {
  if (status.value === 'PENDING') return '暂无待审核菜品'
  if (status.value === 'APPROVED') return '暂无已通过菜品'
  return '暂无已驳回菜品'
})
const emptyHint = computed(() =>
  status.value === 'PENDING'
    ? '商家提交的新菜品会出现在这里'
    : '审核记录会保留在这里'
)

function statusParams() {
  return {
    status: status.value,
    current: current.value,
    size
  }
}

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const res = await listDishAudits(statusParams())
    const data = res.data || {}
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch {
    // 请求层已统一提示，这里保留可重试的空态
    records.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function switchTab(value) {
  if (status.value === value) return
  status.value = value
  current.value = 1
  loadData()
}

function approve(row) {
  ElMessageBox.confirm(
    `审核通过后「${row.name}」会立即上架到学生端，确定通过吗？`,
    '通过审核',
    {
      type: 'warning',
      confirmButtonText: '通过并上架',
      cancelButtonText: '取消'
    }
  )
    .then(async () => {
      actingId.value = row.id
      actingType.value = 'approve'
      try {
        await approveDishAudit(row.id)
        ElMessage.success(`「${row.name}」已通过并上架`)
        await loadData()
      } finally {
        actingId.value = null
        actingType.value = ''
      }
    })
    .catch(() => {})
}

function openReject(row) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
  nextTick(() => reasonInputRef.value?.focus())
}

async function submitReject() {
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  if (reason.length > 300) {
    ElMessage.warning('驳回原因不能超过300字')
    return
  }
  rejectLoading.value = true
  try {
    await rejectDishAudit(rejectTarget.value.id, reason)
    ElMessage.success(`已驳回「${rejectTarget.value.name}」`)
    rejectVisible.value = false
    await loadData()
  } finally {
    rejectLoading.value = false
  }
}

function auditLabel(value) {
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[value] || value || '—'
}

function badgeClass(value) {
  return {
    'is-pending': value === 'PENDING',
    'is-approved': value === 'APPROVED',
    'is-rejected': value === 'REJECTED'
  }
}

function initial(name) {
  return (name || '菜').slice(0, 1)
}

function priceText(value) {
  const number = Number(value)
  return Number.isFinite(number) ? `¥${number.toFixed(2)}` : '—'
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

// 值班助理「菜品待审核」定位：/admin/dish-audits?refer=DISH_AUDIT&id=xx
const { locate } = useFlashLocate(route, (rid) => {
  const idx = records.value.findIndex((r) => r.id === rid)
  if (idx < 0) return null
  const rows = [...document.querySelectorAll('.el-table__row')]
  return rows[idx] || null
}, {
  refer: route.query.refer,
  id: route.query.id,
  getRecId: (item) => item.id,
  onFound: async (rid, found) => {
    if (found || route.query.refer !== 'DISH_AUDIT') return
    status.value = 'PENDING'
    current.value = 1
    await loadData()
    await nextTick()
    const idx = records.value.findIndex((r) => r.id === rid)
    if (idx < 0) return
    const row = [...document.querySelectorAll('.el-table__row')][idx]
    if (row) {
      row.scrollIntoView({ behavior: 'smooth', block: 'center' })
      row.classList.add('flash-target')
      setTimeout(() => row.classList.remove('flash-target'), 2400)
    }
  }
})

onMounted(() => {
  loadData().then(() => nextTick(locate))
})
onActivated(() => {
  if (skipNextActivation) {
    skipNextActivation = false
    return
  }
  loadData()
})
</script>

<style scoped>
.dish-audit-page {
  width: 100%;
  min-height: calc(100vh - 64px);
}

.audit-head {
  align-items: flex-end;
}

.head-copy {
  min-width: 0;
}

.head-copy .ap-sub {
  margin-bottom: 0;
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.audit-card {
  padding: 0;
  overflow: hidden;
  border-radius: 22px;
}

.card-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.toolbar-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.toolbar-count {
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.table-wrap {
  padding: 0 8px;
}

.audit-table {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
  --el-table-text-color: var(--text-1);
  --el-table-header-text-color: var(--text-2);
  width: 100%;
}

.audit-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.audit-table :deep(th.el-table__cell) {
  padding: 12px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.audit-table :deep(td.el-table__cell) {
  padding: 13px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  vertical-align: middle;
}

.audit-table :deep(.el-table__row:last-child td.el-table__cell) {
  border-bottom: none;
}

.audit-table :deep(.cell) {
  padding: 0 12px;
}

.dish-thumb {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f7;
  border: 1px solid rgba(0, 0, 0, 0.04);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dish-thumb-fallback {
  color: var(--text-2);
  font-size: 16px;
  font-weight: 650;
}

.dish-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.35;
}

.dish-desc {
  margin-top: 3px;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-3);
}

.stall-name {
  font-size: 13px;
  color: var(--text-2);
}

.price {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.category {
  display: inline-block;
  padding: 3px 9px;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 12px;
  white-space: nowrap;
}

.time-text {
  font-size: 12px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.audit-state {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.audit-badge {
  display: inline-flex;
  align-items: center;
  height: 23px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}

.audit-badge.is-pending {
  background: #fff5e8;
  color: #a96100;
}

.audit-badge.is-approved {
  background: #e9f8ee;
  color: #1d8a45;
}

.audit-badge.is-rejected {
  background: #ffeceb;
  color: #c9342b;
}

.audit-time {
  font-size: 11.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.audit-note {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-2);
  line-height: 1.45;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.muted {
  color: var(--text-3);
  font-size: 12.5px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.reject-text {
  color: #c9342b;
}

.reject-text:hover {
  background: #ffeceb;
  color: #c9342b;
}

.ap-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ap-btn--ghost:disabled:hover {
  background: transparent;
}

.empty-action {
  margin-top: 14px;
}

.pager {
  display: flex;
  justify-content: center;
  padding: 14px 24px 18px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

.pager :deep(.el-pagination.is-background .el-pager li),
.pager :deep(.el-pagination.is-background .btn-prev),
.pager :deep(.el-pagination.is-background .btn-next) {
  border-radius: 8px;
  background: #f5f5f7;
}

.pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: #1d1d1f;
}

.reject-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.reject-target {
  display: flex;
  align-items: center;
  gap: 14px;
}

.reject-thumb {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.reject-thumb-img {
  width: 100%;
  height: 100%;
}

.reject-thumb-initial {
  font-size: 18px;
  font-weight: 650;
  color: var(--text-2);
}

.reject-meta {
  min-width: 0;
}

.reject-name {
  font-size: 15px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.reject-sub {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.reject-field-label {
  margin-bottom: 7px;
  font-size: 12.5px;
  color: var(--text-2);
}

.reject-tip {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 760px) {
  .audit-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-tools {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
