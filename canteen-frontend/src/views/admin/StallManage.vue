<template>
  <div class="page-container adaptive-page stall-manage" :class="{ 'is-embedded': embedded }">
    <div v-if="!embedded" class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">档口管理</h1>
        <div class="ap-sub">共 {{ list.length }} 个档口 · 按区域查看经营概况</div>
      </div>
      <div class="ap-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增档口
        </el-button>
      </div>
    </div>

    <div class="page-card stall-card">
      <div class="ap-toolbar stall-toolbar">
        <div class="ap-pill-group">
          <button
            v-for="opt in zoneOptions"
            :key="opt.value"
            type="button"
            class="ap-pill"
            :class="{ active: zone === opt.value }"
            @click="setZone(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>
        <span class="ap-toolbar-spacer"></span>
        <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选</el-checkbox>
        <span v-if="embedded" class="toolbar-count">共 {{ list.length }} 个档口</span>
        <span v-if="selectedRows.length" class="selected-hint">已选 {{ selectedRows.length }} 项</span>
        <el-button
          v-if="selectedRows.length"
          type="danger"
          plain
          size="small"
          :loading="batchDeleting"
          @click="handleBatchDelete"
        >
          批量删除
        </el-button>
      </div>

      <div class="adaptive-table">
        <el-table
          ref="tableRef"
          :data="list"
          row-key="id"
          v-loading="loading"
          row-class-name="clickable-row"
          @selection-change="onSelectionChange"
          @row-click="handleRowClick"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column label="档口" min-width="220">
            <template #default="{ row }">
              <div class="stall-cell">
                <span class="stall-name" :title="row.description || row.name">{{ row.name }}</span>
                <span class="stall-meta">
                  {{ row.windowNo }} 窗口 · {{ row.area === 'north' ? '北区' : '南区' }} {{ row.floor }} 楼 · {{ row.category || '未分类' }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="负责人 / 电话" min-width="140">
            <template #default="{ row }">
              <div class="manager-name">{{ row.managerName || '—' }}</div>
              <div class="manager-phone">{{ row.managerPhone || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="商家账号" width="130">
            <template #default="{ row }">
              <span v-if="row.merchantUsername" class="merchant-account">{{ row.merchantUsername }}</span>
              <span v-else class="ap-tag">未建</span>
            </template>
          </el-table-column>
          <el-table-column label="菜品 / 销量" width="120" align="center">
            <template #default="{ row }">
              <div class="metric-main ap-num">{{ row.dishCount }}<span class="metric-unit"> 道</span></div>
              <div class="metric-sub ap-num">{{ row.salesCount }} 份已售</div>
            </template>
          </el-table-column>
          <el-table-column label="评分 / 好评" width="120" align="center">
            <template #default="{ row }">
              <div class="rating ap-num">{{ row.avgRating ?? '—' }}</div>
              <div class="metric-sub ap-num">{{ row.goodRate }}% 好评</div>
            </template>
          </el-table-column>
          <el-table-column label="卫生分" width="100" align="center">
            <template #default="{ row }">
              <span
                v-if="row.hygieneScore != null"
                class="ap-tag"
                :class="Number(row.hygieneScore) < 80 ? 'ap-tag--red' : Number(row.hygieneScore) >= 90 ? 'ap-tag--green' : 'ap-tag--amber'"
              >
                {{ Number(row.hygieneScore).toFixed(1) }}
              </span>
              <span v-else class="ap-tag">未评定</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right">
            <template #default="{ row }">
              <div class="row-actions" @click.stop>
                <el-button link class="row-action" @click="openEdit(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="handleRowCommand($event, row)">
                  <el-button link class="row-action row-action--icon" aria-label="更多操作">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="row.merchantUsername" command="pwd">修改密码</el-dropdown-item>
                      <el-dropdown-item command="notice">发敦促整改</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除档口</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark"><el-icon :size="20"><Shop /></el-icon></div>
              <div class="ap-empty-title">暂无档口</div>
              <div>换个区域筛选，或新增一个档口</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑档口' : '新增档口'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="档口名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="如：川味小炒" />
        </el-form-item>
        <el-form-item label="所属区域 / 楼层" prop="area">
          <el-select v-model="areaFloor" style="width: 100%" @change="onAreaFloorChange">
            <el-option v-for="opt in areaFloorOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="窗口编号" prop="windowNo">
          <el-select v-model="form.windowNo" style="width: 100%">
            <el-option
              v-for="opt in windowNoOptions"
              :key="opt.value"
              :value="opt.value"
              :label="opt.label"
              :disabled="opt.disabled"
            />
          </el-select>
          <span class="muted" style="font-size: 12px">
            共 20 个档位，灰色为已被占用
          </span>
        </el-form-item>
        <el-form-item label="主营品类" prop="category">
          <el-input v-model="form.category" maxlength="30" show-word-limit placeholder="如：川菜" />
        </el-form-item>
        <el-form-item label="档口负责人" prop="managerName">
          <el-input v-model="form.managerName" maxlength="50" show-word-limit placeholder="如：张建国" />
        </el-form-item>
        <el-form-item label="负责人电话" prop="managerPhone">
          <el-input v-model="form.managerPhone" maxlength="20" placeholder="如：13800010001" />
        </el-form-item>
        <el-form-item label="档口简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="卫生分（学校评定）" prop="hygieneScore">
          <el-input-number
            v-model="form.hygieneScore"
            :min="0"
            :max="100"
            :precision="1"
            :step="0.5"
            style="width: 160px"
          />
          <span class="muted" style="margin-left: 10px; font-size: 12px">
            低于 80 分将在档口卡片公示「卫生待整改」
          </span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">营业中</el-radio>
            <el-radio :value="0">歇业</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="!form.id">
          <el-divider content-position="left">商家账号（随档口一起创建）</el-divider>
          <el-form-item label="商家账号" prop="merchantUsername">
            <el-input v-model="form.merchantUsername" maxlength="50" show-word-limit placeholder="商家登录账号" />
          </el-form-item>
          <el-form-item label="商家初始密码" prop="merchantPassword">
            <el-input v-model="form.merchantPassword" maxlength="100" placeholder="商家登录密码" show-password />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="noticeVisible" title="发敦促整改" width="480px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="noticeForm.title" maxlength="100" show-word-limit placeholder="如：卫生限期整改" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="noticeForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitNotice">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="修改商家密码" width="420px">
      <el-form label-position="top">
        <el-form-item :label="`商家账号：${pwdForm.username}`">
          <el-input v-model="pwdForm.password" placeholder="新密码（6-20位）" maxlength="20" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPwd">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listStalls, createStall, updateStall, deleteStall, resetMerchantPassword, listWindowNos } from '@/api/stall'
import { createInspectTodo } from '@/api/merchant'

defineProps({ embedded: { type: Boolean, default: false } })

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const list = ref([])
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
const dialogVisible = ref(false)
const noticeVisible = ref(false)
const pwdVisible = ref(false)
const noticeForm = reactive({ stallId: null, title: '', content: '' })
const pwdForm = reactive({ stallId: null, username: '', password: '' })

const form = reactive({
  id: null,
  name: '',
  windowNo: '',
  area: 'south',
  floor: 1,
  category: '',
  managerName: '',
  managerPhone: '',
  coverImage: '',
  description: '',
  hygieneScore: 85.0,
  status: 1,
  merchantUsername: '',
  merchantPassword: ''
})

// 区域+楼层合成一个下拉：南区两层、北区只有一层
const areaFloor = ref('south-1')
const areaFloorOptions = [
  { value: 'south-1', label: '南区食堂 1 楼' },
  { value: 'south-2', label: '南区食堂 2 楼' },
  { value: 'north-1', label: '北区食堂' }
]

// 列表左上角筛选：全部 / 南区1楼 / 南区2楼 / 北区
const zone = ref('all')
const zoneOptions = [
  { value: 'all', label: '全部' },
  { value: 'south-1', label: '南区 1 楼' },
  { value: 'south-2', label: '南区 2 楼' },
  { value: 'north-1', label: '北区' }
]
const zoneParams = computed(() => {
  if (zone.value === 'all') return {}
  const [area, floor] = zone.value.split('-')
  return { area, floor: Number(floor) }
})

function setZone(value) {
  if (zone.value === value) return
  zone.value = value
  loadData()
}

// 当前区域楼层下 1-20 档位的占用情况
const occupied = ref([])
const windowNoOptions = computed(() => {
  const prefix = areaFloorPrefix.value
  const mine = form.windowNo
  return Array.from({ length: 20 }, (_, i) => {
    const no = `${prefix}${String(i + 1).padStart(2, '0')}`
    const hit = occupied.value.find(o => o.windowNo === no)
    if (hit && hit.windowNo !== mine) {
      return { value: no, label: `${no}（已占用：${hit.stallName}）`, disabled: true }
    }
    if (hit && hit.windowNo === mine) {
      return { value: no, label: `${no}（当前档口）` }
    }
    return { value: no, label: no }
  })
})
const areaFloorPrefix = computed(() => {
  const [area, floor] = areaFloor.value.split('-')
  if (area === 'north') return 'N1'
  return `S${floor}`
})

async function fetchWindowNos() {
  const [area, floor] = areaFloor.value.split('-')
  const res = await listWindowNos({ area, floor: Number(floor) })
  occupied.value = res.data || []
}

function onAreaFloorChange(v) {
  const [area, floor] = String(v).split('-')
  form.area = area
  form.floor = Number(floor) || 1
  form.windowNo = ''
  fetchWindowNos()
}

const formRef = ref()
const rules = {
  name: [
    { required: true, message: '请填写档口名称', trigger: 'blur' },
    { max: 50, message: '档口名称不能超过50字', trigger: 'blur' }
  ],
  windowNo: [
    { required: true, message: '请选择窗口编号', trigger: 'change' }
  ],
  area: [{ required: true, message: '请选择所属区域', trigger: 'change' }],
  merchantUsername: [{ required: true, message: '请填写商家账号', trigger: 'blur' }],
  merchantPassword: [{ required: true, message: '请填写商家初始密码', trigger: 'blur' }],
  category: [{ max: 30, message: '主营品类不能超过30字', trigger: 'blur' }],
  managerName: [{ max: 50, message: '负责人姓名不能超过50字', trigger: 'blur' }],
  managerPhone: [{ pattern: /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})?$/, message: '联系电话格式不正确', trigger: 'blur' }],
  description: [{ max: 500, message: '档口简介不能超过500字', trigger: 'blur' }],
  hygieneScore: [{
    validator: (rule, value, callback) => {
      if (value == null) return callback()
      if (value < 0 || value > 100) return callback(new Error('卫生分需在 0-100 之间'))
      callback()
    },
    trigger: 'change'
  }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listStalls(zoneParams.value)
    list.value = res.data
    selectedRows.value = []
    nextTick(() => tableRef.value?.clearSelection())
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  Object.assign(form, { id: null, name: '', windowNo: '', area: 'south', floor: 1, category: '', managerName: '', managerPhone: '', coverImage: '', description: '', hygieneScore: 85.0, status: 1, merchantUsername: '', merchantPassword: '' })
  areaFloor.value = 'south-1'
  await fetchWindowNos()
  // 自动选中第一个空闲档位
  form.windowNo = (windowNoOptions.value.find(o => !o.disabled) || {}).value || ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openNotice(row) {
  noticeForm.stallId = row.id
  noticeForm.title = ''
  noticeForm.content = ''
  noticeVisible.value = true
}

async function submitNotice() {
  if (!noticeForm.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  submitting.value = true
  try {
    await createInspectTodo({
      stallId: noticeForm.stallId,
      title: noticeForm.title.trim(),
      content: noticeForm.content.trim()
    })
    ElMessage.success('敦促已发送到该档口商家的待办')
    noticeVisible.value = false
  } finally {
    submitting.value = false
  }
}

function openPwd(row) {
  pwdForm.stallId = row.id
  pwdForm.username = row.merchantUsername
  pwdForm.password = ''
  pwdVisible.value = true
}

async function submitPwd() {
  if (!pwdForm.password || pwdForm.password.length < 6 || pwdForm.password.length > 20) {
    ElMessage.warning('密码长度需为 6-20 位')
    return
  }
  submitting.value = true
  try {
    await resetMerchantPassword(pwdForm.stallId, { password: pwdForm.password })
    ElMessage.success('密码已更新')
    pwdVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function openEdit(row) {
  const area = row.area || 'south'
  // 北区归一到 1 楼，防止历史数据出现 north-2 这种组合
  const floor = area === 'north' ? 1 : (row.floor || 1)
  Object.assign(form, { ...row, area, floor })
  areaFloor.value = `${area}-${floor}`
  await fetchWindowNos()
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
      await updateStall(form.id, form)
    } else {
      await createStall(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(
    `确定删除档口「${row.name}」吗？删除后将同时清除该档口下的菜品、评价、订单等全部关联数据，且不可恢复。`,
    '提示',
    { type: 'warning', confirmButtonText: '确定删除', confirmButtonClass: 'el-button--danger' }
  )
    .then(async () => {
      await deleteStall(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

function handleRowCommand(command, row) {
  if (command === 'pwd') openPwd(row)
  else if (command === 'notice') openNotice(row)
  else if (command === 'delete') handleDelete(row)
}

async function handleBatchDelete() {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 项吗？删除后将同时清除这些档口下的菜品、评价、订单等全部关联数据，且不可恢复`, '提示', { type: 'warning', confirmButtonText: '确定删除', confirmButtonClass: 'el-button--danger' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(rows.map(row => deleteStall(row.id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

function handleRowClick(row, column) {
  // 勾选框和操作列不触发跳转
  if (column?.type === 'selection' || column?.label === '操作') return
  router.push(`/admin/stalls/${row.id}`)
}

onMounted(loadData)
// keep-alive 缓存后回访重新拉取，避免列表陈旧（陈旧 id 会导致点进详情报「档口不存在」）
onActivated(loadData)

defineExpose({ openCreate })
</script>

<style scoped>
.stall-manage {
  width: 100%;
}

/* 嵌入「用户与商家」壳层：由壳层负责白卡与留白 */
.stall-manage.is-embedded {
  padding: 16px 18px 12px;
}

.stall-manage.is-embedded .stall-card {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.stall-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.stall-toolbar {
  flex-shrink: 0;
}

.toolbar-count {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.selected-hint {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.stall-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.stall-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stall-meta {
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.manager-name {
  font-size: 13.5px;
  color: var(--text-1);
}

.manager-phone {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 1px;
}

.merchant-account {
  font-size: 13px;
  color: var(--text-2);
}

.metric-main {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
}

.metric-unit {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--text-3);
}

.metric-sub {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 1px;
}

.rating {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
}

.row-action {
  color: var(--text-2);
  font-size: 13px;
}

.row-action:hover {
  color: var(--text-1) !important;
  background: rgba(0, 0, 0, 0.04);
}

.row-action--icon {
  padding: 4px 6px;
}

:deep(.clickable-row) {
  cursor: pointer;
}

:deep(.clickable-row:hover > td) {
  background: #f5f5f7 !important;
}
</style>
