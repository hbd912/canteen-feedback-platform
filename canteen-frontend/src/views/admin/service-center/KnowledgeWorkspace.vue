<template>
  <div class="cs-panel knowledge-workspace">
    <div class="cs-panel-head">
      <div class="cs-panel-title-wrap">
        <span class="cs-panel-title">{{ kind === 'EXPERIENCE' ? '知识库 · 有效回复' : kind === 'FAQ' ? '知识库 · 常用问答' : '知识库' }}</span>
        <span class="cs-panel-count">共 {{ total }} 条，问答与有效回复统一管理</span>
      </div>
      <div class="cs-panel-actions">
        <el-dropdown trigger="click" @command="handleTool">
          <button type="button" class="ap-btn ap-btn--light ap-btn--sm" aria-label="知识管理工具">
            管理工具
            <el-icon :size="12" class="tool-caret"><ArrowDown /></el-icon>
          </button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item command="test">测试回复匹配</el-dropdown-item>
            <el-dropdown-item command="reindex" :disabled="reindexing">{{ reindexing ? '正在更新检索…' : '更新经验检索' }}</el-dropdown-item>
            <el-dropdown-item command="refresh">刷新列表</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
        <button type="button" class="ap-btn ap-btn--sm" @click="openCreate()">
          <el-icon :size="14"><Plus /></el-icon>
          新增知识
        </button>
      </div>
    </div>

    <div class="cs-toolbar">
      <el-input v-model="keyword" class="cs-search" placeholder="搜索问题、答案或关键词" clearable
                aria-label="搜索知识" @keyup.enter="reload" @clear="reload" />
      <el-select :model-value="kind" class="cs-select" placeholder="全部知识" aria-label="知识类型" @change="setKind">
        <el-option label="全部知识" value="" /><el-option label="常用问答" value="FAQ" /><el-option label="有效回复" value="EXPERIENCE" />
      </el-select>
      <el-select v-if="kind === 'FAQ'" v-model="category" class="cs-select" placeholder="全部分类" clearable aria-label="问答分类" @change="reload">
        <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-if="kind === 'EXPERIENCE'" v-model="scene" class="cs-select" placeholder="全部场景" clearable aria-label="回复场景" @change="reload">
        <el-option v-for="item in scenes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="reload">搜索</button>
    </div>

    <div ref="wrapRef" class="cs-table-wrap adaptive-table">
      <el-table ref="tableRef" :data="records" :row-key="rowKey" v-loading="loading" @selection-change="rows => selectedRows = rows">
        <el-table-column type="selection" width="40" />
        <el-table-column label="问题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="cs-primary-text">{{ row.question }}</span></template>
        </el-table-column>
        <el-table-column label="回复内容" min-width="260" show-overflow-tooltip>
          <template #default="{ row }"><span class="cs-secondary-text">{{ row.answer }}</span></template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="kind-cell" :class="row.kind === 'EXPERIENCE' ? 'is-exp' : 'is-faq'">
              <i class="kind-dot"></i>
              {{ row.kind === 'FAQ' ? '常用问答' : '有效回复' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="适用范围" min-width="135" show-overflow-tooltip>
          <template #default="{ row }">{{ row.kind === 'FAQ' ? categoryLabel(row.category) : sceneLabel(row.scene) }}{{ row.stallId ? ` · 档口 ${row.stallId}` : '' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="kind-cell" :class="statusClass(row) === 'good' ? 'is-good' : statusClass(row) === 'warning' ? 'is-warn' : 'is-idle'">
              <i class="kind-dot"></i>
              {{ statusLabel(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <button type="button" class="cs-row-link" @click="openEdit(row)">编辑</button>
            <button type="button" class="cs-row-link cs-row-link--danger" @click="remove(row)">删除</button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="cs-empty">
            <span>{{ loadError ? '暂时无法读取知识' : '没有符合条件的知识' }}</span>
            <button v-if="loadError" type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="loadData">重新加载</button>
            <button v-else type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="openCreate()">新增知识</button>
          </div>
        </template>
      </el-table>
    </div>
    <footer class="cs-footer">
      <div v-if="selectedRows.length" class="cs-selection">
        <span>已选 {{ selectedRows.length }} 条</span>
        <button type="button" class="cs-row-link cs-row-link--danger" :disabled="deleting" @click="batchRemove">
          {{ deleting ? '删除中…' : '删除所选' }}
        </button>
      </div>
      <span v-else class="cs-footer-note">共 {{ total }} 条知识，问答与有效回复统一管理</span>
      <el-pagination :current-page="page.current" :page-size="page.size" :total="total" layout="prev, pager, next" @current-change="changePage" />
    </footer>

    <el-drawer v-model="editorVisible" :title="form.id ? '编辑知识' : '新增知识'" size="min(580px, 100vw)" append-to-body>
      <p class="cs-drawer-intro">常用问答用于明确规则；有效回复用于保留人工验证过的处理方式。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="cs-group-title">类型与范围</div>
        <el-form-item label="知识类型">
          <el-radio-group v-model="form.kind" :disabled="!!form.id" class="kind-radio"><el-radio-button value="FAQ">常用问答</el-radio-button><el-radio-button value="EXPERIENCE">有效回复</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.kind === 'FAQ'" label="分类" prop="category"><el-select v-model="form.category" style="width: 100%"><el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-else label="适用场景" prop="scene"><el-select v-model="form.scene" style="width: 100%"><el-option v-for="item in scenes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>

        <div class="ap-divider cs-form-divider"></div>
        <div class="cs-group-title">问题与回复</div>
        <el-form-item label="问题" prop="question"><el-input v-model="form.question" type="textarea" :rows="2" :maxlength="form.kind === 'FAQ' ? 200 : 1000" show-word-limit placeholder="填写需要回答的问题" /></el-form-item>
        <el-form-item label="回复内容" prop="answer"><el-input v-model="form.answer" type="textarea" :rows="7" maxlength="2000" show-word-limit placeholder="填写已确认、可以实际使用的回复" /></el-form-item>

        <div class="ap-divider cs-form-divider"></div>
        <div class="cs-group-title">检索与状态</div>
        <el-form-item :label="form.kind === 'FAQ' ? '匹配关键词' : '标签'"><el-input v-if="form.kind === 'FAQ'" v-model="form.keywords" maxlength="500" placeholder="逗号分隔，如：退款、订单、取餐" /><el-input v-else v-model="form.tags" maxlength="200" placeholder="如：口味、分量、退款" /></el-form-item>
        <template v-if="form.kind === 'FAQ'">
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="排序号"><el-input-number v-model="form.sortNo" :min="0" :max="999" style="width: 100%" /></el-form-item></el-col>
          </el-row>
        </template>
        <template v-else>
          <el-form-item label="适用档口"><el-input-number v-model="form.stallId" :min="1" :precision="0" controls-position="right" placeholder="留空为全部档口" style="width: 100%" /><span class="cs-form-tip" style="margin: 0 0 0 12px">留空为全部档口</span></el-form-item>
          <el-form-item label="有效评分"><el-rate v-model="form.score" /></el-form-item>
          <p v-if="form.id" class="cs-form-tip">来源：{{ sourceLabel(form.source) }}。修改问题或回复后，检索索引会重新生成。</p>
        </template>
      </el-form>
      <template #footer>
        <button type="button" class="ap-btn ap-btn--light" @click="editorVisible = false">取消</button>
        <button type="button" class="ap-btn" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存知识' }}</button>
      </template>
    </el-drawer>

    <el-drawer v-model="testVisible" title="测试回复匹配" size="min(580px, 100vw)" append-to-body>
      <p class="cs-drawer-intro">输入一个问题，检查有效回复是否能被检索到。每次最多显示 5 条匹配。</p>
      <el-select v-model="testScene" clearable placeholder="全部场景" aria-label="测试场景" style="width: 100%; margin-bottom: 12px"><el-option v-for="item in scenes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-input v-model="testQuestion" type="textarea" :rows="3" maxlength="1000" placeholder="输入要测试的问题" aria-label="测试问题" />
      <div class="cs-detail-actions"><button type="button" class="ap-btn" :disabled="testing" @click="runTest">{{ testing ? '测试中…' : '开始测试' }}</button></div>
      <div v-if="testError" class="test-error">匹配测试失败，请重试。</div>
      <div v-for="row in testResults" :key="row.id" class="test-result">
        <div class="test-result-head">
          <span class="test-scene">{{ sceneLabel(row.scene) }}</span>
          <span class="test-score ap-num">匹配度 {{ Math.round((row.matchScore || 0) * 100) }}%</span>
        </div>
        <div class="test-problem">{{ row.problem }}</div>
        <div class="test-solution">{{ row.solution }}</div>
      </div>
      <div v-if="tested && !testError && !testResults.length" class="cs-empty">没有匹配的有效回复，可补充知识后再试。</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { getKnowledge, updateExperience } from '@/api/serviceCenter'
import { createFaq, updateFaq, deleteFaq } from '@/api/chat'
import { createExperience, deleteExperience, searchExperiences, reindexExperiences } from '@/api/experience'
import { useServicePaging } from './useServicePaging'
import { categories, scenes, categoryLabel, sceneLabel, sourceLabel } from './labels'

const props = defineProps({ kind: { type: String, default: '' } })
const emit = defineEmits(['change-kind'])
const keyword = ref('')
const category = ref('')
const scene = ref('')
const records = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref(false)
const selectedRows = ref([])
const deleting = ref(false)
const reindexing = ref(false)
const tableRef = ref(null)
const wrapRef = ref(null)
const page = reactive({ current: 1, size: 10 })
let requestId = 0
let disposed = false
const rowKey = row => `${row.kind}:${row.id}`
const { bind } = useServicePaging(page, loadData)

function statusLabel(row) { return row.kind === 'FAQ' ? (row.status === 1 ? '已启用' : '已停用') : (row.hasEmbedding ? '可检索' : '待更新检索') }
function statusClass(row) { return row.kind === 'FAQ' ? (row.status === 1 ? 'good' : '') : (row.hasEmbedding ? 'good' : 'warning') }
function setKind(value) { emit('change-kind', value) }
function reload() { page.current = 1; loadData() }
function changePage(value) { page.current = value; loadData() }
async function loadData() {
  const id = ++requestId
  loading.value = true
  loadError.value = false
  try {
    const result = await getKnowledge({ ...page, kind: props.kind || undefined, keyword: keyword.value.trim() || undefined, category: props.kind === 'FAQ' ? category.value || undefined : undefined, scene: props.kind === 'EXPERIENCE' ? scene.value || undefined : undefined })
    if (disposed || id !== requestId) return
    total.value = Number(result.data?.total || 0)
    if (page.current > 1 && !result.data?.records?.length && total.value > 0) { page.current = Math.ceil(total.value / page.size); return loadData() }
    records.value = result.data?.records || []
    selectedRows.value = []
    tableRef.value?.clearSelection()
  } catch {
    if (id === requestId) { records.value = []; total.value = 0; loadError.value = true }
  } finally { if (id === requestId) loading.value = false }
}
watch(() => props.kind, () => { category.value = ''; scene.value = ''; reload() })
onMounted(async () => { loadData(); await nextTick(); bind(wrapRef.value) })
onBeforeUnmount(() => { disposed = true; requestId++ })

const editorVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const emptyForm = () => ({ id: null, kind: 'FAQ', question: '', answer: '', category: 'OTHER', scene: 'CUSTOMER_SERVICE', keywords: '', tags: '', sortNo: 50, status: 1, score: 5, stallId: null, source: '' })
const form = reactive(emptyForm())
const rules = { question: [{ required: true, whitespace: true, message: '请填写问题', trigger: 'blur' }], answer: [{ required: true, whitespace: true, message: '请填写回复内容', trigger: 'blur' }] }
function openCreate(preset = {}) {
  Object.assign(form, emptyForm(), { kind: props.kind === 'EXPERIENCE' ? 'EXPERIENCE' : 'FAQ' }, preset)
  editorVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
function openEdit(row) {
  Object.assign(form, emptyForm(), row, { score: row.score ?? 5, stallId: row.stallId ?? null })
  editorVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
async function save() {
  if (saving.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    if (form.kind === 'FAQ') {
      const payload = { question: form.question.trim(), answer: form.answer.trim(), category: form.category, keywords: form.keywords || '', sortNo: form.sortNo ?? 50, status: form.status ?? 1 }
      if (form.id) await updateFaq(form.id, payload)
      else await createFaq(payload)
    } else {
      const payload = { scene: form.scene, problem: form.question.trim(), solution: form.answer.trim(), tags: form.tags || '', score: form.score || 5, stallId: form.stallId || null }
      if (form.id) await updateExperience(form.id, payload)
      else await createExperience(payload)
    }
    ElMessage.success('知识已保存')
    editorVisible.value = false
    await loadData()
  } catch { /* 请求层显示具体失败原因，保留表单 */ } finally { saving.value = false }
}
async function remove(row) {
  try { await ElMessageBox.confirm(`删除「${row.question.slice(0, 48)}」？删除后将不再用于回复。`, '删除知识', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  try { await (row.kind === 'FAQ' ? deleteFaq(row.id) : deleteExperience(row.id)); ElMessage.success('知识已删除'); await loadData() } catch { /* 请求层提示 */ }
}
async function batchRemove() {
  const rows = [...selectedRows.value]
  if (!rows.length) return
  try { await ElMessageBox.confirm(`删除所选的 ${rows.length} 条知识？此操作不可恢复。`, '删除知识', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  deleting.value = true
  try {
    const result = await Promise.allSettled(rows.map(row => row.kind === 'FAQ' ? deleteFaq(row.id) : deleteExperience(row.id)))
    const success = result.filter(item => item.status === 'fulfilled').length
    if (success === rows.length) ElMessage.success(`已删除 ${success} 条知识`)
    else ElMessage.warning(`已删除 ${success} 条，${rows.length - success} 条删除失败`)
    await loadData()
  } finally { deleting.value = false }
}

const testVisible = ref(false)
const testQuestion = ref('')
const testScene = ref('')
const testResults = ref([])
const testing = ref(false)
const tested = ref(false)
const testError = ref(false)
async function handleTool(command) {
  if (command === 'test') { testVisible.value = true; return }
  if (command === 'refresh') { loadData(); return }
  if (command !== 'reindex' || reindexing.value) return
  reindexing.value = true
  try { const result = await reindexExperiences(); ElMessage.success(`本次已更新 ${result.data?.embedded || 0} 条经验的检索索引`); loadData() } catch { /* 请求层提示 */ } finally { reindexing.value = false }
}
async function runTest() {
  if (!testQuestion.value.trim()) { ElMessage.warning('请先输入测试问题'); return }
  testing.value = true
  testError.value = false
  try { const result = await searchExperiences({ q: testQuestion.value.trim(), scene: testScene.value || undefined, topK: 5 }); testResults.value = result.data || []; tested.value = true } catch { testResults.value = []; testError.value = true } finally { testing.value = false }
}
defineExpose({ openCreate })
</script>

<style scoped>
/* ---------- 面板标题行（新增，位于 cs-toolbar 之上） ---------- */
.cs-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px 0;
  flex-shrink: 0;
}

.cs-panel-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.cs-panel-title {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.cs-panel-count {
  font-size: 12px;
  color: var(--text-3);
}

.cs-panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.tool-caret {
  margin-left: 2px;
  color: var(--text-3);
}

/* ---------- 工具条收窄 ---------- */
.cs-toolbar {
  padding-top: 12px;
}

/* ---------- 表格：类型与状态统一圆点编码 ---------- */
.kind-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}

.kind-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a1a1a6;
  flex-shrink: 0;
}

.kind-cell.is-faq .kind-dot {
  background: #1d1d1f;
}

.kind-cell.is-exp .kind-dot {
  background: var(--brand);
}

.kind-cell.is-good .kind-dot {
  background: #34c759;
}

.kind-cell.is-warn .kind-dot {
  background: #ff9500;
}

.kind-cell.is-idle {
  color: var(--text-3);
}

/* ---------- 行内文字按钮 ---------- */
.cs-row-link {
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

.cs-row-link:hover {
  background: rgba(0, 0, 0, 0.04);
}

.cs-row-link--danger {
  color: #c9342b;
}

.cs-row-link--danger:hover {
  background: #ffeceb;
}

.cs-row-link:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ---------- footer 固定高度，避免选择前后分页跳动 ---------- */
.cs-footer {
  min-height: 52px;
}

/* ---------- 编辑抽屉 ---------- */
.cs-group-title {
  margin: 2px 0 12px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-1);
}

.cs-form-divider {
  margin: 6px 0 20px;
}

/* 类型 radio-button 选中态改深色，替代全局橙色渐变 */
.kind-radio :deep(.el-radio-button__inner) {
  box-shadow: none;
  border-color: var(--border);
  color: var(--text-2);
  background: #fff;
}

.kind-radio :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--text-1);
  border-color: var(--text-1);
  color: #fff;
}

/* ---------- 测试抽屉 ---------- */
.test-error {
  margin-top: 20px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #ffeceb;
  color: #c9342b;
  font-size: 12.5px;
}

.test-result {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f3;
}

.test-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-3);
}

.test-scene {
  font-weight: 600;
  color: #45454d;
}

.test-score {
  font-variant-numeric: tabular-nums;
}

.test-problem {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
  line-height: 1.6;
}

.test-solution {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
