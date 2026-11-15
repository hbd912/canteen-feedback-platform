<template>
  <el-dialog
    v-model="visible"
    width="380px"
    append-to-body
    :close-on-click-modal="false"
    class="pay-dialog"
  >
    <template #header>
      <div class="pay-head">
        <span class="pay-title">支付</span>
        <span class="pay-pill" :class="'pay-pill--' + tagType">{{ statusLabel }}</span>
      </div>
    </template>

    <div class="pay-box">
      <div class="pay-amount">¥{{ session?.amount ?? '0.00' }}</div>
      <div v-if="pay.totalCount > 1" class="pay-sub">
        跨档口拆单，共 {{ pay.totalCount }} 笔 · 第 {{ currentIndex }} 笔
      </div>
      <div class="pay-no">支付单号 <span class="pay-no-val">{{ session?.payNo }}</span></div>

      <div v-if="pay.status === 'CREATED' && session?.channel === 'mock'" class="pay-actions">
        <el-button class="pay-secondary" :loading="pay.mocking" @click="handleMock">模拟支付成功</el-button>
        <div class="pay-mock-note">仅演示环境</div>
      </div>
      <div v-if="pay.status === 'CREATED' && session?.payUrl" class="pay-actions">
        <el-button class="pay-secondary" @click="openCashier">打开收银台</el-button>
        <div class="pay-hint">支付后会自动确认，无需手动刷新</div>
      </div>
      <div v-if="pay.status === 'PAID' && pay.hasNext" class="pay-hint done">
        本笔已支付，继续支付下一笔
      </div>
      <div v-if="pay.status === 'PAID' && !pay.hasNext" class="pay-hint done">
        全部支付完成，可在「我的订单」查看取餐时间
      </div>
    </div>

    <template #footer>
      <div class="pay-foot">
        <button class="pay-close" @click="close">关闭</button>
        <el-button v-if="pay.status === 'PAID' && pay.hasNext" type="primary" class="pay-main" @click="nextPay">下一笔</el-button>
        <el-button v-else-if="pay.status === 'PAID'" type="primary" class="pay-main" @click="goOrders">查看我的订单</el-button>
        <el-button v-else type="primary" class="pay-main" :loading="pay.querying" @click="handleQuery">查询支付结果</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { usePayStore } from '@/store/pay'
import { useCartStore } from '@/store/cart'

const router = useRouter()
const pay = usePayStore()
const cartStore = useCartStore()

const visible = computed({
  get: () => pay.visible,
  set: value => {
    if (!value) pay.dismiss()
  }
})

const session = computed(() => pay.session)

/** 当前是第几笔（1 起），用于多笔进度提示 */
const currentIndex = computed(() => {
  if (!session.value?.payNo || !pay.queue.length) return 1
  const idx = pay.queue.findIndex(s => s.payNo === session.value.payNo)
  return idx === -1 ? 1 : idx + 1
})

const statusLabel = computed(() => {
  if (pay.status === 'PAID') return '已支付'
  if (pay.status === 'CLOSED') return '已关闭'
  return '待支付'
})

const tagType = computed(() => {
  if (pay.status === 'PAID') return 'success'
  if (pay.status === 'CLOSED') return 'info'
  return 'warning'
})

function openCashier() {
  if (session.value?.payUrl) window.open(session.value.payUrl, '_blank', 'noopener')
}

async function handleMock() {
  await pay.mockPay()
}

async function handleQuery() {
  const status = await pay.query()
  // 查单确认已支付后，自动推进到下一笔
  if (status === 'PAID') pay.advance()
}

function nextPay() {
  pay.advance()
}

function close() {
  pay.dismiss()
}

function goOrders() {
  pay.dismiss()
  router.push('/my/orders')
}

// 支付成功：购物车已由后端口径清空，这里同步角标与金额
watch(() => pay.paid, paid => {
  if (paid) {
    cartStore.refresh()
    ElMessage.success('支付完成')
  }
})

onBeforeUnmount(() => pay.stopPoll())
</script>

<style scoped>
/* 弹窗：小屏不横向溢出。
   el-dialog 通过 teleport 挂到 body，内部节点拿不到父组件 scoped 属性，
   这里用唯一类名 .pay-dialog 限定的 :global 覆盖 Element 内部结构。 */
:global(.pay-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 24px;
  overflow: hidden;
}

:global(.pay-dialog .el-dialog__header) {
  padding: 22px 24px 0 !important;
  margin-right: 0 !important;
  border-bottom: none;
}

:global(.pay-dialog .el-dialog__headerbtn) {
  top: 16px;
  right: 16px;
}

:global(.pay-dialog .el-dialog__body) {
  padding: 18px 24px 4px !important;
}

:global(.pay-dialog .el-dialog__footer) {
  padding: 6px 24px 22px !important;
}

.pay-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pay-title {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

/* 状态胶囊：自绘浅底，不再使用 Element 语义色 */
.pay-pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 550;
  background: rgba(0, 0, 0, 0.055);
  color: var(--text-2);
}

.pay-pill--warning {
  background: var(--brand-soft);
  color: #c2410c;
}

.pay-pill--success {
  background: #e9f8ee;
  color: #1d8a45;
}

.pay-pill--info {
  background: rgba(0, 0, 0, 0.055);
  color: var(--text-2);
}

/* 收据式主体 */
.pay-box {
  padding: 4px 0 10px;
  text-align: center;
}

.pay-amount {
  font-size: 34px;
  font-weight: 650;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.pay-sub {
  margin-top: 8px;
  font-size: 12px;
  color: #a1a1a6;
}

.pay-no {
  margin-top: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  color: #a1a1a6;
  word-break: break-all;
}

.pay-no-val {
  color: #86868b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  user-select: text;
}

.pay-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  margin-top: 18px;
}

.pay-secondary {
  height: 36px;
  padding: 0 18px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: transparent;
  color: var(--text-1);
  font-size: 13px;
  box-shadow: none;
}

.pay-secondary:hover {
  border-color: rgba(0, 0, 0, 0.18);
  background: #fafafa;
  color: var(--text-1);
}

.pay-mock-note {
  font-size: 11px;
  color: #a1a1a6;
}

.pay-hint {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-2);
}

.pay-actions .pay-hint {
  margin-top: 0;
}

.pay-hint.done {
  color: var(--text-2);
}

/* 底部：纯文字关闭 + 唯一主 CTA */
.pay-foot {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pay-close {
  flex-shrink: 0;
  padding: 8px 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}

.pay-close:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--text-1);
}

.pay-main {
  flex: 1;
  height: 44px;
  font-size: 14.5px;
  font-weight: 600;
  box-shadow: none !important;
}

@media (max-width: 420px) {
  :global(.pay-dialog .el-dialog__header) {
    padding: 20px 20px 0 !important;
  }

  :global(.pay-dialog .el-dialog__body) {
    padding: 16px 20px 4px !important;
  }

  :global(.pay-dialog .el-dialog__footer) {
    padding: 6px 20px 20px !important;
  }
}
</style>
