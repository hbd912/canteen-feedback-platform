<template>
  <div class="page-container adaptive-page feedback-page">
    <div class="ap-head feedback-page-head">
      <div>
        <h1 class="ap-title">{{ isAdminView ? '全部留言反馈' : '我的留言反馈' }}</h1>
        <div class="ap-sub">
          共 {{ total }} 条<span v-if="records.length"> · 本页 {{ records.filter(i => i.status === 'PENDING').length }} 条待处理</span>
        </div>
      </div>
      <div class="ap-actions">
        <button class="ap-btn ap-btn--brand" @click="openCreate">
          <el-icon><Plus /></el-icon>我要留言
        </button>
      </div>
    </div>

    <div class="page-card feedback-card">
      <div class="feedback-toolbar">
        <div class="ap-pill-group">
          <button class="ap-pill" :class="{ active: !query.status }" @click="query.status = undefined; loadData()">全部</button>
          <button class="ap-pill" :class="{ active: query.status === 'PENDING' }" @click="query.status = 'PENDING'; loadData()">待处理</button>
          <button class="ap-pill" :class="{ active: query.status === 'PROCESSING' }" @click="query.status = 'PROCESSING'; loadData()">处理中</button>
          <button class="ap-pill" :class="{ active: query.status === 'RESOLVED' }" @click="query.status = 'RESOLVED'; loadData()">已解决</button>
        </div>
        <span class="toolbar-spacer"></span>
        <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 128px" @change="loadData">
          <el-option label="建议" value="SUGGEST" />
          <el-option label="投诉" value="COMPLAINT" />
          <el-option label="表扬" value="PRAISE" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索标题 / 内容"
          clearable
          style="width: 220px"
          @keyup.enter="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="loadData">查询</el-button>
        <button class="ap-btn ap-btn--ghost ap-btn--sm" @click="toggleEditing">{{ editing ? '完成' : '管理' }}</button>
      </div>

      <div v-if="editing" class="batch-bar">
        <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选</el-checkbox>
        <span class="selected-hint">已选 {{ selectedIds.length }} 项</span>
        <span class="batch-spacer"></span>
        <el-button
          class="batch-cancel"
          :disabled="!selectedIds.length"
          :loading="batchDeleting"
          @click="handleBatchDelete"
        >
          删除所选
        </el-button>
      </div>

      <div v-loading="loading" class="adaptive-table feedback-scroll">
        <div v-if="!records.length && !loading" class="ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><ChatLineSquare /></el-icon></div>
          <div class="ap-empty-title">暂无留言</div>
          <div>有建议或想表扬的档口，点右上角「我要留言」</div>
        </div>

        <div v-for="item in records" :key="item.id" class="feedback-item" :data-recid="item.id">
          <el-checkbox
            v-if="editing"
            v-model="selectedIds"
            :value="item.id"
            class="item-check"
            @click.stop
          />
          <div class="feedback-main">
            <div class="feedback-head">
              <div class="feedback-title-line">
                <span class="feedback-status">
                  <i class="status-dot" :class="'status-' + item.status"></i>{{ item.statusLabel }}
                </span>
                <span class="feedback-title">{{ item.title }}</span>
                <span class="ap-tag feedback-type">{{ item.typeLabel }}</span>
              </div>
              <div class="feedback-meta">
                <span v-if="isAdminView">{{ item.userName }}</span>
                <span>{{ item.stallName || '未指定档口' }}</span>
                <span>{{ item.createTime }}</span>
              </div>
            </div>

            <div class="feedback-content" :title="item.content">{{ item.content }}</div>

            <div v-if="item.replyContent" class="reply-box">
              <div class="reply-text">{{ item.replyContent }}</div>
              <div class="reply-meta">管理员回复 · {{ item.replyUserName }} · {{ item.replyTime }}</div>
            </div>
            <div v-else class="reply-pending">等待管理员回复</div>

            <div v-if="!editing" class="feedback-foot">
              <el-button
                v-if="isAdminView && item.status !== 'RESOLVED'"
                link
                class="reply-action"
                @click="openReply(item)"
              >
                回复并受理
              </el-button>
              <el-button link class="delete-action" @click="handleDelete(item)">删除</el-button>
            </div>
          </div>
        </div>
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

    <!-- 新增留言 -->
    <el-dialog v-model="createVisible" title="我要留言" width="560px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="关联档口（选填）">
          <el-select v-model="createForm.stallId" placeholder="不指定则发给食堂管理处" clearable style="width: 100%">
            <el-option v-for="s in stalls" :key="s.id" :label="`${s.windowNo}号 ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="createForm.type">
            <el-radio value="SUGGEST">建议</el-radio>
            <el-radio value="COMPLAINT">投诉</el-radio>
            <el-radio value="PRAISE">表扬</el-radio>
            <el-radio value="OTHER">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" maxlength="100" show-word-limit placeholder="一句话概括你的反馈" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="createForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 管理员回复 -->
    <el-dialog v-model="replyVisible" title="回复留言" width="560px">
      <div class="reply-dialog-quote">
        <div class="reply-dialog-title">{{ current.title }}</div>
        <div class="reply-dialog-content">{{ current.content }}</div>
      </div>
      <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-position="top">
        <el-form-item label="回复内容" prop="replyContent">
          <el-input v-model="replyForm.replyContent" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="更新状态">
          <el-radio-group v-model="replyForm.status">
            <el-radio value="PROCESSING">处理中</el-radio>
            <el-radio value="RESOLVED">已解决</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageFeedbacks, createFeedback, replyFeedback, deleteFeedback } from '@/api/feedback'
import { listStalls } from '@/api/stall'
import { useUserStore } from '@/store/user'
import { useFlashLocate } from '@/composables/useFlashLocate'

const userStore = useUserStore()
const route = useRoute()

// 后台管理视角（/admin/feedbacks）显示全部留言+回复；日常使用视角（/feedbacks）与学生端一致，仅看自己的留言
const isAdminView = computed(() => route.path.startsWith('/admin'))
const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const total = ref(0)
const stalls = ref([])
const selectedIds = ref([])
const batchDeleting = ref(false)
// 仅本页使用的编辑态，批量操作默认隐藏
const editing = ref(false)

const allSelected = computed({
  get: () => records.value.length > 0 && selectedIds.value.length === records.value.length,
  set: (val) => {
    selectedIds.value = val ? records.value.map(item => item.id) : []
  }
})
const isIndeterminate = computed(() => selectedIds.value.length > 0 && selectedIds.value.length < records.value.length)

const query = reactive({ current: 1, size: 10, status: undefined, type: undefined, keyword: '' })

const createVisible = ref(false)
const replyVisible = ref(false)
const current = ref({})

const createForm = reactive({ stallId: undefined, type: 'SUGGEST', title: '', content: '' })
const replyForm = reactive({ replyContent: '', status: 'PROCESSING' })

const createFormRef = ref()
const createRules = {
  type: [{ required: true, message: '请选择留言类型', trigger: 'change' }],
  title: [
    { required: true, message: '请填写标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写内容', trigger: 'blur' },
    { max: 1000, message: '内容不能超过1000字', trigger: 'blur' }
  ]
}

const replyFormRef = ref()
const replyRules = {
  replyContent: [
    { required: true, message: '请填写回复内容', trigger: 'blur' },
    { max: 1000, message: '回复内容不能超过1000字', trigger: 'blur' }
  ]
}

function typeTag(type) {
  return { SUGGEST: 'primary', COMPLAINT: 'danger', PRAISE: 'success', OTHER: 'info' }[type] || 'info'
}

function statusTag(status) {
  return { PENDING: 'warning', PROCESSING: 'primary', RESOLVED: 'success' }[status] || 'info'
}

function onAvatarError(e) {
  e.target.style.display = 'none'
  const parent = e.target.parentElement
  if (!parent.querySelector('.avatar-fallback')) {
    const span = document.createElement('span')
    span.className = 'avatar-fallback'
    span.textContent = '客'
    parent.appendChild(span)
  }
}

function toggleEditing() {
  editing.value = !editing.value
  if (!editing.value) selectedIds.value = []
}

async function loadData() {
  loading.value = true
  try {
    // 日常使用视角强制只看自己的留言（管理员身份也按 MINE 返回）
    const params = isAdminView.value ? { ...query } : { ...query, scope: 'MINE' }
    const res = await pageFeedbacks(params)
    records.value = res.data.records
    total.value = res.data.total
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.stallId = undefined
  createForm.type = 'SUGGEST'
  createForm.title = ''
  createForm.content = ''
  createVisible.value = true
  createFormRef.value?.clearValidate()
}

function openReply(item) {
  current.value = item
  replyForm.replyContent = ''
  replyForm.status = 'PROCESSING'
  replyVisible.value = true
  replyFormRef.value?.clearValidate()
}

async function submitCreate() {
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createFeedback(createForm)
    ElMessage.success('留言提交成功，感谢你的反馈！')
    createVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function submitReply() {
  try {
    await replyFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await replyFeedback(current.value.id, replyForm)
    ElMessage.success('回复成功')
    replyVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(item) {
  ElMessageBox.confirm('确定删除这条留言吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteFeedback(item.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

async function handleBatchDelete() {
  const ids = selectedIds.value
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 项吗？删除后不可恢复`, '提示', { type: 'warning' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map(id => deleteFeedback(id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

// 工作台敏感项跳转定位：/admin/feedback-center?tab=student&refer=FEEDBACK&id=xx
const { locate } = useFlashLocate(route, (rid) => document.querySelector(`.feedback-item[data-recid="${rid}"]`), {
  refer: route.query.refer,
  id: route.query.id,
  getRecId: (item) => item.id,
  onFound: async (rid, found) => {
    if (found || route.query.refer !== 'FEEDBACK') {
      // 找到即高亮；或非本类来源则交给各自页面处理
      if (found) document.querySelector(`.feedback-item[data-recid="${rid}"]`)?.classList.add('flash-target')
      return
    }
    // 第 1 页未见：逐页翻查直到找到该记录（按当前排序）
    const totalPages = Math.max(1, Math.ceil(total.value / query.size))
    for (let page = 1; page <= totalPages; page++) {
      query.current = page
      await loadData()
      await nextTick()
      const el = document.querySelector(`.feedback-item[data-recid="${rid}"]`)
      if (el) {
        nextTick(() => el.classList.add('flash-target'))
        return
      }
    }
    // 全部分页都没找到，回到第 1 页
    query.current = 1
    loadData()
  }
})

onMounted(async () => {
  const res = await listStalls()
  stalls.value = res.data
  await loadData()
  // 列表异步加载完成后，再尝试高亮定位（onMounted 时列表可能还没渲染出来）
  nextTick(locate)
})

// 同一组件复用于 /feedbacks（我的留言）与 /admin/feedbacks（全部留言），
// keep-alive 缓存下切换路由不会重新 onMounted，必须 watch 路径重新加载，
// 否则会显示上一个视角的旧数据
watch(
  () => route.path,
  () => {
    query.current = 1
    loadData()
  }
)
</script>

<style scoped>
.feedback-page-head {
  margin-bottom: 16px;
}

.feedback-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.feedback-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.toolbar-spacer {
  flex: 1;
  min-width: 8px;
}

.batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 20px;
  background: #fafafa;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.batch-spacer {
  flex: 1;
  min-width: 8px;
}

.selected-hint {
  font-size: 12px;
  color: var(--text-3);
}

.batch-bar .batch-cancel {
  color: #d92d20;
  background: #fff;
  border-color: var(--border-strong);
}

.batch-bar .batch-cancel:hover:not(.is-disabled) {
  color: #b42318;
  border-color: rgba(217, 45, 32, 0.32);
  background: #fff7f6;
}

.feedback-scroll {
  padding: 0 20px;
}

.feedback-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f2;
  transition: background 0.16s var(--ease-apple);
}

.feedback-item:last-of-type {
  border-bottom: none;
}

.item-check {
  flex-shrink: 0;
  margin-top: 2px;
}

.item-check :deep(.el-checkbox__inner) {
  width: 18px;
  height: 18px;
  border-radius: 50%;
}

.item-check :deep(.el-checkbox__inner::after) {
  left: 5px;
  top: 2px;
}

.feedback-main {
  flex: 1;
  min-width: 0;
}

.feedback-head {
  display: block;
}

.feedback-title-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.feedback-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-3);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-3);
  flex-shrink: 0;
}

.status-dot.status-PENDING {
  background: #ff9500;
}

.status-dot.status-PROCESSING {
  background: #1f6fd1;
}

.status-dot.status-RESOLVED {
  background: #34c759;
}

.feedback-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
  min-width: 0;
}

.feedback-type {
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
}

.feedback-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.feedback-meta span + span::before {
  content: '·';
  margin-right: 6px;
  color: var(--text-3);
}

.feedback-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--text-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.reply-box {
  margin-top: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f5f5f7;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.65;
}

.reply-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.reply-meta {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--text-3);
}

.reply-pending {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
}

.feedback-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-height: 24px;
  margin-top: 8px;
}

.feedback-foot .el-button + .el-button {
  margin-left: 0;
}

.feedback-foot .reply-action {
  color: var(--brand-dark);
  font-size: 12.5px;
  padding: 2px 4px;
}

.feedback-foot .reply-action:hover {
  color: var(--brand);
}

.feedback-foot .delete-action {
  color: var(--text-3);
  font-size: 12.5px;
  padding: 2px 4px;
}

.feedback-foot .delete-action:hover {
  color: #d92d20;
}

@media (hover: hover) {
  .feedback-item .delete-action {
    opacity: 0;
    transition: opacity 0.16s var(--ease-apple), color 0.16s;
  }

  .feedback-item:hover .delete-action,
  .feedback-item:focus-within .delete-action {
    opacity: 1;
  }
}

.adaptive-pager {
  margin-top: 0;
  padding: 14px 20px 16px;
  border-top: 1px solid var(--border);
}

.reply-dialog-quote {
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f5f5f7;
}

.reply-dialog-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
}

.reply-dialog-content {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 760px) {
  .feedback-toolbar {
    padding: 12px 14px;
  }

  .feedback-scroll {
    padding: 0 14px;
  }

  .feedback-toolbar :deep(.el-input),
  .feedback-toolbar :deep(.el-select) {
    width: 100% !important;
  }
}
</style>
