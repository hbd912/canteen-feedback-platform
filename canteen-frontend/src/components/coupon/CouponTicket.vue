<template>
  <div
    class="ct"
    :class="{
      used: coupon.status === 'USED',
      expired: isExpired,
      selectable,
      selected,
      compact
    }"
    @click="$emit('click', coupon)"
  >
    <div class="ct-stub">
      <div v-if="coupon.type === 'DISCOUNT'" class="ct-value">
        <template v-if="Number(coupon.value) === 0">免单</template>
        <template v-else><b>{{ discountLabel }}</b><small>折</small></template>
      </div>
      <div v-else class="ct-value"><small>¥</small><b>{{ money(coupon.value) }}</b></div>
      <div class="ct-cond">{{ condLabel }}</div>
    </div>

    <div class="ct-body">
      <div class="ct-top">
        <span class="ct-name">{{ coupon.name || '优惠券' }}</span>
        <span v-if="selected" class="ct-check">✓</span>
      </div>
      <div class="ct-stall">{{ coupon.stallName || '全平台' }}</div>
      <div class="ct-tags">
        <span class="ct-scope">{{ scopeLabel }}</span>
      </div>
      <div class="ct-time">{{ timeLabel }}</div>
      <div v-if="showAction" class="ct-foot">
        <button class="ct-btn" @click.stop="$emit('use', coupon)">去使用</button>
      </div>
      <div v-else-if="coupon.status === 'USED'" class="ct-stamp">已使用</div>
      <div v-else-if="isExpired" class="ct-stamp">已过期</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  coupon: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  selectable: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  showAction: { type: Boolean, default: false }
})

defineEmits(['click', 'use'])

const isExpired = computed(() => {
  if (props.coupon.status === 'EXPIRED') return true
  if (props.coupon.status === 'USED') return false
  const end = props.coupon.endTime
  return end ? new Date(String(end).replace(/-/g, '/')) < new Date() : false
})

const discountLabel = computed(() => {
  const v = Number(props.coupon.value || 0)
  if (!v) return '免单'
  const zhe = v * 10
  return Number.isInteger(zhe) ? String(zhe) : zhe.toFixed(1)
})

const condLabel = computed(() => {
  const min = Number(props.coupon.minAmount || 0)
  return min > 0 ? `满 ${money(min)} 可用` : '无门槛'
})

const scopeLabel = computed(() => {
  if (props.coupon.scope !== 'ITEM') return '整单可用'
  return props.coupon.dishName ? `限「${props.coupon.dishName}」一份` : '限单份菜'
})

const timeLabel = computed(() => {
  const end = props.coupon.endTime
  if (!end) return '长期有效'
  return `有效期至 ${String(end).slice(0, 10)}`
})

function money(v) {
  return Number(v || 0).toFixed(2)
}
</script>

<style scoped>
.ct {
  position: relative;
  display: flex;
  min-height: 112px;
  background: var(--surface);
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.045);
  transition: transform 0.18s var(--ease-apple), box-shadow 0.18s var(--ease-apple),
    opacity 0.18s var(--ease-apple);
}

.ct.selectable {
  cursor: pointer;
}

.ct.selectable:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.ct.selected {
  border-color: rgba(255, 122, 26, 0.4);
  box-shadow: 0 0 0 2px rgba(255, 122, 26, 0.18);
}

.ct.used,
.ct.expired {
  opacity: 0.55;
}

/* 票根：中性浅橙值区，仅数字用品牌橙 */
.ct-stub {
  position: relative;
  width: 104px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px 8px;
  background: var(--brand-soft);
  color: var(--brand-dark);
}

/* 极轻的齿孔分隔线：保留票券隐喻，但不依赖页面底色 */
.ct-stub::after {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 12px;
  right: 0;
  border-right: 1px dashed rgba(227, 95, 0, 0.2);
}

.ct.used .ct-stub,
.ct.expired .ct-stub {
  background: #f5f5f7;
  color: var(--text-3);
}

.ct.used .ct-stub::after,
.ct.expired .ct-stub::after {
  border-color: rgba(0, 0, 0, 0.08);
}

.ct-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.ct-value b {
  font-size: 34px;
}

.ct-value small {
  font-size: 14px;
  font-weight: 600;
}

.ct-cond {
  font-size: 11.5px;
  line-height: 1.4;
  color: var(--brand-dark);
  opacity: 0.78;
  text-align: center;
}

.ct.used .ct-cond,
.ct.expired .ct-cond {
  color: var(--text-3);
  opacity: 1;
}

.ct-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 16px 18px 14px;
}

.ct-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.ct-name {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ct-check {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  line-height: 1;
}

.ct-stall {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-2);
}

.ct-tags {
  margin-top: 7px;
}

.ct-scope {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 11.5px;
  font-weight: 500;
  line-height: 18px;
}

.ct-time {
  margin-top: auto;
  padding-top: 10px;
  font-size: 11.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.ct-foot {
  margin-top: 10px;
}

.ct-btn {
  border: none;
  height: 32px;
  padding: 0 15px;
  border-radius: 999px;
  background: #1d1d1f;
  color: #fff;
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.ct-btn:hover {
  background: #333336;
}

.ct-btn:active {
  transform: scale(0.97);
}

.ct-stamp {
  margin-top: 10px;
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: #f5f5f7;
  color: #86868b;
  font-size: 11.5px;
  font-weight: 500;
}

.ct.compact {
  min-height: 96px;
  border-radius: 16px;
}

.ct.compact .ct-stub {
  width: 88px;
}

.ct.compact .ct-value,
.ct.compact .ct-value b {
  font-size: 26px;
}

.ct.compact .ct-value small {
  font-size: 12px;
}

.ct.compact .ct-body {
  padding: 12px 14px 10px 16px;
}

.ct.compact .ct-name {
  font-size: 13.5px;
}
</style>
