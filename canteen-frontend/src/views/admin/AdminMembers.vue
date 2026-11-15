<template>
  <div class="page-container adaptive-page members-page">
    <div class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">用户与商家</h1>
        <div class="ap-sub">管理学生、管理员账号与档口商家</div>
      </div>
      <div class="ap-actions members-actions">
        <div class="ap-pill-group" role="tablist" aria-label="用户与商家视图">
          <button
            type="button"
            class="ap-pill"
            :class="{ active: activeTab === 'users' }"
            @click="switchTab('users')"
          >
            用户
          </button>
          <button
            type="button"
            class="ap-pill"
            :class="{ active: activeTab === 'stalls' }"
            @click="switchTab('stalls')"
          >
            商家
          </button>
        </div>
        <el-button v-if="activeTab === 'stalls'" type="primary" @click="stallRef?.openCreate()">
          <el-icon><Plus /></el-icon>新增档口
        </el-button>
      </div>
    </div>

    <div class="page-card members-card">
      <UserManage v-if="activeTab === 'users'" embedded />
      <StallManage v-else ref="stallRef" embedded />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserManage from './UserManage.vue'
import StallManage from './StallManage.vue'

const route = useRoute()
const router = useRouter()
const TABS = ['users', 'stalls']
const activeTab = ref(TABS.includes(route.query.tab) ? route.query.tab : 'users')
const stallRef = ref(null)

// tab 与 URL 同步：刷新、从旧链接 /admin/stalls、/admin/users 跳转都能落到正确标签
function onTabChange(name) {
  if (route.query.tab === name) return
  router.replace({ path: '/admin/members', query: { ...route.query, tab: name } })
}

function switchTab(name) {
  activeTab.value = name
  onTabChange(name)
}

watch(
  () => route.query.tab,
  (tab) => {
    if (TABS.includes(tab)) activeTab.value = tab
  }
)
</script>

<style scoped>
.members-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.members-actions {
  gap: 12px;
}

/* 壳层唯一白卡：内部列表页去掉自己的卡片后直接铺满 */
.members-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.members-card > * {
  flex: 1;
  min-height: 0;
  width: 100%;
}
</style>
