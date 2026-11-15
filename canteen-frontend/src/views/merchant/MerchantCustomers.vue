<template>
  <div class="ap-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">客户统计</h1>
        <div class="ap-sub">总览客群构成与熟客名单 · 共 {{ stats.totalStudents || 0 }} 位同学来过本档口</div>
      </div>
      <div class="ap-actions">
        <button class="ap-btn ap-btn--light" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </button>
      </div>
    </div>

    <div class="ap-grid ap-grid--4 stat-grid">
      <div class="ap-kpi">
        <div class="ap-stat-label">累计来过学生</div>
        <div class="ap-stat-value">{{ stats.totalStudents || 0 }}<span class="ap-stat-unit">人</span></div>
        <div class="ap-stat-hint">在本档口下过单的同学</div>
      </div>
      <div class="ap-kpi">
        <div class="ap-stat-label">累计完成订单</div>
        <div class="ap-stat-value">{{ stats.totalDoneOrders || 0 }}<span class="ap-stat-unit">单</span></div>
        <div class="ap-stat-hint">已出餐完成的订单</div>
      </div>
      <div class="ap-kpi kpi-veteran">
        <div class="ap-stat-label"><i class="dot veteran"></i>老客</div>
        <div class="ap-stat-value">{{ stats.veteranCount || 0 }}<span class="ap-stat-unit">人</span></div>
        <div class="ap-stat-hint">完成 ≥10 单的铁杆熟客</div>
      </div>
      <div class="ap-kpi">
        <div class="ap-stat-label">已发安抚券</div>
        <div class="ap-stat-value">{{ stats.discountsGranted || 0 }}<span class="ap-stat-unit">张</span></div>
        <div class="ap-stat-hint">客服 AI 与手动发放合计</div>
      </div>
    </div>

    <div class="ap-card tier-card">
      <div class="ap-section-head tier-head">
        <div>
          <div class="ap-section-title">顾客结构</div>
          <div class="ap-section-hint">按已完成订单数自动分层</div>
        </div>
        <div class="tier-legend">
          <span><i class="dot new"></i>新客 {{ newPct }}%</span>
          <span><i class="dot regular"></i>常客 {{ regularPct }}%</span>
          <span><i class="dot veteran"></i>老客 {{ veteranPct }}%</span>
        </div>
      </div>

      <div class="stack-bar" aria-hidden="true">
        <i class="seg new" :style="{ width: newPct + '%' }"></i>
        <i class="seg regular" :style="{ width: regularPct + '%' }"></i>
        <i class="seg veteran" :style="{ width: veteranPct + '%' }"></i>
      </div>

      <div class="tier-items">
        <div class="tier-item">
          <div class="tier-dot new"></div>
          <div class="tier-main">
            <b class="ap-num">{{ stats.newCount || 0 }}</b>
            <span>新客</span>
          </div>
          <small>少于 3 单</small>
        </div>
        <div class="tier-item">
          <div class="tier-dot regular"></div>
          <div class="tier-main">
            <b class="ap-num">{{ stats.regularCount || 0 }}</b>
            <span>常客</span>
          </div>
          <small>3 – 9 单</small>
        </div>
        <div class="tier-item">
          <div class="tier-dot veteran"></div>
          <div class="tier-main">
            <b class="ap-num">{{ stats.veteranCount || 0 }}</b>
            <span>老客</span>
          </div>
          <small>≥ 10 单</small>
        </div>
      </div>
    </div>

    <div class="ap-card list-card">
      <div class="ap-section-head list-head">
        <div>
          <div class="ap-section-title">学生明细</div>
          <div class="ap-section-hint">按完成单量排序 · 切换分层筛选</div>
        </div>
        <div class="ap-pill-group">
          <button
            v-for="tab in tierTabs"
            :key="tab.value"
            class="ap-pill"
            :class="{ active: tierFilter === tab.value }"
            @click="tierFilter = tab.value"
          >{{ tab.label }}</button>
        </div>
      </div>

      <div v-loading="loading" class="ap-list">
        <div v-for="s in filteredStudents" :key="s.userId" class="stu-row">
          <span class="stu-rank ap-num">{{ (stats.students || []).findIndex(x => x.userId === s.userId) + 1 }}</span>
          <el-avatar :size="38" :src="s.studentAvatar" class="stu-avatar">
            {{ (s.studentName || '同学').slice(0, 1) }}
          </el-avatar>
          <div class="stu-main">
            <div class="stu-name">{{ s.studentName || ('同学' + s.userId) }}</div>
            <div class="stu-sub">贡献本档口 {{ Math.round((s.doneCount || 0) / (stats.totalDoneOrders || 1) * 100) }}% 的完成单</div>
          </div>
          <div class="stu-bar"><i :style="{ width: barPct(s) + '%' }"></i></div>
          <div class="stu-count"><b class="ap-num">{{ s.doneCount }}</b><span>单</span></div>
          <span class="tier-pill" :class="tierClass(s.tier)"><i class="tier-pill-dot"></i>{{ s.tier }}</span>
        </div>
        <div v-if="!loading && !filteredStudents.length" class="ap-empty">
          <div class="ap-empty-mark"><el-icon :size="20"><User /></el-icon></div>
          <div class="ap-empty-title">{{ tierFilter === 'ALL' ? '还没有顾客记录' : '这个分层还没有学生' }}</div>
          <div>{{ tierFilter === 'ALL' ? '学生完成订单后会自动出现在这里' : '换个筛选看看其他分层' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { getStallCustomerStats } from '@/api/merchant'

const loading = ref(false)
const stats = ref({})
const tierFilter = ref('ALL')

const tierTabs = [
  { value: 'ALL', label: '全部' },
  { value: '新客', label: '新客' },
  { value: '常客', label: '常客' },
  { value: '老客', label: '老客' }
]

const total = computed(() => {
  const sum = (stats.value.newCount || 0) + (stats.value.regularCount || 0) + (stats.value.veteranCount || 0)
  return sum || 1
})
const newPct = computed(() => Math.round((stats.value.newCount || 0) / total.value * 100))
const regularPct = computed(() => Math.round((stats.value.regularCount || 0) / total.value * 100))
const veteranPct = computed(() => Math.max(0, 100 - newPct.value - regularPct.value))

const maxDone = computed(() => {
  const list = stats.value.students || []
  return list.reduce((m, s) => Math.max(m, Number(s.doneCount || 0)), 1)
})

const filteredStudents = computed(() => {
  const list = stats.value.students || []
  if (tierFilter.value === 'ALL') return list
  return list.filter(s => s.tier === tierFilter.value)
})

function barPct(s) {
  return Math.round(Number(s.doneCount || 0) / maxDone.value * 100)
}

function tierClass(tier) {
  return { 新客: 'new', 常客: 'regular', 老客: 'veteran' }[tier] || 'new'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStallCustomerStats()
    stats.value = res.data || {}
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.stat-grid { margin-bottom: 16px; }

.kpi-veteran .ap-stat-label { display: inline-flex; align-items: center; gap: 6px; }

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  background: #c7c7cc;
  flex-shrink: 0;
}

.dot.new { background: #c7c7cc; }
.dot.regular { background: #86868b; }
.dot.veteran { background: var(--brand); }

.tier-card { margin-bottom: 16px; }

.tier-head { margin-bottom: 18px; }

.tier-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 12px;
  color: var(--text-2);
}

.tier-legend span { display: inline-flex; align-items: center; gap: 6px; }

.stack-bar {
  display: flex;
  height: 12px;
  border-radius: 999px;
  overflow: hidden;
  background: #f2f2f4;
  gap: 2px;
}

.stack-bar .seg { height: 100%; transition: width 0.6s var(--ease-apple); }
.stack-bar .seg.new { background: #c7c7cc; }
.stack-bar .seg.regular { background: #86868b; }
.stack-bar .seg.veteran { background: var(--brand); }

.tier-items {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.tier-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--surface-2);
}

.tier-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.tier-dot.new { background: #c7c7cc; }
.tier-dot.regular { background: #86868b; }
.tier-dot.veteran { background: var(--brand); }

.tier-main { display: flex; align-items: baseline; gap: 6px; }
.tier-main b { font-size: 22px; font-weight: 650; letter-spacing: -0.02em; }
.tier-main span { font-size: 13px; color: var(--text-2); }

.tier-item small { margin-left: auto; font-size: 11px; color: var(--text-3); }

.list-head { margin-bottom: 8px; }

.ap-list { margin-top: 4px; }

.stu-row {
  display: grid;
  grid-template-columns: 26px 38px minmax(120px, 1fr) 1.2fr 64px 64px;
  align-items: center;
  gap: 14px;
  padding: 14px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.stu-row:last-child { border-bottom: none; }

.stu-rank {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
  text-align: center;
}

.stu-avatar { flex-shrink: 0; background: #f0f0f2; color: #86868b; font-size: 14px; }

.stu-main { min-width: 0; }
.stu-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stu-sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stu-bar {
  height: 6px;
  border-radius: 999px;
  background: #f2f2f4;
  overflow: hidden;
  min-width: 80px;
}

.stu-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: #86868b;
  transition: width 0.6s var(--ease-apple);
}

.stu-count { display: flex; align-items: baseline; gap: 3px; justify-content: flex-end; }
.stu-count b { font-size: 16px; font-weight: 650; color: var(--text-1); }
.stu-count span { font-size: 11px; color: var(--text-3); }

.tier-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 56px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-2);
  background: #f2f2f4;
}

.tier-pill-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.tier-pill.regular { background: #ececef; color: #4b4b50; }
.tier-pill.veteran { background: var(--brand-soft); color: var(--brand-dark); }

@media (max-width: 1100px) {
  .tier-items { grid-template-columns: 1fr; }
  .stu-row { grid-template-columns: 26px 38px minmax(100px, 1fr) 56px 60px; }
  .stu-bar { display: none; }
}

@media (max-width: 720px) {
  .stat-grid { grid-template-columns: 1fr; }
  .stu-row { grid-template-columns: 22px 38px minmax(90px, 1fr) 56px; }
  .tier-pill { display: none; }
}
</style>
