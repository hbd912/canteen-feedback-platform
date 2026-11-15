<template>
  <div class="page-container page-fill user-manage" :class="{ 'is-embedded': embedded }">
    <div v-if="!embedded" class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">用户管理</h1>
        <div class="ap-sub">共 {{ total }} 个账号 · 学生与管理员账号统一管理</div>
      </div>
    </div>

    <div class="ap-toolbar user-toolbar">
      <el-input v-model="query.keyword" placeholder="搜索账号/姓名/学号" clearable style="width: 220px" @keyup.enter="loadData" />
      <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 140px" @change="loadData">
        <el-option label="学生" value="STUDENT" />
        <el-option label="管理员" value="ADMIN" />
      </el-select>
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="loadData">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <span class="ap-toolbar-spacer"></span>
    </div>

    <div ref="wrapRef" class="table-wrap adaptive-table">
      <el-table :data="records" v-loading="loading">
        <el-table-column label="用户" min-width="170">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">
                <span class="avatar-fallback">{{ avatarText(row) }}</span>
                <img v-if="row.avatar" :src="row.avatar" alt="" @error="$event.target.style.display='none'" />
              </div>
              <div class="user-info">
                <div class="user-line">
                  <span class="user-name">{{ row.realName || row.username }}</span>
                  <span v-if="row.status !== 1" class="ap-tag ap-tag--red">已禁用</span>
                </div>
                <span class="user-account">{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="90" align="center">
          <template #default="{ row }">
            <span class="ap-tag" :class="{ 'ap-tag--blue': row.role === 'ADMIN' }">
              {{ row.roleLabel }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="学号" width="120">
          <template #default="{ row }"><span class="cell-sub">{{ row.studentNo || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="手机号" width="120">
          <template #default="{ row }"><span class="cell-sub">{{ row.phone || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="绑定邮箱" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="cell-sub" :class="{ 'email-empty': !row.email }">{{ row.email || '未绑定' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评价数" width="80" align="center">
          <template #default="{ row }"><span class="ap-num">{{ row.reviewCount ?? '-' }}</span></template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }"><span class="cell-sub">{{ row.createTime }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button
              v-if="row.role === 'STUDENT'"
              link
              class="row-action"
              @click="openProfile(row)"
            >AI 画像</el-button>
            <el-button
              link
              class="row-action"
              :class="row.status === 1 ? 'row-action--danger' : 'row-action--positive'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="ap-empty">
            <div class="ap-empty-mark"><el-icon :size="20"><Box /></el-icon></div>
            <div class="ap-empty-title">暂无用户</div>
            <div>换个关键词或筛选条件试试</div>
          </div>
        </template>
      </el-table>
    </div>

    <div class="adaptive-pager user-pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <!-- 学生 AI 画像抽屉 -->
    <el-drawer
      v-model="profileOpen"
      :size="460"
      :destroy-on-close="true"
      class="profile-drawer"
    >
      <template #header>
        <div class="profile-head">
          <div class="profile-title">AI 学生画像</div>
          <div class="profile-sub">{{ profileUser?.realName || profileUser?.username }} · {{ profileUser?.studentNo || profileUser?.username }}</div>
        </div>
      </template>
      <div v-loading="profileLoading" class="profile-body">
        <template v-if="profile">
          <div class="profile-ai">
            <div class="profile-ai-label">
              <span>画像总结</span>
              <span v-if="refreshing" class="profile-ai-refreshing"><span class="refreshing-dot"></span>画像更新中…</span>
              <span v-else-if="profile.aiSummaryAt" class="profile-ai-time">{{ profile.aiSummaryAt }}</span>
            </div>
            <p class="profile-ai-text">{{ profile.aiSummary || '画像还在积累中——该学生与小助手对话越多，画像会越准确。' }}</p>
          </div>

          <div class="profile-stats">
            <div class="profile-stat">
              <div class="profile-stat-num">¥{{ money(profile.expenseWeek) }}</div>
              <div class="profile-stat-label">最近7天</div>
            </div>
            <div class="profile-stat">
              <div class="profile-stat-num">¥{{ money(profile.expenseMonth) }}</div>
              <div class="profile-stat-label">最近30天</div>
            </div>
            <div class="profile-stat">
              <div class="profile-stat-num">¥{{ money(profile.expenseTotal) }}</div>
              <div class="profile-stat-label">累计消费</div>
            </div>
            <div class="profile-stat">
              <div class="profile-stat-num">{{ profile.orderCount ?? 0 }}</div>
              <div class="profile-stat-label">订单数</div>
            </div>
          </div>

          <div class="profile-section">
            <div class="profile-section-title">口味偏好</div>
            <div class="profile-tags">
              <template v-if="splitTags(profile.tasteTags).length">
                <span v-for="t in splitTags(profile.tasteTags)" :key="t" class="profile-tag">{{ t }}</span>
              </template>
              <span v-else class="profile-none">暂无评价记录</span>
            </div>
          </div>

          <div class="profile-section">
            <div class="profile-section-title">常点档口</div>
            <div class="profile-tags">
              <template v-if="splitTags(profile.favoriteStalls).length">
                <span v-for="t in splitTags(profile.favoriteStalls)" :key="t" class="profile-tag profile-tag--stall">{{ t }}</span>
              </template>
              <span v-else class="profile-none">暂无订单记录</span>
            </div>
          </div>

          <div class="profile-section">
            <div class="profile-section-title">常点菜品</div>
            <div class="profile-tags">
              <template v-if="splitTags(profile.favoriteDishes).length">
                <span v-for="t in splitTags(profile.favoriteDishes)" :key="t" class="profile-tag profile-tag--dish">{{ t }}</span>
              </template>
              <span v-else class="profile-none">暂无订单记录</span>
            </div>
          </div>

          <div class="profile-section">
            <div class="profile-section-title">情绪记录 <span class="profile-section-sub">（来自与小助手的对话）</span></div>
            <div class="profile-mood-list">
              <template v-if="moodNotes.length">
                <div v-for="(m, i) in moodNotes" :key="i" class="profile-mood">{{ m }}</div>
              </template>
              <span v-else class="profile-none">没有情绪记录，对话中提到「心情不好」等会记在这里</span>
            </div>
          </div>

          <div class="profile-foot">累计对话 {{ profile.msgCount ?? 0 }} 轮 · 画像随对话自动更新</div>
        </template>
        <div v-else-if="!profileLoading" class="profile-none" style="padding: 40px 0; text-align: center">
          暂无画像数据
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick , onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, updateUserStatus, getUserAiProfile } from '@/api/user'

defineProps({ embedded: { type: Boolean, default: false } })

const loading = ref(false)
const records = ref([])
const total = ref(0)
const wrapRef = ref(null)

/* ---------------- AI 画像抽屉 ---------------- */
const profileOpen = ref(false)
const profileLoading = ref(false)
const profileUser = ref(null)
const profile = ref(null)
const refreshing = ref(false)

async function openProfile(row) {
  profileUser.value = row
  profile.value = null
  profileOpen.value = true
  profileLoading.value = true
  refreshing.value = false
  try {
    const res = await getUserAiProfile(row.id)
    profile.value = res.data
    // 后端把统计+AI总结的刷新放到了后台线程：立即返回的可能还是旧数据，
    // 延迟 1.5 秒再拉一次拿刷新后的最新画像（提示语只出现一次，不打扰）
    if (profileOpen.value) {
      refreshing.value = true
      setTimeout(async () => {
        try {
          const again = await getUserAiProfile(row.id)
          if (profileOpen.value && profileUser.value?.id === row.id && again.data) {
            profile.value = again.data
          }
        } catch {
          // 静默失败：保留第一次的数据
        } finally {
          refreshing.value = false
        }
      }, 1500)
    }
  } catch (e) {
    ElMessage.error(e?.message || '画像加载失败')
    profileOpen.value = false
  } finally {
    profileLoading.value = false
  }
}

function money(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}

function splitTags(s) {
  if (!s) return []
  return String(s).split(/[,，|]+/).map(t => t.trim()).filter(Boolean)
}

const moodNotes = computed(() => {
  const raw = profile.value?.moodNotes
  if (!raw) return []
  return String(raw).split('|').map(t => t.trim()).filter(Boolean)
})

// 行高 / 表头高与下方 CSS 保持一致，按可视高度自动铺满每页条数
const ROW_HEIGHT = 51
const HEADER_HEIGHT = 47
const MIN_SIZE = 10
const MAX_SIZE = 60

const query = reactive({ current: 1, size: MIN_SIZE, keyword: '', role: undefined, status: undefined })

let ro = null

function fitPageSize() {
  const el = wrapRef.value
  if (!el || el.clientHeight <= 0) return
  const rows = Math.max(MIN_SIZE, Math.floor((el.clientHeight - HEADER_HEIGHT) / ROW_HEIGHT))
  const next = Math.min(rows, MAX_SIZE)
  if (next !== query.size) {
    query.size = next
    query.current = 1
    loadData()
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUsers(query)
    records.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function avatarText(row) {
  const name = row.realName || row.username || '用'
  return String(name).trim().slice(0, 1)
}

function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  ElMessageBox.confirm(`确定${next === 0 ? '禁用' : '启用'}用户「${row.realName}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateUserStatus(row.id, next)
      ElMessage.success('操作成功')
      loadData()
    })
    .catch(() => {})
}

onMounted(async () => {
  await loadData()
  await nextTick()
  ro = new ResizeObserver(fitPageSize)
  if (wrapRef.value) ro.observe(wrapRef.value)
})

onBeforeUnmount(() => {
  if (ro) ro.disconnect()
})
onActivated(loadData)
</script>

<style scoped>
/* 撑满窗口高度，避免大屏下方留白 */
.user-manage {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 嵌入「用户与商家」壳层：由壳层负责白卡与留白 */
.user-manage.is-embedded {
  padding: 16px 18px 12px;
}

.user-toolbar {
  flex-shrink: 0;
}

.table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* 固定行高，配合 fitPageSize 让每页条数铺满可视高度 */
.table-wrap :deep(.el-table__header th.el-table__cell) {
  height: 46px;
}

.table-wrap :deep(.el-table__body td.el-table__cell) {
  height: 50px;
  padding-top: 0;
  padding-bottom: 0;
}

.table-wrap :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f5f5f7 !important;
}

.user-pager {
  justify-content: flex-end;
  flex-shrink: 0;
}

/* 头像 + 姓名 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.user-avatar {
  position: relative;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #f0f0f2;
  overflow: hidden;
  flex-shrink: 0;
}

.user-avatar .avatar-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
}

.user-avatar img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.user-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-account {
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-sub {
  font-size: 12.5px;
  color: var(--text-2);
}

.email-empty {
  color: var(--text-3);
}

/* 行内文字操作：低对比度，仅悬停时强调 */
.row-action {
  color: var(--text-2);
  font-size: 13px;
}

.row-action:hover {
  color: var(--text-1) !important;
  background: rgba(0, 0, 0, 0.04);
}

.row-action--danger:hover {
  color: #c9342b !important;
}

.row-action--positive:hover {
  color: #1d8a45 !important;
}

/* ---------------- AI 画像抽屉 ---------------- */
.profile-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.profile-sub {
  font-size: 12px;
  color: var(--text-3);
}

.profile-body {
  padding: 0 4px;
  min-height: 200px;
}

.profile-ai {
  background: linear-gradient(135deg, #fff4ec 0%, #ffe9d6 100%);
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.profile-ai-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #a35412;
}

.profile-ai-time {
  font-weight: 400;
  color: #b87a3d;
}

.profile-ai-refreshing {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 400;
  color: #b87a3d;
}

.refreshing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #e6822c;
  animation: refreshing-pulse 1s ease-in-out infinite;
}

@keyframes refreshing-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.1); }
}

@media (prefers-reduced-motion: reduce) {
  .refreshing-dot { animation: none; opacity: 0.8; }
}

.profile-ai-text {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #5c3a1e;
  white-space: pre-wrap;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 18px;
}

.profile-stat {
  background: var(--bg);
  border-radius: 12px;
  padding: 10px 6px;
  text-align: center;
}

.profile-stat-num {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.profile-stat-label {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-3);
}

.profile-section {
  margin-bottom: 16px;
}

.profile-section-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 8px;
}

.profile-section-sub {
  font-weight: 400;
  color: var(--text-3);
  font-size: 11px;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.profile-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--bg);
  color: var(--text-2);
  font-size: 12px;
}

.profile-tag--stall {
  background: #eef4ff;
  color: #2b5db8;
}

.profile-tag--dish {
  background: #f0faf2;
  color: #1d8a45;
}

.profile-none {
  font-size: 12px;
  color: var(--text-3);
}

.profile-mood-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.profile-mood {
  font-size: 12.5px;
  color: var(--text-2);
  background: var(--bg);
  border-radius: 10px;
  padding: 8px 12px;
  line-height: 1.5;
}

.profile-foot {
  margin-top: 20px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  font-size: 11.5px;
  color: var(--text-3);
  text-align: center;
}
</style>
