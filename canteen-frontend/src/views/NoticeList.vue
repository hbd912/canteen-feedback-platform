<template>
  <div class="ap-page notice-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">食堂公告</h1>
        <div class="ap-sub">最近 {{ list.length }} 条 · 由食堂管理处发布</div>
      </div>
      <div class="ap-actions">
        <button class="ap-btn ap-btn--light" :disabled="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </button>
      </div>
    </div>

    <div v-loading="loading" class="ap-card notice-card">
      <article v-for="(item, index) in list" :key="item.id" class="notice-item">
        <div class="notice-meta">
          <span>{{ item.createTime }}</span>
          <span class="meta-sep">·</span>
          <span>{{ item.publisherName }}</span>
          <span v-if="index === 0" class="ap-tag ap-tag--brand notice-latest">最新</span>
        </div>
        <h2 class="notice-title">{{ item.title }}</h2>
        <div class="notice-content">{{ item.content }}</div>
      </article>

      <div v-if="!list.length && !loading" class="ap-empty">
        <div class="ap-empty-mark"><el-icon :size="20"><Bell /></el-icon></div>
        <div class="ap-empty-title">暂时没有新公告</div>
        <div>管理员发布后会第一时间出现在这里</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listNotices } from '@/api/notice'

const loading = ref(false)
const list = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await listNotices()
    list.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.notice-page {
  max-width: 1160px;
}

.notice-card {
  padding: 4px 32px;
  min-height: 240px;
}

.notice-item {
  padding: 26px 0;
  border-bottom: 1px solid #f0f0f2;
}

.notice-item:last-of-type {
  border-bottom: none;
}

.notice-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
}

.meta-sep {
  color: var(--text-3);
}

.notice-latest {
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
  margin-left: 2px;
}

.notice-title {
  max-width: 720px;
  margin: 10px 0 8px;
  font-size: 18px;
  font-weight: 650;
  line-height: 1.4;
  letter-spacing: -0.015em;
  color: var(--text-1);
}

.notice-content {
  max-width: 720px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-2);
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

@media (max-width: 720px) {
  .notice-card {
    padding: 4px 20px;
  }
}
</style>
