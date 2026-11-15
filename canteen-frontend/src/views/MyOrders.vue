<template>
  <div class="ap-page orders-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">我的订单</h1>
        <div class="ap-sub">
          共 {{ total }} 笔订单<span v-if="records.length"> · 本页 {{ records.filter(o => o.status === 'PENDING' && o.payStatus === 'PAID').length }} 笔待取餐</span>
        </div>
      </div>
    </div>

    <div class="orders-filter">
      <div class="ap-pill-group">
        <button class="ap-pill" :class="{ active: !query.status }" @click="query.status = undefined; loadData()">全部</button>
        <button class="ap-pill" :class="{ active: query.status === 'PENDING' }" @click="query.status = 'PENDING'; loadData()">待取餐</button>
        <button class="ap-pill" :class="{ active: query.status === 'DONE' }" @click="query.status = 'DONE'; loadData()">已完成</button>
        <button class="ap-pill" :class="{ active: query.status === 'CANCELED' }" @click="query.status = 'CANCELED'; loadData()">已取消</button>
        <button class="ap-pill" :class="{ active: query.status === 'REFUNDED' }" @click="query.status = 'REFUNDED'; loadData()">已退款</button>
      </div>
      <div class="filter-right">
        <el-select v-model="query.payStatus" placeholder="支付状态" clearable style="width: 136px" @change="loadData">
          <el-option label="未支付" value="UNPAID" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <div v-loading="loading" class="orders-list">
      <div v-if="!records.length && !loading" class="ap-card ap-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><Box /></el-icon></div>
        <div class="ap-empty-title">还没有订单</div>
        <div>去菜品页挑点喜欢的，下单后可以在这里查看取餐进度</div>
        <button class="ap-btn empty-action" @click="router.push('/dishes')">去点餐</button>
      </div>

      <article v-for="order in records" :key="order.id" class="order-card">
        <div class="order-head">
          <div class="order-head-main">
            <div class="order-stall">{{ order.stallName }}</div>
            <div class="order-meta">
              {{ order.windowNo }} 号窗口 · {{ order.createTime }} 下单 · 单号 {{ order.orderNo }}
            </div>
          </div>
          <el-tag size="small" effect="light" :type="statusType(order)">{{ order.statusLabel }}</el-tag>
        </div>

        <div
          v-if="order.payStatusLabel || order.reviewStatusLabel || (order.refundStatus && order.refundStatus !== 'NONE')"
          class="order-progress"
        >
          <span v-if="order.payStatusLabel && order.status !== 'REFUNDED'">{{ order.payStatusLabel }}</span>
          <span v-if="order.reviewStatusLabel">{{ order.reviewStatusLabel }}</span>
          <span v-if="order.refundStatus && order.refundStatus !== 'NONE' && order.status !== 'REFUNDED'">
            {{ refundLabel(order.refundStatus) }}
          </span>
        </div>

        <div
          class="order-pickup"
          :class="{ 'is-dim': !(order.status === 'PENDING' && order.payStatus === 'PAID') }"
        >
          <el-icon :size="14"><Clock /></el-icon>
          <span class="pickup-label">取餐时间</span>
          <b>{{ order.pickupTime }}</b>
        </div>

        <div v-if="order.remark" class="order-remark">
          <el-icon :size="13"><ChatLineSquare /></el-icon>
          <span>{{ order.remark }}</span>
        </div>

        <div class="order-items ap-list">
          <div v-for="item in order.items || []" :key="item.id" class="order-item" @click="goDish(item)">
            <div class="order-item-thumb" :style="{ background: coverGradient(item.category) }">
              <img v-if="item.dishImage && !item.imgBroken" :src="item.dishImage" :alt="item.dishName" @error="item.imgBroken = true">
              <span v-else class="order-item-initial">{{ (item.dishName || '菜').slice(0, 1) }}</span>
            </div>
            <div class="order-item-info">
              <div class="order-item-name">
                {{ item.dishName }}
                <span v-if="item.refunded" class="ap-tag ap-tag--red">已退</span>
              </div>
              <div v-if="item.quantity > 1" class="order-item-sub">¥{{ item.price }} × {{ item.quantity }}</div>
            </div>
            <span class="order-item-amount">¥{{ item.amount }}</span>
          </div>
        </div>

        <div class="order-foot">
          <div class="order-total-wrap">
            <span class="order-total-label">合计</span>
            <span class="order-total">¥{{ order.totalAmount }}</span>
          </div>
          <div class="order-actions">
            <el-button v-if="order.payStatus === 'UNPAID'" type="primary" size="small" @click="handlePay(order)">
              去支付
            </el-button>
            <el-button v-if="order.status !== 'CANCELED'" link class="quiet-action" @click="contactService(order)">
              联系客服
            </el-button>
            <el-button v-if="order.payStatus === 'UNPAID'" link class="quiet-action" @click="refreshPayStatus(order)">
              刷新支付状态
            </el-button>
            <el-button v-if="order.reviewStatus === 'UNREVIEWED'" link class="quiet-action" @click="goReview(order)">
              去评价
            </el-button>
            <el-button
              v-if="order.status === 'PENDING' && order.payStatus === 'UNPAID'"
              link
              class="quiet-action quiet-action--danger"
              @click="cancelOrder(order)"
            >
              取消订单
            </el-button>
            <el-button v-if="canRefund(order)" link class="quiet-action quiet-action--danger" @click="openRefund(order)">
              申请退款
            </el-button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="total > query.size" class="orders-pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <el-dialog v-model="refundVisible" title="申请退款" width="520px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="refundForceFull
          ? '该订单使用了整单优惠券，只能整单退款，不支持部分退款；退款按券后实付金额退回。'
          : '可勾选单个菜品部分退款，或全选整单退款；通过后按所选金额退回余额。'"
        style="margin-bottom: 14px"
      />
      <el-form label-width="80px">
        <el-form-item label="订单号">
          <span>{{ refundOrder?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="退款菜品">
          <div class="refund-items">
            <el-checkbox
              :model-value="allRefundSelected"
              :indeterminate="selectedRefundItems.length > 0 && !allRefundSelected"
              :disabled="refundForceFull"
              @change="toggleAllRefund"
            >
              全选
            </el-checkbox>
            <div v-for="item in (refundOrder?.items || [])" :key="item.id" class="refund-item-row">
              <el-checkbox v-model="selectedRefundItems" :value="item.id" :disabled="refundForceFull">
                <span class="ri-name">{{ item.dishName }}</span>
                <span class="ri-spec">¥{{ item.price }} × {{ item.quantity }}</span>
              </el-checkbox>
              <span class="ri-amount">¥{{ Number(item.amount).toFixed(2) }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="退款金额">
          <span class="refund-amount">¥{{ refundAmount.toFixed(2) }}</span>
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input
            v-model="refundReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明退款原因，例如：菜品与描述不符、出餐超时等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageMyOrders, updateOrderStatus } from '@/api/order'
import { createPay, getPayStatus, mockPaySuccess } from '@/api/pay'
import { applyRefund } from '@/api/refund'
import { coverGradient } from '@/utils/dishImage'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)

const query = reactive({ current: 1, size: 10, status: undefined, payStatus: undefined })

const refundVisible = ref(false)
const refundReason = ref('')
const refundSubmitting = ref(false)
const refundOrder = ref(null)
const selectedRefundItems = ref([])

const allRefundSelected = computed(() => {
  const items = refundOrder.value?.items || []
  return items.length > 0 && selectedRefundItems.value.length === items.length
})

/** 整单券订单：券按整单打折，只能整单退款（后端同样强校验），金额按券后实付 */
const refundForceFull = computed(() => refundOrder.value?.couponScope === 'ORDER')

const refundAmount = computed(() => {
  if (refundForceFull.value) {
    return Number(refundOrder.value?.totalAmount || 0)
  }
  const items = refundOrder.value?.items || []
  return items
    .filter(i => selectedRefundItems.value.includes(i.id))
    .reduce((s, i) => s + Number(i.amount || 0), 0)
})

function openRefund(order) {
  refundOrder.value = order
  refundReason.value = ''
  // 默认全选（整单退款）
  selectedRefundItems.value = (order.items || []).map(i => i.id)
  refundVisible.value = true
}

function toggleAllRefund() {
  const items = refundOrder.value?.items || []
  if (allRefundSelected.value) {
    selectedRefundItems.value = []
  } else {
    selectedRefundItems.value = items.map(i => i.id)
  }
}

async function submitRefund() {
  if (!selectedRefundItems.value.length) {
    ElMessage.warning('请至少选择一件要退款的菜品')
    return
  }
  if (!refundReason.value.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  refundSubmitting.value = true
  try {
    await applyRefund(refundOrder.value.id, refundReason.value.trim(), selectedRefundItems.value)
    ElMessage.success('退款申请已提交，等待审核')
    refundVisible.value = false
    loadData()
  } catch {
    // 请求层已统一提示
  } finally {
    refundSubmitting.value = false
  }
}

function statusType(order) {
  // 未支付却停在 PENDING：实际是「待支付」，用灰色；已支付才是「待取餐」橙色
  if (order.status === 'PENDING' && order.payStatus !== 'PAID') return 'info'
  return { PENDING: 'warning', DONE: 'success', CANCELED: 'info', REFUNDED: 'danger' }[order.status] || 'info'
}

function refundTagType(status) {
  return { PENDING: 'warning', REFUNDED: 'danger', REJECTED: 'info', FAILED: 'danger' }[status] || 'info'
}

function refundLabel(status) {
  return { PENDING: '退款审核中', REFUNDED: '已退款', REJECTED: '退款被驳回', FAILED: '退款失败' }[status] || status
}

function canRefund(order) {
  return order.payStatus === 'PAID'
    && order.status !== 'REFUNDED'
    && !['PENDING', 'REFUNDED'].includes(order.refundStatus)
}

function payTagType(payStatus) {
  return { PAID: 'success', CLOSED: 'info' }[payStatus] || 'warning'
}

function reviewTagType(reviewStatus) {
  return reviewStatus === 'REVIEWED' ? 'success' : 'warning'
}

async function handlePay(order) {
  const res = await createPay(order.id)
  const vo = res.data
  if (vo.paid) {
    ElMessage.success('支付成功')
    loadData()
    return
  }
  if (vo.payUrl) {
    window.open(vo.payUrl, '_blank', 'noopener')
    ElMessage.info('已打开收银台，付完款点「刷新支付状态」确认')
  }
  loadData()
}

async function refreshPayStatus(order) {
  if (!order.payNo) {
    ElMessage.warning('该订单还没有支付单，请先点「去支付」')
    return
  }
  const res = await getPayStatus(order.payNo)
  if (res.data.status === 'PAID') {
    ElMessage.success('已支付')
  } else if (res.data.channel === 'mock') {
    await mockPaySuccess(order.payNo)
    ElMessage.success('模拟支付完成')
  } else {
    ElMessage.info('尚未收到支付结果，请完成付款后再试')
  }
  loadData()
}

function goReview(order) {
  const dishId = order.unreviewedDishIds?.[0]
  if (!dishId) {
    ElMessage.info('订单中的菜品都已评价过啦')
    return
  }
  router.push({ path: `/dishes/${dishId}`, query: { from: 'orders' } })
}

function goDish(item) {
  if (item?.dishId) {
    router.push({ path: `/dishes/${item.dishId}`, query: { from: 'orders' } })
  }
}

function contactService(order) {
  if (!order.stallId) {
    ElMessage.warning('该订单缺少档口信息，无法联系客服')
    return
  }
  router.push({ path: '/customer-service', query: { stall: order.stallId, order: order.id } })
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageMyOrders(query)
    records.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function cancelOrder(order) {
  ElMessageBox.confirm(`确定取消订单 ${order.orderNo} 吗？`, '取消确认', { type: 'warning' })
    .then(async () => {
      await updateOrderStatus(order.id, 'CANCELED')
      ElMessage.success('订单已取消')
      loadData()
    })
    .catch(() => {})
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.orders-page {
  max-width: 1180px;
}

.orders-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: -4px 0 18px;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 160px;
}

.order-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  padding: 20px 22px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.18s var(--ease-apple), box-shadow 0.18s var(--ease-apple);
}

.order-card:hover {
  border-color: rgba(0, 0, 0, 0.08);
  box-shadow: var(--shadow-sm);
}

.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.order-head-main {
  min-width: 0;
}

.order-stall {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.order-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
}

.order-progress {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
}

.order-progress span + span::before {
  content: '·';
  margin: 0 6px;
  color: var(--text-3);
}

.order-pickup {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 9px 12px;
  border-radius: 12px;
  background: #f5f5f7;
  font-size: 13px;
  color: var(--text-2);
}

.order-pickup b {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.order-pickup.is-dim {
  padding: 2px 0;
  background: transparent;
  color: var(--text-3);
}

.order-pickup.is-dim b {
  color: var(--text-2);
  font-weight: 500;
}

.order-remark {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
}

.order-items {
  margin-top: 12px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple);
}

.order-item:last-child {
  border-bottom: none;
}

.order-item:hover {
  background: #fafafa;
}

.order-item-thumb {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.order-item-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-item-initial {
  font-size: 20px;
  font-weight: 650;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.order-item-info {
  flex: 1;
  min-width: 0;
}

.order-item-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-item-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-3);
}

.order-item-amount {
  flex-shrink: 0;
  min-width: 62px;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
  padding-top: 14px;
  border-top: 1px solid rgba(0, 0, 0, 0.055);
}

.order-total-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.order-total-label {
  font-size: 12px;
  color: var(--text-3);
}

.order-total {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.order-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.order-actions .el-button + .el-button {
  margin-left: 0;
}

.order-actions .quiet-action {
  color: var(--text-2);
  font-size: 12.5px;
  padding: 2px 4px;
}

.order-actions .quiet-action:hover {
  color: var(--text-1);
}

.order-actions .quiet-action--danger:hover {
  color: #d92d20;
}

.empty-action {
  margin-top: 16px;
}

.orders-pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.refund-amount {
  color: var(--brand-dark);
  font-size: 16px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.refund-items {
  width: 100%;
}

.refund-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}

.refund-item-row:last-child {
  border-bottom: none;
}

.ri-name {
  font-size: 13px;
  color: var(--text-1);
  font-weight: 500;
}

.ri-spec {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-3);
}

.ri-amount {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}
</style>
