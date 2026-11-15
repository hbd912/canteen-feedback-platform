<template>
  <div class="ap-page favorites-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">我的收藏</h1>
        <div class="ap-sub">
          共 {{ list.length }} 道菜<span v-if="editing"> · 已选 {{ selectedIds.length }} 道</span>
        </div>
      </div>
      <div v-if="list.length" class="ap-actions">
        <button class="ap-btn ap-btn--light" @click="toggleEditing">{{ editing ? '完成' : '管理' }}</button>
      </div>
    </div>

    <div v-if="editing" class="ap-card edit-bar">
      <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate">全选</el-checkbox>
      <span class="selected-hint">已选 {{ selectedIds.length }} 项</span>
      <span class="edit-bar-spacer"></span>
      <el-button
        class="batch-cancel"
        :disabled="!selectedIds.length"
        :loading="batchDeleting"
        @click="handleBatchCancel"
      >
        取消收藏
      </el-button>
    </div>

    <div v-loading="loading" class="favorites-body">
      <div v-if="list.length" class="favorite-grid" :class="{ 'is-editing': editing }">
        <div v-for="dish in list" :key="dish.id" class="favorite-item">
          <el-checkbox
            v-if="editing"
            v-model="selectedIds"
            :value="dish.id"
            class="favorite-check"
            @click.stop
          />
          <DishCard :dish="dish" :show-add="false" />
        </div>
      </div>

      <div v-if="!list.length && !loading" class="ap-card ap-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><Collection /></el-icon></div>
        <div class="ap-empty-title">还没有收藏的菜品</div>
        <div>在菜品详情点一下收藏，就会出现在这里</div>
        <button class="ap-btn empty-action" @click="$router.push('/dishes')">去点餐</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import DishCard from '@/components/DishCard.vue'
import { listFavorites, removeFavorite } from '@/api/favorite'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref([])
const selectedIds = ref([])
const batchDeleting = ref(false)
// 仅收藏页使用的编辑态，不影响其他页面与数据
const editing = ref(false)

const allSelected = computed({
  get: () => list.value.length > 0 && selectedIds.value.length === list.value.length,
  set: (val) => {
    selectedIds.value = val ? list.value.map(dish => dish.id) : []
  }
})
const isIndeterminate = computed(() => selectedIds.value.length > 0 && selectedIds.value.length < list.value.length)

function toggleEditing() {
  editing.value = !editing.value
  if (!editing.value) selectedIds.value = []
}

async function loadData() {
  loading.value = true
  try {
    const res = await listFavorites()
    list.value = res.data
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

async function handleBatchCancel() {
  const ids = selectedIds.value
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确定取消收藏选中的 ${ids.length} 项吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map(id => removeFavorite(id)))
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed) ElMessage.warning(`成功取消 ${success} 项，${failed} 项操作失败`)
    else ElMessage.success(`成功取消 ${success} 项收藏`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.favorites-page {
  max-width: 1320px;
}

.edit-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 18px;
  margin-bottom: 16px;
}

.edit-bar-spacer {
  flex: 1;
  min-width: 8px;
}

.selected-hint {
  font-size: 12px;
  color: var(--text-3);
}

.edit-bar .batch-cancel {
  color: #d92d20;
  background: #fff;
  border-color: var(--border-strong);
}

.edit-bar .batch-cancel:hover:not(.is-disabled) {
  color: #b42318;
  border-color: rgba(217, 45, 32, 0.32);
  background: #fff7f6;
}

.favorites-body {
  min-height: 220px;
}

.favorite-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 18px;
}

.favorite-item {
  position: relative;
}

.favorite-grid.is-editing .favorite-item::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  aspect-ratio: 1 / 1;
  border-radius: 19px 19px 0 0;
  background: rgba(29, 29, 31, 0.06);
  pointer-events: none;
  z-index: 1;
}

.favorite-grid.is-editing :deep(.dish-tags) {
  opacity: 0;
}

.favorite-check {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin: 0;
  padding: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.16);
}

.favorite-check :deep(.el-checkbox__input) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.favorite-check :deep(.el-checkbox__label) {
  display: none;
}

.favorite-check :deep(.el-checkbox__inner) {
  width: 18px;
  height: 18px;
  border-radius: 50%;
}

.favorite-check :deep(.el-checkbox__inner::after) {
  left: 5px;
  top: 2px;
}

.empty-action {
  margin-top: 16px;
}

@media (max-width: 760px) {
  .favorite-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 14px;
  }
}
</style>
