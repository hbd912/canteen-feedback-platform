<template>
  <div class="dish-card" :class="{ 'is-soldout': isSoldOut }" @click="goDetail">
    <!-- 图片区：正方形，左上角一个状态标签 -->
    <div class="dish-cover" :style="{ background: coverGradient(dish.category) }">
      <img
        v-if="dish.image && !broken"
        class="dish-photo"
        :src="dish.image"
        :alt="dish.name"
        loading="lazy"
        @error="broken = true"
      >
      <span v-else class="dish-initial">{{ dish.name.slice(0, 1) }}</span>

      <span v-if="dishTags.length" class="dish-badge">{{ dishTags[0].text }}</span>
    </div>

    <!-- 信息区 -->
    <div class="dish-body">
      <div class="dish-name" :title="dish.name">{{ dish.name }}</div>
      <div v-if="subtitle" class="dish-subtitle">{{ subtitle }}</div>
      <div class="dish-meta">
        <span class="meta-rating">
          <el-icon><StarFilled /></el-icon>
          <span>{{ ratingText }}</span>
        </span>
        <span class="meta-sep"></span>
        <span>月售 {{ dish.salesCount || 0 }}</span>
      </div>
      <div class="dish-footer">
        <span class="dish-price">
          <span class="price-symbol">¥</span>
          <span class="price-num">{{ priceInt }}</span>
          <span class="price-decimal">.{{ priceDecimal }}</span>
        </span>
        <button
          v-if="showAdd && !isSoldOut"
          class="dish-add"
          :class="{ loading: adding }"
          :disabled="adding"
          @click.stop="handleAdd"
        >
          <el-icon v-if="!adding"><Plus /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addToCart } from '@/api/cart'
import { coverGradient } from '@/utils/dishImage'

const props = defineProps({
  dish: { type: Object, required: true },
  // 美食广场「罗列菜品」去掉加购按钮；收藏页等处仍可保留
  showAdd: { type: Boolean, default: true },
  // 美食广场点菜品跳「档口详情页」（高亮该菜品）；其他页面跳菜品详情
  goStall: { type: Boolean, default: false }
})

const router = useRouter()
const broken = ref(false)
const adding = ref(false)
watch(() => props.dish.image, () => { broken.value = false })

const priceInt = computed(() => {
  const p = Number(props.dish.price) || 0
  return Math.floor(p)
})
const priceDecimal = computed(() => {
  const p = Number(props.dish.price) || 0
  return String(Math.round((p - Math.floor(p)) * 100)).padStart(2, '0')
})
const ratingText = computed(() => (props.dish.avgRating
  ? Number(props.dish.avgRating).toFixed(1)
  : '—'))
const isSoldOut = computed(() => {
  const d = props.dish || {}
  return d.soldOut === 1 || (d.stockQty !== null && d.stockQty !== undefined && Number(d.stockQty) <= 0)
})
// 菜品特色标签：招牌 / 好评如潮 / 热销 / 新品（与档口详情页一致，最多 2 个）
const dishTags = computed(() => {
  const d = props.dish || {}
  const tags = []
  if (isSoldOut.value) tags.push({ text: '售罄', type: 'gray' })
  if (d.featured === 1) tags.push({ text: '招牌', type: 'gold' })
  if (d.goodRate != null && d.goodRate >= 85 && d.reviewCount >= 5) tags.push({ text: '好评如潮', type: 'green' })
  if (d.salesCount >= 200) tags.push({ text: '热销', type: 'orange' })
  else if (d.reviewCount != null && d.reviewCount <= 3) tags.push({ text: '新品', type: 'blue' })
  return tags.slice(0, 2)
})
const subtitle = computed(() => {
  if (props.dish.description) return props.dish.description
  if (props.dish.category) return `${props.dish.category} · 招牌美味`
  return ''
})

async function handleAdd() {
  if (isSoldOut.value) {
    ElMessage.warning('这道菜已售罄')
    return
  }
  adding.value = true
  try {
    await addToCart(props.dish.id, 1)
    window.dispatchEvent(new CustomEvent('canteen:cart-changed'))
    ElMessage.success(`已加入购物车：${props.dish.name}`)
  } finally {
    adding.value = false
  }
}

function goDetail() {
  // 美食广场：点菜品先跳到该菜品所属档口的详情页，高亮这道菜；再点菜品才进菜品详情
  if (props.goStall && props.dish.stallId) {
    router.push(`/stalls/${props.dish.stallId}?highlight=${props.dish.id}`)
    return
  }
  router.push(`/dishes/${props.dish.id}`)
}
</script>

<style scoped>
.dish-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s var(--ease-out), box-shadow 0.25s var(--ease-out),
    border-color 0.25s var(--ease-out);
}

.dish-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.dish-card:active {
  transform: translateY(-1px);
}

.dish-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f2f2f5;
}

.dish-photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s var(--ease-out);
}

.dish-card:hover .dish-photo {
  transform: scale(1.03);
}

.dish-initial {
  position: relative;
  z-index: 1;
  font-size: 34px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: -0.02em;
}

/* 单一状态标签：深色玻璃质感，代替原来的多色标签 + 橙色评分角标 */
.dish-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(29, 29, 31, 0.62);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  color: #fff;
  font-size: 11px;
  font-weight: 550;
  line-height: 22px;
  letter-spacing: 0.01em;
}

.dish-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 12px 14px 14px;
}

.dish-name {
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.dish-subtitle {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dish-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 7px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
}

.meta-rating {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--text-2);
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.meta-rating .el-icon {
  font-size: 11px;
  color: #f5a623;
}

.meta-sep {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: #c7c7cc;
  flex-shrink: 0;
}

.dish-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 10px;
}

.dish-price {
  display: flex;
  align-items: baseline;
  color: var(--brand-dark);
  font-weight: 650;
  line-height: 1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.price-symbol {
  margin-right: 1px;
  font-size: 12px;
  font-weight: 600;
}

.price-num {
  font-size: 18px;
}

.price-decimal {
  font-size: 12px;
  font-weight: 600;
}

.dish-add {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 1px var(--border-strong);
  color: var(--text-2);
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), color 0.18s var(--ease-apple),
    box-shadow 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.dish-add:hover {
  background: var(--brand);
  box-shadow: 0 0 0 1px var(--brand);
  color: #fff;
}

.dish-add:active {
  transform: scale(0.92);
}

.dish-add.loading {
  opacity: 0.6;
  pointer-events: none;
}

/* 售罄：整卡去色压暗，仍可点进详情 */
.is-soldout .dish-photo,
.is-soldout .dish-initial {
  filter: grayscale(1);
  opacity: 0.5;
}

.is-soldout .dish-name {
  color: var(--text-2);
}

@media (prefers-reduced-motion: reduce) {
  .dish-card,
  .dish-photo {
    transition: none;
  }
  .dish-card:hover,
  .dish-card:active {
    transform: none;
  }
  .dish-card:hover .dish-photo {
    transform: none;
  }
}
</style>
