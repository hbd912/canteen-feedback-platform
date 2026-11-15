<template>
  <span
    class="star-rating"
    :class="readonly ? 'is-readonly' : 'is-editable'"
    :style="size ? { '--star-size': typeof size === 'number' ? size + 'px' : size } : undefined"
    role="group"
    :aria-label="value ? '评分 ' + valueLabel + ' 分' : '暂无评分'"
    :aria-readonly="readonly ? 'true' : 'false'"
  >
    <el-rate
      :model-value="value"
      :disabled="readonly"
      :allow-half="readonly"
      :max="5"
      @update:model-value="val => emit('update:value', val)"
    />
    <span v-if="showValue && value" class="star-value">{{ valueLabel }}</span>
    <span v-if="!readonly && !value" class="star-hint">点击星星评分</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
  /** 自定义星星尺寸：数字按 px 处理，字符串原样作为 CSS 长度 */
  size: { type: [Number, String], default: 0 },
  /** 是否在星星旁显示数值（如 4.3），默认关闭以免与页面已有数字重复 */
  showValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:value'])

const valueLabel = computed(() => {
  const n = Number(props.value || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
})
</script>

<style scoped>
.star-rating {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  vertical-align: middle;
  line-height: 1;
  --star-size: 15px;
}

.star-rating.is-editable {
  --star-size: 25px;
}

.star-rating :deep(.el-rate) {
  --el-rate-fill-color: var(--brand);
  --el-rate-void-color: #e5e5ea;
  --el-rate-disabled-void-color: #e5e5ea;
  --el-rate-icon-size: var(--star-size);
  --el-rate-icon-margin: 1px;
  height: auto;
}

.star-rating :deep(.el-rate__item) {
  vertical-align: middle;
}

.star-value {
  font-size: 12px;
  font-weight: 550;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.star-hint {
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.4;
}
</style>
