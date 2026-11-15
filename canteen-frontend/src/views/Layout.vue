<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="app-aside">
      <div class="brand">
        <div class="brand-logo">
          <img src="/ui/emoji/title.png" alt="logo" />
        </div>
        <div class="brand-text">
          <div class="brand-name">小众点评系统</div>
          <div class="brand-sub">Niche Review</div>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <!-- 管理员：后台管理 / 日常使用 两栏可切换（互斥） -->
        <div v-if="userStore.isAdmin" class="menu-group">
          <div class="menu-group-title menu-group-title--switch">
            <span>{{ adminMode === 'admin' ? '后台管理' : '日常使用' }}</span>
            <button
              class="mode-switch"
              type="button"
              :title="adminMode === 'admin' ? '切换到日常使用' : '切换到后台管理'"
              @click="toggleAdminMode"
            >
              <el-icon :size="13"><component :is="adminMode === 'admin' ? 'Refresh' : 'RefreshLeft'" /></el-icon>
              <span>{{ adminMode === 'admin' ? '日常使用' : '后台管理' }}</span>
            </button>
          </div>
          <el-menu :default-active="activePath" router class="app-menu">
            <template v-if="adminMode === 'admin'">
              <el-menu-item
                v-for="item in adminMenus"
                :key="item.path"
                :index="'/' + item.path"
              >
                <el-icon><component :is="item.meta.icon" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </el-menu-item>
            </template>
            <template v-else>
              <el-menu-item
                v-for="item in dailyMenus"
                :key="item.path"
                :index="'/' + item.path"
              >
                <el-icon><component :is="item.meta.icon" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </el-menu-item>
            </template>
          </el-menu>
        </div>

        <!-- 商家：档口经营 / 日常使用 两栏可切换（互斥） -->
        <div v-else-if="userStore.isMerchant" class="menu-group">
          <div class="menu-group-title menu-group-title--switch">
            <span>{{ merchantMode === 'merchant' ? '档口经营' : '日常使用' }}</span>
            <button
              class="mode-switch"
              type="button"
              :title="merchantMode === 'merchant' ? '切换到日常使用' : '切换到档口经营'"
              @click="toggleMerchantMode"
            >
              <el-icon :size="13"><component :is="merchantMode === 'merchant' ? 'Refresh' : 'RefreshLeft'" /></el-icon>
              <span>{{ merchantMode === 'merchant' ? '日常使用' : '档口经营' }}</span>
            </button>
          </div>
          <el-menu :default-active="activePath" router class="app-menu">
            <template v-if="merchantMode === 'merchant'">
              <el-menu-item
                v-for="item in merchantMenus"
                :key="item.path"
                :index="'/' + item.path"
              >
                <el-icon><component :is="item.meta.icon" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </el-menu-item>
            </template>
            <template v-else>
              <el-menu-item
                v-for="item in dailyMenus"
                :key="item.path"
                :index="'/' + item.path"
              >
                <el-icon><component :is="item.meta.icon" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </el-menu-item>
            </template>
          </el-menu>
        </div>

        <!-- 学生：日常使用 -->
        <div v-else-if="studentMenus.length" class="menu-group">
          <div class="menu-group-title">日常使用</div>
          <el-menu :default-active="activePath" router class="app-menu">
            <el-menu-item
              v-for="item in studentMenus"
              :key="item.path"
              :index="'/' + item.path"
            >
              <el-icon><component :is="item.meta.icon" /></el-icon>
              <span>{{ item.meta.title }}</span>
            </el-menu-item>
          </el-menu>
        </div>
      </el-scrollbar>
    </el-aside>

    <el-container class="app-body">
      <el-header height="64px" class="app-header">
        <div class="header-left">
          <div class="page-heading">{{ currentTitle || '首页' }}</div>
        </div>

        <div class="header-right">
          <el-popover placement="bottom-end" :width="420" trigger="click" @show="openNoticePanel">
            <template #reference>
              <div class="notice-btn">
                <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
                  <el-icon :size="20"><Bell /></el-icon>
                </el-badge>
              </div>
            </template>
            <div class="notice-panel">
              <div class="notice-head">
                <span class="notice-title">消息通知</span>
                <el-button v-if="unreadCount" link type="primary" size="small" @click="readAll">全部已读</el-button>
              </div>
              <div v-if="csUnread" class="notice-item unread" @click="goCustomerService">
                <span class="notice-icon">💬</span>
                <div class="notice-body">
                  <div class="notice-content">有 {{ csUnread }} 条客服新消息，点击查看</div>
                </div>
                <span class="notice-dot"></span>
              </div>
              <el-empty v-if="!notifications.length && !csUnread" description="暂无消息" :image-size="56" />
              <div v-else class="notice-list">
                <div
                  v-for="n in notifications"
                  :key="n.id"
                  class="notice-item"
                  :class="{ unread: !n.isRead }"
                  @click="openNotice(n)"
                >
                  <span class="notice-icon">{{ typeIcon(n.type) }}</span>
                  <div class="notice-body">
                    <div class="notice-line">
                      <span class="notice-tag">{{ typeLabel(n.type) }}</span>
                      <span class="notice-time">{{ shortTime(n.createTime) }}</span>
                    </div>
                    <div class="notice-content">{{ n.content }}</div>
                  </div>
                  <span v-if="!n.isRead" class="notice-dot"></span>
                </div>
              </div>
            </div>
          </el-popover>

          <span class="role-chip">
            <i class="role-dot" :class="roleDotClass"></i>
            {{ userStore.userInfo?.roleLabel }}
          </span>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-chip">
              <div class="avatar" :class="{ 'avatar--logo': userStore.isMerchant }">
                <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" class="avatar-img" />
                <template v-else>{{ avatarText }}</template>
              </div>
              <span class="nickname">{{ userStore.nickname }}</span>
              <el-icon :size="12" class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ userStore.userInfo?.realName || userStore.nickname }}
                </el-dropdown-item>
                <el-dropdown-item command="profile" divided>
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade">
            <keep-alive>
              <component :is="Component" :key="cacheKey" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
  <ChatWidget />
  <CartBar />
  <CartDrawer />
  <PayDialog />
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import ChatWidget from '@/components/ChatWidget.vue'
import CartBar from '@/components/cart/CartBar.vue'
import CartDrawer from '@/components/cart/CartDrawer.vue'
import PayDialog from '@/components/cart/PayDialog.vue'
import { listNotifications, unreadCount as fetchUnread, readAllNotifications, readNotification } from '@/api/notification'
import { csUnreadCount, readAllCsMessages } from '@/api/customerService'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const unreadCount = ref(0)
const notifications = ref([])
const csUnread = ref(0)

async function loadUnread() {
  // 两条通道各自取数后一次性求和：任一接口失败不会被累加到旧值上
  let notifCount = 0
  let csCount = 0
  try {
    const res = await fetchUnread()
    notifCount = res.data?.count || 0
  } catch {
    // 忽略：未登录或接口异常时不显示红点
  }
  try {
    const res = await csUnreadCount()
    csCount = res.data?.count || 0
  } catch {
    // 忽略
  }
  csUnread.value = csCount
  unreadCount.value = notifCount + csCount
}

async function loadNotifications() {
  try {
    const res = await listNotifications(20)
    notifications.value = res.data || []
  } catch {
    notifications.value = []
  }
}

/** 打开铃铛面板：列表和未读数一起刷新，避免显示过期红点 */
function openNoticePanel() {
  loadNotifications()
  loadUnread()
}

async function readAll() {
  try {
    // 普通通知 + 客服未读两条通道一起清，否则铃铛数字会一直挂着
    await Promise.all([
      readAllNotifications().catch(() => {}),
      readAllCsMessages().catch(() => {})
    ])
    unreadCount.value = 0
    csUnread.value = 0
    notifications.value = notifications.value.map(n => ({ ...n, isRead: 1 }))
    // 广播给当前页面（如商家待办里的「N 条客服消息待回复」卡片）一起刷新
    window.dispatchEvent(new CustomEvent('cs:unread-changed'))
  } catch {
    // 忽略
  }
}

function typeIcon(type) {
  return {
    LIKE: '👍',
    REPLY: '💬',
    MERCHANT_REPLY: '🏪',
    ORDER: '🧾',
    ORDER_DONE: '🍱',
    REFUND_APPLY: '💰',
    REFUND_RESULT: '💸',
    REVIEW: '⭐',
    WARN: '⚠️',
    ADMIN_NOTICE: '📣'
  }[type] || '🔔'
}

function typeLabel(type) {
  return {
    LIKE: '点赞',
    REPLY: '回复',
    MERCHANT_REPLY: '商家回复',
    ORDER: '新订单',
    ORDER_DONE: '出餐完成',
    REFUND_APPLY: '退款申请',
    REFUND_RESULT: '退款结果',
    REVIEW: '新评价',
    WARN: '文明提醒',
    ADMIN_NOTICE: '管理员提醒'
  }[type] || '通知'
}

function shortTime(t) {
  if (!t) return ''
  const s = String(t)
  return s.length >= 16 ? s.slice(5, 16) : s
}

function markOneNoticeRead(n) {
  if (!n.isRead) {
    n.isRead = 1
    readNotification(n.id).catch(() => {})
    // 单条已读也要把铃铛数字减掉，否则点完数字还挂着
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
}

function openNotice(n) {
  // 有通用跳转目标则按 targetUrl + highlight 跳转并定位高亮
  if (n.targetUrl) {
    const query = n.targetId ? { highlight: n.targetId } : {}
    markOneNoticeRead(n)
    router.push({ path: n.targetUrl, query })
    return
  }
  // 兼容旧的评价类通知：跳菜品详情并定位对应评价/回复
  if (n.dishId) {
    const query = {}
    if (n.reviewId) query.review = n.reviewId
    if (n.replyId) query.reply = n.replyId
    markOneNoticeRead(n)
    router.push({ path: `/dishes/${n.dishId}`, query })
    return
  }
  // 既没有跳转目标也没有菜品：点一下也视为已读，避免数字永远消不掉
  markOneNoticeRead(n)
}

async function goCustomerService() {
  // 点「有 N 条客服新消息」= 已查看：把客服未读一并清掉再跳转，避免红点跟到页面里
  const hadCs = csUnread.value
  try {
    await readAllCsMessages()
    csUnread.value = 0
    unreadCount.value = Math.max(0, unreadCount.value - hadCs)
  } catch {
    // 忽略
  }
  if (userStore.isMerchant) {
    router.push('/merchant/customer-service')
  } else if (userStore.isAdmin) {
    router.push('/admin/cs-center')
  } else {
    router.push('/customer-service')
  }
}

onMounted(() => {
  loadUnread()
  // 客服页进入会话/全部已读后广播，铃铛立刻刷新
  window.addEventListener('cs:unread-changed', loadUnread)
})

onBeforeUnmount(() => {
  window.removeEventListener('cs:unread-changed', loadUnread)
})

// 管理员侧边栏切换：'admin' 后台管理（默认），'daily' 日常使用（与学生端一致）
const adminMode = ref('admin')
// 商家侧边栏切换：'merchant' 档口经营（默认），'daily' 日常使用（与学生端一致）
const merchantMode = ref('merchant')

const allChildren = computed(() => router.options.routes.find(r => r.path === '/')?.children || [])

// 学生日常使用菜单（非 admin、非 merchant），含学生专属的「客服会话」
const studentMenus = computed(() => allChildren.value.filter(i =>
  !i.meta?.hidden && !i.meta?.admin && !i.meta?.merchant
))
// 管理员/商家「日常使用」菜单：在学生菜单基础上剔除学生专属页（客服会话），
// 避免商家/管理员误入学生客服页（他们有各自的客服中心/客服质检）
const dailyMenus = computed(() => allChildren.value.filter(i =>
  !i.meta?.hidden && !i.meta?.admin && !i.meta?.merchant && !i.meta?.student
))
// 商家档口经营菜单
const merchantMenus = computed(() => allChildren.value.filter(i =>
  !i.meta?.hidden && !!i.meta?.merchant
))
// 管理员后台管理菜单
const adminMenus = computed(() => allChildren.value.filter(i =>
  !i.meta?.hidden && !!i.meta?.admin
))

// 侧栏高亮：取「能作为当前路由前缀的最长菜单路径」，
// 避免 /admin/stalls/certificates 被 /admin/stalls（商家管理）抢走高亮
const activePath = computed(() => {
  const path = route.path
  let best = ''
  for (const item of allChildren.value) {
    const p = '/' + item.path
    if (p.includes(':') ) continue
    if ((path === p || path.startsWith(p + '/')) && p.length > best.length) {
      best = p
    }
  }
  // 档口菜单（/admin/stalls/:id）归属「用户与商家」页
  if (best === '/admin/stalls') best = '/admin/members'
  return best || '/' + path.split('/').slice(1, 3).join('/')
})
const currentTitle = computed(() => route.meta.title || '')

// 缓存键：用户身份 + 路由路径。切换账号时身份变化会强制重挂载，清掉上一账号的 keep-alive 缓存；
// 不同详情路径（如 /dishes/1 与 /dishes/2）也会各自独立缓存，避免陈旧 id 串页。
const cacheKey = computed(() => (userStore.userId || 'guest') + ':' + route.path)

const avatarText = computed(() => (userStore.nickname || 'U').trim().slice(0, 1).toUpperCase())

const roleDotClass = computed(() => {
  if (userStore.isAdmin) return 'is-admin'
  if (userStore.isMerchant) return 'is-merchant'
  return 'is-student'
})

function toggleAdminMode() {
  adminMode.value = adminMode.value === 'admin' ? 'daily' : 'admin'
}

function toggleMerchantMode() {
  merchantMode.value = merchantMode.value === 'merchant' ? 'daily' : 'merchant'
}

function handleCommand(command) {
  if (command === 'logout') {
    doLogout()
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

function doLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  })
    .then(() => {
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<style scoped>
.app-shell {
  height: 100%;
}

/* 侧边栏：半透明材质，选中项为白色胶囊 */
.app-aside {
  background: rgba(250, 250, 252, 0.82);
  backdrop-filter: blur(24px) saturate(180%);
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 20px;
  flex-shrink: 0;
}

.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.brand-name {
  font-weight: 650;
  font-size: 14.5px;
  color: var(--text-1);
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.brand-sub {
  font-size: 10px;
  color: var(--text-3);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.menu-scroll {
  flex: 1;
  padding: 2px 0 18px;
}

.menu-group-title {
  font-size: 11px;
  color: #a1a1a6;
  font-weight: 600;
  letter-spacing: 0.09em;
  padding: 16px 24px 8px;
}

.menu-group-title--switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 9px;
  border: none;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-2);
  font-size: 11px;
  font-weight: 550;
  letter-spacing: 0;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), color 0.18s var(--ease-apple),
    transform 0.12s var(--ease-apple);
}

.mode-switch:hover {
  background: rgba(0, 0, 0, 0.085);
  color: var(--text-1);
}

.mode-switch:active {
  transform: scale(0.96);
}

.app-menu {
  border-right: none;
  background: transparent;
  padding: 0 10px;
}

:deep(.el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin: 1px 0;
  border-radius: 9px;
  color: #4b4b50;
  font-weight: 500;
  font-size: 13.5px;
  letter-spacing: -0.005em;
  transition: background-color 0.16s var(--ease-apple), color 0.16s var(--ease-apple),
    box-shadow 0.16s var(--ease-apple);
}

:deep(.el-menu-item .el-icon) {
  font-size: 16px;
  margin-right: 10px;
  opacity: 0.85;
}

:deep(.el-menu-item:hover) {
  background: rgba(0, 0, 0, 0.045);
  color: var(--text-1);
}

:deep(.el-menu-item.is-active) {
  background: #fff;
  color: var(--text-1);
  font-weight: 600;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.04), 0 1px 3px rgba(0, 0, 0, 0.08);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: var(--brand);
  opacity: 1;
}

/* 顶栏 */
.app-body {
  height: 100%;
}

.app-header {
  background: rgba(245, 245, 247, 0.72);
  backdrop-filter: blur(20px) saturate(180%);
  border-bottom: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  position: relative;
  z-index: 5;
}

.app-header::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -14px;
  height: 14px;
  background: linear-gradient(to bottom, rgba(245, 245, 247, 0.85), rgba(245, 245, 247, 0));
  pointer-events: none;
}

.page-heading {
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.015em;
  color: var(--text-1);
  line-height: 1.2;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-2);
  font-size: 11.5px;
  font-weight: 550;
  letter-spacing: 0.01em;
  flex-shrink: 0;
}

.role-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34c759;
}
.role-dot.is-admin { background: #ff9500; }
.role-dot.is-merchant { background: #0071e3; }

.notice-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  cursor: pointer;
  flex-shrink: 0;
  color: var(--text-2);
  transition: background-color 0.18s var(--ease-apple), color 0.18s var(--ease-apple);
}

.notice-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-1);
}

.notice-panel {
  min-height: 420px;
  max-height: 520px;
  display: flex;
  flex-direction: column;
}

.notice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  margin-bottom: 6px;
}

.notice-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-1);
}

.notice-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.notice-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.notice-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

.notice-item.unread {
  background: #fff8f1;
}

.notice-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.notice-body {
  flex: 1;
  min-width: 0;
}

.notice-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 3px;
}

.notice-tag {
  font-size: 11px;
  color: var(--brand-dark);
  background: var(--brand-soft);
  padding: 1px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.notice-content {
  font-size: 13px;
  color: var(--text-1);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notice-time {
  font-size: 11px;
  color: var(--text-3);
  flex-shrink: 0;
}

.notice-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff3b30;
  flex-shrink: 0;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple);
}

.user-chip:hover {
  background: rgba(0, 0, 0, 0.05);
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(150deg, #ff9a54, #ff6a00);
  color: #fff;
  font-weight: 650;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 商家 logo 去背景后四周留白较多，等比放大填满圆形头像 */
.avatar--logo .avatar-img {
  transform: scale(1.28);
}

.nickname {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
}

.arrow {
  color: var(--text-3);
}

/* 内容区 */
.app-main {
  background: transparent;
  padding: 0;
  overflow-x: hidden;
}

@media (prefers-reduced-transparency: reduce) {
  .app-aside {
    background: #fafafc;
    backdrop-filter: none;
  }
  .app-header {
    background: #f5f5f7;
    backdrop-filter: none;
  }
}
</style>