<template>
  <div class="page-container mm-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">给管理员留言</h1>
        <div class="ap-sub">有问题直接说，官方会在这里回复你</div>
      </div>
    </div>

    <div class="ap-card mm-card">
      <!-- 发送留言 -->
      <div class="mm-send">
        <el-input
          v-model="content"
          type="textarea"
          :rows="2"
          maxlength="1000"
          show-word-limit
          placeholder="如：申请延长营业时间、报修设备、反映问题等"
        />
        <div class="mm-send-row">
          <button type="button" class="ap-btn ap-btn--sm" :disabled="sending || !content.trim()" @click="handleSend">
            {{ sending ? '发送中…' : '发送留言' }}
          </button>
        </div>
      </div>

      <div class="ap-divider mm-divider"></div>

      <!-- 双向往来：与管理员 / 系统通知 -->
      <div class="ap-section-head mm-list-head">
        <span class="ap-section-title">往来记录</span>
        <span v-if="messages.length" class="ap-section-hint">{{ messages.length }} 条</span>
      </div>

      <div v-if="!messages.length && !loading" class="ap-empty mm-empty">
        <div class="ap-empty-mark"><el-icon :size="19"><ChatLineSquare /></el-icon></div>
        <div class="ap-empty-title">还没有往来</div>
        <div>发第一条留言试试</div>
      </div>
      <div v-else class="mm-list">
        <div v-for="m in timeline" :key="m.key" class="mm-item" :class="bubble(m)">
          <span class="mm-avatar" :class="bubble(m) === 'from-admin' ? 'official' : 'mine'">
            <el-icon :size="14"><Service v-if="m.notice || m.direction === 'OUT'" /><Shop v-else /></el-icon>
          </span>
          <div class="mm-bubble">
            <div v-if="m.notice" class="mm-pin">置顶 · 通知</div>
            <div v-if="m.title" class="mm-msg-title">{{ m.title }}</div>
            <div class="mm-msg-body">{{ m.content }}</div>
            <div class="mm-meta">
              <span class="mm-who">{{ whoLabel(m) }}<i v-if="m.direction === 'OUT' && !m.notice" class="mm-official-dot"></i></span>
              <span class="mm-time">{{ m.createTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { sendMessage, listMyMessages } from '@/api/merchant'

const content = ref('')
const sending = ref(false)
const loading = ref(false)
const messages = ref([])

// 商家视角线程：IN=自己发的，OUT=管理员/AI 发来的
const timeline = computed(() =>
  messages.value.map((m, i) => ({
    ...m,
    key: m.id + (m.direction === 'OUT' ? '-o' : '-i') + i
  }))
)
function bubble(m) {
  return m.direction === 'OUT' ? 'from-admin' : 'from-merchant'
}
function whoLabel(m) {
  if (m.notice) return '系统通知'
  if (m.direction === 'OUT') return '小众点评官方'
  return '我（商家）'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listMyMessages()
    messages.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleSend() {
  if (!content.value.trim()) {
    ElMessage.warning('请填写留言内容')
    return
  }
  sending.value = true
  try {
    await sendMessage({ content: content.value.trim() })
    ElMessage.success('留言已发送')
    content.value = ''
    loadData()
  } finally {
    sending.value = false
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.mm-page {
  max-width: 820px;
  margin: 0 auto;
}

.mm-card { padding: 26px 28px; }

.mm-send-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.mm-divider { margin: 22px 0; }

.mm-list-head { margin: 0 0 6px; }

.mm-empty { padding: 44px 20px; }

.mm-list { display: flex; flex-direction: column; gap: 14px; }

.mm-item {
  display: flex;
  gap: 11px;
  align-items: flex-start;
}

.mm-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}
.mm-avatar.official { background: #f0f0f2; color: var(--text-2); }
.mm-avatar.mine { background: #fff4ec; color: var(--brand-dark); }

.mm-bubble {
  flex: 1;
  min-width: 0;
  padding: 11px 14px;
  border-radius: 16px;
  border-top-left-radius: 5px;
  background: #fff;
  border: 1px solid var(--border);
}
.mm-item.from-merchant .mm-bubble {
  background: #f5f5f7;
  border-color: transparent;
  border-top-left-radius: 16px;
  border-top-right-radius: 5px;
}

.mm-pin {
  display: inline-flex;
  align-items: center;
  font-size: 10.5px;
  font-weight: 550;
  color: var(--text-2);
  background: #ececee;
  padding: 2px 9px;
  border-radius: 999px;
  margin-bottom: 7px;
}
.mm-msg-title { font-size: 12.5px; font-weight: 650; color: var(--text-1); margin-bottom: 3px; }
.mm-msg-body { font-size: 14px; line-height: 1.7; color: var(--text-1); white-space: pre-wrap; overflow-wrap: anywhere; }
.mm-meta {
  margin-top: 7px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}
.mm-who { font-weight: 550; color: var(--text-2); display: inline-flex; align-items: center; gap: 5px; }
.mm-official-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand);
  display: inline-block;
}
.mm-item.from-merchant .mm-who { color: var(--text-3); }
.mm-time { color: var(--text-3); }
</style>