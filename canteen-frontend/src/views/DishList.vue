<template>
  <div class="food-plaza">
    <!-- ============ 页头 ============ -->
    <div class="plaza-header">
      <h1 class="page-title">美食探索</h1>
      <div class="plaza-sub">按档口和分类浏览，看看今天大家都在吃什么</div>
    </div>

    <!-- ============ 主体：顶行(轮播+AI助手) + 内容行(筛选+菜品 / 今日最佳+人气榜) ============ -->
    <div class="plaza-body">
      <!-- 顶行：轮播图（左） + AI 助手（右）等高 -->
      <div class="plaza-top">
        <BannerCarousel variant="hero" class="plaza-banner" />

        <!-- 板块2：AI 助手推荐 -->
        <div class="side-card side-card--ai">
          <AiRecommend :dishes="bestList" />
        </div>
      </div>

      <!-- ============ 筛选行：食堂下拉 + 排序下拉 ============ -->
      <div class="filter-row">
        <!-- 食堂筛选下拉 -->
        <div class="canteen-filter">
          <span class="canteen-filter-label">食堂</span>
          <el-dropdown trigger="click" @command="onCanteenCommand">
            <div class="canteen-select">
              <span class="canteen-select-text">{{ canteenText }}</span>
              <el-icon class="canteen-select-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="all">全部食堂</el-dropdown-item>
                <el-dropdown-item command="south:1" :class="{ 'is-active': query.area === 'south' && query.floor === 1 }">
                  南区食堂 · 1 楼
                </el-dropdown-item>
                <el-dropdown-item command="south:2" :class="{ 'is-active': query.area === 'south' && query.floor === 2 }">
                  南区食堂 · 2 楼
                </el-dropdown-item>
                <el-dropdown-item command="north:1" :class="{ 'is-active': query.area === 'north' }">
                  北区食堂
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 排序下拉 -->
        <el-dropdown trigger="click" @command="onSortCommand" class="sort-dropdown">
          <div class="canteen-select">
            <span class="canteen-select-text">{{ sortLabel }}</span>
            <el-icon class="canteen-select-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="stall" :class="{ 'is-active': sortMode === 'stall' }">
                档口排序
              </el-dropdown-item>
              <el-dropdown-item command="sales" :class="{ 'is-active': sortMode === 'sales' }">
                销量排序
              </el-dropdown-item>
              <el-dropdown-item command="rating" :class="{ 'is-active': sortMode === 'rating' }">
                好评优先
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- 分类标签（仅菜品模式显示） -->
      <div v-if="!isStallMode" class="category-bar">
        <span class="category-title">热门菜品</span>
        <div class="category-tabs">
          <button
            class="category-tab"
            :class="{ active: !query.category }"
            @click="setCategory(undefined)"
          >
            <el-icon v-if="!query.category" class="tab-icon"><Shop /></el-icon>
            全部
          </button>
          <button
            v-for="c in categories"
            :key="c"
            class="category-tab"
            :class="{ active: query.category === c }"
            @click="setCategory(c)"
          >
            {{ c }}
          </button>
        </div>
      </div>

      <!-- 内容行：菜品/档口(左) + 今日最佳/人气榜(右) -->
      <div class="plaza-content">
        <div class="plaza-main">
          <!-- 档口排序模式：一行一个档口，左侧档口窗口 + 右侧该档口评分较高的菜品 -->
          <template v-if="isStallMode">
            <div class="stall-tip">
              <span class="stall-tip-main">
                按档口
                <el-dropdown trigger="click" @command="onStallMetricCommand" class="stall-metric-dd">
                  <span class="stall-metric-select">
                    {{ stallMetricLabel }}
                    <el-icon class="stall-metric-arrow"><ArrowDown /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="m in stallMetrics" :key="m.key" :command="m.key" :class="{ 'is-active': stallMetric === m.key }">
                        {{ m.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                排序
              </span>
              <span class="stall-tip-sub">
                共 {{ sortedStalls.length }} 个档口 · 第 {{ stallPage }} / {{ stallPageCount }} 页 · 右侧为该档口评分较高的菜品
              </span>
            </div>

            <div v-loading="stallsLoading" class="stall-list">
              <div v-for="st in pagedStalls" :key="st.id" class="stall-row">
                <!-- 左：档口窗口信息 -->
                <div class="stall-window" @click="selectStall(st)">
                  <div class="stall-cover" :style="{ background: coverGradient(st.category) }">
                    <img v-if="st.coverImage" :src="st.coverImage" :alt="st.name" loading="lazy" @error="$event.target.style.display='none'">
                    <span v-else class="stall-initial">{{ (st.name || st.windowNo || '档').slice(0, 1) }}</span>
                    <span class="stall-window-badge">{{ st.windowNo }} 号窗口</span>
                  </div>
                  <div class="stall-info">
                    <div class="stall-name" :title="st.name">{{ st.name }}</div>
                    <div class="stall-meta">
                      <span class="stall-zone">{{ stallZoneText(st) }}</span>
                      <span v-if="st.avgRating" class="stall-rating">
                        <el-icon><StarFilled /></el-icon>
                        {{ Number(st.avgRating).toFixed(1) }}
                      </span>
                    </div>
                    <div class="stall-desc" :title="st.description">{{ st.description || '暂无档口简介' }}</div>
                    <div class="stall-footer">
                      <span>{{ st.dishCount || 0 }} 道菜品</span>
                      <span class="stall-sales">
                        {{ stallMetric === 'rating' ? '评分 ' + (st.avgRating ? Number(st.avgRating).toFixed(1) : '—') : stallMetric === 'goodRate' ? '好评率 ' + (st.goodRate ? Number(st.goodRate).toFixed(0) : 0) + '%' : stallMetric === 'salesAmount' ? '营收 ¥' + amountText(st.salesAmount) : '月售 ' + (st.salesCount || 0) }}
                      </span>
                      <!-- 联系商家：与档口详情页一致，进客服会话并带上档口 -->
                      <button class="stall-contact" title="联系该档口商家" @click.stop="contactMerchant(st)">
                        <el-icon><Service /></el-icon> 联系商家
                      </button>
                    </div>
                  </div>
                </div>

                <!-- 右：该档口评分较高的菜品 -->
                <div class="stall-dishes">
                  <div
                    v-for="d in (st.topDishes || []).slice(0, stallDishCount)"
                    :key="d.id"
                    class="mini-dish"
                    @click="goDishDetail(d)"
                  >
                    <div class="mini-cover" :style="{ background: coverGradient(d.category) }">
                      <img v-if="d.image" :src="d.image" :alt="d.name" loading="lazy" @error="$event.target.style.display='none'">
                      <span v-else class="mini-initial">{{ (d.name || '菜').slice(0, 1) }}</span>
                      <span v-if="d.avgRating" class="mini-rating">
                        <el-icon><StarFilled /></el-icon>
                        {{ Number(d.avgRating).toFixed(1) }}
                      </span>
                      <!-- 与菜品卡片同款的单一状态标签 -->
                      <span v-if="dishTagList(d).length" class="mini-tag">{{ dishTagList(d)[0].text }}</span>
                    </div>
                    <div class="mini-name" :title="d.name">{{ d.name }}</div>
                    <div class="mini-foot">
                      <span class="mini-price">¥{{ priceText(d.price) }}</span>
                    </div>
                  </div>
                  <div v-if="!(st.topDishes && st.topDishes.length)" class="stall-no-dish">该档口暂无在售菜品</div>
                </div>
              </div>
            </div>
            <div v-if="stallPageCount > 1" class="plaza-pager stall-pager">
              <el-pagination
                v-model:current-page="stallPage"
                v-model:page-size="stallPageSize"
                :total="sortedStalls.length"
                :page-sizes="[STALL_PAGE_MIN, 8, 12, 24]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="onStallPageChange"
                @size-change="onStallSizeChange"
              />
            </div>
            <el-empty v-if="!stallsLoading && !stalls.length" description="暂无档口" />
          </template>

          <!-- 菜品模式 -->
          <div v-if="!isStallMode" v-loading="loading" class="dish-grid">
            <DishCard v-for="dish in records" :key="dish.id" :dish="dish" :show-add="false" :go-stall="true" />
          </div>

          <el-empty v-if="!loading && !isStallMode && !records.length" description="暂无菜品" />

          <div v-if="total > query.size && !isStallMode" class="plaza-pager">
            <el-pagination
              v-model:current-page="query.current"
              :page-size="query.size"
              :total="total"
              layout="total, prev, pager, next"
              background
              @current-change="onDishPageChange"
            />
          </div>
        </div>

        <!-- 右侧：今日最佳 + 人气榜 -->
        <div class="plaza-side">
          <!-- 板块3：趣味抽奖机（点一下随机抽一个档口的菜品） -->
          <div class="side-card side-card--best">
            <div class="side-card-head">
              <span class="side-card-title">今天吃什么</span>
            </div>

            <div class="lucky-box" :class="{ 'is-spinning': luckySpinning }">
              <!-- 抽中结果 -->
              <template v-if="luckyStall">
                <div class="lucky-stall" @click="selectStall(luckyStall)">
                  <span class="lucky-stall-tag">抽中的档口</span>
                  <div class="lucky-stall-name">{{ luckyStall.name }}</div>
                  <div class="lucky-stall-meta">
                    {{ stallZoneText(luckyStall) }} · {{ luckyStall.windowNo }} 号窗口 · 月售 {{ luckyStall.salesCount || 0 }}
                  </div>
                </div>
                <div class="lucky-dishes">
                  <div
                    v-for="d in (luckyStall.topDishes || []).slice(0, 3)"
                    :key="d.id"
                    class="lucky-dish"
                    @click="goDishDetail(d)"
                  >
                    <div class="lucky-cover" :style="{ background: coverGradient(d.category) }">
                      <img v-if="d.image" :src="d.image" :alt="d.name" loading="lazy" @error="$event.target.style.display='none'">
                      <span v-else class="lucky-initial">{{ (d.name || '菜').slice(0, 1) }}</span>
                    </div>
                    <div class="lucky-dish-body">
                      <div class="lucky-dish-name">{{ d.name }}</div>
                      <div class="lucky-dish-foot">
                        <span class="lucky-dish-price">¥{{ priceText(d.price) }}</span>
                        <span v-if="d.avgRating" class="lucky-dish-rating">★ {{ Number(d.avgRating).toFixed(1) }}</span>
                      </div>
                    </div>
                  </div>
                  <div v-if="!(luckyStall.topDishes && luckyStall.topDishes.length)" class="side-empty">该档口暂无在售菜品</div>
                </div>
              </template>

              <!-- 空态 -->
              <div v-else class="lucky-empty">
                <div class="lucky-empty-icon"><el-icon><MagicStick /></el-icon></div>
                <div class="lucky-empty-text">今天吃什么？<br>点一下，随机抽一个档口</div>
              </div>

              <!-- 抽奖按钮 -->
              <button class="lucky-btn" :disabled="luckySpinning" @click="drawLucky">
                {{ luckySpinning ? '抽奖中…' : (luckyStall ? '再抽一次' : '抽一抽') }}
              </button>
            </div>
          </div>

          <!-- 人气榜 -->
          <div class="side-card">
            <div class="side-card-head">
              <span class="side-card-title">人气榜</span>
              <div class="rank-period">
                <button class="period-btn" :class="{ active: rankPeriod === 'today' }" @click="switchRankPeriod('today')">今日</button>
                <button class="period-btn" :class="{ active: rankPeriod === 'week' }" @click="switchRankPeriod('week')">本周</button>
              </div>
            </div>
            <div class="rank-list">
              <div
                v-for="(r, i) in rankList"
                :key="'rank-' + r.dishId"
                class="rank-item"
                @click="goDish(r)"
              >
                <span class="rank-badge" :class="'rank-' + (i + 1)">{{ i + 1 }}</span>
                <div class="rank-thumb" :style="{ background: coverGradient(r.category) }">
                  <img v-if="r.image" :src="r.image" :alt="r.dishName" loading="lazy" @error="$event.target.style.display='none'">
                  <span v-else class="recommend-initial">{{ (r.dishName || '菜').slice(0, 1) }}</span>
                </div>
                <div class="rank-info">
                  <div class="rank-name">{{ r.dishName }}</div>
                  <div class="rank-rating">
                    <el-icon><StarFilled /></el-icon>
                    <span>{{ r.avgRating ? Number(r.avgRating).toFixed(1) : '—' }}</span>
                  </div>
                </div>
                <span class="rank-price">¥{{ priceText(r.price) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DishCard from '@/components/DishCard.vue'
import BannerCarousel from '@/components/BannerCarousel.vue'
import AiRecommend from '@/components/AiRecommend.vue'
import { pageDishes, listCategories } from '@/api/dish'
import { listStalls } from '@/api/stall'
import { getDishRanking } from '@/api/stats'
import { coverGradient } from '@/utils/dishImage'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const categories = ref([])
const rankList = ref([])
// 人气榜时间维度：今日 / 本周
const rankPeriod = ref('today')
// 今日最佳 + AI 小助手共用的固定榜单（不随人气榜的今日/本周切换而变化）
const bestList = ref([])

// 排序：默认进入即「档口排序」，其余为菜品维度的排序
const sortMode = ref('stall') // stall | sales | rating
const stalls = ref([])
const stallsLoading = ref(false)
// 档口行右侧展示的菜品数：向後端多取（后端上限 12），渲染时按行宽自适应截取，
// 宽窗口一行能放下更多菜品就不留空白
const MAX_STALL_DISHES = 12
// 档口分页：每页固定 4 个档口为下限，避免一页只放一两个档口
const STALL_PAGE_MIN = 4
const stallPage = ref(1)
const stallPageSize = ref(STALL_PAGE_MIN)

// 菜品列表每页固定 4 行，每行条数按实际列数动态计算
const DISH_ROWS = 4

const query = reactive({
  current: 1,
  size: DISH_ROWS * 5,
  keyword: '',
  stallId: route.query.stallId ? Number(route.query.stallId) : undefined,
  area: normalizeArea(route.query.area),
  floor: normalizeFloor(route.query.floor, route.query.area),
  category: undefined,
  sortBy: 'sales'
})

const isStallMode = computed(() => sortMode.value === 'stall')

const sortLabels = {
  sales: '销量排序',
  rating: '好评优先',
  stall: '档口排序'
}
const sortLabel = computed(() => sortLabels[sortMode.value] || '档口排序')

const canteenText = computed(() => {
  if (!query.area) return '全部食堂'
  if (query.area === 'south') return `南区食堂 · ${query.floor || 1} 楼`
  return '北区食堂'
})

// 趣味抽奖机：点一下随机抽一个档口，展示该档口评分较高的菜品
const luckyStall = ref(null)
const luckySpinning = ref(false)
let luckyTimer = null

function drawLucky() {
  if (luckySpinning.value) return
  const pool = stalls.value.filter(s => s.topDishes && s.topDishes.length)
  if (!pool.length) {
    luckyStall.value = null
    return
  }
  luckySpinning.value = true
  // 快速轮换档口制造「抽奖跳动」的趣味感，最后随机定格一个
  const rounds = 10 + Math.floor(Math.random() * 6) // 10~15 次跳动
  let count = 0
  luckyTimer = setInterval(() => {
    luckyStall.value = pool[Math.floor(Math.random() * pool.length)]
    count += 1
    if (count >= rounds) {
      clearInterval(luckyTimer)
      luckyTimer = null
      luckySpinning.value = false
    }
  }, 80)
}

function stallZoneText(st) {
  if (!st) return ''
  if (st.area === 'north') return '北区'
  return `南区${st.floor || 1}楼`
}

// 档口排序：默认按累计销量从多到少，可在 销量/营收/评分/好评率 之间切换
// （销量相同再看评分、窗口号，保证顺序稳定）
const stallMetrics = [
  { key: 'sales', label: '累计销量' },
  { key: 'salesAmount', label: '营收' },
  { key: 'rating', label: '评分' },
  { key: 'goodRate', label: '好评率' }
]
const stallMetric = ref('sales')
const stallMetricLabel = computed(() => {
  const m = stallMetrics.find(x => x.key === stallMetric.value)
  return m ? m.label : '累计销量'
})

function onStallMetricCommand(key) {
  if (stallMetric.value === key) return
  stallMetric.value = key
  stallPage.value = 1
}

const metricValue = st => {
  switch (stallMetric.value) {
    case 'salesAmount': return Number(st.salesAmount || 0)
    case 'rating': return Number(st.avgRating || 0)
    case 'goodRate': return Number(st.goodRate || 0)
    default: return Number(st.salesCount || 0)
  }
}

const sortedStalls = computed(() => {
  return [...stalls.value].sort((a, b) => {
    const byMetric = metricValue(b) - metricValue(a)
    if (byMetric !== 0) return byMetric
    const byRating = (b.avgRating || 0) - (a.avgRating || 0)
    if (byRating !== 0) return byRating
    return String(a.windowNo || '').localeCompare(String(b.windowNo || ''))
  })
})

/** 营收金额展示：万以下保留整数，过万缩写为 x.x 万 */
function amountText(v) {
  const n = Number(v || 0)
  if (n >= 10000) return (n / 10000).toFixed(1) + ' 万'
  return String(Math.round(n))
}

/** 与 DishCard 一致的菜品特色标签（售罄/招牌/好评如潮/热销/新品，最多 2 个） */
function dishTagList(d) {
  if (!d) return []
  const tags = []
  const soldOut = d.soldOut === 1 || (d.stockQty !== null && d.stockQty !== undefined && Number(d.stockQty) <= 0)
  if (soldOut) tags.push({ text: '售罄', type: 'gray' })
  if (d.featured === 1) tags.push({ text: '招牌', type: 'gold' })
  if (d.goodRate != null && d.goodRate >= 85 && d.reviewCount >= 5) tags.push({ text: '好评如潮', type: 'green' })
  if (d.salesCount >= 200) tags.push({ text: '热销', type: 'orange' })
  else if (d.reviewCount != null && d.reviewCount <= 3) tags.push({ text: '新品', type: 'blue' })
  return tags.slice(0, 2)
}

/** 联系商家：与档口详情页同款跳转，进学生客服会话并带上档口 id */
function contactMerchant(st) {
  if (!st || !st.id) return
  router.push({ path: '/customer-service', query: { stall: st.id } })
}

async function loadData() {
  loading.value = true
  try {
    query.sortBy = sortMode.value === 'stall' ? 'sales' : sortMode.value
    const res = await pageDishes(query)
    records.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadStalls() {
  stallsLoading.value = true
  try {
    const res = await listStalls({
      area: query.area || undefined,
      floor: query.floor || undefined,
      // 让后端一并返回每个档口评分较高的菜品，省掉 N 次请求；取满上限供宽窗多列展示
      dishLimit: MAX_STALL_DISHES
    })
    stalls.value = res.data || []
    // 筛选后档口变少时，把页码收回到最后一页
    const maxPage = Math.max(1, Math.ceil(stalls.value.length / Math.max(STALL_PAGE_MIN, stallPageSize.value)))
    if (stallPage.value > maxPage) stallPage.value = maxPage
    // 行渲染完成后按实际行宽度量右侧菜品卡展示数量
    await nextTick()
    measureStallDishCount()
  } catch {
    stalls.value = []
  } finally {
    stallsLoading.value = false
  }
}

async function loadRanking() {
  try {
    const res = await getDishRanking(10, rankPeriod.value)
    rankList.value = res.data || []
  } catch {
    rankList.value = []
  }
}

// 今日最佳 + AI 小助手：固定用综合排行，不随人气榜的今日/本周切换
async function loadBestList() {
  try {
    const res = await getDishRanking(10, 'all')
    bestList.value = res.data || []
  } catch {
    bestList.value = []
  }
}

function switchRankPeriod(p) {
  if (rankPeriod.value === p) return
  rankPeriod.value = p
  loadRanking()
}

/** 当前页要渲染的档口 */
const pagedStalls = computed(() => {
  const size = Math.max(STALL_PAGE_MIN, stallPageSize.value)
  const start = (stallPage.value - 1) * size
  return sortedStalls.value.slice(start, start + size)
})

const stallPageCount = computed(() => {
  const size = Math.max(STALL_PAGE_MIN, stallPageSize.value)
  return Math.max(1, Math.ceil(sortedStalls.value.length / size))
})

/** 菜品网格当前实际列数（auto-fill 会随宽度变化） */
function measureDishCols() {
  const el = document.querySelector('.dish-grid')
  if (!el) return 0
  const cols = getComputedStyle(el).gridTemplateColumns.split(' ').filter(Boolean).length
  return cols > 0 ? cols : 0
}

// 档口行右侧菜品卡的目标宽度：卡片区宽 = 行宽 − 档口窗 − 间隙，按 ~180px/卡自适应
// （卡片 flex:1 会拉伸到均分，目标值只决定「放几张」，偏小一点让宽窗尽量铺满）
const MINI_DISH_TARGET = 150
// 实测一行能放下的卡位数；最终展示数取「实测位数」与「当前页各档口菜品数最小值」
// 的较小者——否则菜品多的档口 5 张、少的 4 张，行尾留白还参差不齐
const measuredSlots = ref(4)
const stallDishCount = computed(() => {
  const counts = pagedStalls.value
    .map(s => (s.topDishes || []).length)
    .filter(n => n > 0)
  const dataMin = counts.length ? Math.min(...counts) : MAX_STALL_DISHES
  return Math.max(1, Math.min(measuredSlots.value, dataMin, MAX_STALL_DISHES))
})
function measureStallDishCount() {
  if (!isStallMode.value) return
  const row = document.querySelector('.stall-row')
  if (!row) return
  // <900px 时行改为上下堆叠、菜品区换行铺开，不再按横排行宽截位数
  if (getComputedStyle(row).flexDirection === 'column') {
    measuredSlots.value = MAX_STALL_DISHES
    return
  }
  // 直接量真实 DOM：菜品区 flex:1 宽度决定了能放下几张卡（卡间 gap 12px）
  const dishes = document.querySelector('.stall-dishes')
  if (!dishes || !dishes.clientWidth) return
  measuredSlots.value = Math.max(1, Math.floor((dishes.clientWidth + 12) / (MINI_DISH_TARGET + 12)))
}

/** 按「每页 4 行」对齐每页条数，列数变化时才重新加载 */
function applyDishPageSize() {
  const cols = measureDishCols()
  if (!cols) return
  const size = cols * DISH_ROWS
  if (size !== query.size) {
    query.size = size
    query.current = 1
    loadData()
  }
}

// 窗口宽度变化会让网格列数变化，列数变了就重新对齐每页条数；
// 档口模式下则重算右侧菜品卡展示数量
let resizeTimer = null
function onWindowResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    if (isStallMode.value) {
      measureStallDishCount()
    } else {
      applyDishPageSize()
    }
  }, 350)
}

async function onDishPageChange() {
  await nextTick()
  const el = document.querySelector('.dish-grid')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  loadData()
}

/** 翻页后回到列表顶部，避免停在上一页的底部 */
async function backToStallTop() {
  await nextTick()
  const el = document.querySelector('.stall-tip')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onStallPageChange() {
  backToStallTop()
  // 翻页后档口组合变了，「各档口菜品数最小值」可能变化，重算展示数
  nextTick(measureStallDishCount)
}

function onStallSizeChange() {
  stallPage.value = 1
  backToStallTop()
  nextTick(measureStallDishCount)
}

async function onSortCommand(cmd) {
  sortMode.value = cmd
  query.current = 1
  stallPage.value = 1
  // 用户主动切排序 = 离开「只看某档口」的场景，清掉遗留的 stallId，
  // 避免之后每次请求都只带回该档口的几道菜
  if (route.query.stallId) {
    router.replace({ path: '/dishes', query: buildListQuery() })
    query.stallId = undefined
  }
  if (cmd === 'stall') {
    loadStalls()
    return
  }
  // 菜品模式的网格刚挂载，等 DOM 就绪后按实际列数算出「4 行」的条数再请求
  await nextTick()
  const cols = measureDishCols()
  if (cols) query.size = cols * DISH_ROWS
  loadData()
}

/** 当前列表查询条件里适合留在 URL 的部分（排序切换/筛选联动时同步地址栏） */
function buildListQuery() {
  const q = {}
  if (query.area) {
    q.area = query.area
    if (query.area === 'south' && query.floor) q.floor = String(query.floor)
  }
  if (query.category) q.category = query.category
  return q
}

function selectStall(st) {
  // 点击档口直接进入档口详情页（含招牌/好评/热销/新品标签 + 加购按钮）
  router.push(`/stalls/${st.id}`)
}

function onCanteenCommand(cmd) {
  if (cmd === 'all') {
    applyArea('', null)
  } else if (cmd.startsWith('south:')) {
    applyArea('south', Number(cmd.split(':')[1]))
  } else if (cmd.startsWith('north:')) {
    applyArea('north', 1)
  }
}

function applyArea(area, floor) {
  query.area = area
  query.floor = floor
  query.current = 1
  stallPage.value = 1
  // 换食堂筛选同样视为离开单档口场景，清掉遗留 stallId
  if (route.query.stallId) {
    router.replace({ path: '/dishes', query: buildListQuery() })
    query.stallId = undefined
  }
  if (sortMode.value === 'stall') {
    loadStalls()
  } else {
    loadData()
  }
}

function setCategory(category) {
  if (sortMode.value === 'stall') {
    sortMode.value = 'sales'
  }
  query.category = category
  query.current = 1
  // 点分类同样清掉遗留 stallId，避免两者叠加后结果集过小
  if (route.query.stallId) {
    router.replace({ path: '/dishes', query: buildListQuery() })
    query.stallId = undefined
  }
  loadData()
}

/** 档口行右侧的菜品卡：直接进菜品详情 */
function goDishDetail(dish) {
  if (!dish || !dish.id) return
  router.push(`/dishes/${dish.id}`)
}

function goDish(item) {
  // 点菜品先跳对应档口页并高亮，再点菜品才进详情（与美食广场主网格一致）
  if (!item || !item.dishId) return
  if (item.stallId) {
    router.push(`/stalls/${item.stallId}?highlight=${item.dishId}`)
  } else {
    router.push(`/dishes/${item.dishId}`)
  }
}

function priceText(p) {
  if (p == null) return '—'
  return Number(p).toFixed(2)
}

function normalizeArea(value) {
  return value === 'north' || value === 'south' ? value : ''
}

function normalizeFloor(floorValue, area) {
  if (area === 'north') return 1
  if (area === 'south') {
    return Number(floorValue) === 2 ? 2 : 1
  }
  return null
}

watch(
  () => route.query.stallId,
  value => {
    // stallId 只在 URL 明确带上时生效；离开链接（切排序/清筛选）后要清掉，
    // 否则后续所有请求都带着 stallId，只会返回这一个档口的菜，看起来「大部分菜加载不出来」
    query.stallId = value ? Number(value) : undefined
    query.current = 1
    if (value) {
      // 从外部链接（卡片「看这个档口的菜」/详情页返回）带 stallId 进来时，
      // 切到菜品模式，让列表展示该档口的菜品
      sortMode.value = 'sales'
      loadData()
    } else if (!isStallMode.value) {
      loadData()
    }
  },
  { immediate: true }
)

watch(
  () => route.query.area,
  value => {
    query.area = normalizeArea(value)
    query.floor = normalizeFloor(route.query.floor, query.area)
    query.current = 1
    if (sortMode.value === 'stall') {
      loadStalls()
    } else {
      loadData()
    }
  }
)

onMounted(async () => {
  window.addEventListener('resize', onWindowResize)
  const [catRes] = await Promise.all([listCategories(), loadRanking(), loadBestList()])
  categories.value = catRes.data
  // 默认档口排序：加载档口列表（含各档口热评菜品）；其余排序模式才加载菜品
  if (isStallMode.value) {
    loadStalls()
  } else if (!records.value.length) {
    loadData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  clearTimeout(resizeTimer)
  if (luckyTimer) clearInterval(luckyTimer)
})
</script>

<style scoped>
/* ============ 页面骨架 ============ */
.food-plaza {
  /* 底部留白避开悬浮结算栏 */
  padding: 28px 32px 116px;
  box-sizing: border-box;
  color: var(--text-1);
  -webkit-font-smoothing: antialiased;
}

.plaza-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.022em;
  line-height: 1.15;
  color: var(--text-1);
}

.plaza-sub {
  margin-top: 7px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.5;
}

/* ============ 主体 ============ */
.plaza-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.plaza-top {
  display: flex;
  align-items: stretch;
  gap: 16px;
}

.plaza-top .plaza-banner {
  flex: 1;
  min-width: 0;
}

.plaza-top .side-card--ai {
  width: clamp(280px, 24vw, 380px);
  flex-shrink: 0;
}

/* ============ 吸顶筛选条：半透明白色胶囊 ============ */
.filter-row {
  position: sticky;
  top: 8px;
  z-index: 20;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 4px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--border);
  border-radius: 999px;
  box-shadow: var(--shadow-xs);
}

.canteen-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.canteen-filter-label {
  padding-left: 8px;
  font-size: 12px;
  font-weight: 550;
  letter-spacing: 0.02em;
  color: var(--text-3);
}

.canteen-select {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 150px;
  padding: 7px 14px;
  border-radius: 999px;
  background: #f2f2f5;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple);
}

.canteen-select:hover {
  background: #ebebef;
}

.canteen-select-text {
  flex: 1;
  font-size: 13px;
  font-weight: 550;
  color: var(--text-1);
}

.canteen-select-arrow {
  font-size: 12px;
  color: var(--text-3);
}

/* ============ 分类 ============ */
.category-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 4px;
}

.category-title {
  flex-shrink: 0;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}

.category-tabs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.category-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.045);
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), color 0.18s var(--ease-apple),
    transform 0.12s var(--ease-apple);
}

.category-tab:hover {
  background: rgba(0, 0, 0, 0.07);
  color: var(--text-1);
}

.category-tab:active {
  transform: scale(0.97);
}

.category-tab.active {
  background: #1d1d1f;
  color: #fff;
  font-weight: 600;
}

.tab-icon {
  font-size: 13px;
}

/* ============ 内容两栏 ============ */
.plaza-content {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.plaza-main {
  flex: 1;
  min-width: 0;
}

/* 菜品网格：CSS Grid，measureDishCols() 依赖这里的自动列数 */
.dish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(156px, 1fr));
  gap: 16px;
  min-height: 120px;
  scroll-margin-top: 72px;
}

.plaza-pager {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

/* ============ 档口排序 ============ */
.stall-tip {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
  /* 翻页后 scrollIntoView 时避开吸顶筛选条 */
  scroll-margin-top: 72px;
}

.stall-tip-main {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.stall-tip-sub {
  font-size: 12px;
  color: var(--text-3);
}

.stall-metric-dd {
  display: inline-block;
}

.stall-metric-select {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.05);
  color: var(--brand-dark);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple);
}

.stall-metric-select:hover {
  background: rgba(0, 0, 0, 0.08);
}

.stall-metric-arrow {
  font-size: 11px;
}

.stall-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 120px;
}

.stall-pager {
  margin-top: 18px;
}

/* 一行一个档口：flex 方向被 measureStallDishCount() 读取，≤900px 才改成 column */
.stall-row {
  display: flex;
  gap: 18px;
  min-height: 208px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s var(--ease-apple), box-shadow 0.2s var(--ease-apple);
}

.stall-row:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.stall-window {
  display: flex;
  flex-direction: column;
  width: 208px;
  flex-shrink: 0;
  min-width: 0;
  overflow: hidden;
  border-radius: 14px;
  cursor: pointer;
}

.stall-window .stall-cover {
  width: 100%;
  height: 128px;
  aspect-ratio: auto;
  flex-shrink: 0;
  border-radius: 14px;
}

.stall-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f2f2f5;
}

.stall-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stall-initial {
  position: relative;
  z-index: 1;
  font-size: 30px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.stall-window-badge {
  position: absolute;
  left: 8px;
  bottom: 8px;
  z-index: 2;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(29, 29, 31, 0.62);
  backdrop-filter: blur(10px) saturate(180%);
  -webkit-backdrop-filter: blur(10px) saturate(180%);
  color: #fff;
  font-size: 10.5px;
  font-weight: 550;
  line-height: 20px;
  letter-spacing: 0.01em;
}

.stall-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 10px 2px 2px;
}

.stall-name {
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stall-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.stall-zone {
  color: var(--text-3);
}

.stall-rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--text-2);
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.stall-rating .el-icon {
  font-size: 11px;
  color: #f5a623;
}

.stall-desc {
  display: -webkit-box;
  margin-top: 8px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.55;
  color: var(--text-3);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.stall-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 10px;
  font-size: 11.5px;
  color: var(--text-3);
}

.stall-sales {
  color: var(--text-2);
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.stall-contact {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  margin-left: auto;
  padding: 0 10px;
  border: none;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-2);
  font-family: inherit;
  font-size: 11.5px;
  font-weight: 550;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}

.stall-contact:hover {
  background: rgba(0, 0, 0, 0.09);
  color: var(--text-1);
}

/* 档口右侧菜品卡 */
.stall-dishes {
  display: flex;
  flex: 1;
  align-items: stretch;
  flex-wrap: nowrap;
  gap: 12px;
  min-width: 0;
  padding-left: 18px;
  border-left: 1px solid var(--border);
}

.mini-dish {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-width: 0;
  cursor: pointer;
  transition: transform 0.2s var(--ease-out);
}

.mini-dish:hover {
  transform: translateY(-2px);
}

.mini-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 12px;
  background: #f2f2f5;
}

.mini-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mini-initial {
  font-size: 22px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.mini-rating {
  position: absolute;
  right: 7px;
  bottom: 7px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--text-1);
  font-size: 10.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.mini-rating .el-icon {
  font-size: 9px;
  color: #f5a623;
}

.mini-tag {
  position: absolute;
  top: 7px;
  left: 7px;
  z-index: 2;
  height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(29, 29, 31, 0.62);
  backdrop-filter: blur(10px) saturate(180%);
  -webkit-backdrop-filter: blur(10px) saturate(180%);
  color: #fff;
  font-size: 10px;
  font-weight: 550;
  line-height: 18px;
  letter-spacing: 0.01em;
}

.mini-name {
  margin-top: 7px;
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mini-foot {
  margin-top: 2px;
}

.mini-price {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.stall-no-dish {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 0;
  font-size: 12px;
  color: var(--text-3);
}

/* ============ 右侧栏 ============ */
.plaza-side {
  display: flex;
  flex-direction: column;
  align-self: stretch;
  gap: 16px;
  width: clamp(280px, 24vw, 380px);
  flex-shrink: 0;
}

.side-card {
  padding: 18px 18px 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

/* 高度与轮播图一致（由顶行 flex 拉伸撑满） */
.side-card--ai {
  display: flex;
  align-items: stretch;
  box-sizing: border-box;
  overflow: hidden;
}

.side-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.side-card-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.012em;
  color: var(--text-1);
}

/* 今天吃什么 */
.lucky-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.lucky-stall {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f5f5f7;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple);
}

.lucky-stall:hover {
  background: #efeff2;
}

.lucky-stall-tag {
  font-size: 11px;
  font-weight: 550;
  letter-spacing: 0.02em;
  color: var(--text-3);
}

.lucky-stall-name {
  margin-top: 3px;
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.014em;
  color: var(--text-1);
}

.lucky-stall-meta {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--text-3);
}

.lucky-dishes {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.lucky-dish {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  margin: 0 -6px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple);
}

.lucky-dish:hover {
  background: #fafafa;
}

.lucky-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 10px;
  background: #f2f2f5;
}

.lucky-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.lucky-initial {
  font-size: 16px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.lucky-dish-body {
  flex: 1;
  min-width: 0;
}

.lucky-dish-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lucky-dish-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 1px;
}

.lucky-dish-price {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.lucky-dish-rating {
  font-size: 11px;
  color: var(--text-3);
}

.lucky-empty {
  padding: 10px 0 6px;
  text-align: center;
}

.lucky-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin: 0 auto 10px;
  border-radius: 50%;
  background: #f2f2f5;
  color: var(--text-3);
  font-size: 18px;
}

.lucky-empty-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-3);
}

.lucky-btn {
  width: 100%;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: #1d1d1f;
  color: #fff;
  font-family: inherit;
  font-size: 13px;
  font-weight: 550;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), transform 0.12s var(--ease-apple),
    opacity 0.18s var(--ease-apple);
}

.lucky-btn:hover {
  background: #333336;
}

.lucky-btn:active {
  transform: scale(0.98);
}

.lucky-btn:disabled {
  opacity: 0.45;
  cursor: default;
  transform: none;
}

.side-empty {
  padding: 14px 0;
  font-size: 12px;
  color: var(--text-3);
  text-align: center;
}

/* 人气榜 */
.rank-period {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: 999px;
  background: #f2f2f5;
}

.period-btn {
  border: none;
  background: transparent;
  padding: 4px 10px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--text-2);
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), color 0.18s var(--ease-apple),
    box-shadow 0.18s var(--ease-apple);
}

.period-btn.active {
  background: #fff;
  color: var(--text-1);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.rank-list {
  display: flex;
  flex-direction: column;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 6px;
  margin: 0 -6px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.16s var(--ease-apple);
}

.rank-item:hover {
  background: #fafafa;
}

.rank-badge {
  width: 16px;
  flex-shrink: 0;
  text-align: center;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.rank-badge.rank-1 {
  color: var(--text-1);
}

.rank-badge.rank-2,
.rank-badge.rank-3 {
  color: var(--text-2);
}

.rank-thumb {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 9px;
  background: #f2f2f5;
}

.rank-thumb img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recommend-initial {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-rating {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 1px;
  font-size: 11px;
  color: var(--text-3);
}

.rank-rating .el-icon {
  font-size: 9px;
  color: #f5a623;
}

.rank-price {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

/* ============ 响应式 ============ */
@media (min-width: 1600px) {
  .stall-window {
    width: 236px;
  }

  .stall-window .stall-cover {
    height: 140px;
  }
}

@media (max-width: 1360px) {
  .stall-window {
    width: 188px;
  }

  .stall-window .stall-cover {
    height: 118px;
  }
}

@media (max-width: 1100px) {
  .plaza-side,
  .side-card--ai {
    display: none;
  }

  .food-plaza {
    padding: 24px 22px 40px;
  }
}

@media (max-width: 900px) {
  .stall-row {
    flex-direction: column;
    gap: 12px;
  }

  .stall-window {
    width: 100%;
  }

  .stall-dishes {
    flex-wrap: wrap;
    padding-left: 0;
    padding-top: 12px;
    border-left: none;
    border-top: 1px solid var(--border);
  }
}

@media (max-width: 768px) {
  .food-plaza {
    padding: 20px 16px 32px;
  }

  .page-title {
    font-size: 24px;
  }

  .filter-row {
    flex-direction: column;
    align-items: stretch;
    border-radius: 18px;
  }

  .canteen-select {
    width: 100%;
  }

  .sort-dropdown {
    width: 100%;
  }

  .dish-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .filter-row {
    background: #fff;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .category-tab:active,
  .lucky-btn:active,
  .mini-dish:hover {
    transform: none;
  }
}
</style>
