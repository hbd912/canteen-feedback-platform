<template>
  <div class="page-container service-center">
    <header class="service-header cs-head">
      <div>
        <h1 class="ap-title">客服中心</h1>
        <p class="ap-sub">维护回复内容，查看服务记录。</p>
      </div>
      <button type="button" class="ap-btn ap-btn--light" @click="auditVisible = true">处理记录</button>
    </header>

    <nav class="service-navigation" aria-label="客服工作区">
      <div class="ap-pill-group service-segments" role="tablist" aria-label="工作区">
        <button id="knowledge-tab" type="button" role="tab" :aria-selected="view === 'knowledge'"
                aria-controls="service-workspace" :class="['ap-pill', { active: view === 'knowledge' }]"
                @click="switchView('knowledge')">知识与回复</button>
        <button id="records-tab" type="button" role="tab" :aria-selected="view === 'records'"
                aria-controls="service-workspace" :class="['ap-pill', { active: view === 'records' }]"
                @click="switchView('records')">会话与记录</button>
      </div>
    </nav>

    <section id="service-workspace" class="service-workspace" role="tabpanel"
             :aria-labelledby="view === 'knowledge' ? 'knowledge-tab' : 'records-tab'">
      <KnowledgeWorkspace v-if="view === 'knowledge'" ref="knowledgeRef" :kind="kind" @change-kind="changeKind" />
      <ServiceRecordsWorkspace v-else :kind="kind" @change-kind="changeKind" @save-knowledge="saveKnowledge" />
    </section>

    <el-drawer v-model="auditVisible" title="处理记录" size="min(1120px, 100vw)" append-to-body destroy-on-close>
      <div class="service-audit-drawer"><AdminAudit embedded @navigate="auditVisible = false" /></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount, onDeactivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KnowledgeWorkspace from './service-center/KnowledgeWorkspace.vue'
import ServiceRecordsWorkspace from './service-center/ServiceRecordsWorkspace.vue'
import AdminAudit from './AdminAudit.vue'
import './service-center/service-center.css'

const route = useRoute()
const router = useRouter()
const knowledgeRef = ref(null)
const auditVisible = ref(false)
const legacy = {
  faq: { tab: 'knowledge', kind: 'FAQ' },
  experience: { tab: 'knowledge', kind: 'EXPERIENCE' },
  chatlog: { tab: 'records', kind: 'CHAT' },
  qc: { tab: 'records', kind: 'SESSION' },
  audit: { tab: 'records', kind: '' }
}
const view = computed(() => legacy[route.query.tab]?.tab || (route.query.tab === 'records' ? 'records' : 'knowledge'))
const kind = computed(() => legacy[route.query.tab]?.kind || String(route.query.kind || ''))

function switchView(tab) {
  const query = { ...route.query, tab }
  delete query.kind
  router.replace({ path: '/admin/cs-center', query })
}
function changeKind(value) {
  const query = { ...route.query, tab: view.value }
  if (value) query.kind = value
  else delete query.kind
  router.replace({ path: '/admin/cs-center', query })
}
async function saveKnowledge(preset) {
  await router.replace({ path: '/admin/cs-center', query: { tab: 'knowledge' } })
  await nextTick()
  knowledgeRef.value?.openCreate(preset)
}
function onPresetFaq(event) {
  if (route.path === '/admin/cs-center') saveKnowledge({ kind: 'FAQ', question: event.detail?.question || '' })
}
watch(() => route.query.tab, (tab) => {
  if (tab === 'audit') auditVisible.value = true
}, { immediate: true })
onMounted(() => window.addEventListener('canteen:faq-preset', onPresetFaq))
onBeforeUnmount(() => window.removeEventListener('canteen:faq-preset', onPresetFaq))
onDeactivated(() => { auditVisible.value = false })
</script>

<style scoped>
/* 仅覆盖外壳：service-center.css 未在改造范围内，样式收敛在这里。 */
.cs-head h1.ap-title {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-1);
}

.cs-head .ap-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-2);
}

.cs-head .ap-btn--light {
  flex-shrink: 0;
}

.service-navigation {
  margin-bottom: 16px;
}

.service-navigation .ap-pill-group {
  background: transparent;
  padding: 0;
  gap: 8px;
}

.service-navigation .ap-pill {
  padding: 7px 18px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
  background: transparent;
  border: 1px solid var(--border);
  box-shadow: none;
}

.service-navigation .ap-pill:hover {
  color: var(--text-1);
  background: rgba(0, 0, 0, 0.03);
}

.service-navigation .ap-pill.active {
  background: var(--text-1);
  border-color: var(--text-1);
  color: #fff;
  box-shadow: none;
}

.service-navigation .ap-pill:focus-visible {
  outline: 2px solid var(--brand-dark);
  outline-offset: 2px;
}

/* 面板对齐 ap-card：22px 圆角、发丝线边框、柔和阴影 */
.service-workspace :deep(.cs-panel) {
  border-radius: 22px;
  border-color: rgba(0, 0, 0, 0.055);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03), 0 8px 24px rgba(0, 0, 0, 0.03);
}
</style>
