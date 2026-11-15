<template>
  <div class="page-container stall-zone-page">
    <el-card shadow="never" class="page-card" style="margin-bottom: 16px">
      <div class="zone-head">
        <div class="zone-title">
          <div class="zone-mark" aria-hidden="true"></div>
          <div>
            <div class="zone-name">{{ areaLabel }}</div>
            <div class="zone-desc">{{ area === 'north' ? '共一层，直达档口' : '共两层，请选择楼层' }}</div>
          </div>
        </div>
        <el-radio-group v-if="area === 'south'" v-model="floor" class="floor-switch" @change="loadData">
          <el-radio-button :value="1">1 楼</el-radio-button>
          <el-radio-button :value="2">2 楼</el-radio-button>
        </el-radio-group>
      </div>

      <div class="filter-bar" style="margin-bottom: 0; margin-top: 14px">
        <el-input
          v-model="keyword"
          placeholder="搜索档口 / 窗口号 / 品类"
          clearable
          style="width: 220px"
          @keyup.enter="loadData"
          @clear="loadData"
        />
        <el-select v-model="category" placeholder="全部分类" clearable style="width: 140px" @change="loadData">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col v-for="s in filtered" :key="s.id" :xs="24" :sm="12" :md="8" style="margin-bottom: 16px">
        <el-card shadow="never" class="stall-card" @click="goDishes(s)">
          <div class="stall-cover" :style="coverStyle(s)">
            <span class="stall-window">{{ s.windowNo }} 号窗口</span>
            <span v-if="isLowHygiene(s)" class="stall-warn">
              <em class="warn-dot"></em>卫生待整改
            </span>
          </div>

          <div class="stall-body">
            <div class="stall-head">
              <span class="stall-name">{{ s.name }}</span>
              <span v-if="s.status !== 1" class="ap-tag">歇业</span>
            </div>

            <div class="stall-meta">
              <span v-if="area === 'south'" class="ap-tag ap-tag--green">{{ s.floor }} 楼</span>
              <span class="ap-tag ap-tag--brand">{{ s.category || '综合' }}</span>
              <span class="stall-loc">{{ s.location || '位置未标注' }}</span>
            </div>

            <div class="stall-manager">
              <el-icon :size="13"><User /></el-icon>
              负责人：{{ s.managerName || '待分配' }}
            </div>

            <div class="stall-signature">
              <span class="sig-label">招牌菜</span>
              <span v-for="d in s.signatureDishes || []" :key="d.id" class="ap-tag">{{ d.name }}</span>
              <span v-if="!s.signatureDishes?.length" class="sig-none">暂未设置</span>
            </div>

            <div class="stall-hygiene" :class="{ 'is-low': isLowHygiene(s) }">
              <span class="hygiene-label">卫生分</span>
              <b>{{ hygieneText(s) }}</b>
              <span class="hygiene-tag">{{ hygieneLevel(s) }}</span>
            </div>

            <div class="stall-stats">
              <span><b>{{ s.avgRating }}</b> 分</span>
              <span class="stat-sep"></span>
              <span>{{ s.dishCount }} 道菜</span>
              <span class="stat-sep"></span>
              <span>好评 {{ s.goodRate }}%</span>
            </div>

            <div class="stall-actions">
              <el-button type="primary" plain size="small" @click.stop="goDishes(s)">挑选菜品并加购</el-button>
              <el-button size="small" @click.stop="openCart">打开购物车</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!filtered.length && !loading" description="暂无档口" />
  </div>

</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listStalls } from '@/api/stall'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref([])
const keyword = ref('')
const category = ref('')

// 从路由判断当前看的是南区还是北区食堂
const area = computed(() => (route.path.startsWith('/stalls/north') ? 'north' : 'south'))
const areaLabel = computed(() => (area.value === 'north' ? '北区食堂' : '南区食堂'))
const floor = ref(1)

const categories = computed(() => [...new Set(list.value.map(s => s.category).filter(Boolean))])

const filtered = computed(() => {
  const kw = keyword.value.trim()
  return list.value.filter(s => {
    const matchKw = !kw
      || s.name.includes(kw)
      || String(s.windowNo).includes(kw)
      || (s.category || '').includes(kw)
      || (s.managerName || '').includes(kw)
    const matchCat = !category.value || s.category === category.value
    return matchKw && matchCat
  })
})

// 用招牌菜实拍图做封面，没有招牌菜时退回中性色块
const GRADIENTS = [
  'linear-gradient(150deg, #d9c3ae 0%, #b99a80 100%)',
  'linear-gradient(150deg, #cfd8c6 0%, #a8b79b 100%)',
  'linear-gradient(150deg, #c9d5dd 0%, #9fb0bd 100%)',
  'linear-gradient(150deg, #d3ceDA 0%, #aaa3b8 100%)'
]

function coverPhoto(stall) {
  return stall.coverImage || stall.signatureDishes?.find(d => d.image)?.image || ''
}

function coverStyle(stall) {
  const photo = coverPhoto(stall)
  if (photo) {
    return {
      backgroundImage: `linear-gradient(180deg, rgba(0,0,0,0.05), rgba(0,0,0,0.42)), url(${photo})`
    }
  }
  return { backgroundImage: GRADIENTS[stall.id % GRADIENTS.length] }
}

const HYGIENE_WARN = 80

function hygieneText(stall) {
  return stall.hygieneScore == null ? '--' : Number(stall.hygieneScore).toFixed(1)
}

function isLowHygiene(stall) {
  return stall.hygieneScore != null && Number(stall.hygieneScore) < HYGIENE_WARN
}

function hygieneLevel(stall) {
  const v = Number(stall.hygieneScore)
  if (stall.hygieneScore == null) return '未评定'
  if (v >= 90) return '优秀'
  if (v >= 80) return '良好'
  return '待整改'
}

function goDishes(stall) {
  router.push({ path: '/dishes', query: { stallId: stall.id } })
}

function openCart() {
  window.dispatchEvent(new CustomEvent('canteen:open-cart'))
}

async function loadData() {
  loading.value = true
  try {
    const res = await listStalls({
      area: area.value,
      floor: area.value === 'south' ? floor.value : 1
    })
    list.value = res.data
  } catch {
    ElMessage.error('档口加载失败')
  } finally {
    loading.value = false
  }
}

// 南区/北区是同一个组件，切换菜单时复用实例，需要监听路由重新加载
watch(() => route.path, () => {
  floor.value = 1
  category.value = ''
  keyword.value = ''
  loadData()
})

onMounted(loadData)
</script>

<style scoped>
.zone-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.zone-title {
  display: flex;
  align-items: center;
  gap: 14px;
}

.zone-mark {
  width: 4px;
  height: 34px;
  border-radius: 2px;
  background: #1d1d1f;
}

.zone-name {
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.014em;
  color: var(--text-1);
}

.zone-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-3);
}

.floor-switch :deep(.el-radio-button__inner) {
  padding: 8px 22px;
}

.stall-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s var(--ease-out), box-shadow 0.25s var(--ease-out),
    border-color 0.25s var(--ease-out);
}

.stall-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.stall-cover {
  position: relative;
  height: 110px;
  background-size: cover;
  background-position: center;
}

.stall-window {
  position: absolute;
  right: 12px;
  bottom: 10px;
  height: 20px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(29, 29, 31, 0.62);
  backdrop-filter: blur(10px) saturate(180%);
  -webkit-backdrop-filter: blur(10px) saturate(180%);
  color: #fff;
  font-size: 11px;
  font-weight: 550;
  line-height: 20px;
  letter-spacing: 0.01em;
}

.stall-body {
  padding: 14px 16px 16px;
}

.stall-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.stall-name {
  font-size: 15.5px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}

.stall-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.stall-loc {
  font-size: 12px;
  color: var(--text-3);
}

.stall-manager {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-2);
  padding: 0 0 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
}

.stall-signature {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  min-height: 24px;
  margin: 10px 0;
}

.sig-label {
  font-size: 11.5px;
  color: var(--text-3);
}

.sig-none {
  font-size: 12px;
  color: var(--text-3);
}

.stall-hygiene {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 10px;
  color: #1d8a45;
}

.stall-hygiene b {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.stall-hygiene .hygiene-label {
  color: var(--text-3);
}

.stall-hygiene .hygiene-tag {
  padding: 1px 8px;
  border-radius: 999px;
  background: #e9f8ee;
}

.stall-hygiene.is-low {
  color: #c9342b;
}

.stall-hygiene.is-low .hygiene-tag {
  background: #ffeceb;
}

.stall-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-3);
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.045);
}

.stall-stats b {
  color: var(--text-1);
  font-size: 13px;
  font-weight: 650;
}

.stat-sep {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: #c7c7cc;
}

.stall-warn {
  position: absolute;
  right: 12px;
  top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(201, 52, 43, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  font-size: 11px;
  font-weight: 550;
  letter-spacing: 0.01em;
}

.warn-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #fff;
}

.stall-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

@media (prefers-reduced-motion: reduce) {
  .stall-card:hover {
    transform: none;
  }
}
</style>
