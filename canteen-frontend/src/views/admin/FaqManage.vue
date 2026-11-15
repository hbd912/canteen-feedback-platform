<template>
  <div class="page-container adaptive-page faq-page">
    <div class="ap-head manage-head">
      <div>
        <h1 class="ap-title">客服知识库</h1>
        <p class="ap-sub">共 {{ total }} 条 · 已启用的问答会被客服自动引用，答案请保持可直接使用</p>
      </div>
      <div class="ap-actions">
        <button type="button" class="ap-btn" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon>
          新增问答
        </button>
      </div>
    </div>

    <div class="page-card table-card">
      <div class="faq-toolbar">
        <el-select
          v-model="query.category"
          placeholder="全部分类"
          clearable
          style="width: 150px"
          @change="loadData"
        >
          <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索问题或答案"
          clearable
          style="width: 240px"
          @keyup.enter="loadData"
          @clear="loadData"
        />
        <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="loadData">查询</button>
        <span class="toolbar-spacer"></span>
        <template v-if="selectedRows.length">
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
        </template>
      </div>

      <div ref="wrapRef" class="adaptive-table">
        <el-table
          ref="tableRef"
          :data="list"
          row-key="id"
          v-loading="loading"
          class="faq-table"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column label="分类" width="120">
            <template #default="{ row }">
              <span class="ap-tag">{{ categoryLabel(row.category) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标准问法" min-width="220">
            <template #default="{ row }">
              <div class="faq-question">{{ row.question }}</div>
            </template>
          </el-table-column>
          <el-table-column label="答案" min-width="300">
            <template #default="{ row }">
              <div class="faq-answer">{{ row.answer }}</div>
            </template>
          </el-table-column>
          <el-table-column label="匹配关键词" min-width="170">
            <template #default="{ row }">
              <span class="faq-keywords">{{ row.keywords || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <span class="status-cell" :class="row.status === 1 ? 'is-on' : 'is-off'">
                <i class="status-dot"></i>
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
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
                <el-icon :size="20"><ChatLineSquare /></el-icon>
              </div>
              <div class="ap-empty-title">暂无问答</div>
              <div>新增第一条，客服即可自动引用</div>
              <button
                type="button"
                class="ap-btn ap-btn--light ap-btn--sm empty-action"
                @click="openCreate"
              >
                新增第一条问答
              </button>
            </div>
          </template>
        </el-table>
      </div>

      <div class="adaptive-pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :current-page="query.current"
          :page-size="query.size"
          @current-change="changePage"
        />
      </div>
    </div>

    <el-drawer
      v-model="dialogVisible"
      :title="form.id ? '编辑问答' : '新增问答'"
      size="min(580px, 100vw)"
      append-to-body
    >
      <p class="drawer-intro">填写学生常见问法与可直接使用的答案，关键词用于提升匹配命中率。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="drawer-group-title">问题与答案</div>
        <el-form-item label="标准问法" prop="question">
          <el-input v-model="form.question" maxlength="200" show-word-limit placeholder="如：怎么投诉某个窗口" />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input
            v-model="form.answer"
            type="textarea"
            :rows="7"
            maxlength="2000"
            show-word-limit
            placeholder="给出明确步骤，学生看得懂即可"
          />
        </el-form-item>

        <div class="ap-divider drawer-divider"></div>
        <div class="drawer-group-title">检索与状态</div>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔，如：投诉、举报、反馈" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="form.sortNo" :min="0" :max="999" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch
                v-model="form.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <button type="button" class="ap-btn ap-btn--light" @click="dialogVisible = false">取消</button>
        <button type="button" class="ap-btn" :disabled="submitting" @click="submitForm">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed, nextTick, onActivated, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createFaq, deleteFaq, getFaqs, updateFaq } from '@/api/chat'
import { useAdaptivePaging } from '@/composables/useAdaptivePaging'
const categories = [
  { value: 'EVALUATE', label: '评价相关' },
  { value: 'FEEDBACK', label: '投诉与反馈' },
  { value: 'RULE', label: '食堂规则' },
  { value: 'ORDER', label: '订单相关' },
  { value: 'OTHER', label: '其他问题' }
]

const query = reactive({ current: 1, size: 10, category: '', keyword: '' })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref()
const wrapRef = ref(null)
const selectedRows = ref([])
const batchDeleting = ref(false)

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
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

const emptyForm = () => ({
  id: null, category: 'OTHER', question: '', answer: '', keywords: '', sortNo: 50, status: 1
})
const form = reactive(emptyForm())

const rules = {
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  question: [{ required: true, message: '请填写标准问法', trigger: 'blur' }],
  answer: [{ required: true, message: '请填写答案', trigger: 'blur' }],
  keywords: [{ max: 500, message: '关键词不能超过500字', trigger: 'blur' }]
}

function categoryLabel(value) {
  const hit = categories.find(c => c.value === value)
  return hit ? hit.label : value
}

function tagType(value) {
  return { EVALUATE: 'success', FEEDBACK: 'danger', RULE: 'warning', ORDER: 'primary' }[value] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getFaqs({ ...query })
    list.value = res.data.records || []
    total.value = res.data.total || 0
    selectedRows.value = []
    nextTick(() => tableRef.value?.clearSelection())
  } finally {
    loading.value = false
  }
}

const { bind } = useAdaptivePaging(query, loadData)

function changePage(page) {
  query.current = page
  loadData()
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = { ...form }
    delete payload.id
    if (form.id) {
      await updateFaq(form.id, payload)
    } else {
      await createFaq(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.question}」吗？`, '提示', { type: 'warning' })
  await deleteFaq(row.id)
  ElMessage.success('已删除')
  loadData()
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
    const results = await Promise.allSettled(rows.map(row => deleteFaq(row.id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

onMounted(async () => {
  await loadData()
  await nextTick()
  bind(wrapRef.value)
  window.addEventListener('canteen:faq-preset', onPresetFaq)
})
onActivated(loadData)
onBeforeUnmount(() => window.removeEventListener('canteen:faq-preset', onPresetFaq))

// 从「问答记录」页点「补录为问答」时，通过全局事件预填并打开新增对话框
function onPresetFaq(e) {
  const preset = e?.detail?.question
  if (preset) {
    Object.assign(form, emptyForm(), { question: preset })
    dialogVisible.value = true
  }
}
</script>

<style scoped>
.faq-page {
  width: 100%;
}

.manage-head {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.table-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px 18px 10px;
  border-radius: 22px;
}

.faq-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-bottom: 4px;
  padding: 2px 2px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.toolbar-spacer {
  flex: 1;
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

.faq-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.faq-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.faq-question {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
  line-height: 1.45;
}

.faq-answer {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.5;
}

.faq-keywords {
  display: block;
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.status-cell.is-off {
  color: var(--text-3);
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

.empty-action {
  margin-top: 14px;
}

.adaptive-pager {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

.drawer-intro {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-2);
}

.drawer-group-title {
  margin: 2px 0 12px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-1);
}

.drawer-divider {
  margin: 6px 0 20px;
}

@media (max-width: 760px) {
  .faq-toolbar .el-select,
  .faq-toolbar .el-input {
    width: 100% !important;
  }
}
</style>
