<template>
  <div class="page-container adaptive-page order-manage">
    <div class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">订单</h1>
        <div class="ap-sub">共 {{ total }} 条订单 · 处理取餐与未支付取消</div>
      </div>
    </div>

    <div class="page-card order-card">
      <div class="ap-toolbar order-toolbar">
        <div class="ap-pill-group" role="tablist" aria-label="订单状态">
          <button type="button" class="ap-pill" :class="{ active: !query.status }" @click="setStatus(undefined)">全部</button>
          <button type="button" class="ap-pill" :class="{ active: query.status === 'PENDING' }" @click="setStatus('PENDING')">待取餐</button>
          <button type="button" class="ap-pill" :class="{ active: query.status === 'DONE' }" @click="setStatus('DONE')">已完成</button>
          <button type="button" class="ap-pill" :class="{ active: query.status === 'CANCELED' }" @click="setStatus('CANCELED')">已取消</button>
        </div>
        <el-select v-model="query.stallId" placeholder="全部档口" clearable style="width: 170px" @change="loadData">
          <el-option v-for="s in stalls" :key="s.id" :label="`${s.windowNo}号 ${s.name}`" :value="s.id" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索订单号 / 学生 / 档口" clearable style="width: 220px" @keyup.enter="loadData" />
        <span class="ap-toolbar-spacer"></span>
        <span class="toolbar-count">共 {{ total }} 条</span>
      </div>

      <div ref="wrapRef" class="adaptive-table">
        <el-table :data="records" v-loading="loading">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-box">
                <div v-for="item in row.items || []" :key="item.id" class="expand-item">
                  <div class="expand-thumb">
                    <img v-if="item.dishImage" :src="item.dishImage" :alt="item.dishName" @error="onImgError($event)" />
                    <span v-else>{{ (item.dishName || '菜').slice(0, 1) }}</span>
                  </div>
                  <span class="expand-name">
                    {{ item.dishName }}
                    <span v-if="item.refunded" class="ap-tag ap-tag--red">已退</span>
                  </span>
                  <span class="expand-qty ap-num">¥{{ item.price }} × {{ item.quantity }}</span>
                  <span class="expand-amount ap-num">¥{{ item.amount }}</span>
                </div>
                <div v-if="row.remark" class="expand-remark">备注：{{ row.remark }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="订单 / 学生" min-width="190">
            <template #default="{ row }">
              <div class="order-cell">
                <span class="order-no">{{ row.orderNo }}</span>
                <span class="order-user">{{ row.userName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="档口" min-width="140">
            <template #default="{ row }">
              <span class="stall-name" :title="row.windowNo ? `${row.windowNo} 号窗口` : ''">{{ row.stallName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="取餐时间" width="160">
            <template #default="{ row }"><span class="pickup-time">{{ row.pickupTime }}</span></template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">
              <span class="order-amount ap-num">¥{{ row.totalAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <span
                class="ap-tag"
                :class="{
                  'ap-tag--amber': statusType(row) === 'warning',
                  'ap-tag--green': statusType(row) === 'success',
                  'ap-tag--red': statusType(row) === 'danger'
                }"
              >
                {{ row.statusLabel }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING' && row.payStatus === 'PAID'"
                link
                class="row-action"
                @click="changeStatus(row, 'DONE')"
              >
                标记取餐
              </el-button>
              <el-button
                v-else-if="row.status === 'PENDING' && row.payStatus === 'UNPAID'"
                link
                class="row-action row-action--danger"
                @click="changeStatus(row, 'CANCELED')"
              >
                取消
              </el-button>
              <span v-else class="done-mark">
                <el-icon v-if="row.status === 'DONE'"><Check /></el-icon>
                <template v-else>—</template>
              </span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark"><el-icon :size="20"><Box /></el-icon></div>
              <div class="ap-empty-title">暂无订单</div>
              <div>换个状态、档口或关键词试试</div>
            </div>
          </template>
        </el-table>
      </div>

      <div class="adaptive-pager order-pager">
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
import { ref, reactive, onMounted, nextTick , onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { pageOrders, updateOrderStatus } from '@/api/order'
import { listStalls } from '@/api/stall'
import { useAdaptivePaging } from '@/composables/useAdaptivePaging'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const stalls = ref([])
const wrapRef = ref(null)

const query = reactive({ current: 1, size: 10, status: undefined, stallId: undefined, keyword: '' })

function onImgError(e) {
  e.target.style.display = 'none'
  const parent = e.target.parentElement
  const span = document.createElement('span')
  span.textContent = '菜'
  parent.appendChild(span)
}

function statusType(row) {
  // 未支付却停在 PENDING：实际是「待支付」，用灰色；已支付才是「待取餐」橙色
  if (row.status === 'PENDING' && row.payStatus !== 'PAID') return 'info'
  return { PENDING: 'warning', DONE: 'success', CANCELED: 'info', REFUNDED: 'danger' }[row.status] || 'info'
}

function setStatus(value) {
  if (query.status === value) return
  query.status = value
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageOrders(query)
    records.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const { bind } = useAdaptivePaging(query, loadData)

async function changeStatus(row, status) {
  await updateOrderStatus(row.id, status)
  ElMessage.success(status === 'DONE' ? '已标记为取餐完成' : '订单已取消')
  loadData()
}

onMounted(async () => {
  const res = await listStalls()
  stalls.value = res.data
  await loadData()
  await nextTick()
  bind(wrapRef.value)
})
onActivated(loadData)
</script>

<style scoped>
.order-manage {
  width: 100%;
}

.order-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.order-toolbar {
  flex-shrink: 0;
}

.toolbar-count {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.order-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.order-no {
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-user {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

.stall-name {
  font-size: 13.5px;
  color: var(--text-1);
}

.pickup-time {
  font-size: 12.5px;
  color: var(--text-2);
}

.order-amount {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.done-mark {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  color: var(--text-3);
  font-size: 13px;
}

.done-mark .el-icon {
  color: #34c759;
}

.row-action {
  color: var(--text-2);
  font-size: 13px;
}

.row-action:hover {
  color: var(--text-1) !important;
  background: rgba(0, 0, 0, 0.04);
}

.row-action--danger:hover {
  color: #c9342b !important;
}

.order-pager {
  justify-content: flex-end;
  flex-shrink: 0;
}

/* 展开行：浅灰 inset 面板 */
:deep(.el-table__expanded-cell) {
  background: transparent !important;
  padding: 4px 16px 14px !important;
}

.expand-box {
  background: var(--surface-2);
  border-radius: 12px;
  padding: 10px 14px;
}

.expand-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  padding: 6px 0;
}

.expand-thumb {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f7;
  color: var(--text-3);
  font-size: 16px;
  font-weight: 600;
}

.expand-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.expand-name {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: var(--text-1);
}

.expand-qty {
  font-size: 12.5px;
  color: var(--text-3);
  white-space: nowrap;
}

.expand-amount {
  color: var(--text-1);
  font-weight: 600;
  min-width: 70px;
  text-align: right;
}

.expand-remark {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-3);
}

:deep(.el-table__row:hover > td.el-table__cell) {
  background: #f5f5f7 !important;
}
</style>
