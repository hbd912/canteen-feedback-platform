<template>
  <div class="page-container adaptive-page fc-page">
    <div class="ap-head" style="margin-bottom: 16px">
      <div>
        <h1 class="ap-title">反馈与评价</h1>
        <div class="ap-sub">学生留言 · 商家沟通 · 评价审核</div>
      </div>
      <div class="ap-actions">
        <div class="ap-pill-group" role="tablist" aria-label="反馈工作区">
          <button
            type="button"
            class="ap-pill"
            :class="{ active: activeTab === 'student' }"
            @click="switchTab('student')"
          >
            学生留言
          </button>
          <button
            type="button"
            class="ap-pill"
            :class="{ active: activeTab === 'merchant' }"
            @click="switchTab('merchant')"
          >
            商家留言
          </button>
          <button
            type="button"
            class="ap-pill"
            :class="{ active: activeTab === 'review' }"
            @click="switchTab('review')"
          >
            评价审核
          </button>
        </div>
      </div>
    </div>

    <div class="fc-body">
      <FeedbackList v-if="activeTab === 'student'" />
      <MerchantMessageManage v-else-if="activeTab === 'merchant'" />
      <ReviewManage v-else embedded />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeedbackList from '../FeedbackList.vue'
import MerchantMessageManage from './MerchantMessageManage.vue'
import ReviewManage from './ReviewManage.vue'

const route = useRoute()
const router = useRouter()
const TABS = ['student', 'merchant', 'review']
const activeTab = ref(TABS.includes(route.query.tab) ? route.query.tab : 'student')

function switchTab(name) {
  if (activeTab.value === name) return
  activeTab.value = name
  router.replace({ path: '/admin/feedback-center', query: { ...route.query, tab: name } })
}

// 支持从工作台待办跳转：/admin/feedback-center?tab=review|merchant|student
watch(
  () => route.query.tab,
  (tab) => {
    if (TABS.includes(tab)) activeTab.value = tab
  }
)
</script>

<style scoped>
.fc-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 内容区交给各子页自己的白卡，壳层不再套第二层卡片 */
.fc-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.fc-body > * {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.fc-body :deep(.page-container) {
  padding: 0;
}

.fc-body :deep(.page-card) {
  margin-bottom: 0 !important;
}
</style>
