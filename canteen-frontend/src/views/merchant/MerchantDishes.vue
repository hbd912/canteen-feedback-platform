<template>
  <div class="page-container adaptive-page dishes-page">
    <div class="ap-head dishes-head">
      <div>
        <h1 class="ap-title">菜品管理</h1>
        <div class="ap-sub">共 {{ total }} 道菜 · 预览学生端的样子，或切到管理进行编辑、上下架与提交审核</div>
      </div>
      <div class="ap-actions dishes-actions">
        <div class="ap-pill-group">
          <button class="ap-pill" :class="{ active: mode === 'preview' }" @click="switchPreview">档口预览</button>
          <button class="ap-pill" :class="{ active: mode === 'manage' }" @click="mode = 'manage'">菜品管理</button>
        </div>
        <button class="ap-btn ap-btn--light" @click="goAiUpload">
          <el-icon><MagicStick /></el-icon>
          AI 传图上架
        </button>
        <button class="ap-btn ap-btn--brand" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新增菜品
        </button>
      </div>
    </div>

    <el-card v-show="mode === 'manage'" shadow="never" class="page-card dishes-card">
      <template #header>
        <div class="manage-toolbar">
          <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选本页</el-checkbox>
          <template v-if="selectedRows.length">
            <span class="selected-hint">已选 {{ selectedRows.length }} 项</span>
            <el-button type="danger" plain size="small" :loading="batchDeleting" @click="handleBatchDelete">批量删除</el-button>
          </template>
          <span class="ap-toolbar-spacer"></span>
          <el-select
            v-model="query.auditStatus"
            placeholder="全部状态"
            clearable
            style="width: 132px"
            @change="onAuditFilterChange"
          >
            <el-option label="在售" value="APPROVED_ON" />
            <el-option label="审核中" value="PENDING" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已下架" value="APPROVED_OFF" />
          </el-select>
          <span class="total-hint">共 {{ total }} 道菜</span>
        </div>
      </template>

      <!-- AI 草稿：先预览效果，满意后提交管理员审核 -->
      <div v-if="draftDishes.length" class="draft-strip">
        <div class="draft-strip-head">
          <div class="draft-strip-title">
            AI 草稿
            <span class="ap-tag ap-tag--brand">{{ draftDishes.length }}</span>
          </div>
          <div class="draft-strip-sub">还没上架，可先切到「档口预览」看效果；提交审核并通过后才会出现在学生端</div>
        </div>
        <div class="draft-cards">
          <div v-for="d in draftDishes" :key="d.localId" class="draft-card">
            <img v-if="d.image" :src="d.image" class="draft-thumb" alt="" />
            <div v-else class="draft-thumb fallback">{{ (d.name || '菜').slice(0, 1) }}</div>
            <div class="draft-info">
              <div class="draft-name">{{ d.name || '未命名' }}</div>
              <div class="draft-meta ap-num">¥{{ d.price }} · {{ d.category || '未分类' }}</div>
            </div>
            <div class="draft-actions">
              <el-button size="small" type="primary" @click="publishDraft(d)">提交审核</el-button>
              <button class="ap-link" @click="redoDraft(d)">打回重做</button>
              <button class="ap-link danger" @click="discardDraft(d)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <div ref="wrapRef" class="adaptive-table">
      <el-table ref="tableRef" :data="records" row-key="id" v-loading="loading" class="dishes-table" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column label="图片" width="70" align="center">
          <template #default="{ row }">
            <el-image v-if="row.image" :src="row.image" class="dish-thumb" fit="cover" />
            <span v-else class="no-image">无图</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="菜名" min-width="140" />
        <el-table-column label="价格" width="100" align="right">
          <template #default="{ row }"><span class="price ap-num">¥{{ row.price }}</span></template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <span
                class="status-pill"
                :class="row.auditStatus === 'PENDING' ? 'amber' : row.auditStatus === 'REJECTED' ? 'red' : row.status === 1 ? 'green' : 'gray'"
              >
                <i class="status-dot"></i>
                {{ row.auditStatus === 'PENDING' ? '审核中' : row.auditStatus === 'REJECTED' ? '已驳回' : row.status === 1 ? '在售' : '下架' }}
              </span>
              <span v-if="row.auditStatus === 'REJECTED' && row.auditRemark" class="audit-remark" :title="row.auditRemark">
                {{ row.auditRemark }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.soldOut === 1" class="ap-tag ap-tag--red">售罄</span>
            <span v-else-if="row.stockQty === null || row.stockQty === undefined" class="stock-none">不限</span>
            <span v-else class="ap-num" :class="{ low: row.stockQty <= 5 }">{{ row.stockQty }} 份</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="简介" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="ap-link neutral" @click="openEdit(row)">编辑</button>
              <button
                class="ap-link"
                :disabled="row.auditStatus === 'PENDING'"
                @click="toggleStatus(row)"
              >
                {{ toggleLabel(row) }}
              </button>
              <button class="ap-link danger" @click="handleDelete(row)">删除</button>
            </div>
          </template>
        </el-table-column>
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜品' : '新增菜品'" width="560px">
      <div v-if="draftNote" class="draft-note">
        <el-icon><MagicStick /></el-icon>
        <span>{{ draftNote }}</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="dish-form">
        <el-form-item label="AI 辅助（可选）">
          <div class="ai-draft-bar">
            <el-input
              v-model="form.hint"
              maxlength="200"
              placeholder="告诉 AI 口味/卖点，例如：微辣、分量足、有花生"
            />
            <el-button type="primary" plain :loading="drafting" @click="generateDraft">
              <el-icon><MagicStick /></el-icon>
              AI 生成草稿
            </el-button>
          </div>
          <div class="ai-draft-tip">AI 只生成菜名/分类/描述草稿，价格由你定，确认后才保存</div>
        </el-form-item>
        <el-form-item label="菜名" prop="name">
          <el-input v-model="form.name" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="价格（元）" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" style="width: 160px" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" maxlength="30" placeholder="如：荤菜 / 素菜 / 汤品" />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="菜品图片">
          <el-upload
            :show-file-list="false"
            :http-request="doUpload"
            accept="image/*"
          >
            <div v-if="form.image" class="img-upload-box">
              <el-image :src="form.image" class="img-upload-preview" fit="cover" />
              <span class="img-upload-tip">点击更换</span>
            </div>
            <div v-else class="img-upload-box img-upload-empty">
              <el-icon :size="24"><Plus /></el-icon>
              <span class="img-upload-empty-text">上传图片</span>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在售</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="每日库存">
          <el-input-number v-model="form.stockQty" :min="0" :precision="0" :step="10" style="width: 160px" placeholder="留空表示不限" />
          <span class="stock-tip">留空 = 不限量；填 0 会显示售罄</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 档口预览：学生视角 -->
    <div v-show="mode === 'preview'" v-loading="previewLoading" class="preview-wrap">
      <div class="ap-card pv-hero">
        <img v-if="stallInfo.coverImage" :src="stallInfo.coverImage" class="pv-cover" alt="" />
        <div v-else class="pv-cover fallback">{{ (stallInfo.name || '档').slice(0, 1) }}</div>
        <div class="pv-info">
          <div class="pv-title">{{ stallInfo.name || '我的档口' }}</div>
          <div class="pv-tags">
            <span v-if="stallInfo.windowNo" class="pv-tag">{{ stallInfo.windowNo }} 号窗口</span>
            <span v-if="stallInfo.area" class="pv-tag">{{ stallInfo.area }}</span>
            <span v-if="stallInfo.floor" class="pv-tag">{{ stallInfo.floor }} 楼</span>
            <span v-if="stallInfo.category" class="pv-tag">{{ stallInfo.category }}</span>
          </div>
          <div v-if="stallInfo.hygieneScore" class="pv-hygiene">卫生评分 <b class="ap-num">{{ stallInfo.hygieneScore }}</b></div>
          <div v-if="stallInfo.description" class="pv-desc">{{ stallInfo.description }}</div>
        </div>
      </div>

      <div class="ap-card pv-dishes">
        <div class="ap-section-head">
          <div>
            <div class="ap-section-title">菜品 · {{ previewAll.length }}</div>
            <div class="ap-section-hint">学生看到的就在这里；待审核/已驳回的菜去「菜品管理」查看</div>
          </div>
        </div>
        <div class="pv-grid">
          <div v-for="d in previewAll" :key="d.id || d.localId" class="pv-dish" @click="openPreviewDish(d)">
            <div class="pv-dish-cover">
              <img v-if="d.image" :src="d.image" alt="" />
              <span v-else class="pv-initial">{{ (d.name || '菜').slice(0, 1) }}</span>
              <span v-if="d.isDraft" class="pv-badge ap-tag ap-tag--brand">草稿</span>
            </div>
            <div class="pv-dish-body">
              <div class="pv-dish-name">{{ d.name }}</div>
              <div class="pv-dish-price ap-num">¥{{ d.price }}</div>
            </div>
          </div>
          <div v-if="!previewLoading && !previewAll.length" class="ap-empty pv-empty">
            <div class="ap-empty-mark"><el-icon :size="20"><Dish /></el-icon></div>
            <div class="ap-empty-title">还没有在售菜品</div>
            <div>新增菜品并提交审核后，会出现在学生端预览里</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 预览详情：只提供编辑，不提供下架/删除 -->
    <el-dialog v-model="previewDetailVisible" :title="previewDetail.name || '菜品详情'" width="520px">
      <div class="pv-detail">
        <div class="pv-detail-cover">
          <img v-if="previewDetail.image" :src="previewDetail.image" alt="" />
          <span v-else class="pv-initial">{{ (previewDetail.name || '菜').slice(0, 1) }}</span>
        </div>
        <div class="pv-detail-row">
          <span class="pv-detail-price ap-num">¥{{ previewDetail.price }}</span>
          <span class="pv-detail-cat">{{ previewDetail.category || '未分类' }}</span>
          <span v-if="previewDetail.isDraft" class="ap-tag ap-tag--brand">AI 草稿 · 未上架</span>
        </div>
        <p class="pv-detail-desc">{{ previewDetail.description || '暂无描述' }}</p>
        <div class="pv-detail-meta">月售 {{ previewDetail.salesCount || 0 }} · 库存 {{ previewDetail.stockQty == null ? '不限' : previewDetail.stockQty }}</div>
      </div>
      <template #footer>
        <el-button @click="previewDetailVisible = false">关闭</el-button>
        <el-button v-if="previewDetail.isDraft" type="primary" @click="goConfirmDraft">去确认上架</el-button>
        <el-button v-else type="primary" @click="editFromPreview">去编辑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick , onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useDishDraftStore } from '@/store/dishDraft'
import { pageDishes, listAllDishes, createDish, updateDish, deleteDish, aiDishDraft } from '@/api/dish'
import { uploadImage } from '@/api/sales'
import { useAdaptivePaging } from '@/composables/useAdaptivePaging'
import request from '@/api/request'
import { coverGradient } from '@/utils/dishImage'

const userStore = useUserStore()
const router = useRouter()
const draftStore = useDishDraftStore()
const draftDishes = computed(() => draftStore.drafts)
/** 预览里把 AI 草稿排在最前面，标上「草稿」 */
const previewAll = computed(() => [
  ...draftDishes.value.map(d => ({ ...d, isDraft: true })),
  ...previewDishes.value
])

function goAiUpload() {
  // 上传与对话统一在经营助理里操作
  router.push('/merchant/ai-assistant')
}
const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const tableRef = ref()
const wrapRef = ref(null)
const selectedRows = ref([])
const batchDeleting = ref(false)
const drafting = ref(false)
const draftNote = ref('')
/** manage=菜品管理；preview=档口预览（学生视角效果） */
const mode = ref('preview')
const stallInfo = ref({})
const previewDishes = ref([])
const previewLoading = ref(false)
const previewDetail = ref({})
const previewDetailVisible = ref(false)

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

const query = reactive({ current: 1, size: 10, stallId: null, auditStatus: undefined, status: undefined })

/** 状态筛选：在售/已下架 = auditStatus APPROVED + status 叠加；审核中/已驳回只看审核状态 */
function onAuditFilterChange(val) {
  query.current = 1
  if (val === 'APPROVED_ON') {
    query.auditStatus = 'APPROVED'
    query.status = 1
  } else if (val === 'APPROVED_OFF') {
    query.auditStatus = 'APPROVED'
    query.status = 0
  } else if (val === 'PENDING' || val === 'REJECTED') {
    query.auditStatus = val
    query.status = undefined
  } else {
    query.auditStatus = undefined
    query.status = undefined
  }
  loadData()
}
const form = reactive({ id: null, name: '', price: 1, category: '', description: '', image: '', status: 1, stockQty: null, soldOut: 0, hint: '', stallId: null })
const formRef = ref()
const rules = {
  name: [{ required: true, message: '请填写菜名', trigger: 'blur' }],
  price: [{ required: true, message: '请填写价格', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    query.stallId = userStore.userInfo?.stallId
    const res = await pageDishes(query)
    records.value = res.data.records || []
    total.value = res.data.total || 0
    selectedRows.value = []
    nextTick(() => tableRef.value?.clearSelection())
  } finally {
    loading.value = false
  }
}

const { bind } = useAdaptivePaging(query, loadData)

function openCreate() {
  Object.assign(form, { id: null, name: '', price: 1, category: '', description: '', image: '', status: 1, stockQty: null, soldOut: 0, hint: '', stallId: userStore.userInfo?.stallId })
  draftNote.value = ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  Object.assign(form, { ...row, hint: '', stallId: userStore.userInfo?.stallId })
  draftNote.value = ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/* ---------- 档口预览 ---------- */
async function switchPreview() {
  mode.value = 'preview'
  previewLoading.value = true
  try {
    const stallId = userStore.userInfo?.stallId
    const [st, dishes] = await Promise.all([
      request.get('/merchant/certificates'),
      listAllDishes(stallId, { includeOffShelf: true })
    ])
    stallInfo.value = st.data || {}
    // 档口预览 = 学生端真实所见：只展示「在售且审核通过」的菜品。
    // 待审核/已驳回的菜学生看不到，预览里也不出现（想看状态去「菜品管理」tab）。
    previewDishes.value = (dishes || [])
      .filter(d => d.status === 1 && d.auditStatus === 'APPROVED')
  } catch {
    previewDishes.value = []
  } finally {
    previewLoading.value = false
  }
}

function openPreviewDish(d) {
  previewDetail.value = d
  previewDetailVisible.value = true
}

/** 预览里只允许「去编辑」，下架/删除仍在管理页操作 */
function editFromPreview() {
  const d = previewDetail.value
  previewDetailVisible.value = false
  mode.value = 'manage'
  openEdit(d)
}

/** AI 草稿：满意后真正上架，并从草稿里移除 */
async function publishDraft(d) {
  try {
    await createDish({
      stallId: userStore.userInfo?.stallId,
      name: d.name,
      price: d.price,
      category: d.category || '',
      description: d.description || '',
      image: d.image || '',
      featured: 0,
      status: 1,
      stockQty: null,
      soldOut: 0
    })
    draftStore.remove(d.localId)
    ElMessage.success('已提交审核，管理员通过后上架')
    if (mode.value === 'preview') switchPreview()
    loadData()
  } catch {
    ElMessage.error('提交审核失败')
  }
}

/** 不满意：去经营助理里打回重做（草稿会保留在对话里） */
function redoDraft() {
  router.push('/merchant/ai-assistant')
}

async function discardDraft(d) {
  try {
    await ElMessageBox.confirm('删除这条 AI 草稿？删除后可重新生成。', '删除草稿', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  draftStore.remove(d.localId)
  ElMessage.success('已删除草稿')
}

function goConfirmDraft() {
  previewDetailVisible.value = false
  router.push('/merchant/ai-assistant')
}

async function generateDraft() {
  if (!form.image && !form.name && !form.hint) {
    ElMessage.warning('请先上传图片，或填写菜名/口味提示')
    return
  }
  drafting.value = true
  draftNote.value = ''
  try {
    const res = await aiDishDraft({
      image: form.image || undefined,
      name: form.name || undefined,
      price: form.price,
      category: form.category || undefined,
      hint: form.hint || undefined
    })
    const d = res.data || {}
    if (d.name) form.name = d.name
    if (d.category) form.category = d.category
    if (d.description) form.description = d.description
    draftNote.value = d.note || '草稿已生成，请核对后保存'
    ElMessage.success(d.visionUsed ? '已根据图片生成草稿' : '已根据文字生成草稿')
  } catch {
    // 请求层已统一提示
  } finally {
    drafting.value = false
  }
}

async function doUpload(option) {
  try {
    const res = await uploadImage(option.file)
    form.image = res.data
    ElMessage.success('图片已上传')
  } catch {
    ElMessage.error('图片上传失败')
  }
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = {
      stallId: form.stallId,
      name: form.name,
      price: form.price,
      category: form.category,
      description: form.description,
      image: form.image,
      status: form.status,
      stockQty: form.stockQty,
      soldOut: form.soldOut
    }
    if (form.id) {
      await updateDish(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await createDish(payload)
      ElMessage.success('已提交审核，管理员通过后上架')
    }
    dialogVisible.value = false
    draftNote.value = ''
    loadData()
  } finally {
    submitting.value = false
  }
}

function toggleLabel(row) {
  if (row.auditStatus === 'PENDING') return '审核中'
  if (row.status === 1) return '下架'
  if (row.auditStatus === 'REJECTED') return '重新提交'
  return '提交审核'
}

async function toggleStatus(row) {
  if (row.auditStatus === 'PENDING') return
  const target = row.status === 1 ? 0 : 1
  try {
    await updateDish(row.id, {
      ...row,
      stallId: userStore.userInfo?.stallId,
      status: target
    })
    ElMessage.success(target === 1 ? '已提交审核，管理员通过后上架' : '已下架')
    loadData()
  } catch {
    // 请求层已提示
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
  await loadData()
  await nextTick()
  bind(wrapRef.value)
  // 默认先看「档口预览」，需要时再手动切到菜品管理
  await switchPreview()
})
onActivated(() => {
  loadData()
  if (mode.value === 'preview') switchPreview()
})
</script>

<style scoped>
.dishes-page {
  max-width: 1480px;
  margin: 0 auto;
}

.dishes-head {
  flex-shrink: 0;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.dishes-actions { flex-wrap: wrap; }

.manage-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.ap-toolbar-spacer { flex: 1; }

.selected-hint { font-size: 12px; color: var(--text-2); }

.total-hint { font-size: 12px; color: var(--text-3); }

.dishes-table :deep(.el-table__header th.el-table__cell) { background: #fafafa; }

.dishes-table :deep(.el-table__cell) { border-bottom-color: rgba(0, 0, 0, 0.045); }

.dishes-table :deep(.el-table__row:hover > td.el-table__cell) { background: #fafafa; }

.dish-thumb {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: block;
  overflow: hidden;
  background: #f2f2f5;
}

.no-image { font-size: 12px; color: var(--text-3); }

.price { font-size: 14px; font-weight: 600; color: var(--text-1); }

.status-cell { display: flex; flex-direction: column; align-items: center; gap: 4px; }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 550;
  white-space: nowrap;
}

.status-pill .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.status-pill.amber { background: #fff5e6; color: #a96100; }
.status-pill.red { background: #ffeceb; color: #c9342b; }
.status-pill.green { background: #e9f8ee; color: #1d8a45; }
.status-pill.gray { background: rgba(0, 0, 0, 0.055); color: var(--text-2); }

.audit-remark {
  max-width: 112px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #c9342b;
  font-size: 10.5px;
}

.stock-none { font-size: 13px; color: var(--text-3); }

.low { color: #c9342b; font-weight: 600; }

.row-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.ap-link.neutral { color: var(--text-2); }
.ap-link.neutral:hover { color: var(--text-1); }
.ap-link.danger { color: #c9342b; }
.ap-link.danger:hover { color: #a92c24; }
.ap-link:disabled { opacity: 0.4; cursor: not-allowed; }

/* AI 草稿区 */
.draft-strip {
  flex-shrink: 0;
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--surface-2);
}

.draft-strip-head { margin-bottom: 10px; }

.draft-strip-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--text-1);
}

.draft-strip-sub { margin-top: 5px; font-size: 12px; color: var(--text-3); line-height: 1.5; }

.draft-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 168px;
  overflow-y: auto;
}

.draft-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
}

.draft-thumb {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f2f2f5;
}

.draft-thumb.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #86868b;
  background: #f2f2f5;
}

.draft-info { flex: 1; min-width: 0; }

.draft-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-meta { margin-top: 2px; font-size: 11.5px; color: var(--text-3); }

.draft-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.draft-actions .ap-link { font-size: 12.5px; }

/* 档口预览 */
.preview-wrap { display: flex; flex-direction: column; gap: 16px; padding-bottom: 24px; }

.pv-hero { display: flex; gap: 18px; align-items: flex-start; }

.pv-cover {
  width: 96px;
  height: 96px;
  border-radius: 18px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f0f0f2;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pv-cover.fallback { font-size: 32px; font-weight: 650; color: #86868b; }

.pv-info { min-width: 0; }
.pv-title { font-size: 20px; font-weight: 650; letter-spacing: -0.02em; color: var(--text-1); }
.pv-tags { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; }

.pv-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12px;
}

.pv-hygiene { margin-top: 9px; font-size: 12px; color: var(--text-2); }
.pv-hygiene b { color: var(--text-1); font-weight: 600; }
.pv-desc { margin-top: 8px; font-size: 13px; color: var(--text-2); line-height: 1.65; }

.pv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
  min-height: 140px;
}

.pv-dish {
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  background: var(--surface);
  transition: transform 0.18s var(--ease-apple), box-shadow 0.18s var(--ease-apple);
}

.pv-dish:hover { transform: translateY(-2px); box-shadow: var(--shadow-sm); }

.pv-dish-cover {
  position: relative;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f2f2f5;
}

.pv-dish-cover img { width: 100%; height: 100%; object-fit: cover; }

.pv-initial { font-size: 28px; font-weight: 650; color: #86868b; }

.pv-badge { position: absolute; top: 8px; left: 8px; }

.pv-dish-body { padding: 10px 12px 12px; }

.pv-dish-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-dish-price { margin-top: 4px; font-size: 14px; font-weight: 650; color: var(--brand-dark); }

.pv-empty { grid-column: 1 / -1; }

.pv-detail-cover {
  aspect-ratio: 16 / 9;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  margin-bottom: 14px;
  background: #f2f2f5;
}

.pv-detail-cover img { width: 100%; height: 100%; object-fit: cover; }

.pv-detail-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px 12px; margin-bottom: 12px; }
.pv-detail-price { font-size: 26px; font-weight: 650; color: var(--brand-dark); letter-spacing: -0.02em; }
.pv-detail-cat { font-size: 13px; color: var(--text-2); }
.pv-detail-desc { font-size: 14px; color: var(--text-1); line-height: 1.7; }
.pv-detail-meta { margin-top: 12px; font-size: 12px; color: var(--text-3); }

/* 表单 */
.dish-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
  padding-bottom: 6px;
  line-height: 1.4;
}

.dish-form :deep(.el-form-item) { margin-bottom: 18px; }

.draft-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12.5px;
  line-height: 1.5;
}

.draft-note .el-icon { color: var(--brand-dark); flex-shrink: 0; }

.ai-draft-bar { display: flex; gap: 8px; width: 100%; flex-wrap: wrap; }
.ai-draft-bar .el-input { flex: 1; min-width: 180px; }

.ai-draft-tip {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-3);
}

.stock-tip { margin-left: 8px; font-size: 12px; color: var(--text-3); }

.img-upload-box {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
}

.img-upload-preview { width: 100%; height: 100%; display: block; }

.img-upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px solid var(--border-strong);
  background: var(--surface-2);
  color: var(--text-3);
}

.img-upload-empty-text { font-size: 12px; }

.img-upload-tip {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 3px 0;
  font-size: 11.5px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  text-align: center;
}

@media (max-width: 760px) {
  .pv-hero { flex-direction: column; }
  .pv-cover { width: 76px; height: 76px; border-radius: 14px; }
  .draft-card { flex-wrap: wrap; }
  .draft-actions { width: 100%; justify-content: flex-end; }
}
</style>
