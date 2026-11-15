<template>
  <div class="ap-card draft-card">
    <div class="dc-head">
      <img v-if="image" :src="image" class="dc-img" alt="" />
      <div v-else class="dc-img fallback">{{ (draft.name || '菜').slice(0, 1) }}</div>
      <div class="dc-head-main">
        <div class="dc-name-row">
          <div class="dc-name">{{ draft.name || '未命名' }}</div>
          <span class="dc-draft-tag">AI 草稿</span>
        </div>
        <div class="dc-meta">
          <span class="dc-tag">{{ draft.category || '未分类' }}</span>
          <span v-if="draft.priceSuggested" class="dc-tag suggested">AI 建议价</span>
        </div>
      </div>
    </div>

    <div class="dc-desc">{{ draft.description || '暂无描述' }}</div>

    <div class="dc-price-row">
      <span class="dc-label">售价</span>
      <div class="dc-price-input">
        <el-input-number v-model="draft.price" :min="0.01" :max="999" :precision="2" :step="1"
          size="small" controls-position="right" />
        <span class="dc-unit">元</span>
      </div>
    </div>
    <div v-if="draft.note" class="dc-note">{{ draft.note }}</div>

    <template v-if="uploaded">
      <div class="ap-divider dc-divider"></div>
      <div class="dc-uploaded">
        <el-icon :size="15" color="#34c759"><CircleCheckFilled /></el-icon>
        已提交审核，等待管理员通过
      </div>
    </template>
    <template v-else>
      <div class="dc-actions">
        <div v-if="redoOpen" class="dc-feedback">
          <el-input v-model="feedback" size="small" maxlength="200" clearable
            :placeholder="'不满意可说明原因（选填）'" />
          <div class="dc-feedback-btns">
            <el-button size="small" :loading="generating" @click="redo">确认重做</el-button>
            <button type="button" class="dc-cancel" @click="redoOpen = false">取消</button>
          </div>
        </div>
        <div v-else class="dc-btn-row">
          <button type="button" class="dc-redo" @click="redoOpen = true">
            <el-icon :size="12"><RefreshLeft /></el-icon>
            不满意，重做
          </button>
          <button type="button" class="dc-discard" :disabled="discarding" @click="discard">
            {{ discarding ? '放弃中…' : '放弃' }}
          </button>
        </div>
        <button type="button" class="ap-btn ap-btn--sm dc-submit" :disabled="submitting || discarding" @click="submit">
          {{ submitting ? '提交中…' : '提交审核' }}
        </button>
      </div>
      <div class="dc-jump-row">
        <button class="dc-jump" @click="goPreview">去菜品管理 · 档口预览看效果 →</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { aiDishDraft, createDish } from '@/api/dish'
import { useDishDraftStore } from '@/store/dishDraft'
import { useUserStore } from '@/store/user'

const props = defineProps({
  draft: { type: Object, required: true },
  image: { type: String, default: '' },
  stallId: { type: [Number, String], default: null }
})
const emit = defineEmits(['uploaded', 'superseded', 'discarded'])

const draftStore = useDishDraftStore()
const userStore = useUserStore()
const router = useRouter()

/** 商家身份兜底：stallId 未显式传入时取登录态里的档口 */
function resolveStallId() {
  return props.stallId || userStore.userInfo?.stallId || null
}
const feedback = ref('')
const submitting = ref(false)
const generating = ref(false)
const uploaded = ref(false)
const redoOpen = ref(false)
const discarding = ref(false)

/** 放弃这份草稿：从草稿箱移除、卡片收起。不会创建任何菜品。 */
async function discard() {
  if (discarding.value) return
  discarding.value = true
  try {
    const d = props.draft
    if (d.localId) draftStore.remove(d.localId)
    ElMessage.info('已放弃这份草稿，不会提交任何内容')
    emit('discarded', d)
  } finally {
    discarding.value = false
  }
}

function goPreview() { router.push('/merchant/dishes') }

async function submit() {
  const d = props.draft
  if (!d.name) { ElMessage.warning('草稿缺少菜名'); return }
  if (!(Number(d.price) > 0)) { ElMessage.warning('请先填一个售价'); return }
  submitting.value = true
  try {
    await createDish({
      stallId: resolveStallId(),
      name: d.name,
      price: d.price,
      category: d.category || '',
      description: d.description || '',
      image: props.image || '',
      featured: 0, status: 1, stockQty: null, soldOut: 0
    })
    uploaded.value = true
    if (d.localId) draftStore.remove(d.localId)
    ElMessage.success('已提交审核，管理员通过后上架')
    emit('uploaded')
  } catch { ElMessage.error('提交审核失败') }
  finally { submitting.value = false }
}

async function redo() {
  const d = props.draft
  generating.value = true
  try {
    const res = await aiDishDraft({
      image: props.image || undefined,
      feedback: feedback.value.trim() || undefined,
      previousName: d.name,
      previousCategory: d.category,
      previousDescription: d.description,
      previousPrice: d.price
    })
    const nd = res.data || {}
    if (d.localId) draftStore.remove(d.localId)
    // 通知父级在本条消息下面追加一版新草稿
    emit('superseded', { ...nd, localId: 'draft-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7) })
    feedback.value = ''
  } catch { ElMessage.error('重做失败，请稍后再试') }
  finally { generating.value = false }
}
</script>

<style scoped>
.draft-card { padding: 18px 20px; max-width: 460px; }

.dc-head { display: flex; gap: 12px; align-items: flex-start; }
.dc-img {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f5f5f7;
}
.dc-img.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  font-size: 22px;
  font-weight: 650;
}
.dc-head-main { flex: 1; min-width: 0; }
.dc-name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dc-name { font-size: 16px; font-weight: 650; letter-spacing: -0.01em; color: var(--text-1); }
.dc-draft-tag {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 550;
  color: var(--text-2);
  background: #f5f5f7;
  padding: 2px 8px;
  border-radius: 999px;
}
.dc-meta { margin-top: 7px; display: flex; gap: 6px; }
.dc-tag { padding: 2px 8px; border-radius: 999px; background: #f5f5f7; color: var(--text-2); font-size: 11px; font-weight: 550; }
.dc-tag.suggested { background: var(--brand-soft); color: var(--brand-dark); }

.dc-desc { margin-top: 12px; font-size: 13px; color: var(--text-2); line-height: 1.65; }

.dc-price-row {
  margin-top: 12px;
  padding-top: 11px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.dc-label { font-size: 13px; color: var(--text-2); }
.dc-price-input { display: inline-flex; align-items: center; gap: 5px; }
.dc-unit { font-size: 12px; color: var(--text-3); }
.dc-note { margin-top: 9px; font-size: 12px; color: var(--text-3); line-height: 1.6; }

.dc-divider { margin: 12px 0 10px; }

.dc-uploaded {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 550;
  color: #34c759;
}

.dc-actions { margin-top: 12px; display: flex; flex-direction: column; gap: 10px; }

.dc-feedback { display: flex; flex-direction: column; gap: 8px; }
.dc-feedback-btns { display: flex; align-items: center; gap: 10px; justify-content: flex-end; }
.dc-cancel {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text-3);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  padding: 2px 4px;
}
.dc-cancel:hover { color: var(--text-1); }

.dc-btn-row { display: flex; align-items: center; gap: 10px; }
.dc-discard {
  appearance: none;
  border: none;
  background: transparent;
  color: #ff3b30;
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  padding: 3px 6px;
  border-radius: 999px;
  transition: color 0.15s ease, background-color 0.15s ease;
}
.dc-discard:hover:not(:disabled) { background: rgba(255, 59, 48, 0.08); }
.dc-discard:disabled { opacity: 0.5; cursor: not-allowed; }
.dc-redo {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 3px 6px;
  border-radius: 999px;
  transition: color 0.15s ease, background-color 0.15s ease;
}
.dc-redo:hover { color: var(--text-1); background: #f5f5f7; }

.dc-submit { align-self: flex-start; }

.dc-jump-row { margin-top: 10px; }
.dc-jump {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  font-weight: 550;
  color: var(--text-3);
  cursor: pointer;
  transition: color 0.15s ease;
}
.dc-jump:hover { color: var(--brand-dark); }
</style>
