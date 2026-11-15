<template>
  <div class="page-container stall-dishes-page" v-loading="loading">
    <div class="detail-crumb">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/admin/stalls' }">商家管理</el-breadcrumb-item>
        <el-breadcrumb-item>{{ stall?.name || '档口菜单' }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div v-if="!stall && !loading" class="ap-card">
      <div class="ap-empty">
        <div class="ap-empty-mark">
          <el-icon :size="20"><Shop /></el-icon>
        </div>
        <div class="ap-empty-title">档口不存在或已被删除</div>
        <div>返回商家管理，选择一个仍在营业的档口</div>
        <button type="button" class="ap-btn ap-btn--light ap-btn--sm empty-action" @click="backToStalls">
          返回商家管理
        </button>
      </div>
    </div>

    <template v-if="stall">
      <section class="ap-card hero-card">
        <div class="hero-main">
          <span class="hero-icon">
            <img
              v-if="stall?.merchantAvatar"
              :src="stall.merchantAvatar"
              alt="商家头像"
              @error="$event.target.style.display = 'none'"
            >
            <img v-else src="/ui/emoji/stall.png" alt="">
          </span>
          <div class="hero-text">
            <h1 class="hero-title">{{ stall?.name || '档口菜单' }}</h1>
            <div class="hero-meta">
              <span>{{ stall?.windowNo }} 号窗口</span>
              <span>{{ areaText }}</span>
              <span>{{ stall?.category || '暂无主营分类' }}</span>
              <span>评分 {{ stall?.avgRating || 0 }}</span>
              <span>销量 {{ stall?.salesCount || 0 }}</span>
            </div>
          </div>
        </div>
        <div class="hero-actions">
          <button
            v-if="manageMode"
            type="button"
            class="ap-btn ap-btn--light ap-btn--sm"
            @click="manageMode = false"
          >
            退出管理
          </button>
          <button
            v-else
            type="button"
            class="ap-btn ap-btn--light ap-btn--sm"
            @click="manageMode = true"
          >
            管理菜品
          </button>
          <button type="button" class="ap-btn ap-btn--sm" @click="openCreate">
            <el-icon :size="14"><Plus /></el-icon>
            新增菜品
          </button>
        </div>
      </section>

      <section class="ap-card cert-card">
        <div class="card-section-head">
          <div>
            <div class="ap-section-title">商家认证</div>
            <div class="section-hint">由商家在个人中心提交，管理员查看</div>
          </div>
        </div>
        <div class="cert-grid">
          <div class="cert-item">
            <div class="cert-title">健康证</div>
            <div class="cert-box">
              <el-image
                v-if="stall?.healthCertUrl"
                :src="stall.healthCertUrl"
                :preview-src-list="[stall.healthCertUrl]"
                fit="cover"
                class="cert-img"
              />
              <div v-else class="cert-empty">未上传</div>
            </div>
          </div>
          <div class="cert-item">
            <div class="cert-title">营业执照</div>
            <div class="cert-box">
              <el-image
                v-if="stall?.licenseUrl"
                :src="stall.licenseUrl"
                :preview-src-list="[stall.licenseUrl]"
                fit="cover"
                class="cert-img"
              />
              <div v-else class="cert-empty">未上传</div>
            </div>
          </div>
          <div class="cert-item cert-account">
            <div class="cert-title">商家账号</div>
            <div class="cert-info">
              <div class="info-row">
                <span class="info-label">商家账号</span>
                <span class="info-value">{{ stall?.merchantUsername || '未建' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">负责人</span>
                <span class="info-value">{{ stall?.managerName || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">联系电话</span>
                <span class="info-value">{{ stall?.managerPhone || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">绑定邮箱</span>
                <span class="info-value">{{ stall?.merchantEmail || '未绑定' }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="menu-head">
        <div class="menu-title-wrap">
          <span class="ap-section-title">档口菜单</span>
          <span class="ap-section-hint">共 {{ total }} 道菜</span>
        </div>
        <div v-if="manageMode" class="manage-bar">
          <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选</el-checkbox>
          <span class="selected-hint">已选 {{ selectedIds.length }} 道</span>
          <button
            type="button"
            class="ap-btn ap-btn--ghost ap-btn--sm danger-text"
            :disabled="!selectedIds.length || batchDeleting"
            @click="deleteSelected"
          >
            {{ batchDeleting ? '删除中…' : '删除所选' }}
          </button>
        </div>
      </div>

      <section class="ap-card filter-card">
        <div class="filter-bar">
          <el-input
            v-model="keyword"
            placeholder="搜索菜品名称 / 分类"
            clearable
            style="width: 240px"
            @keyup.enter="loadDishes"
            @clear="loadDishes"
          />
          <el-select v-model="sortBy" style="width: 160px" @change="loadDishes">
            <el-option label="好评优先" value="rating" />
            <el-option label="销量优先" value="sales" />
            <el-option label="价格从低到高" value="priceAsc" />
          </el-select>
          <button type="button" class="ap-btn ap-btn--light ap-btn--sm" @click="loadDishes">查询</button>
          <button type="button" class="ap-btn ap-btn--ghost ap-btn--sm" @click="resetFilter">重置</button>
        </div>
      </section>

      <div class="dish-grid">
        <div v-for="dish in dishes" :key="dish.id" class="dish-card" :class="{ managing: manageMode }">
          <div class="dish-thumb">
            <img :src="dishImage(dish)" :alt="dish.name" loading="lazy">
            <span v-if="dish.featured" class="featured-badge">招牌</span>
            <el-checkbox v-if="manageMode" v-model="selectedIds" :value="dish.id" class="select-box" @click.stop />
          </div>
          <div class="dish-body">
            <div class="dish-title">{{ dish.name }}</div>
            <div class="dish-meta">{{ dish.category || '未分类' }} · 评分 {{ dish.avgRating || 0 }}</div>
            <div class="dish-foot">
              <span class="dish-price">¥{{ Number(dish.price || 0).toFixed(2) }}</span>
              <span class="dish-sales">{{ dish.salesCount || 0 }} 份售出</span>
            </div>
            <div v-if="manageMode" class="dish-actions">
              <button type="button" class="row-link" @click="openEdit(dish)">编辑</button>
              <button type="button" class="row-link danger-text" @click="deleteDishItem(dish)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!loading && !dishes.length" class="ap-card">
        <div class="ap-empty">
          <div class="ap-empty-mark">
            <el-icon :size="20"><Dish /></el-icon>
          </div>
          <div class="ap-empty-title">该档口暂无菜品</div>
          <div>新增第一道菜，学生端即可看到</div>
          <button type="button" class="ap-btn ap-btn--light ap-btn--sm empty-action" @click="openCreate">
            新增菜品
          </button>
        </div>
      </div>
    </template>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑菜品' : '新增菜品'" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-group-title">菜品信息</div>
        <el-form-item label="菜品名称" prop="name">
          <el-input v-model="form.name" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" maxlength="30" show-word-limit placeholder="如：川菜" />
        </el-form-item>
        <el-form-item label="图片地址" prop="image">
          <div class="image-field">
            <el-input v-model="form.image" maxlength="255" placeholder="可选，https://..." />
            <div class="image-preview">
              <span class="image-preview-initial">{{ (form.name || '菜').slice(0, 1) }}</span>
              <img
                v-if="form.image"
                :src="form.image"
                alt=""
                @error="$event.target.style.display = 'none'"
              >
            </div>
          </div>
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>

        <div class="ap-divider form-divider"></div>
        <div class="form-group-title">展示与状态</div>
        <el-form-item label="标记">
          <el-space>
            <el-checkbox v-model="form.featuredBool">招牌菜</el-checkbox>
            <el-radio-group v-model="form.status">
              <el-radio :value="1">在售</el-radio>
              <el-radio :value="0">下架</el-radio>
            </el-radio-group>
          </el-space>
        </el-form-item>
      </el-form>
      <template #footer>
        <button type="button" class="ap-btn ap-btn--light" @click="formVisible = false">取消</button>
        <button type="button" class="ap-btn" :disabled="submitting" @click="submitDish">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStall } from '@/api/stall'
import { pageDishes, createDish, updateDish, deleteDish } from '@/api/dish'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const stall = ref(null)
const dishes = ref([])
const total = ref(0)
const keyword = ref('')
const sortBy = ref('rating')
const manageMode = ref(false)
const selectedIds = ref([])
const batchDeleting = ref(false)

const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  stallId: null,
  name: '',
  price: 0,
  category: '',
  image: '',
  description: '',
  featuredBool: false,
  status: 1
})
const rules = {
  name: [{ required: true, message: '请填写菜品名称', trigger: 'blur' }],
  price: [{ required: true, message: '请填写价格', trigger: 'blur' }],
  category: [{ required: true, message: '请填写分类', trigger: 'blur' }]
}

const areaText = computed(() => {
  if (!stall.value) return ''
  const area = stall.value.area === 'north' ? '北区食堂' : '南区食堂'
  return `${area} ${stall.value.floor || 1} 楼`
})
const allSelected = computed({
  get: () => dishes.value.length > 0 && selectedIds.value.length === dishes.value.length,
  set: val => {
    selectedIds.value = val ? dishes.value.map(d => d.id) : []
  }
})
const isIndeterminate = computed(() =>
  selectedIds.value.length > 0 && selectedIds.value.length < dishes.value.length
)

async function loadStall() {
  try {
    const res = await getStall(route.params.id)
    stall.value = res.data
  } catch {
    // 档口不存在（可能已被删除）：清空，页面显示友好空态
    stall.value = null
  }
}

async function loadDishes() {
  loading.value = true
  try {
    const res = await pageDishes({
      stallId: route.params.id,
      keyword: keyword.value.trim() || undefined,
      current: 1,
      size: 100,
      sortBy: sortBy.value
    })
    dishes.value = res.data?.records || []
    total.value = res.data?.total || dishes.value.length
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  keyword.value = ''
  sortBy.value = 'rating'
  loadDishes()
}

function dishImage(dish) {
  return dish.image || '/ui/emoji/dish.png'
}

function backToStalls() {
  router.push('/admin/stalls')
}

function openCreate() {
  Object.assign(form, {
    id: null,
    stallId: stall.value?.id,
    name: '',
    price: 0,
    category: stall.value?.category || '',
    image: '',
    description: '',
    featuredBool: false,
    status: 1
  })
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(dish) {
  Object.assign(form, {
    id: dish.id,
    stallId: dish.stallId || stall.value?.id,
    name: dish.name,
    price: Number(dish.price),
    category: dish.category || '',
    image: dish.image || '',
    description: dish.description || '',
    featuredBool: dish.featured === 1 || dish.featured === true,
    status: dish.status === 0 ? 0 : 1
  })
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function submitDish() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = {
      stallId: form.stallId || stall.value.id,
      name: form.name.trim(),
      price: form.price,
      category: form.category.trim(),
      image: form.image.trim(),
      description: form.description.trim(),
      featured: form.featuredBool ? 1 : 0,
      status: form.status
    }
    if (form.id) {
      await updateDish(form.id, payload)
    } else {
      await createDish(payload)
    }
    ElMessage.success('菜品保存成功')
    formVisible.value = false
    await Promise.all([loadDishes(), loadStall()])
  } finally {
    submitting.value = false
  }
}

function deleteDishItem(dish) {
  ElMessageBox.confirm(`确定删除菜品「${dish.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteDish(dish.id)
      ElMessage.success('删除成功')
      await Promise.all([loadDishes(), loadStall()])
    })
    .catch(() => {})
}

async function deleteSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 道菜吗？删除后不可恢复`, '提示', { type: 'warning' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(selectedIds.value.map(id => deleteDish(id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功删除 ${success} 项，${failed} 项删除失败`)
    else ElMessage.success(`成功删除 ${success} 项`)
    await Promise.all([loadDishes(), loadStall()])
  } finally {
    batchDeleting.value = false
  }
}

onMounted(async () => {
  await loadStall()
  await loadDishes()
})

// 商家管理列表被 keep-alive 缓存，切换不同档口 id 时需重新加载（否则显示上一个档口的数据）
// 返回列表时 route.params.id 会变成 undefined，此时不加载，避免用无效 id 请求后端报「参数 id 类型不正确」
// 关键：/dishes/:id 与 /stalls/:id 共用 :id 参数名，必须用 route.name 判空，
// 否则切到菜品详情页时本组件（仍被 keep-alive 缓存）会把菜品 id 当成档口 id 去查，报「档口不存在」
watch(
  () => (route.name === 'AdminStallDishes' ? route.params.id : null),
  async (id) => {
    if (!id) return
    manageMode.value = false
    selectedIds.value = []
    keyword.value = ''
    sortBy.value = 'rating'
    await loadStall()
    await loadDishes()
  }
)
</script>

<style scoped>
.stall-dishes-page {
  width: 100%;
}

.detail-crumb {
  margin-bottom: 16px;
}

.detail-crumb :deep(.el-breadcrumb__inner) {
  font-size: 13px;
  color: var(--text-2);
  font-weight: 500;
}

.detail-crumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-1);
  font-weight: 600;
}

.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 20px 24px;
  border-radius: 22px;
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.hero-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #f5f5f7;
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.hero-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-title {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  color: var(--text-1);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 13px;
  color: #86868b;
}

.hero-meta span + span::before {
  content: '·';
  margin-right: 8px;
  color: #c7c7cc;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cert-card {
  margin-top: 16px;
  padding: 20px 22px;
}

.card-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.cert-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 20px;
}

.cert-title {
  margin-bottom: 8px;
  font-size: 12.5px;
  color: var(--text-2);
}

.cert-box {
  width: 100%;
  height: 96px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: #f5f5f7;
}

.cert-img {
  width: 100%;
  height: 100%;
}

.cert-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  font-size: 12.5px;
}

.cert-info {
  padding: 2px 0 0;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  font-size: 12.5px;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: var(--text-3);
  flex-shrink: 0;
}

.info-value {
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin: 28px 2px 12px;
}

.menu-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.manage-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.selected-hint {
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.filter-card {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 20px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.dish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.dish-card {
  background: #fff;
  border: 1px solid var(--border) !important;
  border-radius: 20px !important;
  overflow: hidden;
  box-shadow: var(--shadow-xs) !important;
  transition: transform 0.2s var(--ease-apple), box-shadow 0.2s var(--ease-apple);
}

.dish-card:hover {
  transform: translateY(-2px) !important;
  box-shadow: var(--shadow-sm) !important;
}

.dish-card.managing {
  border-color: rgba(255, 122, 26, 0.22) !important;
}

.dish-thumb {
  position: relative;
  height: 146px;
  background: #f5f5f7;
}

.dish-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.featured-badge {
  position: absolute;
  left: 8px;
  top: 8px;
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--brand-dark);
  font-size: 11.5px;
  font-weight: 600;
  backdrop-filter: blur(8px);
}

.dish-card.managing .featured-badge {
  display: none;
}

.select-box {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 24px;
  height: 24px;
  padding: 0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  align-items: center;
  justify-content: center;
}

.select-box :deep(.el-checkbox__inner) {
  width: 16px;
  height: 16px;
}

.dish-body {
  padding: 14px 16px 16px;
}

.dish-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  letter-spacing: -0.005em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-meta {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.dish-price {
  font-size: 15px;
  font-weight: 650;
  color: var(--brand-dark);
  font-variant-numeric: tabular-nums;
}

.dish-sales {
  font-size: 11.5px;
  color: var(--text-3);
}

.dish-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.045);
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

.danger-text {
  color: #c9342b;
}

.danger-text:hover {
  background: #ffeceb;
  color: #c9342b;
}

.empty-action {
  margin-top: 14px;
}

.form-group-title {
  margin: 2px 0 10px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-1);
}

.form-divider {
  margin: 4px 0 18px;
}

.image-field {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.image-field .el-input {
  flex: 1;
}

.image-preview {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-preview-initial {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-2);
}

.image-preview img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 900px) {
  .cert-grid {
    grid-template-columns: 1fr 1fr;
  }

  .cert-account {
    grid-column: 1 / -1;
  }
}

@media (max-width: 600px) {
  .cert-grid {
    grid-template-columns: 1fr;
  }
}
</style>
