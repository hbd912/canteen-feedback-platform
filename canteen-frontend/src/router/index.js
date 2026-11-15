import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/request'

const USER_KEY = 'canteen_user'

/** 从本地缓存读取当前登录用户角色（router 初始化早于 Pinia，不能直接用 store） */
function getRole() {
  try {
    const u = JSON.parse(localStorage.getItem(USER_KEY) || 'null')
    return u && u.role ? u.role : null
  } catch {
    return null
  }
}

/** 首页按角色落地：管理员进后台数据看板，商家进待办事项，其余进美食探索（日常使用首页） */
function homePath() {
  const role = getRole()
  if (role === 'ADMIN') return '/admin/dashboard'
  if (role === 'MERCHANT') return '/merchant/todos'
  return '/dishes'
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: () => homePath(),
    children: [
      // 旧版数据看板已下线：老链接 /dashboard 按角色落到各自首页
      { path: 'dashboard', redirect: () => homePath(), meta: { hidden: true } },
      { path: 'dishes', name: 'DishList', component: () => import('@/views/DishList.vue'), meta: { title: '美食探索', icon: 'Shop' } },
      { path: 'stalls/south', redirect: '/dishes?area=south&floor=1', meta: { hidden: true } },
      { path: 'stalls/north', redirect: '/dishes?area=north&floor=1', meta: { hidden: true } },
      { path: 'dishes/:id', name: 'DishDetail', component: () => import('@/views/DishDetail.vue'), meta: { hidden: true, title: '菜品详情' } },
      { path: 'stalls/:id', name: 'StallDetail', component: () => import('@/views/StallDetail.vue'), meta: { hidden: true, title: '档口详情' } },
      { path: 'my/orders', name: 'MyOrders', component: () => import('@/views/MyOrders.vue'), meta: { title: '我的订单', icon: 'List' } },
      { path: 'my/reviews', name: 'MyReviews', component: () => import('@/views/MyReviews.vue'), meta: { title: '我的评价', icon: 'EditPen' } },
      { path: 'my/favorites', name: 'MyFavorites', component: () => import('@/views/MyFavorites.vue'), meta: { title: '我的收藏', icon: 'Star' } },
      { path: 'my/coupons', name: 'MyCoupons', component: () => import('@/views/MyCoupons.vue'), meta: { title: '我的优惠券', icon: 'Ticket', student: true } },
      { path: 'customer-service', name: 'CustomerService', component: () => import('@/views/CustomerService.vue'), meta: { title: '客服会话', icon: 'Service', student: true } },
      { path: 'merchant/todos', name: 'MerchantTodos', component: () => import('@/views/merchant/MerchantTodos.vue'), meta: { title: '待办事项', icon: 'Bell', merchant: true } },
      { path: 'merchant/customers', name: 'MerchantCustomers', component: () => import('@/views/merchant/MerchantCustomers.vue'), meta: { title: '客户统计', icon: 'User', merchant: true } },
      { path: 'merchant/orders', name: 'MerchantOrders', component: () => import('@/views/merchant/MerchantOrders.vue'), meta: { title: '订单处理', icon: 'List', merchant: true } },
      { path: 'merchant/sales', name: 'MerchantSales', component: () => import('@/views/merchant/MerchantSales.vue'), meta: { title: '销售情况', icon: 'TrendCharts', merchant: true } },
      { path: 'merchant/finance', name: 'MerchantFinance', component: () => import('@/views/merchant/MerchantFinance.vue'), meta: { title: '财务中心', icon: 'Wallet', merchant: true } },
      { path: 'merchant/reviews', name: 'MerchantReviews', component: () => import('@/views/merchant/MerchantReviews.vue'), meta: { title: '学生评价', icon: 'Star', merchant: true } },
      { path: 'merchant/refunds', name: 'MerchantRefunds', component: () => import('@/views/merchant/MerchantRefunds.vue'), meta: { title: '退款处理', icon: 'Money', merchant: true } },
      { path: 'merchant/customer-service', name: 'MerchantCustomerService', component: () => import('@/views/merchant/MerchantCustomerService.vue'), meta: { title: '客服中心', icon: 'Service', merchant: true } },
      { path: 'merchant/dishes', name: 'MerchantDishes', component: () => import('@/views/merchant/MerchantDishes.vue'), meta: { title: '菜品管理', icon: 'Dish', merchant: true } },
      { path: 'merchant/ai-assistant', name: 'MerchantAiAssistant', component: () => import('@/views/merchant/MerchantAiAssistant.vue'), meta: { title: '经营助理', icon: 'ChatDotSquare', merchant: true } },
      { path: 'merchant/messages', name: 'MerchantMessages', component: () => import('@/views/merchant/MerchantMessages.vue'), meta: { title: '给管理员留言', icon: 'Message', merchant: true } },
      { path: 'feedbacks', name: 'FeedbackList', component: () => import('@/views/FeedbackList.vue'), meta: { title: '留言反馈', icon: 'ChatDotRound' } },
      { path: 'notices', name: 'NoticeList', component: () => import('@/views/NoticeList.vue'), meta: { title: '食堂公告', icon: 'Bell' } },
      // 后台管理菜单（顺序即侧栏顺序）：字数短的在前，字数长的排在最后
      { path: 'admin/workbench', name: 'AdminWorkbench', component: () => import('@/views/admin/AdminWorkbench.vue'), meta: { title: '工作台', icon: 'Odometer', admin: true } },
      { path: 'admin/dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/DataScreen.vue'), meta: { title: '数据看板', icon: 'Monitor', admin: true } },
      { path: 'admin/orders', name: 'OrderManage', component: () => import('@/views/admin/OrderManage.vue'), meta: { title: '订单管理', icon: 'Calendar', admin: true } },
      { path: 'admin/members', name: 'AdminMembers', component: () => import('@/views/admin/AdminMembers.vue'), meta: { title: '用户与商家', icon: 'User', admin: true } },
      // 旧链接兼容：统一落到「用户与商家」并切到对应标签
      { path: 'admin/stalls', redirect: '/admin/members?tab=stalls', meta: { hidden: true, admin: true } },
      { path: 'admin/users', redirect: '/admin/members?tab=users', meta: { hidden: true, admin: true } },
      { path: 'admin/stalls/:id', name: 'AdminStallDishes', component: () => import('@/views/admin/AdminStallDishes.vue'), meta: { hidden: true, title: '档口菜单', admin: true } },
      { path: 'admin/cs-center', name: 'CustomerServiceCenter', component: () => import('@/views/admin/CustomerServiceCenter.vue'), meta: { title: '客服中心', icon: 'Service', admin: true } },
      { path: 'admin/notices', name: 'NoticeManage', component: () => import('@/views/admin/NoticeManage.vue'), meta: { title: '公告管理', icon: 'Notification', admin: true } },
      { path: 'admin/refunds', name: 'AdminRefunds', component: () => import('@/views/admin/AdminRefunds.vue'), meta: { title: '退款记录', icon: 'Money', admin: true } },
      { path: 'admin/dish-audits', name: 'AdminDishAudits', component: () => import('@/views/admin/AdminDishAudits.vue'), meta: { title: '菜品审核', icon: 'Dish', admin: true } },
      // 旧 AI 运营地址兼容：经验进入统一知识表，日志在客服中心抽屉查看
      { path: 'admin/ai-ops', redirect: to => ({ path: '/admin/cs-center', query: { ...to.query, tab: to.query.tab === 'audit' ? 'audit' : 'knowledge', ...(to.query.tab === 'audit' ? {} : { kind: 'EXPERIENCE' }) } }), meta: { hidden: true, admin: true } },
      { path: 'admin/banners', name: 'BannerManage', component: () => import('@/views/admin/BannerManage.vue'), meta: { title: '轮播广告', icon: 'Picture', admin: true } },
      { path: 'admin/feedback-center', name: 'FeedbackCenter', component: () => import('@/views/admin/FeedbackCenter.vue'), meta: { title: '反馈与评价', icon: 'ChatDotRound', admin: true } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { title: '个人中心', hidden: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: () => homePath() }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.public) {
    return next()
  }
  if (!getToken()) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  // 角色校验：防止学生/商家/管理员跨角色 URL 直访（侧边栏只是隐藏菜单，不是权限隔离）
  const role = getRole()
  if (to.meta.admin && role !== 'ADMIN') {
    return next({ path: homePath() })
  }
  if (to.meta.merchant && role !== 'MERCHANT') {
    return next({ path: homePath() })
  }
  // 学生专属页（客服会话）：商家/管理员访问时重定向到各自的客服页
  if (to.meta.student && role !== 'STUDENT') {
    if (role === 'MERCHANT') return next({ path: '/merchant/customer-service' })
    if (role === 'ADMIN') return next({ path: '/admin/cs-center' })
    return next({ path: homePath() })
  }
  next()
})

export default router
