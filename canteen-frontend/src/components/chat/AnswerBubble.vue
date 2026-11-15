<template>
  <div class="bubble">
    <p v-for="(line, i) in lines" :key="i" class="bubble-line">{{ line }}</p>

    <div v-if="answer.items?.length" class="item-list">
      <div
        v-for="item in answer.items"
        :key="item.id + item.title"
        class="chat-item"
        :class="{ 'chat-item-clickable': item.link }"
        @click="item.link && go(item.link)"
      >
        <img v-if="item.image" class="chat-item-thumb" :src="item.image" :alt="item.title" loading="lazy">

        <div class="chat-item-main">
          <div class="chat-item-title">
            <span class="chat-item-name">{{ item.title }}</span>
            <span v-if="item.badge" class="badge" :class="'badge-' + item.badgeType">{{ item.badge }}</span>
          </div>
          <div class="chat-item-sub">{{ item.subtitle }}</div>
          <div v-if="item.meta" class="chat-item-meta">
            <template v-for="(part, idx) in item.meta.split('｜')" :key="idx">
              <span v-if="idx" class="meta-sep">·</span>
              <span :class="{ 'chat-item-price': idx === 0 && part.trim().startsWith('¥') }">{{ part.trim() }}</span>
            </template>
          </div>
        </div>
        <button
          v-if="item.canAddToCart && item.id"
          class="chat-item-cart"
          :disabled="addingId === item.id"
          @click.stop="addCart(item)"
        >加购</button>
        <span v-if="item.link" class="chat-item-go">
          {{ item.linkLabel || '查看' }}
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 6l6 6-6 6"/>
          </svg>
        </span>
      </div>
    </div>

    <div v-if="answer.actions?.length" class="action-row">
      <button
        v-for="action in answer.actions"
        :key="action.label + action.path"
        class="action-btn"
        :class="{ 'action-primary': action.primary }"
        @click="runAction(action)"
      >{{ action.label }}</button>
    </div>

    <div v-if="answer.sources?.length" class="sources" :title="answer.sources.join(' · ')">
      <span class="sources-label">来源</span>{{ answer.sources.join(' · ') }}
    </div>

    <div v-if="answer.quickReplies?.length" class="quick">
      <span
        v-for="q in answer.quickReplies"
        :key="q"
        class="quick-chip"
        @click="emit('ask', q)"
      >{{ q }}</span>
    </div>

    <div class="bubble-foot">
      <template v-if="rated === null">
        <span class="foot-label">有帮助吗</span>
        <button class="foot-btn" @click="rate(1)">有帮助</button>
        <button class="foot-btn" @click="rate(0)">没帮助</button>
      </template>
      <span v-else class="foot-done">谢谢反馈</span>
      <button
        v-if="answer.canFeedback"
        class="foot-btn"
        @click="go('/feedbacks')"
      >
        去留言反馈
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { rateChat } from '@/api/chat'
import { addToCart } from '@/api/cart'
import { useCartStore } from '@/store/cart'

const props = defineProps({
  answer: { type: Object, required: true }
})
const emit = defineEmits(['ask'])
const router = useRouter()
const cartStore = useCartStore()
const rated = ref(null)
const addingId = ref(null)

const lines = computed(() => String(props.answer.text || '').split('\n').filter(Boolean))

function go(path) {
  router.push(path)
}

function runAction(action) {
  if (!action?.path) return
  // 购物车是全局面板而非独立路由，/cart 直接打开购物车抽屉
  if (action.path === '/cart') {
    cartStore.openDrawer()
    return
  }
  router.push(action.path)
}

async function addCart(item) {
  if (addingId.value) return
  addingId.value = item.id
  try {
    await addToCart(item.id, 1)
    window.dispatchEvent(new CustomEvent('canteen:cart-changed'))
    ElMessage.success(`已加入购物车：${item.title}`)
    // 加购成功后直接打开购物车抽屉，让用户立刻看到已加购的商品
    cartStore.openDrawer()
  } catch {
    // 请求层已统一弹出错误提示，这里保持静默
  } finally {
    addingId.value = null
  }
}

async function rate(helpful) {
  if (!props.answer.logId) return
  try {
    await rateChat(props.answer.logId, helpful)
    rated.value = helpful
  } catch {
    rated.value = helpful
  }
}
</script>

<style scoped>
/* 回答直接落在浮层画布上，不再套第二层卡片 */
.bubble {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  box-shadow: none;
  color: var(--text-1);
}

.bubble-line {
  margin: 0 0 6px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-line:last-of-type {
  margin-bottom: 0;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

/* 只有结果条目用白卡承载 */
.chat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 14px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.16s var(--ease-apple), box-shadow 0.16s var(--ease-apple),
    transform 0.16s var(--ease-apple);
}

.chat-item-clickable {
  cursor: pointer;
}

.chat-item-clickable:hover {
  border-color: rgba(0, 0, 0, 0.07);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.chat-item-thumb {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
  background: var(--bg);
}

.chat-item-main {
  flex: 1;
  min-width: 0;
}

.chat-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.chat-item-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
}

.chat-item-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-item-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0 5px;
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}

.meta-sep {
  color: var(--text-3);
}

.chat-item-price {
  color: var(--brand-dark);
  font-weight: 600;
}

.chat-item-go {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 500;
  color: var(--brand-dark);
  white-space: nowrap;
}

.chat-item-clickable:hover .chat-item-go {
  opacity: 0.7;
}

.chat-item-cart {
  flex-shrink: 0;
  height: 28px;
  padding: 0 12px;
  border: none;
  border-radius: 999px;
  background: #1d1d1f;
  color: #fff;
  font-size: 12px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), opacity 0.15s var(--ease-apple),
    transform 0.12s var(--ease-apple);
}

.chat-item-cart:hover:not(:disabled) {
  background: #333336;
}

.chat-item-cart:active:not(:disabled) {
  transform: scale(0.97);
}

.chat-item-cart:disabled {
  opacity: 0.4;
  cursor: default;
}

.badge {
  flex-shrink: 0;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 550;
  line-height: 18px;
  background: rgba(0, 0, 0, 0.055);
  color: var(--text-2);
  white-space: nowrap;
}

.badge-success { background: #e7f7ec; color: #17803d; }
.badge-warning { background: #fdf1dc; color: #a55a00; }
.badge-danger  { background: #fdeaea; color: #b42318; }
.badge-info    { background: #e8f1fd; color: #175cd3; }

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.action-btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-1);
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), border-color 0.15s var(--ease-apple),
    transform 0.12s var(--ease-apple);
}

.action-btn:hover {
  background: var(--bg);
}

.action-btn:active {
  transform: scale(0.97);
}

.action-primary {
  border-color: transparent;
  background: #1d1d1f;
  color: #fff;
}

.action-primary:hover {
  background: #333336;
}

.sources {
  margin-top: 9px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sources-label {
  margin-right: 5px;
  color: var(--text-3);
}

.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.quick-chip {
  height: 26px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s var(--ease-apple), color 0.15s var(--ease-apple);
}

.quick-chip:hover {
  border-color: rgba(0, 0, 0, 0.14);
  color: var(--text-1);
}

/* 反馈收成右下角的一行轻量文字，不再用虚线分区 */
.bubble-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 8px;
}

.foot-label,
.foot-done {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-3);
}

.foot-btn {
  border: none;
  background: transparent;
  padding: 2px 5px;
  border-radius: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  font-family: inherit;
  color: var(--text-3);
  cursor: pointer;
  transition: background 0.15s var(--ease-apple), color 0.15s var(--ease-apple);
}

.foot-btn:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--text-1);
}
</style>
