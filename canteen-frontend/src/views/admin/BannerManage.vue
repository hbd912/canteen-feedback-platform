<template>
  <div class="page-container adaptive-page banner-page">
    <div class="ap-head manage-head">
      <div>
        <h1 class="ap-title">轮播广告</h1>
        <p class="ap-sub">共 {{ list.length }} 条 · 维护学生端首页轮播的顺序、配色与跳转</p>
      </div>
      <div class="ap-actions">
        <button type="button" class="ap-btn" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon>
          新增广告
        </button>
      </div>
    </div>

    <div class="page-card table-card">
      <div class="table-top">
        <div class="table-title-wrap">
          <span class="table-title">全部广告</span>
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
          class="banner-table"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column type="index" label="#" width="48" align="center" />
          <el-table-column label="预览" width="150">
            <template #default="{ row }">
              <div class="banner-preview" :style="previewStyle(row)">
                <span class="banner-preview-emoji">{{ row.emoji || '🍽️' }}</span>
                <span class="banner-preview-title">{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="220">
            <template #default="{ row }">
              <div class="banner-title">{{ row.title }}</div>
              <div class="banner-subtitle">{{ row.subtitle || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="关联档口" width="140">
            <template #default="{ row }">
              <span class="cell-muted">{{ row.stallName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="跳转路径" width="180">
            <template #default="{ row }">
              <span class="cell-mono">{{ row.link || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <span class="status-cell" :class="row.status === 1 ? 'is-on' : 'is-off'">
                <i class="status-dot"></i>
                {{ row.status === 1 ? '展示中' : '已下架' }}
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
                <el-icon :size="20"><Picture /></el-icon>
              </div>
              <div class="ap-empty-title">还没有轮播广告</div>
              <div>新增第一条，学生端首页立即可见</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑广告' : '新增广告'" width="560px">
      <div class="banner-live-preview" :style="previewStyle(form)">
        <span class="banner-live-emoji">{{ form.emoji || '🍽️' }}</span>
        <div class="banner-live-copy">
          <div class="banner-live-title">{{ form.title || '广告标题' }}</div>
          <div class="banner-live-sub">{{ form.subtitle || '副标题会显示在这里' }}</div>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-group-title">内容</div>
        <el-form-item label="广告标题" prop="title">
          <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="如：麻辣香锅 · 今日特惠" />
        </el-form-item>
        <el-form-item label="副标题" prop="subtitle">
          <el-input v-model="form.subtitle" maxlength="100" show-word-limit placeholder="如：两人同行第二份半价" />
        </el-form-item>
        <el-form-item label="关联档口">
          <el-select v-model="form.stallId" placeholder="不关联则留空" clearable style="width: 100%">
            <el-option v-for="s in stalls" :key="s.id" :label="`${s.windowNo}号 ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>

        <div class="ap-divider form-divider"></div>
        <div class="form-group-title">展示</div>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="背景色">
              <el-color-picker v-model="form.bgColor" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="图标" prop="emoji">
              <el-input v-model="form.emoji" maxlength="10" placeholder="🍲" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序号" prop="sortNo">
              <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图片地址（留空则使用背景色渐变）" prop="image">
          <el-input v-model="form.image" maxlength="255" placeholder="如：https://xxx.com/banner.jpg" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">展示中</el-radio>
            <el-radio :value="0">已下架</el-radio>
          </el-radio-group>
        </el-form-item>

        <div class="ap-divider form-divider"></div>
        <div class="form-group-title">跳转</div>
        <el-form-item label="点击跳转路径" prop="link">
          <el-input v-model="form.link" maxlength="255" placeholder="如：/dishes?stallId=4" />
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
import { pageBanners, createBanner, updateBanner, deleteBanner } from '@/api/banner'
import { listStalls } from '@/api/stall'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const stalls = ref([])
const dialogVisible = ref(false)
const tableRef = ref()
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

const emptyForm = {
  id: null,
  title: '',
  subtitle: '',
  stallId: undefined,
  image: '',
  bgColor: '#ff7a1a',
  emoji: '🍽️',
  link: '',
  sortNo: 10,
  status: 1
}
const form = reactive({ ...emptyForm })

const formRef = ref()
const rules = {
  title: [
    { required: true, message: '请填写广告标题', trigger: 'blur' },
    { max: 60, message: '广告标题不能超过60字', trigger: 'blur' }
  ],
  subtitle: [{ max: 100, message: '副标题不能超过100字', trigger: 'blur' }],
  emoji: [{ max: 10, message: '图标最多10个字符', trigger: 'blur' }],
  link: [{ max: 255, message: '跳转路径过长', trigger: 'blur' }],
  image: [{ max: 255, message: '图片地址过长', trigger: 'blur' }],
  sortNo: [{ type: 'number', min: 0, max: 9999, message: '排序号范围为0-9999', trigger: 'change' }]
}

function previewStyle(row) {
  if (row.image) return { backgroundImage: `url(${row.image})` }
  const base = row.bgColor || '#ff7a1a'
  return { backgroundImage: `linear-gradient(120deg, ${base}, rgba(0,0,0,0.55))` }
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageBanners({ current: 1, size: 100 })
    list.value = res.data.records
    selectedRows.value = []
    nextTick(() => tableRef.value?.clearSelection())
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm)
  dialogVisible.value = true
  formRef.value?.clearValidate()
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
      await updateBanner(form.id, form)
    } else {
      await createBanner(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除广告「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteBanner(row.id)
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
    const results = await Promise.allSettled(rows.map(row => deleteBanner(row.id)))
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
  const res = await listStalls()
  stalls.value = res.data
  loadData()
})
onActivated(loadData)
</script>

<style scoped>
.banner-page {
  width: 100%;
}

.manage-head {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.banner-page > .page-card {
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

.banner-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.banner-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.banner-preview {
  width: 120px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  color: #fff;
  background-size: cover;
  background-position: center;
  filter: saturate(0.82);
  overflow: hidden;
}

.banner-preview-emoji {
  font-size: 16px;
  margin-right: 6px;
  flex-shrink: 0;
}

.banner-preview-title {
  font-size: 11.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-subtitle {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-muted {
  font-size: 12.5px;
  color: var(--text-2);
}

.cell-mono {
  display: block;
  font-size: 12px;
  color: var(--text-2);
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

.banner-live-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 72px;
  padding: 0 20px;
  margin-bottom: 20px;
  border-radius: 14px;
  background-size: cover;
  background-position: center;
  color: #fff;
  overflow: hidden;
}

.banner-live-emoji {
  font-size: 26px;
  flex-shrink: 0;
}

.banner-live-copy {
  min-width: 0;
}

.banner-live-title {
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-live-sub {
  margin-top: 3px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-group-title {
  margin: 2px 0 10px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-1);
}

.form-divider {
  margin: 4px 0 18px;
}

@media (max-width: 760px) {
  .adaptive-table {
    max-height: none;
  }
}
</style>
