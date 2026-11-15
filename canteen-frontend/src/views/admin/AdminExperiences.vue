<template>
  <div class="page-container adaptive-page exp-page">
    <div class="ap-head manage-head">
      <div>
        <h1 class="ap-title">AI 经验库</h1>
        <p class="ap-sub">仅收录人工验证过的问答，AI 自动回复不会入库，避免自我强化。</p>
      </div>
      <div class="ap-actions">
        <button type="button" class="ap-btn ap-btn--ghost ap-btn--sm" @click="refresh">刷新</button>
        <button type="button" class="ap-btn ap-btn--light ap-btn--sm" :disabled="reindexing" @click="reindex">
          {{ reindexing ? '更新中…' : '重建向量' }}
        </button>
        <button type="button" class="ap-btn ap-btn--sm" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon>
          新增经验
        </button>
      </div>
    </div>

    <div class="page-card table-card">
      <div class="exp-stats">
        <div class="exp-stat">
          <span class="exp-stat-label">经验总数</span>
          <b class="exp-stat-value">{{ stats.total }}</b>
        </div>
        <div class="exp-stat">
          <span class="exp-stat-label">已向量化</span>
          <b class="exp-stat-value">{{ stats.embedded }}</b>
        </div>
        <div class="exp-stat">
          <span class="exp-stat-label">
            <i class="stat-dot"></i>
            待补向量
          </span>
          <b class="exp-stat-value">{{ stats.pending }}</b>
        </div>
      </div>

      <div class="exp-toolbar">
        <el-select v-model="scene" clearable placeholder="全部场景" style="width: 180px" @change="loadData">
          <el-option label="客服会话" value="CUSTOMER_SERVICE" />
          <el-option label="经营助理" value="MERCHANT_ASSISTANT" />
          <el-option label="评价回复" value="REVIEW_REPLY" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="输入一个问题，测试 RAG 检索效果"
          clearable
          style="width: 320px"
          @keyup.enter="doSearch"
        />
        <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="doSearch">检索测试</button>
        <button v-if="searched" type="button" class="ap-btn ap-btn--ghost ap-btn--sm" @click="clearSearch">
          返回列表
        </button>
      </div>

      <div v-if="searched" class="search-state">
        <span class="ap-tag ap-tag--brand">检索结果 · 按匹配度排序</span>
        <span class="search-hint">最多显示 5 条最相关的经验</span>
      </div>

      <div class="adaptive-table">
        <el-table :data="records" v-loading="loading" class="exp-table">
          <el-table-column label="场景" width="120">
            <template #default="{ row }">
              <span class="scene-text">{{ sceneLabel(row.scene) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="问题" min-width="200">
            <template #default="{ row }">
              <div class="problem-text">{{ row.problem }}</div>
              <div v-if="searched && row.matchScore != null" class="match-text">
                匹配度 {{ (row.matchScore * 100).toFixed(0) }}%
              </div>
            </template>
          </el-table-column>
          <el-table-column label="有效解法" min-width="260">
            <template #default="{ row }">
              <div class="solution-text">{{ row.solution }}</div>
            </template>
          </el-table-column>
          <el-table-column label="评分" width="70" align="center">
            <template #default="{ row }">
              <span class="score-text ap-num">{{ row.score ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="120">
            <template #default="{ row }">
              <span class="source-text">{{ row.source || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="向量" width="100">
            <template #default="{ row }">
              <span class="vector-cell" :class="row.hasEmbedding ? 'is-ready' : 'is-pending'">
                <i class="vector-dot"></i>
                {{ row.hasEmbedding ? '已生成' : '待补' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">
              <span class="time-text ap-num">{{ short(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <button type="button" class="row-link danger-text" @click="remove(row)">删除</button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark">
                <el-icon :size="20"><Collection /></el-icon>
              </div>
              <div class="ap-empty-title">{{ searched ? '没有匹配的经验' : '暂无经验' }}</div>
              <div>{{ searched ? '换个问题或清空场景筛选再试' : '收到人工验证的问答后会收录在这里' }}</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="新增经验" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-group-title">内容</div>
        <el-form-item label="问题" prop="problem">
          <el-input v-model="form.problem" type="textarea" :rows="2" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="有效解法" prop="solution">
          <el-input v-model="form.solution" type="textarea" :rows="5" maxlength="2000" show-word-limit />
        </el-form-item>

        <div class="ap-divider form-divider"></div>
        <div class="form-group-title">归类与评分</div>
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="场景" prop="scene">
              <el-select v-model="form.scene" style="width: 100%">
                <el-option label="客服会话" value="CUSTOMER_SERVICE" />
                <el-option label="经营助理" value="MERCHANT_ASSISTANT" />
                <el-option label="评价回复" value="REVIEW_REPLY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="评分">
              <el-input-number v-model="form.score" :min="1" :max="5" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="归属档口">
              <el-input-number
                v-model="form.stallId"
                :min="1"
                :precision="0"
                placeholder="留空=全场"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签">
          <el-input v-model="form.tags" maxlength="200" placeholder="例如：口味、分量、退款" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button type="button" class="ap-btn ap-btn--light" @click="dialogVisible = false">取消</button>
        <button type="button" class="ap-btn" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中…' : '保存并向量化' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminExperiences, searchExperiences, createExperience,
  deleteExperience, reindexExperiences, experienceStats
} from '@/api/experience'

const loading = ref(false)
const reindexing = ref(false)
const submitting = ref(false)
const records = ref([])
const stats = reactive({ total: 0, embedded: 0, pending: 0 })
const scene = ref('')
const keyword = ref('')
const searched = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const form = reactive({
  scene: 'CUSTOMER_SERVICE',
  problem: '',
  solution: '',
  tags: '',
  score: 5,
  stallId: null
})

const rules = {
  scene: [{ required: true, message: '请选择场景', trigger: 'change' }],
  problem: [{ required: true, message: '请填写问题', trigger: 'blur' }],
  solution: [{ required: true, message: '请填写解法', trigger: 'blur' }]
}

function sceneLabel(s) {
  return { CUSTOMER_SERVICE: '客服会话', MERCHANT_ASSISTANT: '经营助理', REVIEW_REPLY: '评价回复' }[s] || s
}

function short(t) {
  return t ? String(t).slice(0, 16) : ''
}

async function loadStats() {
  try {
    const res = await experienceStats()
    Object.assign(stats, res.data || {})
  } catch {
    // 忽略
  }
}

async function loadData() {
  loading.value = true
  searched.value = false
  try {
    const res = await adminExperiences({ scene: scene.value || undefined, limit: 100 })
    records.value = res.data || []
  } catch {
    records.value = []
  } finally {
    loading.value = false
    loadStats()
  }
}

async function doSearch() {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入要测试的问题')
    return
  }
  loading.value = true
  searched.value = true
  try {
    const res = await searchExperiences({
      q: keyword.value.trim(),
      scene: scene.value || undefined,
      topK: 5
    })
    records.value = res.data || []
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

function clearSearch() {
  keyword.value = ''
  loadData()
}

function openCreate() {
  Object.assign(form, { scene: 'CUSTOMER_SERVICE', problem: '', solution: '', tags: '', score: 5, stallId: null })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createExperience(form)
    ElMessage.success('已保存并向量化')
    dialogVisible.value = false
    loadData()
  } catch {
    // 请求层已统一提示
  } finally {
    submitting.value = false
  }
}

async function reindex() {
  reindexing.value = true
  try {
    const res = await reindexExperiences()
    ElMessage.success(`已补向量 ${res.data?.embedded ?? 0} 条`)
    loadData()
  } catch {
    // 请求层已统一提示
  } finally {
    reindexing.value = false
  }
}

function remove(row) {
  ElMessageBox.confirm('删除这条经验？删除后不会再被检索到。', '提示', { type: 'warning' })
    .then(async () => {
      await deleteExperience(row.id)
      ElMessage.success('已删除')
      loadData()
    })
    .catch(() => {})
}

function refresh() {
  loadData()
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.exp-page {
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
  padding: 0 18px 10px;
  border-radius: 22px;
}

/* ---------- 统计 ---------- */
.exp-stats {
  display: flex;
  align-items: stretch;
  flex-shrink: 0;
  padding: 14px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.exp-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 150px;
  padding: 0 24px;
  border-left: 1px solid rgba(0, 0, 0, 0.05);
}

.exp-stat:first-child {
  border-left: none;
  padding-left: 6px;
}

.exp-stat-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
}

.exp-stat-value {
  font-size: 24px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  flex-shrink: 0;
}

/* ---------- 工具条 ---------- */
.exp-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 14px 2px;
}

.search-state {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-bottom: 10px;
}

.search-hint {
  font-size: 12px;
  color: var(--text-3);
}

/* ---------- 表格 ---------- */
.exp-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.exp-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.scene-text,
.source-text {
  font-size: 12.5px;
  color: var(--text-2);
}

.problem-text {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-1);
  line-height: 1.45;
}

.match-text {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.solution-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.5;
}

.score-text {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
}

.vector-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-2);
  white-space: nowrap;
}

.vector-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff9500;
}

.vector-cell.is-ready .vector-dot {
  background: #34c759;
}

.vector-cell.is-pending {
  color: var(--text-3);
}

.time-text {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.row-link {
  border: none;
  background: transparent;
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple);
}

.danger-text {
  color: #c9342b;
}

.danger-text:hover {
  background: #ffeceb;
  color: #c9342b;
}

/* ---------- 弹窗 ---------- */
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
  .exp-stat {
    min-width: 0;
    flex: 1;
    padding: 0 12px;
  }

  .exp-toolbar .el-select,
  .exp-toolbar .el-input {
    width: 100% !important;
  }
}
</style>
