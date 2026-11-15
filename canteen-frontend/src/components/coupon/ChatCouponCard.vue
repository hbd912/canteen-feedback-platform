<template>
  <div class="chat-coupon" :class="{ claimed: isClaimed, mine: !canClaim }">
    <div class="cc-stub">
      <div class="cc-face">
        <template v-if="message.couponType === 'DISCOUNT'">
          <template v-if="Number(message.couponValue) === 0">免单</template>
          <template v-else><b>{{ zhe }}</b><small>折</small></template>
        </template>
        <template v-else><small>¥</small><b>{{ money(message.couponValue) }}</b></template>
      </div>
      <div class="cc-scope">{{ scopeLabel }}</div>
    </div>

    <div class="cc-body">
      <div class="cc-name">{{ message.couponName || '优惠券' }}</div>
      <div class="cc-sub">{{ scopeDetail }}</div>
      <div v-if="message.couponEndTime" class="cc-time">有效期至 {{ String(message.couponEndTime).slice(0, 10) }}</div>
    </div>

    <button
      v-if="canClaim && !isClaimed"
      class="cc-claim"
      :disabled="claiming"
      @click.stop="$emit('claim', message)"
    >{{ claiming ? '领取中' : '领取' }}</button>
    <span v-else class="cc-status" :class="{ done: isClaimed }">
      <el-icon v-if="isClaimed" :size="11"><Select /></el-icon>
      {{ isClaimed ? '已领取' : '待学生领取' }}
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  message: { type: Object, required: true },
  canClaim: { type: Boolean, default: false },
  claiming: { type: Boolean, default: false }
})

defineEmits(['claim'])

const isClaimed = computed(() => props.message.couponOfferStatus === 'CLAIMED')

const zhe = computed(() => {
  const z = Number(props.message.couponValue || 0) * 10
  return Number.isInteger(z) ? z : z.toFixed(1)
})

const scopeLabel = computed(() => (props.message.couponScope === 'ITEM' ? '单份菜' : '整单'))

const scopeDetail = computed(() => {
  if (props.message.couponScope === 'ITEM') {
    return props.message.couponDishName
      ? `仅限「${props.message.couponDishName}」一份，不与其他菜叠加`
      : '仅限一道菜的一份，不与其他菜叠加'
  }
  return '整单可用，可与菜品数量叠加'
})

function money(v) {
  return Number(v || 0).toFixed(0)
}
</script>

<style scoped>
/* 聊天里的券是一条结构化消息：浅橙值区 + 白卡体 */
.chat-coupon {
  display: flex;
  align-items: stretch;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  margin: 8px 0 2px;
  background: var(--surface);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 14px;
  overflow: hidden;
}

.cc-stub {
  position: relative;
  width: 80px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px 6px;
  background: var(--brand-soft);
  color: var(--brand-dark);
}

.cc-stub::after {
  content: '';
  position: absolute;
  top: 10px;
  bottom: 10px;
  right: 0;
  border-right: 1px dashed rgba(227, 95, 0, 0.18);
}

.chat-coupon.claimed .cc-stub {
  background: #f5f5f7;
  color: var(--text-3);
}

.chat-coupon.claimed .cc-stub::after {
  border-color: rgba(0, 0, 0, 0.07);
}

.cc-face {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.cc-face b {
  font-size: 30px;
}

.cc-face small {
  font-size: 12px;
  font-weight: 600;
}

.cc-scope {
  font-size: 11px;
  line-height: 1.4;
  color: var(--brand-dark);
  opacity: 0.75;
}

.chat-coupon.claimed .cc-scope {
  color: var(--text-3);
  opacity: 1;
}

.cc-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px 4px 12px 12px;
}

.cc-name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cc-sub {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.45;
  color: #86868b;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.cc-time {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.cc-claim {
  align-self: center;
  margin-right: 12px;
  flex-shrink: 0;
  height: 30px;
  padding: 0 15px;
  border: none;
  border-radius: 999px;
  background: #1d1d1f;
  color: #fff;
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), transform 0.12s var(--ease-apple),
    opacity 0.15s var(--ease-apple);
}

.cc-claim:hover:not(:disabled) {
  background: #333336;
}

.cc-claim:active:not(:disabled) {
  transform: scale(0.97);
}

.cc-claim:disabled {
  opacity: 0.45;
  cursor: default;
}

.cc-status {
  align-self: center;
  margin-right: 12px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: #f5f5f7;
  color: #86868b;
  font-size: 11.5px;
  font-weight: 500;
  white-space: nowrap;
}

.cc-status.done {
  background: #e7f7ec;
  color: #17803d;
  font-weight: 600;
}

.chat-coupon.mine {
  max-width: 100%;
}
</style>
