<template>
  <div class="ap-card wz-panel">
    <div class="wz-head">
      <div>
        <div class="ap-section-title">上传菜品</div>
        <div class="ap-section-hint">传一张图或写一句话，AI 生成菜名、分类、描述与建议售价</div>
      </div>
      <span class="ap-tag">AI</span>
    </div>

    <div ref="listRef" class="wz-list">
      <div v-if="!messages.length" class="ap-empty wz-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><Dish /></el-icon></div>
        <div class="ap-empty-title">上传一张菜品图，或先用一句话描述</div>
        <div>AI 会看图、定价、写文案；满意后提交审核，管理员通过后上架</div>
      </div>

      <div v-for="m in messages" :key="m.id" class="wz-msg" :class="m.role">
        <template v-if="m.role === 'user'">
          <div v-if="m.image" class="wz-user-img"><img :src="m.image" alt="" /></div>
          <div v-if="m.text" class="wz-user-text">{{ m.text }}</div>
        </template>

        <div v-else-if="m.role === 'ai-text'" class="wz-ai-text">
          <span class="wz-ai-label">AI</span>
          <p>{{ m.text }}</p>
        </div>

        <div v-else class="wz-draft">
          <div class="wz-draft-head">
            <img v-if="m.image" :src="m.image" class="wz-draft-img" alt="" />
            <div v-else class="wz-draft-img wz-draft-img--empty">
              <el-icon :size="24"><Dish /></el-icon>
            </div>
            <div class="wz-draft-head-main">
              <div class="wz-draft-name">{{ m.draft.name || '未命名' }}</div>
              <div class="wz-draft-meta">
                <span class="ap-tag">{{ m.draft.category || '未分类' }}</span>
                <span v-if="m.draft.priceSuggested" class="ap-tag ap-tag--brand">AI 建议价</span>
              </div>
            </div>
          </div>
          <div class="wz-draft-desc">{{ m.draft.description || '暂无描述' }}</div>
          <div class="wz-draft-price-row">
            <span class="wz-label">售价</span>
            <el-input-number
              v-model="m.draft.price"
              class="wz-price-input"
              :min="0.01"
              :max="999"
              :precision="2"
              :step="1"
              size="small"
              controls-position="right"
            />
            <span class="wz-unit">元</span>
          </div>
          <div v-if="m.draft.note" class="wz-draft-note">{{ m.draft.note }}</div>

          <div v-if="m.uploaded" class="wz-status-row">
            <span class="ap-tag ap-tag--amber">待审核</span>
            <span class="wz-status-text">已提交审核，管理员通过后上架</span>
            <button class="ap-link" @click="goPreview">去菜品管理查看</button>
          </div>
          <div v-else-if="m.superseded" class="wz-draft-note">已被重做版替换</div>
          <div v-else class="wz-actions">
            <el-input
              v-model="feedbackText"
              size="small"
              maxlength="200"
              placeholder="不满意可在此说明原因（选填）"
              clearable
            />
            <div class="wz-btn-row">
              <button class="ap-link wz-redo" :disabled="generating" @click="redo(m)">
                {{ generating ? '重做中…' : '不满意？调整重做' }}
              </button>
              <el-button type="primary" size="small" :loading="submitting" @click="submit(m)">提交审核</el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="generating" class="wz-msg ai wz-generating">
        <span class="wz-ai-label">AI</span>
        <span class="wz-typing">
          <el-icon class="is-loading" :size="14"><Loading /></el-icon>
          正在生成草稿…
        </span>
      </div>
    </div>

    <div class="wz-input">
      <el-upload :show-file-list="false" :http-request="doUpload" accept="image/*" class="wz-upload">
        <button class="ap-btn ap-btn--light ap-btn--sm wz-img-btn" :disabled="uploading">
          <el-icon><Picture /></el-icon>
          {{ uploading ? '上传中…' : '图片' }}
        </button>
      </el-upload>
      <img v-if="pendingImage" :src="pendingImage" class="wz-pending-img" alt="" />
      <el-input
        v-model="inputText"
        maxlength="200"
        placeholder="一句话告诉 AI（可留空，AI 会自己看图定价）"
        @keydown.enter.exact.prevent="send"
      />
      <button class="ap-btn ap-btn--brand" :disabled="generating || (!pendingImage && !inputText)" @click="send">
        {{ generating ? '生成中…' : '生成草稿' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@/api/sales'
import { aiDishDraft, createDish } from '@/api/dish'
import { prepareImageFile } from '@/utils/imageUpload'
import { useDishDraftStore } from '@/store/dishDraft'

const props = defineProps({
  stallId: { type: [Number, String], default: null }
})
const emit = defineEmits(['created'])

const draftStore = useDishDraftStore()
const router = useRouter()
const messages = ref([])
const inputText = ref('')
const feedbackText = ref('')
const pendingImage = ref('')
const uploading = ref(false)
const generating = ref(false)
const submitting = ref(false)
const listRef = ref(null)

let seq = 0

/** 进入页面时把未处理的草稿恢复出来，这样去预览/管理逛一圈回来还能继续确认 */
onMounted(() => {
  if (messages.value.length || !draftStore.drafts.length) return
  const list = [...draftStore.drafts].reverse()
  for (const d of list) {
    messages.value.push({ id: ++seq, role: 'ai', image: d.image, draft: { ...d }, uploaded: false })
    messages.value.push({ id: ++seq, role: 'ai-text', text: '这是之前生成的草稿，满意就上架，不满意可以重做。' })
  }
})

function newDraftId() {
  return 'draft-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7)
}

/** 草稿已存进草稿箱，跳去菜品管理（默认档口预览）看效果 */
function goPreview() {
  router.push('/merchant/dishes')
}

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

async function doUpload(option) {
  uploading.value = true
  try {
    const file = await prepareImageFile(option.file)
    if (!file) {
      ElMessage.error('这个图片格式浏览器无法处理，请转成 JPG/PNG 再上传')
      return
    }
    const res = await uploadImage(file)
    pendingImage.value = res.data
    ElMessage.success('图片已就绪，点击「让 AI 上架」生成')
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    uploading.value = false
  }
}

async function send() {
  if (!pendingImage.value && !inputText.value.trim()) {
    ElMessage.warning('请先传图片或写一句说明')
    return
  }
  messages.value.push({ id: ++seq, role: 'user', image: pendingImage.value, text: inputText.value.trim() })
  const image = pendingImage.value
  const hint = inputText.value.trim()
  inputText.value = ''
  scrollBottom()

  generating.value = true
  try {
    const res = await aiDishDraft({ image: image || undefined, hint: hint || undefined })
    const d = res.data || {}
    const draft = { ...d, localId: newDraftId() }
    messages.value.push({ id: ++seq, role: 'ai', image, draft: { ...draft }, uploaded: false })
    draftStore.upsert({ ...draft, image, createdAt: new Date().toISOString() })
    messages.value.push({
      id: ++seq,
      role: 'ai-text',
      text: d.priceSuggested
        ? '这版草稿和 AI 建议售价先给你看看，价格可以改，满意我就上架。'
        : '这版草稿先给你看看，满意我就上架。'
    })
    scrollBottom()
  } catch {
    // 图片仍保留在输入区，提示直接重试，避免用户重新传图
    messages.value.push({
      id: ++seq,
      role: 'ai-text',
      text: '这次视觉模型没响应（图片还在输入框里）。直接再点一次「让 AI 上架」重试，或补一句菜品描述再生成。'
    })
    scrollBottom()
    ElMessage.error('生成失败，请重试')
  } finally {
    generating.value = false
  }
}

async function submit(m) {
  const d = m.draft
  if (!d.name) {
    ElMessage.warning('草稿缺少菜名')
    return
  }
  if (!(Number(d.price) > 0)) {
    ElMessage.warning('请先填一个售价')
    return
  }
  submitting.value = true
  try {
    await createDish({
      stallId: props.stallId,
      name: d.name,
      price: d.price,
      category: d.category || '',
      description: d.description || '',
      image: m.image || '',
      featured: 0,
      status: 1,
      stockQty: null,
      soldOut: 0
    })
    m.uploaded = true
    draftStore.remove(d.localId)
    messages.value.push({ id: ++seq, role: 'ai-text', text: '已上架 ✅ 可以在「菜品管理」的档口预览里看效果。' })
    ElMessage.success('菜品已上架')
    emit('created')
    scrollBottom()
  } catch {
    ElMessage.error('上架失败')
  } finally {
    submitting.value = false
  }
}

async function redo(m) {
  const d = m.draft
  generating.value = true
  try {
    const res = await aiDishDraft({
      image: m.image || undefined,
      hint: inputText.value || undefined,
      feedback: feedbackText.value.trim() || undefined,
      previousName: d.name,
      previousCategory: d.category,
      previousDescription: d.description,
      previousPrice: d.price
    })
    const nd = res.data || {}
    // 收回旧草稿，换成重做后的这版（预览/管理里同步替换）
    if (d.localId) draftStore.remove(d.localId)
    m.superseded = true
    const nextDraft = { ...nd, localId: newDraftId() }
    messages.value.push({ id: ++seq, role: 'ai', image: m.image, draft: { ...nextDraft }, uploaded: false })
    draftStore.upsert({ ...nextDraft, image: m.image, createdAt: new Date().toISOString() })
    messages.value.push({ id: ++seq, role: 'ai-text', text: '已按你的意见重做一版，再看看满意吗？' })
    feedbackText.value = ''
    scrollBottom()
  } catch {
    ElMessage.error('重做失败，请稍后再试')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.wz-panel { display: flex; flex-direction: column; min-height: 620px; }

.wz-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.wz-head .ap-tag { flex-shrink: 0; margin-top: 2px; letter-spacing: 0.04em; }

.wz-list {
  flex: 1;
  min-height: 380px;
  max-height: 520px;
  overflow-y: auto;
  padding: 16px 4px 8px 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.wz-empty { flex: 1; }

.wz-msg { display: flex; flex-direction: column; gap: 8px; }
.wz-msg.user { align-items: flex-end; }

.wz-user-img img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.wz-user-text {
  background: #f5f5f7;
  color: var(--text-1);
  padding: 9px 14px;
  border-radius: 16px;
  border-bottom-right-radius: 6px;
  font-size: 13.5px;
  line-height: 1.55;
  max-width: 72%;
}

.wz-ai-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 7px;
  background: var(--brand-soft);
  color: var(--brand-dark);
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.wz-ai-text {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 14px;
  color: var(--text-1);
  line-height: 1.7;
}
.wz-ai-text p { margin: 0; }

.wz-draft {
  background: var(--surface);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  padding: 16px 18px;
  max-width: 640px;
}

.wz-draft-head { display: flex; gap: 12px; align-items: center; }
.wz-draft-img {
  width: 88px;
  height: 88px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f2f2f5;
}
.wz-draft-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #86868b;
}
.wz-draft-head-main { flex: 1; min-width: 0; }
.wz-draft-name {
  font-size: 16px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.01em;
}
.wz-draft-meta { margin-top: 7px; display: flex; gap: 6px; flex-wrap: wrap; }
.wz-draft-desc { margin-top: 10px; font-size: 13px; color: var(--text-2); line-height: 1.65; }

.wz-draft-price-row { margin-top: 12px; display: flex; align-items: center; gap: 8px; }
.wz-label { font-size: 13px; color: var(--text-2); }
.wz-price-input { width: 120px; }
.wz-unit { font-size: 13px; color: var(--text-2); }

.wz-draft-note { margin-top: 8px; font-size: 12px; color: var(--text-3); }

.wz-status-row {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.wz-status-text { font-size: 12px; color: var(--text-3); }
.wz-status-row .ap-link { margin-left: auto; }

.wz-actions { margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(0, 0, 0, 0.05); display: flex; flex-direction: column; gap: 10px; }
.wz-btn-row { display: flex; gap: 12px; align-items: center; justify-content: flex-end; }
.wz-redo { font-size: 12.5px; }
.wz-redo:disabled { opacity: 0.45; cursor: not-allowed; }

.wz-generating {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  color: var(--text-3);
  font-size: 13px;
}
.wz-typing { display: inline-flex; align-items: center; gap: 6px; }

.wz-input {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding-top: 14px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  margin-top: 4px;
}
.wz-input .el-input { flex: 1; }
.wz-upload { flex-shrink: 0; }
.wz-img-btn { white-space: nowrap; }
.wz-img-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.wz-pending-img {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

@media (max-width: 560px) {
  .wz-user-text { max-width: 90%; }
  .wz-draft-head { flex-direction: column; }
  .wz-draft-img { width: 100%; height: 140px; }
}
</style>
