<template>
  <div class="page-container adaptive-page customer-service-page">
    <div class="ap-head manage-head">
      <div>
        <h1 class="ap-title">客服会话</h1>
        <p class="ap-sub">查看学生与商家的客服会话，抽查待处理问题。</p>
      </div>
      <div class="ap-actions">
        <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="loadData">
          <el-icon :size="14"><Refresh /></el-icon>
          刷新
        </button>
      </div>
    </div>

    <div class="ap-card stats-band">
      <div class="stat-item">
        <span class="stat-label">会话总数</span>
        <span class="stat-value ap-num">{{ stats.sessionTotal ?? 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">进行中会话</span>
        <span class="stat-value ap-num">{{ stats.openTotal ?? 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">消息总数</span>
        <span class="stat-value ap-num">{{ stats.messageTotal ?? 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">AI 回复数</span>
        <span class="stat-value ap-num">{{ stats.aiMessageTotal ?? 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">
          <i class="stat-dot"></i>
          待商家处理
        </span>
        <span class="stat-value ap-num">{{ stats.unhandledTotal ?? 0 }}</span>
      </div>
    </div>

    <div class="page-card table-card">
      <div class="table-top">
        <div class="table-title-wrap">
          <span class="table-title">全部会话</span>
          <span class="table-count">共 {{ total }} 段</span>
        </div>
      </div>

      <div class="adaptive-table">
        <el-table :data="records" v-loading="loading" class="session-table">
          <el-table-column label="会话" width="86">
            <template #default="{ row }">
              <span class="session-id ap-num">#{{ row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="档口" min-width="150">
            <template #default="{ row }">
              <span class="stall-name">{{ row.stallName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="学生" width="120">
            <template #default="{ row }">
              <span class="student-name">{{ row.studentName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最后消息" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="last-message">{{ row.lastMessage || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="商家未读" width="96">
            <template #default="{ row }">
              <span v-if="row.merchantUnread" class="unread-pill ap-num">{{ row.merchantUnread }}</span>
              <span v-else class="unread-zero">0</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <span class="status-cell" :class="row.status === 'OPEN' ? 'is-open' : 'is-closed'">
                <i class="status-dot"></i>
                {{ row.status === 'OPEN' ? '进行中' : '已结束' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="最后消息时间" width="160">
            <template #default="{ row }">
              <span class="time-text ap-num">{{ row.lastMessageTime || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="76" fixed="right">
            <template #default>
              <button type="button" class="row-link" @click="goRecords">查看</button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="ap-empty">
              <div class="ap-empty-mark">
                <el-icon :size="20"><ChatLineSquare /></el-icon>
              </div>
              <div class="ap-empty-title">暂无客服会话</div>
              <div>学生发起客服会话后会显示在这里</div>
            </div>
          </template>
        </el-table>
      </div>

      <div v-if="total > 0" class="adaptive-pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { adminListSessions, adminCsStats } from '@/api/customerService'

const router = useRouter()

const records = ref([])
const total = ref(0)
const loading = ref(false)
const stats = ref({})
const query = reactive({ current: 1, size: 10 })

async function loadData() {
  loading.value = true
  try {
    const [sessionsRes, statsRes] = await Promise.all([
      adminListSessions(query),
      adminCsStats()
    ])
    records.value = sessionsRes.data?.records || []
    total.value = sessionsRes.data?.total || 0
    stats.value = statsRes.data || {}
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 质检入口：跳到客服中心「会话与记录」，不改变原有列表加载逻辑
function goRecords() {
  router.push({ path: '/admin/cs-center', query: { tab: 'records', kind: 'SESSION' } })
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.customer-service-page {
  width: 100%;
}

.manage-head {
  flex-shrink: 0;
  margin-bottom: 16px;
}

/* ---------- 统计带 ---------- */
.stats-band {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 18px 8px;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.stat-item {
  min-width: 0;
  padding: 2px 20px;
  border-left: 1px solid rgba(0, 0, 0, 0.05);
}

.stat-item:first-child {
  border-left: none;
}

.stat-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
}

.stat-value {
  display: block;
  margin-top: 6px;
  font-size: 28px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  flex-shrink: 0;
}

/* ---------- 表格卡片 ---------- */
.table-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px 18px 10px;
  border-radius: 22px;
}

.table-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-shrink: 0;
  padding: 2px 2px 12px;
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

.session-table {
  width: 100%;
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #fafafa;
}

.session-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.session-id {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.stall-name {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-1);
}

.student-name {
  font-size: 13px;
  color: var(--text-2);
}

.last-message {
  display: block;
  font-size: 13px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-pill {
  display: inline-block;
  min-width: 20px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-dark);
  font-size: 11.5px;
  font-weight: 600;
  text-align: center;
  line-height: 1.6;
}

.unread-zero {
  font-size: 12px;
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

.status-cell.is-open .status-dot {
  background: #34c759;
}

.status-cell.is-closed {
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
  color: var(--brand-dark);
  font-family: inherit;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple);
}

.row-link:hover {
  background: rgba(0, 0, 0, 0.04);
}

.adaptive-pager {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

@media (max-width: 900px) {
  .stats-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 18px;
  }

  .stat-item:nth-child(3),
  .stat-item:nth-child(5) {
    border-left: none;
  }
}
</style>
