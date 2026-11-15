<template>
  <div class="page-container stall-detail" v-loading="loading">
    <button class="back-link" type="button" @click="goBack">
      <el-icon><ArrowLeft /></el-icon>
      返回美食探索
    </button>

    <el-empty v-if="!stall && !loading" description="档口不存在或已被删除">
      <el-button type="primary" @click="goBack">返回美食探索</el-button>
    </el-empty>

    <template v-if="stall">
      <!-- 档口信息头 -->
      <section class="stall-hero">
        <div class="hero-avatar">
          <img v-if="stall.merchantAvatar" :src="stall.merchantAvatar" :alt="stall.name" @error="$event.target.style.display='none'">
          <img v-else-if="stall.coverImage" :src="stall.coverImage" :alt="stall.name" @error="$event.target.style.display='none'">
          <span v-else class="hero-initial">{{ (stall.name || '档').slice(0, 1) }}</span>
        </div>

        <div class="hero-info">
          <div class="hero-head">
            <div class="hero-title-wrap">
              <h1 class="hero-name">{{ stall.name }}</h1>
              <div class="hero-tags">
                <span class="ap-tag">{{ stall.windowNo }} 号窗口</span>
                <span class="ap-tag">{{ zoneText }}</span>
                <span v-if="stall.category" class="ap-tag ap-tag--brand">{{ stall.category }}</span>
              </div>
            </div>
            <button class="ap-btn ap-btn--light hero-contact" type="button" @click="contactService">
              <el-icon><Service /></el-icon>
              联系商家
            </button>
          </div>

          <div class="hero-stats">
            <div class="hero-rating">
              <el-icon><StarFilled /></el-icon>
              <span class="rating-num">{{ stall.avgRating ? Number(stall.avgRating).toFixed(1) : '—' }}</span>
              <span class="rating-unit">分</span>
            </div>
            <div class="hero-facts">
              <span>月售 {{ stall.salesCount || 0 }}</span>
              <span class="fact-sep"></span>
              <span>好评率 {{ stall.goodRate ? Number(stall.goodRate).toFixed(0) : 0 }}%</span>
              <template v-if="stall.hygieneScore">
                <span class="fact-sep"></span>
                <span class="hygiene" :class="{ 'hygiene-low': stall.hygieneScore < 80 }">
                  <em class="hygiene-dot"></em>
                  卫生 {{ Number(stall.hygieneScore).toFixed(0) }}
                </span>
              </template>
            </div>
          </div>

          <div v-if="stall.signatureDishes && stall.signatureDishes.length" class="hero-signature">
            <span class="sig-label">招牌菜</span>
            <span v-for="d in stall.signatureDishes.slice(0, 3)" :key="d.id" class="ap-tag ap-tag--amber">{{ d.name }}</span>
          </div>
          <p v-if="stall.description" class="hero-desc">{{ stall.description }}</p>
        </div>
      </section>

      <!-- 菜品列表 -->
      <section class="ap-card dish-list-card">
        <div class="dishes-head">
          <div class="dishes-title">全部菜品</div>
          <span class="ap-tag">{{ dishes.length }} 道</span>
        </div>

        <div v-loading="dishesLoading" class="dish-grid">
          <div
            v-for="dish in dishes"
            :key="dish.id"
            class="dish-item"
            :class="{ highlighted: highlightId === dish.id }"
          >
            <div class="dish-cover" :style="{ background: coverGradient(dish.category) }" @click="goDish(dish)">
              <img v-if="dish.image" :src="dish.image" :alt="dish.name" loading="lazy" @error="$event.target.style.display='none'">
              <span v-else class="dish-initial">{{ (dish.name || '菜').slice(0, 1) }}</span>
              <span v-if="dishTags(dish).length" class="dish-badge">{{ dishTags(dish)[0].text }}</span>
            </div>
            <div class="dish-info" @click="goDish(dish)">
              <div class="dish-name" :title="dish.name">{{ dish.name }}</div>
              <div class="dish-meta">
                <el-icon class="meta-star"><StarFilled /></el-icon>
                <span>{{ dish.avgRating ? Number(dish.avgRating).toFixed(1) : '—' }}</span>
                <span class="meta-sep"></span>
                <span>月售 {{ dish.salesCount || 0 }}</span>
              </div>
              <div class="dish-foot">
                <span class="dish-price">¥{{ priceText(dish.price) }}</span>
                <button class="dish-add" title="加入购物车" @click.stop="addCart(dish)">
                  <el-icon><Plus /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!dishesLoading && !dishes.length" class="ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><Dish /></el-icon></div>
          <div class="ap-empty-title">该档口暂无菜品</div>
          <div>换个档口看看，或稍后再来</div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStall } from '@/api/stall'
import { listAllDishes } from '@/api/dish'
import { addToCart } from '@/api/cart'
import { coverGradient } from '@/utils/dishImage'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const dishesLoading = ref(false)
const stall = ref(null)
const dishes = ref([])
const addingId = ref(null)

// 从哪个菜品点进来的，用于高亮
const highlightId = computed(() => {
  const h = route.query.highlight
  return h ? Number(h) : null
})

const zoneText = computed(() => {
  const s = stall.value
  if (!s) return ''
  if (s.area === 'north') return '北区'
  return `南区 ${s.floor || 1} 楼`
})

async function loadStall() {
  loading.value = true
  try {
    const res = await getStall(route.params.id)
    stall.value = res.data
  } catch {
    stall.value = null
  } finally {
    loading.value = false
  }
}

async function loadDishes() {
  dishesLoading.value = true
  try {
    dishes.value = await listAllDishes(route.params.id, { sortBy: 'rating' })
  } catch {
    dishes.value = []
  } finally {
    dishesLoading.value = false
  }
}

function goBack() {
  router.push('/dishes')
}

function goDish(dish) {
  if (dish && dish.id) router.push(`/dishes/${dish.id}`)
}

function contactService() {
  if (!stall.value?.id) return
  router.push({ path: '/customer-service', query: { stall: stall.value.id } })
}

async function addCart(dish) {
  if (addingId.value) return
  addingId.value = dish.id
  try {
    await addToCart(dish.id, 1)
    window.dispatchEvent(new CustomEvent('canteen:cart-changed'))
    ElMessage.success(`已加入购物车：${dish.name}`)
  } catch {
    // 请求层已统一提示
  } finally {
    addingId.value = null
  }
}

function priceText(p) {
  if (p == null) return '—'
  return Number(p).toFixed(2)
}

// 根据菜品数据生成特色标签，最多 2 个，展现每个商家的菜品特点
function dishTags(dish) {
  const tags = []
  if (dish.soldOut === 1 || (dish.stockQty != null && dish.stockQty <= 0)) tags.push({ text: '售罄', type: 'gray' })
  if (dish.featured === 1) tags.push({ text: '招牌', type: 'gold' })
  if (dish.goodRate != null && dish.goodRate >= 85 && dish.reviewCount >= 5) tags.push({ text: '好评如潮', type: 'green' })
  if (dish.salesCount >= 200) tags.push({ text: '热销', type: 'orange' })
  else if (dish.reviewCount != null && dish.reviewCount <= 3) tags.push({ text: '新品', type: 'blue' })
  return tags.slice(0, 2)
}

onMounted(async () => {
  await loadStall()
  await loadDishes()
})

// keep-alive 复用：切换档口 id 时重新加载
watch(
  () => (route.name === 'StallDetail' ? route.params.id : null),
  async (id) => {
    if (!id) return
    stall.value = null
    dishes.value = []
    await loadStall()
    await loadDishes()
  }
)
</script>

<style scoped>
.stall-detail {
  padding: 26px 30px 116px;
  max-width: 1480px;
  margin: 0 auto;
  box-sizing: border-box;
}

/* ============ 返回 ============ */
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  padding: 6px 2px;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  font-weight: 550;
  cursor: pointer;
  transition: color 0.16s var(--ease-apple);
}

.back-link:hover {
  color: var(--text-1);
}

.back-link .el-icon {
  font-size: 14px;
}

/* ============ 档口信息头 ============ */
.stall-hero {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  padding: 22px 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xs);
}

.hero-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 132px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 18px;
  background: #f2f2f5;
}

.hero-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-initial {
  font-size: 44px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--text-2);
}

.hero-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.hero-title-wrap {
  min-width: 0;
}

.hero-name {
  margin: 0;
  font-size: 27px;
  font-weight: 650;
  letter-spacing: -0.024em;
  line-height: 1.15;
  color: var(--text-1);
}

.hero-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.hero-contact {
  flex-shrink: 0;
  height: 36px;
  font-size: 13px;
}

.hero-stats {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 20px;
  margin-top: 18px;
}

.hero-rating {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--text-1);
}

.hero-rating .el-icon {
  align-self: center;
  font-size: 14px;
  color: #f5a623;
}

.rating-num {
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.rating-unit {
  font-size: 12px;
  color: var(--text-3);
}

.hero-facts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}

.fact-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #c7c7cc;
}

.hygiene {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-2);
}

.hygiene-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34c759;
}

.hygiene-low {
  color: #c9342b;
}

.hygiene-low .hygiene-dot {
  background: #ff3b30;
}

.hero-signature {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}

.sig-label {
  margin-right: 2px;
  font-size: 11.5px;
  font-weight: 550;
  color: var(--text-3);
}

.hero-desc {
  margin: 12px 0 0;
  max-width: 70ch;
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-2);
}

/* ============ 菜品列表 ============ */
.dish-list-card {
  padding: 22px 24px 24px;
}

.dishes-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.dishes-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}

.dish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 16px;
}

.dish-item {
  position: relative;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  transition: transform 0.25s var(--ease-out), box-shadow 0.25s var(--ease-out),
    border-color 0.25s var(--ease-out);
}

.dish-item:hover {
  transform: translateY(-2px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

/* 从美食广场点进来的菜品：细橙描边高亮 */
.dish-item.highlighted {
  border-color: var(--brand-line);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.dish-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: #f2f2f5;
  cursor: pointer;
}

.dish-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dish-initial {
  position: relative;
  z-index: 1;
  font-size: 34px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

/* 单一状态标签：深色玻璃 */
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

.dish-info {
  padding: 12px 14px 14px;
  cursor: pointer;
}

.dish-name {
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.3;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dish-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.meta-star {
  font-size: 11px;
  color: #f5a623;
}

.meta-sep {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: #c7c7cc;
}

.dish-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.dish-price {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
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

@media (max-width: 860px) {
  .stall-detail {
    padding: 20px 16px 32px;
  }

  .stall-hero {
    flex-direction: column;
    gap: 16px;
    padding: 18px;
  }

  .hero-avatar {
    width: 100%;
    height: 160px;
  }

  .hero-head {
    flex-direction: column;
  }

  .hero-contact {
    align-self: flex-start;
  }

  .dish-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dish-item:hover,
  .dish-add:active {
    transform: none;
  }
}
</style>
