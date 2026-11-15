<template>
  <div class="page-container adaptive-page orders-page">
    <div class="ap-head orders-head">
      <div>
        <h1 class="ap-title">订单处理</h1>
        <div class="ap-sub">工作队列 · 当前筛选共 {{ total }} 单，已支付订单可直接出餐</div>
      </div>
      <div class="ap-actions orders-actions">
        <div class="ap-pill-group">
          <button class="ap-pill" :class="{ active: query.status === '' }" @click="query.status = ''; query.current = 1; loadData()">全部</button>
          <button class="ap-pill" :class="{ active: query.status === 'PENDING' }" @click="query.status = 'PENDING'; query.current = 1; loadData()">待处理</button>
          <button class="ap-pill" :class="{ active: query.status === 'DONE' }" @click="query.status = 'DONE'; query.current = 1; loadData()">已完成</button>
          <button class="ap-pill" :class="{ active: query.status === 'CANCELED' }" @click="query.status = 'CANCELED'; query.current = 1; loadData()">已取消</button>
        </div>
        <button class="ap-btn ap-btn--light" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </button>
      </div>
    </div>

    <el-card shadow="never" class="page-card orders-card">
      <div ref="wrapRef" class="adaptive-table">
        <el-table :data="records" v-loading="loading" :row-class-name="rowClass" class="orders-table">
          <!-- 展开行：显示订单内每道菜的图片与明细 -->
          <el-table-column type="expand" width="36">
            <template #default="{ row }">
              <div class="order-expand">
                <div v-for="it in row.items" :key="it.id" class="oe-item">
                  <div class="oe-thumb">
                    <span class="oe-initial">{{ (it.dishName || '菜').slice(0, 1) }}</span>
                    <img v-if="it.dishImage" :src="it.dishImage" alt="" @error="$event.target.style.display='none'" />
                  </div>
                  <div class="oe-name">
                    {{ it.dishName }}
                    <span v-if="it.refunded" class="ap-tag ap-tag--red">已退</span>
                  </div>
                  <div class="oe-spec ap-num">¥{{ num(it.price) }} × {{ it.quantity }}</div>
                  <div class="oe-amount ap-num">¥{{ num(it.amount) }}</div>
                </div>
                <div v-if="row.remark" class="oe-remark">备注：{{ row.remark }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="订单号" width="170">
            <template #default="{ row }"><span class="order-no ap-num">{{ row.orderNo }}</span></template>
          </el-table-column>
          <el-table-column label="菜品" min-width="230">
            <template #default="{ row }">
              <div class="dish-cell">
                <div class="dish-thumb">
                  <span>{{ ((row.items && row.items[0] && row.items[0].dishName) || '菜').slice(0, 1) }}</span>
                  <img
                    v-if="row.items && row.items[0] && row.items[0].dishImage"
                    :src="row.items[0].dishImage"
                    alt=""
                    @error="$event.target.style.display='none'"
                  />
                </div>
                <div class="dish-main">
                  <div class="dish-name">{{ summaryText(row) }}</div>
                  <div class="dish-sub">{{ row.remark ? '备注：' + row.remark : '展开左侧查看菜品明细' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="userName" label="下单人" width="96" />
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }"><span class="amount ap-num">¥{{ num(row.totalAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="取餐时间" width="150" align="right">
            <template #default="{ row }"><span class="pickup-time ap-num">{{ row.pickupTime }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <div class="status-cell">
                <div class="status-main">
                  <i class="status-dot" :class="statusType(row)"></i>
                  <span>{{ row.statusLabel }}</span>
                  <span v-if="row.partialRefund" class="status-extra">含退款</span>
                </div>
                <div class="status-sub">{{ row.payStatusLabel }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button v-if="row.payStatus === 'PAID'" type="primary" size="small" @click="setStatus(row, 'DONE')">出餐完成</el-button>
                <button v-else class="ap-link danger" @click="setStatus(row, 'CANCELED')">取消订单</button>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="adaptive-pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick , onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageStallOrders, updateOrderStatus } from '@/api/order'
import { useAdaptivePaging } from '@/composables/useAdaptivePaging'

const route = useRoute()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const wrapRef = ref(null)
const query = reactive({ current: 1, size: 10, status: '' })
const highlightId = ref(null)

/** 待办跳转时按订单 id 高亮定位 */
function rowClass({ row }) {
  return row.id === highlightId.value ? 'todo-highlight' : ''
}

function statusType(row) {
  // 部分退款用红色强调
  if (row.partialRefund) return 'danger'
  // 未支付却停在 PENDING：实际是「待支付」，用灰色；已支付才是「待取餐」橙色
  if (row.status === 'PENDING' && row.payStatus !== 'PAID') return 'info'
  return row.status === 'DONE' ? 'success' : row.status === 'CANCELED' ? 'info' : 'warning'
}

function num(v) {
  return v == null ? '—' : Number(v).toFixed(2)
}

function summaryText(row) {
  const items = row.items || []
  if (!items.length) return '—'
  const first = items[0].dishName
  const count = items.reduce((s, it) => s + (it.quantity || 0), 0)
  return items.length > 1 ? `${first} 等 ${count} 件` : `${first} × ${items[0].quantity}`
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageStallOrders(query)
    records.value = res.data.records || []
    total.value = res.data.total || 0
    // 待办跳转定位：读 ?highlight=订单id
    highlightId.value = route.query.highlight ? Number(route.query.highlight) : null
  } finally {
    loading.value = false
  }
}

/** 待办跳转：?status=PENDING 直接筛到「待取餐」，聚焦待出餐订单 */
function applyQueryFilter() {
  const s = route.query.status
  if (s === 'PENDING' || s === 'DONE' || s === 'CANCELED') {
    query.status = s
    query.current = 1
  }
}

const { bind } = useAdaptivePaging(query, loadData)

function setStatus(row, status) {
  const tip = status === 'DONE' ? '确认该订单已出餐完成？' : '确定取消该订单？'
  ElMessageBox.confirm(tip, '提示', { type: 'warning' })
    .then(async () => {
      await updateOrderStatus(row.id, status)
      ElMessage.success('已更新')
      loadData()
    })
    .catch(() => {})
}

onMounted(async () => {
  applyQueryFilter()
  await loadData()
  await nextTick()
  bind(wrapRef.value)
})
onActivated(() => {
  applyQueryFilter()
  loadData()
})
</script>

<style scoped>
.orders-page {
  max-width: 1480px;
  margin: 0 auto;
}

.orders-head {
  flex-shrink: 0;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.orders-actions { flex-wrap: wrap; }

.orders-card :deep(.el-card__body) {
  padding: 8px 10px 14px;
}

.orders-table :deep(.el-table__header th.el-table__cell) {
  background: #fafafa;
}

.orders-table :deep(.el-table__cell) {
  border-bottom-color: rgba(0, 0, 0, 0.045);
}

.orders-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #fafafa;
}

.muted { color: var(--text-3); }

.ap-link.danger { color: #c9342b; }
.ap-link.danger:hover { opacity: 0.7; }

.order-no {
  font-size: 13px;
  color: var(--text-2);
}

.dish-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.dish-thumb {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #f2f2f5;
  color: #86868b;
  overflow: hidden;
  flex-shrink: 0;
}

.dish-thumb span {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.dish-thumb img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dish-main { min-width: 0; }

.dish-name {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amount {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

.pickup-time {
  font-size: 12.5px;
  color: var(--text-2);
}

.status-cell { display: flex; flex-direction: column; gap: 3px; }

.status-main {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-1);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--text-3);
}

.status-dot.warning { background: var(--brand); }
.status-dot.success { background: #1d9d55; }
.status-dot.danger { background: #d92d20; }
.status-dot.info { background: #a1a1a6; }

.status-extra {
  font-size: 10.5px;
  font-weight: 550;
  color: #c9342b;
}

.status-sub {
  padding-left: 13px;
  font-size: 11px;
  color: var(--text-3);
}

/* 展开的订单明细 */
.order-expand {
  padding: 8px 10px 8px 50px;
}

.oe-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 9px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.oe-item:last-of-type { border-bottom: none; }

.oe-thumb {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #f2f2f5;
  color: #86868b;
  overflow: hidden;
  flex-shrink: 0;
}

.oe-thumb .oe-initial {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.oe-thumb img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.oe-name {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: var(--text-1);
}

.oe-spec {
  width: 140px;
  color: var(--text-3);
  font-size: 13px;
}

.oe-amount {
  width: 90px;
  text-align: right;
  font-weight: 600;
  color: var(--text-1);
}

.oe-remark {
  padding: 8px 0 2px;
  font-size: 12px;
  color: var(--text-3);
}
</style>