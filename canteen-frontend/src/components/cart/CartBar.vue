<template>
  <transition name="cart-bar">
    <div v-if="cartStore.count > 0" class="cart-bar">
      <button class="bar-info" @click="cartStore.openDrawer()">
        <span class="bar-icon" :class="{ bump: bumping }">
          <el-icon :size="20"><ShoppingCart /></el-icon>
          <i class="bar-count">{{ cartStore.count > 99 ? '99+' : cartStore.count }}</i>
        </span>
        <span class="bar-money">
          <b>¥{{ cartStore.totalAmount }}</b>
          <small>{{ cartStore.count }} 件 · {{ cartStore.stallCount }} 个档口</small>
        </span>
      </button>
      <button class="bar-go" @click="cartStore.openDrawer()">去结算</button>
    </div>
  </transition>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useCartStore } from '@/store/cart'

const cartStore = useCartStore()
const bumping = ref(false)
let bumpTimer = null

function bump() {
  bumping.value = false
  clearTimeout(bumpTimer)
  bumpTimer = setTimeout(() => {
    bumping.value = true
    bumpTimer = setTimeout(() => { bumping.value = false }, 320)
  }, 0)
}

function onChanged() {
  cartStore.refresh()
  bump()
}

function onOpen() {
  cartStore.openDrawer()
}

function onClose() {
  cartStore.closeDrawer()
}

onMounted(() => {
  window.addEventListener('canteen:cart-changed', onChanged)
  window.addEventListener('canteen:open-cart', onOpen)
  window.addEventListener('canteen:close-cart', onClose)
  cartStore.refresh()
})

onBeforeUnmount(() => {
  window.removeEventListener('canteen:cart-changed', onChanged)
  window.removeEventListener('canteen:open-cart', onOpen)
  window.removeEventListener('canteen:close-cart', onClose)
  clearTimeout(bumpTimer)
})
</script>

<style scoped>
/* 悬浮结算栏：毛玻璃材质，不再使用大面积深色块 */
.cart-bar {
  position: fixed;
  right: 24px;
  bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  /* 高于小助手窗口(1000)，低于购物车抽屉(1900)，保证加购后购物车栏不被小助手挡住 */
  z-index: 1200;
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  color: var(--text-1);
  font-family: var(--font-sans);
}

.bar-info {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 14px 0 10px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.bar-info:hover {
  background: rgba(0, 0, 0, 0.04);
}

.bar-info:active {
  transform: scale(0.98);
}

.bar-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--text-1);
  transition: transform 0.18s var(--ease-apple);
}

.bar-icon.bump {
  transform: scale(1.06);
}

.bar-count {
  position: absolute;
  top: 0;
  right: -2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  text-align: center;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.92);
}

.bar-money {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 108px;
  line-height: 1.15;
}

.bar-money b {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
  color: var(--text-1);
}

.bar-money small {
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}

/* 唯一主 CTA：纯色胶囊，不加渐变与橙色投影 */
.bar-go {
  flex-shrink: 0;
  height: 40px;
  padding: 0 20px;
  margin-left: 4px;
  border: none;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 13.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.bar-go:hover {
  background: #f26100;
}

.bar-go:active {
  transform: scale(0.97);
}

/* 出现/消失：180ms 淡入 + 8px 上移，去掉弹跳 */
.cart-bar-enter-active,
.cart-bar-leave-active {
  transition: opacity 0.18s ease, transform 0.18s var(--ease-apple);
}

.cart-bar-enter-from,
.cart-bar-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 640px) {
  .cart-bar {
    right: 16px;
    bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  }

  .bar-money {
    min-width: 96px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bar-icon,
  .cart-bar-enter-active,
  .cart-bar-leave-active {
    transition: none;
  }
}
</style>
