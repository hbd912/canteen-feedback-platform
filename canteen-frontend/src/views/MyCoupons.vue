<template>
  <div class="ap-page coupons-page">
    <div class="ap-head">
      <div class="title-wrap">
        <button v-if="activeGroup" class="ap-back coupon-back" aria-label="返回商家列表" @click="leaveMerchant">
          <el-icon :size="16"><ArrowLeft /></el-icon>
        </button>
        <div>
          <h1 class="ap-title">{{ activeGroup ? activeGroup.stallName : '我的优惠券' }}</h1>
          <div class="ap-sub">
            {{ activeGroup
              ? `${activeCoupons.length} 张 · 可按整单 / 单份菜筛选`
              : `${available.length} 张可用 · 领券中心 ${claimable.length} 张可领` }}
          </div>
        </div>
      </div>
      <button class="ap-btn ap-btn--light" @click="load">
        <el-icon><Refresh /></el-icon>刷新
      </button>
    </div>

    <div class="coupon-tabs">
      <div class="ap-pill-group">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="ap-pill"
          :class="{ active: activeTab === tab.value }"
          @click="activeTab = tab.value"
        >{{ tab.label }} {{ tab.count }}</button>
      </div>
    </div>

    <!-- 领券中心：公开可领的券 -->
    <template v-if="activeTab === 'center'">
      <div v-if="loading" class="coupon-grid">
        <div v-for="n in 6" :key="'csk' + n" class="coupon-skeleton"></div>
      </div>
      <template v-else>
        <div class="coupon-grid">
          <div v-for="c in claimable" :key="'c' + c.id" class="ct-wrap">
            <CouponTicket :coupon="c" />
            <div class="claim-foot">
              <button class="claim-btn" :disabled="c.claimed" @click="claim(c)">
                {{ c.claimed ? '已领取' : '立即领取' }}
              </button>
            </div>
          </div>
        </div>
        <div v-if="!claimable.length" class="ap-card ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><Ticket /></el-icon></div>
          <div class="ap-empty-title">暂无可领取的优惠券</div>
          <div>商家上新后会出现在这里</div>
        </div>
      </template>
    </template>

    <!-- 第一层：按商家列出 -->
    <div v-if="!activeGroup && activeTab !== 'center'" class="merchant-wrap">
      <div v-if="loading" class="ap-card merchant-card-box">
        <div v-for="n in 4" :key="'msk' + n" class="merchant-skeleton"></div>
      </div>
      <template v-else>
        <div v-if="groupedList.length" class="ap-card merchant-card-box">
          <button
            v-for="g in groupedList"
            :key="g.stallId || 'all'"
            class="ap-row merchant-row"
            @click="enterMerchant(g)"
          >
            <span class="ap-row-icon merchant-icon"><el-icon :size="18"><Ticket /></el-icon></span>
            <span class="ap-row-main">
              <span class="ap-row-title">{{ g.stallName }}</span>
              <span class="ap-row-sub">{{ tabLabel }}券 · 点开查看并下单使用</span>
            </span>
            <span class="merchant-count">{{ g.list.length }} 张</span>
            <el-icon class="merchant-arrow" :size="15"><ArrowRight /></el-icon>
          </button>
        </div>
        <div v-else class="ap-card ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><Ticket /></el-icon></div>
          <div class="ap-empty-title">{{ emptyText }}</div>
        </div>
      </template>
    </div>

    <!-- 第二层：该商家的券 + 整单/单份筛选 -->
    <template v-else-if="activeTab !== 'center'">
      <div class="scope-switch">
        <span class="scope-label">券类型</span>
        <button class="scope-btn" :class="{ active: scopeFilter === 'ALL' }" @click="scopeFilter = 'ALL'">
          全部 {{ activeCoupons.length }}
        </button>
        <button class="scope-btn" :class="{ active: scopeFilter === 'ORDER' }" @click="scopeFilter = 'ORDER'">
          整单 {{ orderCount }}
        </button>
        <button class="scope-btn" :class="{ active: scopeFilter === 'ITEM' }" @click="scopeFilter = 'ITEM'">
          单份菜 {{ itemCount }}
        </button>
      </div>

      <div v-if="loading" class="coupon-grid">
        <div v-for="n in 6" :key="'gsk' + n" class="coupon-skeleton"></div>
      </div>
      <template v-else>
        <div class="coupon-grid">
          <CouponTicket
            v-for="c in filteredCoupons"
            :key="c.id"
            :coupon="c"
            show-action
            @use="goUse"
          />
        </div>
        <div v-if="!filteredCoupons.length" class="ap-card ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><Ticket /></el-icon></div>
          <div class="ap-empty-title">{{ emptyText }}</div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import CouponTicket from '@/components/coupon/CouponTicket.vue'
import { myCoupons, availableCoupons, claimCoupon } from '@/api/coupon'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const coupons = ref([])
const claimable = ref([])
const activeTab = ref('available')
const activeStallId = ref(null)
const scopeFilter = ref('ALL')

function isExpired(c) {
  if (c.status === 'EXPIRED') return true
  if (c.status === 'USED') return false
  return c.endTime ? new Date(String(c.endTime).replace(/-/g, '/')) < new Date() : false
}

const available = computed(() => coupons.value.filter(c => c.status === 'UNUSED' && !isExpired(c)))
const used = computed(() => coupons.value.filter(c => c.status === 'USED'))
const expired = computed(() => coupons.value.filter(c => c.status !== 'USED' && isExpired(c)))

const tabs = computed(() => [
  { value: 'available', label: '可用', count: available.value.length },
  { value: 'center', label: '领券中心', count: claimable.value.length },
  { value: 'used', label: '已使用', count: used.value.length },
  { value: 'expired', label: '已过期', count: expired.value.length }
])

const currentList = computed(() => {
  if (activeTab.value === 'used') return used.value
  if (activeTab.value === 'expired') return expired.value
  return available.value
})

const tabLabel = computed(() => ({ available: '可用', used: '已使用', expired: '已过期' }[activeTab.value] || ''))

/** 第一层：按商家聚合 */
const groupedList = computed(() => {
  const map = new Map()
  for (const c of currentList.value) {
    const key = c.stallId ?? 'all'
    let g = map.get(key)
    if (!g) {
      g = { stallId: c.stallId, stallName: c.stallName || '平台通用', list: [] }
      map.set(key, g)
    }
    g.list.push(c)
  }
  return [...map.values()].sort((a, b) => b.list.length - a.list.length)
})

const activeGroup = computed(() =>
  activeStallId.value == null
    ? null
    : groupedList.value.find(g => String(g.stallId) === String(activeStallId.value)) || null
)

const activeCoupons = computed(() => activeGroup.value?.list || [])
const orderCount = computed(() => activeCoupons.value.filter(c => c.scope !== 'ITEM').length)
const itemCount = computed(() => activeCoupons.value.filter(c => c.scope === 'ITEM').length)

const filteredCoupons = computed(() => {
  if (scopeFilter.value === 'ALL') return activeCoupons.value
  return activeCoupons.value.filter(c => (c.scope === 'ITEM' ? 'ITEM' : 'ORDER') === scopeFilter.value)
})

const emptyText = computed(() => {
  if (activeTab.value === 'used') return '还没有使用过优惠券'
  if (activeTab.value === 'expired') return '没有已过期的优惠券'
  return '暂无可用优惠券，商家发券后会出现在这里'
})

function enterMerchant(g) {
  activeStallId.value = g.stallId
  scopeFilter.value = 'ALL'
}

function leaveMerchant() {
  activeStallId.value = null
}

/** 领取公开券 */
async function claim(c) {
  if (c.claimed) return
  try {
    await claimCoupon(c.id)
    c.claimed = true
    ElMessage.success(`已领取「${c.name}」，可在“可用”里查看`)
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '领取失败，可能已被领完')
  }
}

function goUse(c) {
  if (c.stallId) {
    router.push(`/stalls/${c.stallId}`)
  } else {
    router.push('/dishes')
  }
}

async function load() {
  loading.value = true
  try {
    const res = await myCoupons()
    coupons.value = res.data || []
  } catch {
    coupons.value = []
  } finally {
    loading.value = false
  }
  // 领券中心单独拉取，不阻塞“我的券”展示
  try {
    const res2 = await availableCoupons()
    claimable.value = (res2.data || []).filter(c => !c.claimed && (!c.endTime || new Date(String(c.endTime).replace(/-/g, '/')) >= new Date()))
  } catch {
    claimable.value = []
  }
}

onMounted(load)
onActivated(load)
</script>

<style scoped>
.coupons-page {
  max-width: 1240px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.coupon-back {
  flex-shrink: 0;
  padding-bottom: 0;
  font-size: 16px;
}

.coupon-tabs {
  margin-bottom: 20px;
}

/* 领券中心：票面 + 票内底部领取 */
.ct-wrap {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-xs);
  transition: transform 0.18s var(--ease-apple), box-shadow 0.18s var(--ease-apple);
}

.ct-wrap:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.ct-wrap :deep(.ct) {
  border: none;
  border-radius: 0;
  box-shadow: none;
  min-height: 118px;
}

.coupon-grid :deep(.ct-stub),
.ct-wrap :deep(.ct-stub) {
  background: #fff3e8;
  color: var(--brand-dark);
}

.coupon-grid :deep(.ct-stub::before),
.coupon-grid :deep(.ct-stub::after),
.ct-wrap :deep(.ct-stub::before),
.ct-wrap :deep(.ct-stub::after) {
  background: #fff;
}

.coupon-grid :deep(.ct-cond),
.ct-wrap :deep(.ct-cond) {
  color: var(--text-2);
}

.coupon-grid :deep(.ct.used .ct-stub),
.coupon-grid :deep(.ct.expired .ct-stub) {
  background: #f2f2f5;
  color: var(--text-3);
}

.claim-foot {
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
  background: #fff;
}

.claim-btn {
  height: 30px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: var(--text-1);
  color: #fff;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), transform 0.12s var(--ease-apple), opacity 0.16s;
}

.claim-btn:hover:not(:disabled) {
  background: #333336;
}

.claim-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.claim-btn:disabled {
  background: #f5f5f7;
  color: var(--text-3);
  cursor: not-allowed;
}

/* 第一层：商家清单 */
.merchant-card-box {
  padding: 6px 22px;
}

.merchant-row {
  width: 100%;
  padding: 14px 2px;
  border-top: none;
  border-left: none;
  border-right: none;
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.merchant-row:hover {
  background: transparent;
}

.merchant-row:hover .ap-row-title {
  color: var(--brand-dark);
}

.merchant-row .ap-row-main {
  display: block;
}

.merchant-row .ap-row-title,
.merchant-row .ap-row-sub {
  display: block;
}

.merchant-icon {
  color: var(--text-2);
}

.merchant-count {
  flex-shrink: 0;
  min-width: 28px;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 550;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;
}

.merchant-arrow {
  flex-shrink: 0;
  color: #c7c7cc;
}

.merchant-skeleton {
  height: 46px;
  margin: 14px 0;
  border-radius: 10px;
  background: #f5f5f7;
}

/* 第二层：安静的券类型文字切换 */
.scope-switch {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 12px;
}

.scope-label {
  margin-right: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.scope-btn {
  padding: 4px 10px;
  border: none;
  border-radius: 999px;
  background: transparent;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--text-3);
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}

.scope-btn:hover {
  color: var(--text-1);
}

.scope-btn.active {
  background: #fff;
  color: var(--text-1);
  font-weight: 600;
  box-shadow: 0 0 0 1px var(--border), 0 1px 2px rgba(0, 0, 0, 0.03);
}

/* 券网格 */
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  min-height: 160px;
}

.coupon-skeleton {
  height: 158px;
  border-radius: 20px;
  background: #f5f5f7;
  border: 1px solid var(--border);
}

@media (max-width: 1180px) {
  .coupon-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .coupon-grid {
    grid-template-columns: 1fr;
  }
}
</style>
