<template>
  <div class="page-container adaptive-page refund-page">
    <div class="ap-head refund-head">
      <div>
        <h1 class="ap-title">退款处理</h1>
        <div class="ap-sub">
          {{ pendingCount ? `待审核 ${pendingCount} 笔 · 合计 ¥${pendingAmount}` : '当前筛选下没有待审核的退款' }}
          <span class="refund-rule">退款走原支付通道原路退回，失败会记录原因</span>
        </div>
      </div>
      <div class="ap-actions">
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 132px" @change="loadData">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已退款" value="REFUNDED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="退款失败" value="FAILED" />
        </el-select>
        <button class="ap-btn ap-btn--light ap-btn--icon" title="刷新" @click="loadData">
          <el-icon :size="16"><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <el-card shadow="never" class="page-card refund-card">
      <div class="adaptive-table">
      <el-table :data="records" v-loading="loading" :row-class-name="rowClass" class="refund-table">
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="amount" :class="{ pending: row.status === 'PENDING' }">¥{{ row.amount }}</span>
            <i v-if="row.status === 'PENDING'" class="amount-dot"></i>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="退款原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="退款菜品" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemSummary || '整单' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status-cell"><i class="status-dot" :class="row.status?.toLowerCase()"></i>{{ row.statusLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ short(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="处理信息" min-width="180">
          <template #default="{ row }">
            <span v-if="row.status === 'REFUNDED'" class="muted">退款单号：{{ row.refundNo || '—' }}</span>
            <span v-else-if="row.status === 'FAILED'" class="fail">{{ row.failReason }}</span>
            <span v-else-if="row.status === 'REJECTED'" class="muted">{{ row.remark || '已驳回' }}</span>
            <span v-else class="muted">待处理</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="approve(row)">同意退款</el-button>
              <el-button link @click="reject(row)">驳回</el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!records.length && !loading" class="ap-empty refund-empty">
        <div class="ap-empty-mark"><el-icon :size="19"><Wallet /></el-icon></div>
        <div class="ap-empty-title">没有符合条件的退款</div>
        <div>当前筛选下暂无退款申请</div>
      </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantRefunds, approveMerchantRefund, rejectMerchantRefund } from '@/api/refund'

const route = useRoute()
const loading = ref(false)
const records = ref([])
const status = ref('PENDING')
const highlightId = ref(null)

// 页头摘要：只统计待审核的笔数与金额
const pendingList = computed(() => (status.value === 'PENDING' || !status.value) ? records.value.filter(r => r.status === 'PENDING') : [])
const pendingCount = computed(() => pendingList.value.length)
const pendingAmount = computed(() => pendingList.value.reduce((s, r) => s + (Number(r.amount) || 0), 0).toFixed(2))

function rowClass({ row }) {
  return row.id === highlightId.value ? 'todo-highlight' : ''
}

function short(t) {
  return t ? String(t).slice(0, 16) : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await merchantRefunds(status.value || undefined)
    records.value = res.data || []
    highlightId.value = route.query.highlight ? Number(route.query.highlight) : null
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

function approve(row) {
  ElMessageBox.confirm(
    `将按原支付通道退款 ¥${row.amount}，确定同意吗？`,
    '同意退款',
    { type: 'warning' }
  )
    .then(async () => {
      const res = await approveMerchantRefund(row.id)
      if (res.data?.status === 'REFUNDED') {
        ElMessage.success('退款成功')
      } else {
        ElMessage.error(res.data?.failReason || '退款失败，请查看原因')
      }
      loadData()
    })
    .catch(() => {})
}

function reject(row) {
  ElMessageBox.prompt('请填写驳回原因（会展示给学生）', '驳回退款', {
    inputPlaceholder: '例如：餐品已制作完成，无法退款',
    inputValidator: v => (v && v.trim() ? true : '请填写原因')
  })
    .then(async ({ value }) => {
      await rejectMerchantRefund(row.id, value.trim())
      ElMessage.success('已驳回')
      loadData()
    })
    .catch(() => {})
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.refund-page { width: 100%; }

.refund-head { margin-bottom: 16px; }
.refund-rule { color: var(--text-3); }

.refund-card { padding: 6px 8px; }

.refund-table { font-size: 13.5px; }

.amount {
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
  color: var(--text-1);
  font-size: 14.5px;
}
.amount-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  margin-left: 6px;
  vertical-align: 2px;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--text-1);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--text-3);
}
.status-dot.pending { background: #ff9500; }
.status-dot.refunded { background: #34c759; }
.status-dot.rejected { background: #c7c7cc; }
.status-dot.failed { background: #ff3b30; }

.muted { color: var(--text-3); font-size: 12px; }
.fail {
  font-size: 12px;
  color: var(--text-2);
  background: #f5f5f7;
  padding: 3px 8px;
  border-radius: 8px;
  display: inline-block;
  line-height: 1.5;
}

.refund-empty { padding: 48px 20px; }
</style>
