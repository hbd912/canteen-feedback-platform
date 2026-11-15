<template>
  <div class="auth-page">
    <header class="auth-nav">
      <div class="auth-brand">
        <span class="auth-logo"><img src="/ui/emoji/title.png" alt="logo" /></span>
        <span class="auth-brand-name">小众点评</span>
      </div>
      <span class="auth-nav-note">校园食堂 · 智能反馈与运营平台</span>
    </header>

    <main class="auth-main">
      <section class="auth-story">
        <div class="auth-eyebrow">NICHE REVIEW SYSTEM</div>
        <h1 class="auth-display">
          食堂里，<br />
          每一条反馈<br />
          <span>都值得被认真对待。</span>
        </h1>
        <p class="auth-lede">
          从点餐评价到食品安全的闭环处理，AI 与真实经营数据一起，
          让食堂的每一餐都更好一点。
        </p>

        <ul class="auth-features">
          <li v-for="f in features" :key="f.title" class="auth-feature">
            <span class="feature-dot"></span>
            <span class="feature-text">
              <b>{{ f.title }}</b>
              <em>{{ f.desc }}</em>
            </span>
          </li>
        </ul>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <h2 class="auth-card-title">{{ activeTab === 'login' ? '欢迎回来' : '创建学生账号' }}</h2>
          <p class="auth-card-sub">
            {{ activeTab === 'login'
              ? '登录后可以点餐评价、收藏菜品、查看数据看板'
              : '注册后即可参与菜品评价与留言反馈' }}
          </p>

          <div class="auth-switch" role="tablist">
            <button
              type="button"
              class="auth-switch-btn"
              :class="{ active: activeTab === 'login' }"
              @click="activeTab = 'login'"
            >登录</button>
            <button
              type="button"
              class="auth-switch-btn"
              :class="{ active: activeTab === 'register' }"
              @click="activeTab = 'register'"
            >注册</button>
          </div>

          <el-form
            v-if="activeTab === 'login'"
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            size="large"
            @submit.prevent
          >
            <el-form-item label="账号" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入账号" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
              登录
            </el-button>
          </el-form>

          <el-form
            v-else
            ref="regFormRef"
            :model="regForm"
            :rules="regRules"
            label-position="top"
            size="large"
            @submit.prevent
          >
            <el-form-item label="账号" prop="username">
              <el-input v-model="regForm.username" placeholder="4-20 位字母 / 数字 / 下划线" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="regForm.password" type="password" show-password placeholder="6-20 位" />
            </el-form-item>
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="regForm.realName" placeholder="请输入真实姓名" />
            </el-form-item>
            <div class="form-row">
              <el-form-item label="学号（选填）">
                <el-input v-model="regForm.studentNo" placeholder="请输入学号" />
              </el-form-item>
              <el-form-item label="手机号（选填）">
                <el-input v-model="regForm.phone" placeholder="请输入手机号" />
              </el-form-item>
            </div>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
              创建账号
            </el-button>
          </el-form>

          <div class="demo-block">
            <div class="demo-title">演示账号 · 点击自动填入</div>
            <div class="demo-list">
              <button
                v-for="a in demoAccounts"
                :key="a.username"
                class="demo-row"
                type="button"
                @click="fillDemoAccount(a)"
              >
                <span class="demo-role">
                  <i class="demo-dot" :class="a.tone"></i>
                  {{ a.role }}
                </span>
                <span class="demo-account">{{ a.username }} / {{ a.password }}</span>
                <span class="demo-fill">填入</span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>

    <footer class="auth-footer">© 2026 小众点评系统 · 校园食堂智能运营平台</footer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { register } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const features = [
  { title: '智能点餐与评价', desc: '按档口、分类与评分浏览，一人一菜一评' },
  { title: 'AI 客服与经营助理', desc: '学生提问即时回应，商家经营数据一问即答' },
  { title: '真实数据看板', desc: '销售、口碑与食品安全风险，全部来自真实订单' }
]

const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref()
const regFormRef = ref()

const demoAccounts = [
  { role: '管理员', tone: 'admin', username: 'admin', password: 'admin123' },
  { role: '学生', tone: 'student', username: 'student01', password: '123456' },
  { role: '商家', tone: 'merchant', username: '100012', password: '123456' }
]

const loginForm = reactive({ username: 'admin', password: 'admin123' })
const regForm = reactive({ username: '', password: '', realName: '', studentNo: '', phone: '' })

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const regRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '4-20位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

function fillDemoAccount(account) {
  activeTab.value = 'login'
  loginForm.username = account.username
  loginForm.password = account.password
  loginFormRef.value?.clearValidate()
  ElMessage.success(`已填入${account.role}账号，点击登录即可`)
}

async function handleLogin() {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success(`欢迎回来，${userStore.nickname}`)
    const redirect = route.query.redirect
    if (redirect) {
      router.push(redirect)
    } else if (userStore.isMerchant) {
      router.push('/merchant/todos')
    } else if (userStore.isAdmin) {
      router.push('/admin/dashboard')
    } else {
      router.push('/dishes')
    }
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  await regFormRef.value.validate()
  loading.value = true
  try {
    await register(regForm)
    ElMessage.success('注册成功，请登录')
    loginForm.username = regForm.username
    loginForm.password = ''
    activeTab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  color: #1d1d1f;
  font-family: var(--font-sans);
  -webkit-font-smoothing: antialiased;
  background:
    radial-gradient(1100px 640px at 88% -12%, rgba(255, 138, 61, 0.16), transparent 62%),
    radial-gradient(900px 620px at -8% 108%, rgba(255, 186, 120, 0.18), transparent 58%),
    #f5f5f7;
}

/* 顶部品牌栏 */
.auth-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 44px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.auth-logo {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  overflow: hidden;
  display: flex;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
}

.auth-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auth-brand-name {
  font-size: 14.5px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.auth-nav-note {
  font-size: 12px;
  color: #a1a1a6;
  letter-spacing: 0.02em;
}

/* 主区域：叙事 + 表单 */
.auth-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(48px, 8vw, 120px);
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px 44px 44px;
}

.auth-story {
  max-width: 480px;
  animation: auth-rise 0.6s var(--ease-out) both;
}

.auth-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.24em;
  color: #a1a1a6;
}

.auth-display {
  margin: 18px 0 0;
  font-size: clamp(34px, 4.2vw, 50px);
  line-height: 1.14;
  letter-spacing: -0.035em;
  font-weight: 650;
  color: #1d1d1f;
}

.auth-display span {
  color: #86868b;
}

.auth-lede {
  margin: 22px 0 0;
  max-width: 420px;
  font-size: 14.5px;
  line-height: 1.75;
  color: #6e6e73;
}

.auth-features {
  list-style: none;
  margin: 34px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.auth-feature {
  display: flex;
  gap: 12px;
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff7a1a;
  margin-top: 7px;
  flex-shrink: 0;
}

.feature-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.feature-text b {
  font-size: 13.5px;
  font-weight: 600;
  color: #1d1d1f;
}

.feature-text em {
  font-style: normal;
  font-size: 12.5px;
  line-height: 1.5;
  color: #8e8e93;
}

/* 表单卡 */
.auth-panel {
  width: 420px;
  flex-shrink: 0;
  animation: auth-rise 0.6s 0.08s var(--ease-out) both;
}

.auth-card {
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(30px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 26px;
  padding: 30px 30px 24px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03), 0 24px 60px rgba(0, 0, 0, 0.08);
}

.auth-card-title {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.auth-card-sub {
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: #8e8e93;
}

.auth-switch {
  display: flex;
  padding: 3px;
  border-radius: 999px;
  background: #ebebef;
  margin: 20px 0 22px;
}

.auth-switch-btn {
  flex: 1;
  height: 34px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6e6e73;
  font-size: 13px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.18s var(--ease-apple), color 0.18s var(--ease-apple),
    box-shadow 0.18s var(--ease-apple), transform 0.12s var(--ease-apple);
}

.auth-switch-btn:active {
  transform: scale(0.98);
}

.auth-switch-btn.active {
  background: #fff;
  color: #1d1d1f;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
}

:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-form-item__label) {
  font-size: 12.5px;
  padding-bottom: 3px !important;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 4px;
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  background: #1d1d1f !important;
  border: none !important;
  box-shadow: none !important;
  color: #fff !important;
}

.submit-btn:hover {
  background: #333336 !important;
}

.submit-btn:active {
  transform: scale(0.985);
}

/* 演示账号 */
.demo-block {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.demo-title {
  font-size: 11.5px;
  font-weight: 550;
  letter-spacing: 0.04em;
  color: #a1a1a6;
  margin-bottom: 10px;
}

.demo-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.demo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: background-color 0.16s var(--ease-apple);
}

.demo-row:hover {
  background: rgba(0, 0, 0, 0.045);
}

.demo-role {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 64px;
  flex-shrink: 0;
  font-size: 12px;
  color: #6e6e73;
}

.demo-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34c759;
}
.demo-dot.admin { background: #ff9500; }
.demo-dot.merchant { background: #0071e3; }

.demo-account {
  flex: 1;
  font-size: 12.5px;
  color: #1d1d1f;
  font-variant-numeric: tabular-nums;
}

.demo-fill {
  font-size: 11.5px;
  font-weight: 600;
  color: #ff6a00;
  opacity: 0;
  transition: opacity 0.16s var(--ease-apple);
}

.demo-row:hover .demo-fill {
  opacity: 1;
}

.auth-footer {
  padding: 16px 44px 22px;
  text-align: center;
  font-size: 11.5px;
  color: #a1a1a6;
}

@keyframes auth-rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 960px) {
  .auth-nav {
    padding: 18px 24px;
  }
  .auth-nav-note {
    display: none;
  }
  .auth-main {
    flex-direction: column;
    gap: 34px;
    padding: 12px 24px 36px;
    align-items: stretch;
  }
  .auth-story {
    max-width: none;
  }
  .auth-display {
    font-size: 32px;
  }
  .auth-features {
    display: none;
  }
  .auth-panel {
    width: 100%;
    max-width: 460px;
    margin: 0 auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-story,
  .auth-panel {
    animation: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .auth-card {
    background: #fff;
    backdrop-filter: none;
  }
}
</style>
