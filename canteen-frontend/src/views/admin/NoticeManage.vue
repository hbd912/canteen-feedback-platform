<template>
  <div class="page-container adaptive-page notice-page">
    <div class="ap-head manage-head">
      <div>
        <h1 class="ap-title">公告管理</h1>
        <p class="ap-sub">共 {{ list.length }} 条 · 发布后学生端立即可见，草稿仅管理员可见</p>
      </div>
      <div class="ap-actions">
        <button type="button" class="ap-btn" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon>
          发布公告
        </button>
      </div>
    </div>

    <div class="page-card table-card">
      <div class="table-top">
        <div class="table-title-wrap">
          <span class="table-title">全部公告</span>
          <span class="table-count">{{ list.length }} 条</span>
        </div>
        <div v-if="selectedRows.length" class="selection-bar">
          <span class="selected-hint">已选 {{ selectedRows.length }} 项</span>
          <button
            type="button"
            class="ap-btn ap-btn--ghost ap-btn--sm danger-text"
            :disabled="batchDeleting"
            @click="handleBatchDelete"
          >
            {{ batchDeleting ? '删除中…' : '删除所选' }}
          </button>
          <button type="button" class="ap-btn ap-btn--ghost ap-btn--sm" @click="tableRef?.clearSelection()">
            清除
          </button>
        </div>
      </div>

      <div class="adaptive-table">
        <el-table
          ref="tableRef"
          :data="list"
          row-key="id"
          v-loading="loading"
          class="notice-table"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column label="标题" min-width="230">
            <template #default="{ row }">
              <div class="notice-title">{{ row.title }}</div>
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="300">
            <template #default="{ row }">
              <div class="notice-summary">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span class="status-cell" :class="row.status === 1 ? 'is-on' : 'is-draft'">
                <i class="status-dot"></i>
                {{ row.status === 1 ? '已发布' : '草稿' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="发布时间" width="170">
            <template #default="{ row }">
              <span class="time-text">{{ row.createTime || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button type="button" class="row-link" @click="openEdit(row)">编辑</button>
                <button type="button" class="row-link danger-text" @click="handleDelete(row)">删除</button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark">
                <el-icon :size="20"><Notification /></el-icon>
              </div>
              <div class="ap-empty-title">还没有公告</div>
              <div>发布后学生端立即可见</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" width="600px" class="notice-dialog">
      <template #header>
        <div class="dialog-head">
          <span class="dialog-title">{{ form.id ? '编辑公告' : '发布公告' }}</span>
          <div class="ap-pill-group" aria-label="公告状态">
            <button
              type="button"
              class="ap-pill"
              :class="{ active: form.status === 1 }"
              @click="form.status = 1"
            >
              发布
            </button>
            <button
              type="button"
              class="ap-pill"
              :class="{ active: form.status === 0 }"
              @click="form.status = 0"
            >
              草稿
            </button>
          </div>
        </div>
      </template>

      <div class="ai-draft">
        <div class="ai-draft-label">
          <el-icon :size="14"><MagicStick /></el-icon>
          用 AI 起草
        </div>
        <div class="ai-draft-row">
          <el-input
            v-model="aiTopic"
            placeholder="输入主题，AI 会生成标题与正文"
            clearable
            @keyup.enter="handleAiGenerate"
          />
          <button
            type="button"
            class="ap-btn ap-btn--light ap-btn--sm"
            :disabled="aiGenerating"
            @click="handleAiGenerate"
          >
            {{ aiGenerating ? '生成中…' : '生成' }}
          </button>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item prop="title" class="title-item">
          <el-input
            v-model="form.title"
            class="notice-title-input"
            maxlength="100"
            show-word-limit
            placeholder="公告标题"
          />
        </el-form-item>
        <el-form-item prop="content" class="content-item">
          <el-input
            v-model="form.content"
            class="notice-body-input"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="写下公告正文…"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <button type="button" class="ap-btn ap-btn--light" @click="dialogVisible = false">取消</button>
        <button type="button" class="ap-btn" :disabled="submitting" @click="submitForm">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick , onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listNotices, createNotice, updateNotice, deleteNotice } from '@/api/notice'
import { noticeGenerate } from '@/api/admin'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const tableRef = ref()
const selectedRows = ref([])
const batchDeleting = ref(false)
const aiTopic = ref('')
const aiGenerating = ref(false)

const allSelected = computed({
  get: () => list.value.length > 0 && selectedRows.value.length === list.value.length,
  set: (val) => {
    tableRef.value?.clearSelection()
    if (val) list.value.forEach(row => tableRef.value?.toggleRowSelection(row, true))
  }
})
const isIndeterminate = computed(() => selectedRows.value.length > 0 && selectedRows.value.length < list.value.length)

function onSelectionChange(rows) {
  selectedRows.value = rows
}

const form = reactive({ id: null, title: '', content: '', status: 1 })

const formRef = ref()
const rules = {
  title: [
    { required: true, message: '请填写公告标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写公告内容', trigger: 'blur' },
    { max: 2000, message: '内容不能超过2000字', trigger: 'blur' }
  ]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listNotices()
    list.value = res.data
    selectedRows.value = []
    nextTick(() => tableRef.value?.clearSelection())
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, title: '', content: '', status: 1 })
  aiTopic.value = ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleAiGenerate() {
  if (!aiTopic.value.trim()) {
    ElMessage.warning('请先输入公告主题')
    return
  }
  aiGenerating.value = true
  try {
    const res = await noticeGenerate(aiTopic.value.trim())
    form.title = res.data?.title || form.title
    form.content = res.data?.content || form.content
    ElMessage.success('已生成，可修改后保存')
  } catch {
    // 请求层已统一提示
  } finally {
    aiGenerating.value = false
  }
}

function openEdit(row) {
  Object.assign(form, { ...row })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateNotice(form.id, form)
    } else {
      await createNotice(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteNotice(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

async function handleBatchDelete() {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 项吗？删除后不可恢复`, '提示', { type: 'warning' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(rows.map(row => deleteNotice(row.id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.notice-page {
  width: 100%;
}

.manage-head {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.notice-page > .page-card {
  flex: 0 1 auto;
}

.table-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px 18px 10px;
  border-radius: 22px;
}

.adaptive-table {
  max-height: 600px;
}

.table-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 2px 2px 12px;
}

.table-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.table-title {
  font-size: 15px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.table-count {
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.selection-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-hint {
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.danger-text {
  color: #c9342b;
}

.danger-text:hover {
  background: #ffeceb;
  color: #c9342b;
}

.notice-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.notice-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.notice-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.4;
  letter-spacing: -0.005em;
}

.notice-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.5;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-2);
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a1a1a6;
}

.status-cell.is-on .status-dot {
  background: #34c759;
}

.status-cell.is-draft {
  color: var(--text-3);
}

.time-text {
  font-size: 12px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-link {
  border: none;
  background: transparent;
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--brand-dark);
  font-family: inherit;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple);
}

.row-link:hover {
  background: rgba(0, 0, 0, 0.04);
}

.row-link.danger-text {
  color: #c9342b;
}

.row-link.danger-text:hover {
  background: #ffeceb;
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-right: 34px;
}

.dialog-title {
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.ai-draft {
  padding: 12px 14px;
  margin-bottom: 20px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #fafafc;
}

.ai-draft-label {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 550;
  color: var(--text-2);
}

.ai-draft-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-draft-row .el-input {
  flex: 1;
}

.title-item {
  margin-bottom: 6px !important;
}

.notice-title-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  padding-left: 0;
  padding-right: 0;
  background: transparent;
}

.notice-title-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.015em;
  color: var(--text-1);
  height: 36px;
}

.notice-title-input :deep(.el-input__count) {
  background: transparent;
  font-size: 11px;
  color: var(--text-3);
}

.content-item {
  margin-top: 8px !important;
  margin-bottom: 4px !important;
}

.notice-body-input :deep(.el-textarea__inner) {
  min-height: 180px !important;
  padding: 10px 0;
  font-size: 14px;
  line-height: 1.8;
  box-shadow: none !important;
  background: transparent;
  resize: vertical;
}

.notice-body-input :deep(.el-input__count) {
  background: transparent;
  font-size: 11px;
  color: var(--text-3);
}

@media (max-width: 760px) {
  .adaptive-table {
    max-height: none;
  }

  .ai-draft-row {
    flex-wrap: wrap;
  }
}
</style>
