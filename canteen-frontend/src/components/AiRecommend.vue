<template>
  <div class="ai-rec">
    <!-- 头部：AI 小助手 + 打招呼 + 机器人形象 -->
    <div class="ai-rec-head">
      <div class="ai-rec-hd-left">
        <div class="ai-rec-title">AI 小助手</div>
        <div class="ai-rec-sub">今天想吃点什么？</div>
      </div>
      <img src="/ui/assistant/happy.png" class="ai-rec-mascot" alt="AI 小助手">
    </div>

    <!-- 快捷提问：点击直接唤醒全局 AI 小助手并发送问题 -->
    <button class="ai-rec-ask" type="button" @click="generate">
      <span class="ai-rec-ask-text">推荐一下今天的热销菜</span>
      <span class="ai-rec-ask-icon">
        <el-icon><ArrowRight /></el-icon>
      </span>
    </button>

    <!-- 推荐结果 -->
    <div class="ai-rec-result">
      <div class="ai-rec-result-head">
        <span class="ai-rec-result-title">热门推荐</span>
        <button class="ai-rec-refresh" type="button" @click="shuffle">
          换一批
          <el-icon><Refresh /></el-icon>
        </button>
      </div>

      <div v-if="thinking" class="ai-rec-skeleton">
        <div v-for="i in 3" :key="'sk-' + i" class="skeleton-row">
          <span class="skeleton-thumb"></span>
          <span class="skeleton-lines"><i></i><i></i></span>
        </div>
      </div>
      <div v-else-if="picks.length" class="ai-rec-dishes">
        <div v-for="d in picks" :key="'ai-' + d.dishId" class="ai-rec-dish" @click="go(d)">
          <div class="ai-rec-img" :style="{ background: grad(d.category) }">
            <img v-if="d.image" :src="d.image" :alt="d.dishName" loading="lazy" @error="$event.target.style.display='none'">
            <span v-else class="ai-rec-initial">{{ (d.dishName || '菜').slice(0, 1) }}</span>
          </div>
          <div class="ai-rec-body">
            <div class="ai-rec-name" :title="d.dishName">{{ d.dishName }}</div>
            <div class="ai-rec-price">¥{{ price(d.price) }}</div>
          </div>
          <el-icon class="ai-rec-chevron"><ArrowRight /></el-icon>
        </div>
      </div>
      <div v-else class="ai-rec-empty">AI 小助手正在盘点今日热销菜…</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { coverGradient } from '@/utils/dishImage'

const props = defineProps({
  dishes: { type: Array, default: () => [] }
})

const router = useRouter()
const offset = ref(0)
const thinking = ref(false)

const picks = computed(() => {
  const list = props.dishes || []
  if (!list.length) return []
  const n = list.length
  return Array.from({ length: Math.min(3, n) }, (_, i) => list[(offset.value + i) % n])
})

function shuffle() {
  const n = (props.dishes || []).length
  if (n > 0) offset.value = (offset.value + 3) % n
}

// 「发送」把问题发给 AI 小助手：唤醒全局小助手窗口并自动发送该问题
function generate() {
  const question = '推荐一下今天的热销菜吧！'
  window.dispatchEvent(new CustomEvent('canteen:assistant-ask', { detail: question }))
}

function go(d) {
  // 点菜品先跳对应档口页并高亮，再点菜品才进详情（与美食广场主网格一致）
  if (!d || !d.dishId) return
  if (d.stallId) {
    router.push(`/stalls/${d.stallId}?highlight=${d.dishId}`)
  } else {
    router.push(`/dishes/${d.dishId}`)
  }
}

function grad(cat) {
  return coverGradient(cat)
}

function price(p) {
  if (p == null) return '—'
  return Number(p).toFixed(2)
}
</script>

<style scoped>
.ai-rec {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
}

.ai-rec-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  position: relative;
}

.ai-rec-hd-left {
  min-width: 0;
}

.ai-rec-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}

.ai-rec-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-3);
}

.ai-rec-mascot {
  width: 36px;
  height: 36px;
  object-fit: contain;
  flex-shrink: 0;
}

/* 快捷提问：浅灰胶囊，右侧黑色圆形箭头 */
.ai-rec-ask {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  margin-top: 14px;
  padding: 6px 6px 6px 14px;
  border: none;
  border-radius: 999px;
  background: #f2f2f5;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background-color 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.ai-rec-ask:hover {
  background: #ebebef;
}

.ai-rec-ask:active {
  transform: scale(0.99);
}

.ai-rec-ask-text {
  min-width: 0;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-rec-ask-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #1d1d1f;
  color: #fff;
  font-size: 12px;
}

.ai-rec-result {
  margin-top: 18px;
}

.ai-rec-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.ai-rec-result-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-3);
}

.ai-rec-refresh {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  padding: 2px 0;
  font-family: inherit;
  font-size: 12px;
  color: var(--text-3);
  cursor: pointer;
  transition: color 0.15s var(--ease-apple);
}

.ai-rec-refresh:hover {
  color: var(--text-1);
}

.ai-rec-refresh .el-icon {
  font-size: 11px;
}

.ai-rec-dishes {
  display: flex;
  flex-direction: column;
}

.ai-rec-dish {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 8px;
  margin: 0 -8px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple);
}

.ai-rec-dish:hover {
  background: #fafafa;
}

.ai-rec-img {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 11px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-rec-img img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ai-rec-initial {
  font-size: 18px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.ai-rec-body {
  flex: 1;
  min-width: 0;
}

.ai-rec-name {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-rec-price {
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.ai-rec-chevron {
  flex-shrink: 0;
  font-size: 12px;
  color: #c7c7cc;
}

.ai-rec-empty {
  padding: 18px 0;
  font-size: 12px;
  color: var(--text-3);
  text-align: center;
}

/* 加载骨架：保持与真实行一致的尺寸，避免跳动 */
.ai-rec-skeleton {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 4px;
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 8px;
  margin: 0 -8px;
}

.skeleton-thumb {
  width: 44px;
  height: 44px;
  border-radius: 11px;
  background: #f2f2f5;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skeleton-lines i {
  display: block;
  height: 9px;
  border-radius: 5px;
  background: #f2f2f5;
}

.skeleton-lines i:first-child {
  width: 62%;
}

.skeleton-lines i:last-child {
  width: 34%;
  height: 8px;
}

.skeleton-thumb,
.skeleton-lines i {
  animation: skeleton-pulse 1.4s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-thumb,
  .skeleton-lines i {
    animation: none;
  }
  .ai-rec-ask:active {
    transform: none;
  }
}
</style>
