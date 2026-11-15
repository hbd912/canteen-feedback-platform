<template>
  <div class="page-container adaptive-page dish-manage-page">
    <div class="ap-head manage-head">
      <div>
        <h1 class="ap-title">菜品管理</h1>
        <p class="ap-sub">共 {{ total }} 道菜 · 跨档口维护价格、图片与在售状态</p>
      </div>
      <div class="ap-actions">
        <button type="button" class="ap-btn" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon>
          新增菜品
        </button>
      </div>
    </div>

    <div class="page-card table-card">
      <div class="manage-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索菜名"
          clearable
          style="width: 210px"
          @keyup.enter="loadData"
        />
        <el-select
          v-model="query.stallId"
          placeholder="全部档口"
          clearable
          style="width: 180px"
          @change="loadData"
        >
          <el-option v-for="s in stalls" :key="s.id" :label="`${s.windowNo}号 ${s.name}`" :value="s.id" />
        </el-select>
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
          :data="records"
          row-key="id"
          v-loading="loading"
          class="manage-table"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column label="图" width="68" align="center">
            <template #default="{ row }">
              <img v-if="row.image" :src="row.image" class="dish-thumb" loading="lazy" alt="">
              <span v-else class="dish-thumb dish-thumb-empty">{{ (row.name || '菜').slice(0, 1) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="菜品名称" min-width="140">
            <template #default="{ row }">
              <span class="dish-name">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="所属档口" min-width="150">
            <template #default="{ row }">
              <span class="stall-name">{{ row.stallName }}</span>
              <span class="stall-window">{{ row.windowNo }} 号窗口</span>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="92">
            <template #default="{ row }">
              <span class="category-text">{{ row.category || '未分类' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="价格" width="100" align="right">
            <template #default="{ row }">
              <span class="dish-price">¥{{ Number(row.price || 0).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="评分" width="100" align="center">
            <template #default="{ row }">
              <span class="rating-main">{{ row.avgRating || '—' }}</span>
              <span class="rating-count">({{ row.reviewCount || 0 }})</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <span class="status-cell" :class="row.status === 1 ? 'is-on' : 'is-off'">
                <i class="status-dot"></i>
                {{ row.status === 1 ? '在售' : '已下架' }}
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
                <el-icon :size="20"><Dish /></el-icon>
              </div>
              <div class="ap-empty-title">暂无菜品</div>
              <div>换个档口或关键词试试</div>
            </div>
          </template>
        </el-table>
      </div>

      <div class="adaptive-pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜品' : '新增菜品'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-group-title">基础信息</div>
        <el-form-item label="所属档口" prop="stallId">
          <el-select v-model="form.stallId" placeholder="请选择档口" style="width: 100%">
            <el-option v-for="s in stalls" :key="s.id" :label="`${s.windowNo}号 ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜品名称" prop="name">
          <el-input v-model="form.name" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :max="999999" :precision="2" :step="0.5" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <div class="ap-divider form-divider"></div>
        <div class="form-group-title">展示</div>
        <el-form-item label="招牌菜">
          <el-switch v-model="form.featured" :active-value="1" :inactive-value="0" active-text="设为招牌" />
        </el-form-item>
        <el-form-item label="图片地址">
          <div class="image-field">
            <el-input v-model="form.image" maxlength="255" placeholder="如 /dishes/1.jpg，留空则显示首字" />
            <div class="image-preview">
              <span class="image-preview-initial">{{ (form.name || '菜').slice(0, 1) }}</span>
              <img
                v-if="form.image"
                :src="form.image"
                alt=""
                @error="$event.target.style.display = 'none'"
              >
            </div>
          </div>
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>

        <div class="ap-divider form-divider"></div>
        <div class="form-group-title">状态</div>
        <el-form-item label="上架状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在售</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
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
import { pageDishes, listCategories, createDish, updateDish, deleteDish } from '@/api/dish'
import { listStalls } from '@/api/stall'
import { useAdaptivePaging } from '@/composables/useAdaptivePaging'

const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const total = ref(0)
const tableRef = ref()
const wrapRef = ref(null)
const selectedRows = ref([])
const batchDeleting = ref(false)

const allSelected = computed({
  get: () => records.value.length > 0 && selectedRows.value.length === records.value.length,
  set: (val) => {
    tableRef.value?.clearSelection()
    if (val) records.value.forEach(row => tableRef.value?.toggleRowSelection(row, true))
  }
})
const isIndeterminate = computed(() => selectedRows.value.length > 0 && selectedRows.value.length < records.value.length)

function onSelectionChange(rows) {
  selectedRows.value = rows
}
const stalls = ref([])
const categories = ref(['主食', '荤菜', '素菜', '汤品', '小吃', '饮品', '甜品'])

const query = reactive({ current: 1, size: 12, keyword: '', stallId: undefined })

const dialogVisible = ref(false)
const form = reactive({
  id: null,
  stallId: undefined,
  name: '',
  price: 0,
  category: '荤菜',
  image: '',
  featured: 0,
  description: '',
  status: 1
})

const formRef = ref()
const rules = {
  stallId: [{ required: true, message: '请选择所属档口', trigger: 'change' }],
  name: [
    { required: true, message: '请填写菜品名称', trigger: 'blur' },
    { max: 60, message: '菜品名称不能超过60字', trigger: 'blur' }
  ],
  price: [
    { required: true, message: '请填写价格', trigger: 'change' },
    { type: 'number', min: 0, message: '价格不能为负数', trigger: 'change' }
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  description: [{ max: 500, message: '简介不能超过500字', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageDishes(query)
    records.value = res.data.records
    total.value = res.data.total
    selectedRows.value = []
    nextTick(() => tableRef.value?.clearSelection())
  } finally {
    loading.value = false
  }
}

const { bind } = useAdaptivePaging(query, loadData)

function openCreate() {
  Object.assign(form, { id: null, stallId: undefined, name: '', price: 0, category: '荤菜', image: '', description: '', status: 1 })
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
      await updateDish(form.id, form)
    } else {
      await createDish(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除菜品「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteDish(row.id)
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
    const results = await Promise.allSettled(rows.map(row => deleteDish(row.id)))
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
  await loadData()
  await nextTick()
  bind(wrapRef.value)
})
onActivated(loadData)
</script>

<style scoped>
.dish-manage-page {
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

.manage-toolbar {
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

.manage-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.manage-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.dish-thumb {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  object-fit: cover;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}

.dish-thumb-empty {
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 15px;
  font-weight: 600;
}

.dish-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
}

.stall-name {
  display: block;
  font-size: 13px;
  color: var(--text-1);
}

.stall-window {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-3);
}

.category-text {
  font-size: 12.5px;
  color: var(--text-2);
}

.dish-price {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.rating-main {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.rating-count {
  margin-left: 3px;
  font-size: 11.5px;
  color: var(--text-3);
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

.form-group-title {
  margin: 2px 0 10px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.005em;
}

.form-group-title:not(:first-child) {
  margin-top: 4px;
}

.form-divider {
  margin: 4px 0 18px;
}

.image-field {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.image-field .el-input {
  flex: 1;
}

.image-preview {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-preview-initial {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-2);
}

.image-preview img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 760px) {
  .manage-toolbar .el-input,
  .manage-toolbar .el-select {
    width: 100% !important;
  }
}
</style>
