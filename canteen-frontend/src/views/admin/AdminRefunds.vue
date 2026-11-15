<template>
  <div class="page-container adaptive-page refund-admin">
    <div class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">退款记录</h1>
        <div class="ap-sub">退款由商家处理，平台仅留档查询 · 共 {{ records.length }} 条</div>
      </div>
      <div class="ap-actions refund-actions">
        <el-button @click="loadData">刷新</el-button>
      </div>
    </div>

    <div class="page-card refund-card">
      <div class="ap-toolbar refund-toolbar">
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px" @change="loadData">
          <el-option label="待商家处理" value="PENDING" />
          <el-option label="已退款" value="REFUNDED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="退款失败" value="FAILED" />
        </el-select>
        <span class="ap-toolbar-spacer"></span>
      </div>

      <div class="ap-grid ap-grid--4 refund-stats">
        <div class="ap-kpi refund-stat">
          <div class="stat-head"><i class="stat-dot pending"></i><span class="stat-label">待商家处理</span></div>
          <div class="stat-num ap-num">{{ stats.pending }}</div>
        </div>
        <div class="ap-kpi refund-stat">
          <div class="stat-head"><i class="stat-dot refunded"></i><span class="stat-label">已退款</span></div>
          <div class="stat-num ap-num">{{ stats.refunded }}</div>
        </div>
        <div class="ap-kpi refund-stat">
          <div class="stat-head"><i class="stat-dot rejected"></i><span class="stat-label">已驳回</span></div>
          <div class="stat-num ap-num">{{ stats.rejected }}</div>
        </div>
        <div class="ap-kpi refund-stat">
          <div class="stat-head"><i class="stat-dot failed"></i><span class="stat-label">退款失败</span></div>
          <div class="stat-num ap-num">{{ stats.failed }}</div>
        </div>
      </div>

      <div class="adaptive-table">
        <el-table :data="records" v-loading="loading" row-key="id">
          <el-table-column prop="orderNo" label="订单号" width="180" />
          <el-table-column prop="stallName" label="档口" width="120" />
          <el-table-column prop="userName" label="学生" width="100" />
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">
              <span class="refund-amount ap-num" :class="{ 'is-pending': row.status === 'PENDING' }">¥{{ row.amount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="退款菜品" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.itemSummary || '整单' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <span
                class="ap-tag"
                :class="{
                  'ap-tag--amber': statusType(row.status) === 'warning',
                  'ap-tag--green': statusType(row.status) === 'success',
                  'ap-tag--red': statusType(row.status) === 'danger'
                }"
              >
                {{ row.statusLabel }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="140">
            <template #default="{ row }">{{ short(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="处理记录" min-width="200">
            <template #default="{ row }">
              <span v-if="row.status === 'REFUNDED'" class="record-text">退款单号：{{ row.refundNo || '—' }}</span>
              <span v-else-if="row.status === 'FAILED'" class="record-text record-fail">{{ row.failReason || '退款失败' }}</span>
              <span v-else-if="row.status === 'REJECTED'" class="record-text">{{ row.remark || '商家已驳回' }}</span>
              <span v-else class="record-text">等待商家处理</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark"><el-icon :size="20"><Money /></el-icon></div>
              <div class="ap-empty-title">暂无退款记录</div>
              <div>换个状态筛选试试</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { adminRefunds } from '@/api/refund'
import { useFlashLocate } from '@/composables/useFlashLocate'

const route = useRoute()
const loading = ref(false)
const records = ref([])
const status = ref('')

const stats = computed(() => {
  const result = { pending: 0, refunded: 0, rejected: 0, failed: 0 }
  for (const r of records.value) {
    if (r.status === 'PENDING') result.pending += 1
    else if (r.status === 'REFUNDED') result.refunded += 1
    else if (r.status === 'REJECTED') result.rejected += 1
    else if (r.status === 'FAILED') result.failed += 1
  }
  return result
})

function short(t) {
  return t ? String(t).slice(0, 16) : ''
}

function statusType(s) {
  return { PENDING: 'warning', REFUNDED: 'success', REJECTED: 'info', FAILED: 'danger' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminRefunds(status.value || undefined)
    records.value = res.data || []
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

// 工作台退款敏感项跳转定位：/admin/refunds?refer=REFUND&id=xx
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
    if (found || route.query.refer !== 'REFUND') return
    // 数据未加载完：清状态重新加载后定位
    status.value = ''
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
onActivated(loadData)
</script>

<style scoped>
.refund-admin {
  width: 100%;
}

.refund-actions {
  gap: 10px;
}

.refund-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.refund-toolbar {
  flex-shrink: 0;
}

.refund-stats {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.refund-stat {
  padding: 15px 18px 14px;
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.stat-label {
  margin-bottom: 0;
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.stat-dot.pending { background: #ff9500; }
.stat-dot.refunded { background: #34c759; }
.stat-dot.rejected { background: #8e8e93; }
.stat-dot.failed { background: #ff3b30; }

.stat-num {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--text-1);
}

.refund-amount {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.refund-amount.is-pending {
  color: var(--brand-dark);
}

.record-text {
  font-size: 12px;
  color: var(--text-3);
}

.record-fail {
  color: #c9342b;
}

:deep(.el-table__row:hover > td.el-table__cell) {
  background: #f5f5f7 !important;
}
</style>
