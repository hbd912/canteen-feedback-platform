<template>
  <teleport to="body">
    <transition name="mask">
      <div v-if="cartStore.drawerOpen" class="cart-mask" @click="close" />
    </transition>

    <transition name="drawer">
      <aside v-if="cartStore.drawerOpen" class="cart-drawer" role="dialog" aria-label="购物车">
        <header class="dr-head">
          <div class="head-main">
            <template v-if="step === 'list'">
              <h3>购物车</h3>
              <p>{{ cart.totalQuantity || 0 }} 件</p>
            </template>
            <template v-else>
              <button class="dr-back" @click="step = 'list'">
                <el-icon :size="13"><ArrowLeft /></el-icon>
                返回购物车
              </button>
              <h3>确认订单</h3>
              <p>{{ checkedQuantity }} 件已选</p>
            </template>
          </div>
          <div class="head-actions">
            <button v-if="step === 'list'" class="dr-clear" @click="clearAll">清空购物车</button>
            <button class="dr-close" title="关闭" @click="close">
              <el-icon :size="17"><Close /></el-icon>
            </button>
          </div>
        </header>

        <div v-if="step === 'list'" class="dr-body">
          <div v-if="!allItems.length" class="ap-empty dr-empty">
            <div class="ap-empty-mark">
              <el-icon :size="20"><ShoppingCart /></el-icon>
            </div>
            <div class="ap-empty-title">购物车还空着</div>
            <div class="empty-sub">去菜单挑几道喜欢的菜吧</div>
            <button class="ap-btn dr-empty-go" @click="goDishes">去挑几个菜</button>
          </div>

          <div v-else class="items-card">
            <template v-for="(g, gi) in groups" :key="g.stallId">
              <div class="stall-title" :class="{ first: gi === 0 }">
                <span class="stall-name">{{ g.stallName }}</span>
                <span class="stall-window">{{ g.windowNo }} 号窗口</span>
                <span class="stall-qty">{{ g.totalQuantity }} 件</span>
              </div>
              <div
                v-for="item in g.items"
                :key="item.id"
                class="row"
                :class="{ off: item.dishStatus !== 1 }"
              >
                <label class="check" @click.prevent="toggleItem(item)">
                  <span class="box" :class="{ checked: isChecked(item) }">
                    <el-icon v-if="isChecked(item)" :size="11"><Check /></el-icon>
                  </span>
                </label>

                <div class="thumb" :style="{ background: coverGradient(item.category) }">
                  <img v-if="item.image && !broken[item.id]" :src="item.image" alt="" @error="broken[item.id] = true">
                  <span v-else>{{ (item.dishName || '菜').slice(0, 1) }}</span>
                </div>

                <div class="info">
                  <div class="name">
                    {{ item.dishName }}
                    <em v-if="item.dishStatus !== 1">已下架</em>
                  </div>
                  <div class="unit">¥{{ item.price }}<small> / 份</small></div>
                </div>

                <div class="stepper">
                  <button :disabled="displayQty(item) <= 1" @click="changeQty(item, displayQty(item) - 1)">
                    <el-icon :size="12"><Minus /></el-icon>
                  </button>
                  <span>{{ displayQty(item) }}</span>
                  <button :disabled="displayQty(item) >= maxQty" @click="changeQty(item, displayQty(item) + 1)">
                    <el-icon :size="12"><Plus /></el-icon>
                  </button>
                </div>

                <button class="del" title="移除" @click="removeItem(item)">
                  <el-icon :size="15"><Delete /></el-icon>
                </button>
              </div>
            </template>
          </div>
        </div>

        <div v-else class="dr-body confirm">
          <div class="cf-block">
            <div class="cf-label">取餐信息</div>
            <div class="cf-field">
              <span class="cf-field-label">取餐时间</span>
              <el-date-picker
                v-model="form.pickupTime"
                type="datetime"
                placeholder="选择取餐时间"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm:ss"
                :disabled-date="disabledDate"
                class="cf-picker"
              />
            </div>
            <div class="cf-field">
              <span class="cf-field-label">备注 <small>口味、忌口、打包等</small></span>
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="选填"
              />
            </div>
          </div>

          <div class="cf-block">
            <div class="cf-label">优惠券</div>
            <div v-if="selectedStallIds.length !== 1" class="cf-coupon-empty">
              {{ selectedStallIds.length > 1 ? '使用优惠券时请只结算一个档口' : '暂无可结算的菜品' }}
            </div>
            <div v-else-if="couponLoading" class="cf-coupon-empty">加载中…</div>
            <div v-else-if="!usableCoupons.length" class="cf-coupon-empty">该档口暂无可用优惠券</div>
            <div v-else class="cf-coupons">
              <button
                v-for="c in displayCoupons"
                :key="c.id"
                class="cf-coupon"
                :class="{ active: couponId === c.id }"
                @click="couponId = c.id"
              >
                <span class="cf-coupon-val">{{ couponFace(c) }}</span>
                <span class="cf-coupon-main">
                  <span class="cf-coupon-name">{{ c.name }}</span>
                  <span class="cf-coupon-cond">{{ couponCond(c) }}</span>
                </span>
              </button>
              <button class="cf-coupon-none" :class="{ active: couponId === null }" @click="couponId = null">
                不使用优惠券
              </button>
              <div v-if="usableCoupons.length > displayCoupons.length" class="cf-coupon-more">
                还有 {{ usableCoupons.length - displayCoupons.length }} 张可用券，可在「我的优惠券」查看
              </div>
            </div>
            <div v-if="couponDiscount > 0" class="cf-coupon-saved">
              已优惠 <b>¥{{ couponDiscount.toFixed(2) }}</b>
            </div>
          </div>

          <div class="cf-block">
            <div class="cf-label">
              商品
              <small>{{ checkedItems.length }} 种 · {{ groups.length }} 个档口</small>
            </div>
            <div class="cf-list">
              <div v-for="item in checkedItems" :key="item.id" class="cf-row">
                <span class="cf-name">{{ item.dishName }}<i class="cf-stall">{{ item.stallName }}</i></span>
                <span class="cf-qty">×{{ displayQty(item) }}</span>
                <span class="cf-amt">¥{{ lineAmount(item) }}</span>
              </div>
            </div>
            <p class="cf-tip">不同档口将自动拆单分别支付；打包费、配送费按下单结果计算</p>
          </div>
        </div>

        <footer v-if="groups.length" class="dr-foot">
          <template v-if="step === 'list'">
            <label class="check foot-check" @click.prevent="toggleAll">
              <span class="box" :class="{ checked: allChecked, half: !allChecked && checkedItems.length > 0 }">
                <el-icon v-if="allChecked" :size="11"><Check /></el-icon>
                <i v-else-if="checkedItems.length" class="half-bar" />
              </span>
              全选
            </label>
            <div class="foot-sum">
              <span>已选 <b>{{ checkedQuantity }}</b> 件</span>
              <strong>¥{{ checkedAmount }}</strong>
            </div>
            <button class="ap-btn ap-btn--brand foot-go" :disabled="!checkedQuantity" @click="toConfirm">去结算</button>
          </template>
          <template v-else>
            <div class="foot-sum">
              <span>
                {{ checkedQuantity }} 件
                <template v-if="couponDiscount > 0"> · 券减 ¥{{ couponDiscount.toFixed(2) }}</template>
              </span>
              <strong>¥{{ payableAmount }}</strong>
            </div>
            <button class="ap-btn ap-btn--brand foot-go" :disabled="submitting" @click="submit">
              {{ submitting ? '提交中…' : '提交并支付' }}
            </button>
          </template>
        </footer>
      </aside>
    </transition>
  </teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkoutCart, clearCart, getCart, removeCartItem, updateCartQuantity } from '@/api/cart'
import { myCoupons } from '@/api/coupon'
import { useCartStore } from '@/store/cart'
import { usePayStore } from '@/store/pay'
import { coverGradient } from '@/utils/dishImage'

const router = useRouter()
const cartStore = useCartStore()
const payStore = usePayStore()

const cart = ref({ groups: [], totalQuantity: 0, totalAmount: '0.00', maxQuantityPerDish: 10 })
const groups = computed(() => cart.value.groups || [])
const maxQty = computed(() => cart.value.maxQuantityPerDish || 10)

const step = ref('list')
const submitting = ref(false)
const form = reactive({ pickupTime: '', remark: '' })

/** 用户主动取消勾选的条目 id，其余默认选中（加购新品即自动进入结算范围） */
const deselected = reactive({})
/** 数量本地覆盖，点一下立刻响应，不等待接口 */
const localQty = reactive({})
const broken = reactive({})

/** 所有档口的菜品合并成一个平铺列表，不分档口展示 */
const allItems = computed(() =>
  groups.value.flatMap(g => (g.items || []).map(i => ({ ...i })))
)
/** 在售菜品（可勾选） */
const salableItems = computed(() => allItems.value.filter(i => i.dishStatus === 1))

const checkedItems = computed(() => salableItems.value.filter(i => isChecked(i)))
const allChecked = computed(() => salableItems.value.length > 0 && salableItems.value.every(i => isChecked(i)))
const checkedQuantity = computed(() => checkedItems.value.reduce((sum, i) => sum + displayQty(i), 0))
const checkedAmount = computed(() =>
  checkedItems.value.reduce((sum, i) => sum + Number(i.price) * displayQty(i), 0).toFixed(2)
)

/* ---------- 优惠券 ---------- */
const coupons = ref([])
const couponLoading = ref(false)
const couponId = ref(null)

/** 本次结算涉及的档口（用券只允许单一档口，与后端规则一致） */
const selectedStallIds = computed(() => [...new Set(checkedItems.value.map(i => i.stallId).filter(Boolean))])

/** 券的抵扣基数：整单券=已选小计；单份菜券=适用菜品的一份单价（不累加） */
function couponBase(c) {
  const items = checkedItems.value
  if (c.scope !== 'ITEM') return Number(checkedAmount.value)
  const candidates = c.dishId
    ? items.filter(i => String(i.dishId) === String(c.dishId))
    : items
  if (!candidates.length) return 0
  return Math.max(...candidates.map(i => Number(i.price || 0)))
}

function discountOf(c) {
  const base = couponBase(c)
  if (base <= 0) return 0
  if (c.type === 'DISCOUNT') {
    return Math.max(0, base * (1 - Number(c.value || 0)))
  }
  return Math.min(base, Number(c.value || 0))
}

/** 当前可用的券：未使用、未过期、属于本档口、满足门槛且本单确实能用，按抵扣力度从大到小 */
const usableCoupons = computed(() => {
  if (selectedStallIds.value.length !== 1) return []
  const stallId = selectedStallIds.value[0]
  const sub = Number(checkedAmount.value)
  return coupons.value
    .filter(c => {
      if (c.status !== 'UNUSED') return false
      if (String(c.stallId) !== String(stallId)) return false
      if (c.endTime && new Date(String(c.endTime).replace(/-/g, '/')) < new Date()) return false
      if (Number(c.minAmount || 0) > sub) return false
      // 单份菜券：本单必须包含适用菜品，否则不展示
      if (c.scope === 'ITEM' && couponBase(c) <= 0) return false
      return true
    })
    .sort((a, b) => discountOf(b) - discountOf(a))
})

/** 结算页只展示最省的前 4 张，避免券多时一屏拉很长 */
const displayCoupons = computed(() => usableCoupons.value.slice(0, 4))

const chosenCoupon = computed(() => usableCoupons.value.find(c => c.id === couponId.value) || null)

const couponDiscount = computed(() => {
  const c = chosenCoupon.value
  return c ? discountOf(c) : 0
})

const payableAmount = computed(() =>
  Math.max(0.01, Number(checkedAmount.value) - couponDiscount.value).toFixed(2)
)

function couponFace(c) {
  if (c.type === 'DISCOUNT') {
    const v = Number(c.value || 0)
    if (!v) return '免单'
    const zhe = v * 10
    return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`
  }
  return `¥${Number(c.value || 0).toFixed(0)}`
}

function couponCond(c) {
  const min = Number(c.minAmount || 0)
  const base = min > 0 ? `满${min.toFixed(0)}可用` : '无门槛'
  const scope = c.scope === 'ITEM'
    ? (c.dishName ? `限${c.dishName}一份` : '限单份菜')
    : '整单'
  return `${scope} · ${base}`
}

async function loadCoupons() {
  couponId.value = null
  coupons.value = []
  if (selectedStallIds.value.length !== 1) return
  couponLoading.value = true
  try {
    const res = await myCoupons()
    coupons.value = res.data || []
    // 自动选最省的一张（usableCoupons 已按抵扣力度排序），用户可手动改
    couponId.value = usableCoupons.value.length ? usableCoupons.value[0].id : null
  } catch {
    coupons.value = []
  } finally {
    couponLoading.value = false
  }
}

function isChecked(item) {
  return !deselected[item.id]
}

function displayQty(item) {
  return localQty[item.id] ?? item.quantity
}

function lineAmount(item) {
  return (Number(item.price) * displayQty(item)).toFixed(2)
}

function toggleItem(item) {
  if (item.dishStatus !== 1) return
  if (deselected[item.id]) delete deselected[item.id]
  else deselected[item.id] = true
}

function toggleAll() {
  const target = !allChecked.value
  for (const item of salableItems.value) {
    if (target) delete deselected[item.id]
    else deselected[item.id] = true
  }
}

async function loadCart() {
  const res = await getCart()
  cart.value = res.data || { groups: [] }
  cartStore.count = cart.value.totalQuantity || 0
  cartStore.totalAmount = cart.value.totalAmount ?? '0.00'
  cartStore.stallCount = groups.value.length
  if (!groups.value.length) {
    step.value = 'list'
  }
}

async function changeQty(item, qty) {
  if (qty < 1 || qty > maxQty.value) return
  localQty[item.id] = qty
  try {
    await updateCartQuantity(item.id, qty)
    await loadCart()
    delete localQty[item.id]
  } catch {
    delete localQty[item.id]
  }
}

function removeItem(item) {
  ElMessageBox.confirm(`把「${item.dishName}」从购物车移除？`, '提示', { type: 'warning' })
    .then(async () => {
      await removeCartItem(item.id)
      delete deselected[item.id]
      delete localQty[item.id]
      await loadCart()
      cartStore.refresh()
    })
    .catch(() => {})
}

function clearAll() {
  const total = groups.value.reduce((sum, g) => sum + (g.totalQuantity || 0), 0)
  ElMessageBox.confirm(`清空购物车的 ${total} 件商品？`, '提示', { type: 'warning' })
    .then(async () => {
      await clearCart()
      deselected && Object.keys(deselected).forEach(k => delete deselected[k])
      await loadCart()
      cartStore.refresh()
      ElMessage.success('已清空购物车')
    })
    .catch(() => {})
}

function toConfirm() {
  if (!checkedQuantity.value) {
    ElMessage.warning('请先勾选要结算的菜品')
    return
  }
  form.pickupTime = form.pickupTime || defaultPickupTime()
  step.value = 'confirm'
  loadCoupons()
}

function defaultPickupTime() {
  const d = new Date(Date.now() + 30 * 60000)
  d.setSeconds(0, 0)
  d.setMinutes(Math.ceil(d.getMinutes() / 5) * 5)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

function disabledDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const limit = new Date(today)
  limit.setDate(limit.getDate() + 7)
  return date.getTime() < today.getTime() || date.getTime() > limit.getTime()
}

async function submit() {
  if (!form.pickupTime) {
    ElMessage.warning('请选择取餐时间')
    return
  }
  submitting.value = true
  try {
    const res = await checkoutCart({
      pickupTime: form.pickupTime,
      remark: form.remark || undefined,
      itemIds: checkedItems.value.map(i => i.id),
      userCouponId: couponId.value || undefined
    })
    // 后端跨档口会自动拆成多笔订单，返回数组
    const list = Array.isArray(res.data) ? res.data : [res.data]
    step.value = 'list'
    form.remark = ''
    form.pickupTime = ''
    couponId.value = null
    coupons.value = []
    for (const key of Object.keys(localQty)) delete localQty[key]
    await loadCart()
    cartStore.refresh()

    // 余额扣款：下单即支付成功，直接提示，不再弹收银台
    if (list.length && list.every(s => s.paid)) {
      const total = list.reduce((sum, s) => sum + Number(s.amount || 0), 0)
      ElMessage.success(`支付成功，已从余额扣款 ¥${total.toFixed(2)}`)
    } else {
      payStore.openBatch(list)
    }
    cartStore.closeDrawer()
  } finally {
    submitting.value = false
  }
}

function close() {
  cartStore.closeDrawer()
}

function goDishes() {
  cartStore.closeDrawer()
  router.push('/dishes')
}

const openDrawer = () => cartStore.openDrawer()
const closeDrawer = () => cartStore.closeDrawer()
const onCartChanged = () => {
  cartStore.refresh()
  // 抽屉已经打开时，也要重新拉取列表，否则 AI 加购后抽屉里看不到新加的商品
  if (cartStore.drawerOpen) loadCart()
}

watch(() => cartStore.drawerOpen, value => {
  if (value) loadCart()
})

onMounted(() => {
  window.addEventListener('canteen:open-cart', openDrawer)
  window.addEventListener('canteen:close-cart', closeDrawer)
  window.addEventListener('canteen:cart-changed', onCartChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('canteen:open-cart', openDrawer)
  window.removeEventListener('canteen:close-cart', closeDrawer)
  window.removeEventListener('canteen:cart-changed', onCartChanged)
})
</script>

<style scoped>
/* ---------- 抽屉外壳：白色大圆角面板 + 中性灰内容区 ---------- */
.cart-mask {
  position: fixed;
  inset: 0;
  z-index: 1900;
  background: rgba(0, 0, 0, 0.32);
  -webkit-backdrop-filter: blur(2px);
  backdrop-filter: blur(2px);
}

.cart-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 1901;
  width: min(480px, 100vw);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 24px 0 0 24px;
  overflow: hidden;
  box-shadow: -16px 0 48px rgba(0, 0, 0, 0.16);
  color: var(--text-1);
  font-family: var(--font-sans);
}

/* ---------- 头部 ---------- */
.dr-head {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: calc(20px + env(safe-area-inset-top, 0px)) 20px 14px;
  background: #fff;
}

.dr-head h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  line-height: 1.2;
  color: var(--text-1);
}

.dr-head p {
  margin: 5px 0 0;
  font-size: 13px;
  color: #86868b;
}

.dr-back {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin: 0 0 9px;
  padding: 0;
  border: none;
  background: transparent;
  color: #86868b;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.16s var(--ease-apple);
}

.dr-back:hover {
  color: var(--text-1);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.dr-clear {
  padding: 6px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #86868b;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}

.dr-clear:hover {
  background: rgba(0, 0, 0, 0.04);
  color: #d92d20;
}

.dr-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}

.dr-close:hover {
  background: #f0f0f2;
  color: var(--text-1);
}

/* ---------- 内容区 ---------- */
.dr-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px 16px 20px;
  background: #f5f5f7;
}

/* 空态 */
.dr-empty {
  padding: 72px 24px 48px;
}

.empty-sub {
  font-size: 12.5px;
  color: #a1a1a6;
}

.dr-empty-go {
  margin-top: 18px;
}

/* 列表：整张抽屉只保留这一层白色圆角表面 */
.items-card {
  background: #fff;
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.stall-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 6px 16px 4px;
}

.stall-title.first {
  padding-top: 16px;
}

.stall-title:not(.first) {
  margin-top: 4px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f2;
}

.stall-name {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: #86868b;
}

.stall-window {
  font-size: 11px;
  color: #a1a1a6;
}

.stall-qty {
  margin-left: auto;
  font-size: 11px;
  color: #a1a1a6;
  font-variant-numeric: tabular-nums;
}

/* 商品行 */
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 12px 16px;
}

.row + .row {
  border-top: 1px solid #f0f0f2;
}

.row.off {
  opacity: 0.55;
}

.row .check {
  width: 32px;
  height: 32px;
  justify-content: center;
  margin-left: -6px;
}

.check {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-2);
}

.box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 1.5px solid rgba(0, 0, 0, 0.2);
  border-radius: 50%;
  background: #fff;
  color: #fff;
  transition: background-color 0.16s var(--ease-apple), border-color 0.16s var(--ease-apple);
}

.box.checked {
  border-color: var(--brand);
  background: var(--brand);
}

.box.half {
  border-color: var(--brand);
}

.half-bar {
  width: 9px;
  height: 2.5px;
  border-radius: 2px;
  background: var(--brand);
}

.thumb {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.name em {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 6px;
  background: #f2f2f5;
  color: #86868b;
  font-size: 10px;
  font-style: normal;
  font-weight: 400;
}

.unit {
  margin-top: 3px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.unit small {
  font-weight: 400;
  font-size: 11px;
  color: #a1a1a6;
}

/* 步进器：iOS 分段控件（灰轨道 + 圆角段） */
.stepper {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 32px;
  border-radius: 999px;
  background: #f2f2f5;
  overflow: hidden;
}

.stepper button {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-1);
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple), transform 0.1s var(--ease-apple);
}

.stepper button:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.06);
}

.stepper button:active:not(:disabled) {
  transform: scale(0.94);
}

.stepper button:disabled {
  color: #c7c7cc;
  cursor: not-allowed;
}

.stepper span {
  min-width: 26px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  line-height: 32px;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

/* 删除：常显 35%，悬停变红 */
.del {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #a1a1a6;
  cursor: pointer;
  opacity: 0.35;
  transition: opacity 0.16s var(--ease-apple), background-color 0.16s var(--ease-apple),
    color 0.16s var(--ease-apple);
}

.del:hover {
  opacity: 1;
  background: #ffeceb;
  color: #d92d20;
}

/* ---------- 底部结算栏 ---------- */
.dr-foot {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom, 0px));
  background: #fff;
  border-top: 1px solid rgba(0, 0, 0, 0.055);
}

.foot-check {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-2);
}

.foot-sum {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.foot-sum span {
  font-size: 11.5px;
  color: #86868b;
}

.foot-sum strong {
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.foot-go {
  flex-shrink: 0;
  height: 44px;
  padding: 0 26px;
  font-size: 14px;
  font-weight: 600;
  background: var(--brand);
  box-shadow: none;
}

.foot-go:hover {
  background: #f26100;
}

.foot-go:disabled {
  background: #f2f2f5;
  color: #a1a1a6;
  opacity: 1;
}

/* ---------- 确认步骤：三张白卡 ---------- */
.dr-body.confirm {
  padding-top: 12px;
}

.cf-block {
  margin-bottom: 12px;
  padding: 18px 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 20px;
  box-shadow: var(--shadow-xs);
}

.cf-block:last-child {
  margin-bottom: 0;
}

.cf-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
}

.cf-label small {
  font-weight: 400;
  font-size: 11.5px;
  color: #a1a1a6;
}

.cf-field + .cf-field {
  margin-top: 14px;
}

.cf-field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-2);
}

.cf-field-label small {
  font-size: 11px;
  color: #a1a1a6;
}

.cf-picker {
  width: 100%;
}

/* 优惠券：朴素白行，选中态只加品牌色描边与浅橙底 */
.cf-coupons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cf-coupon {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid rgba(0, 0, 0, 0.055);
  border-radius: 14px;
  background: #fff;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s var(--ease-apple), background-color 0.18s var(--ease-apple);
}

.cf-coupon:hover {
  border-color: rgba(0, 0, 0, 0.14);
}

.cf-coupon.active {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.cf-coupon-val {
  flex-shrink: 0;
  min-width: 46px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.cf-coupon-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cf-coupon-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cf-coupon-cond {
  font-size: 11px;
  color: #a1a1a6;
}

.cf-coupon-none {
  align-self: flex-start;
  padding: 4px 2px;
  border: none;
  background: transparent;
  color: #86868b;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.16s var(--ease-apple);
}

.cf-coupon-none:hover {
  color: var(--text-1);
}

.cf-coupon-none.active {
  color: var(--text-1);
  font-weight: 600;
}

.cf-coupon-empty {
  padding: 2px 0;
  font-size: 12px;
  color: #a1a1a6;
}

.cf-coupon-saved {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-2);
}

.cf-coupon-saved b {
  color: var(--text-1);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.cf-coupon-more {
  padding-top: 6px;
  font-size: 11px;
  color: #a1a1a6;
}

/* 已选商品清单 */
.cf-list {
  display: flex;
  flex-direction: column;
}

.cf-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px solid #f0f0f2;
}

.cf-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.cf-name {
  flex: 1;
  min-width: 0;
  color: var(--text-1);
}

.cf-name .cf-stall {
  margin-left: 6px;
  font-size: 11px;
  font-style: normal;
  color: #a1a1a6;
}

.cf-qty {
  font-size: 12px;
  color: #86868b;
  font-variant-numeric: tabular-nums;
}

.cf-amt {
  min-width: 60px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.cf-tip {
  margin: 12px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: #a1a1a6;
}

/* ---------- 过渡 ---------- */
.mask-enter-active,
.mask-leave-active {
  transition: opacity 0.22s ease;
}

.mask-enter-from,
.mask-leave-to {
  opacity: 0;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.3s var(--ease-apple);
}

.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(100%);
}

@media (max-width: 640px) {
  .cart-drawer {
    width: 100vw;
    border-radius: 0;
  }

  .dr-head {
    padding-left: 16px;
    padding-right: 16px;
  }

  .dr-body {
    padding: 8px 12px 16px;
  }

  .dr-foot {
    padding-left: 14px;
    padding-right: 14px;
  }

  .row,
  .stall-title {
    padding-left: 12px;
    padding-right: 12px;
  }

  .row {
    gap: 10px;
  }

  .row .check {
    margin-left: -4px;
  }

  .foot-sum strong {
    font-size: 20px;
  }

  .foot-go {
    padding: 0 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mask-enter-active,
  .mask-leave-active,
  .drawer-enter-active,
  .drawer-leave-active {
    transition: none;
  }
}
</style>
