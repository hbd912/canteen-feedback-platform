<template>
  <div class="ap-page profile-page">
    <div class="ap-head">
      <div>
        <h1 class="ap-title">个人中心</h1>
        <div class="ap-sub">{{ isMerchant ? '个人资料、消费账户与档口经营概览' : '个人资料、余额与账号安全' }}</div>
      </div>
    </div>

    <!-- 身份 + 余额 + 统计 -->
    <div class="ap-card summary-card">
      <div class="summary-top">
        <div class="identity">
          <el-upload :show-file-list="false" :http-request="doUploadAvatar" accept="image/*">
            <div class="profile-avatar">
              <img v-if="form.avatar" :src="form.avatar" class="avatar-img" alt="头像" />
              <div v-else class="avatar-fallback">{{ avatarText }}</div>
              <div class="avatar-mask">换头像</div>
            </div>
          </el-upload>
          <div class="identity-info">
            <div class="identity-name">{{ userStore.nickname }}</div>
            <div class="identity-tags">
              <span v-if="form.roleLabel" class="ap-tag">{{ form.roleLabel }}</span>
              <span v-if="form.studentNo" class="identity-no">学号 {{ form.studentNo }}</span>
            </div>
          </div>
        </div>

        <div class="balance-summary">
          <div class="balance-summary-label">账户余额</div>
          <div class="balance-summary-value">¥{{ fmt(stats.balance) }}</div>
          <button class="ap-btn ap-btn--brand ap-btn--sm" @click="openRecharge">充值</button>
        </div>
      </div>

      <div class="summary-stats">
        <div class="stat-group">
          <div v-if="isMerchant" class="stat-group-title">个人消费</div>
          <div class="mini-stats">
            <div class="mini-stat">
              <span>{{ isMerchant ? '消费订单' : '总订单' }}</span>
              <b>{{ stats.orderCount ?? 0 }}</b>
            </div>
            <div class="mini-stat">
              <span>总消费</span>
              <b>¥{{ fmt(stats.totalSpent) }}</b>
            </div>
            <div class="mini-stat">
              <span>已完成</span>
              <b>{{ stats.completedCount ?? 0 }}</b>
            </div>
          </div>
        </div>

        <div v-if="isMerchant" class="stat-group stat-group--biz">
          <div class="stat-group-title">档口经营</div>
          <div class="mini-stats">
            <div class="mini-stat">
              <span>累计订单</span>
              <b>{{ stats.businessOrderCount ?? 0 }}</b>
            </div>
            <div class="mini-stat">
              <span>累计收益</span>
              <b>¥{{ fmt(stats.businessIncome) }}</b>
            </div>
            <div class="mini-stat">
              <span>可提现余额</span>
              <b>¥{{ fmt(stats.available) }}</b>
            </div>
            <div class="mini-stat">
              <span>本月收益</span>
              <b>¥{{ fmt(stats.monthIncome) }}</b>
            </div>
            <div class="mini-stat">
              <span>客户数</span>
              <b>{{ stats.businessCustomerCount ?? 0 }}</b>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 设置中心：左侧导航 + 右侧面板 -->
    <div class="profile-layout">
      <aside class="ap-card nav-card">
        <button
          v-for="m in sideMenus"
          :key="m.key"
          class="nav-row"
          :class="{ active: activeMenu === m.key, 'nav-row--logout': m.key === 'logout' }"
          @click="onMenuClick(m)"
        >
          <span class="nav-label">{{ m.label }}</span>
          <el-icon v-if="m.key !== 'logout'" :size="14" class="nav-arrow"><ArrowRight /></el-icon>
        </button>
      </aside>

      <section class="ap-card panel-card">
        <!-- 基本资料 -->
        <div v-show="activeMenu === 'base'" class="panel-section">
          <p class="panel-lead">姓名、邮箱与电话将用于订单通知和安全验证。</p>
          <el-form label-position="top" class="profile-form">
            <el-form-item label="姓名">
              <el-input v-model="form.realName" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="绑定邮箱">
              <el-input v-model="form.email" maxlength="100" placeholder="用于接收食堂通知与安全验证" />
            </el-form-item>
            <el-form-item label="绑定电话">
              <el-input v-model="form.phone" maxlength="20" placeholder="手机号或座机" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 余额管理 -->
        <div v-show="activeMenu === 'balance'" class="panel-section">
          <div class="balance-overview">
            <div class="balance-copy">
              <div class="panel-label">当前余额</div>
              <div class="balance-big">¥{{ fmt(stats.balance) }}</div>
              <div class="balance-desc">下单时直接从余额扣款，充值跳转支付宝沙盒收银台完成。</div>
            </div>
            <button class="ap-btn ap-btn--brand" @click="openRecharge">去充值</button>
          </div>

          <div class="balance-meta">
            已消费 <b>¥{{ fmt(stats.totalSpent) }}</b> · 已完成订单 <b>{{ stats.completedCount ?? 0 }}</b> 单
          </div>

          <div class="ap-section-head">
            <span class="ap-section-title">支付明细</span>
            <span class="ap-section-hint">最近 100 条</span>
          </div>
          <div v-if="!transactions.length" class="tx-empty">暂无明细</div>
          <div v-else class="ap-list">
            <div v-for="t in transactions" :key="t.id" class="ap-row">
              <div class="ap-row-main">
                <div class="ap-row-title">
                  {{ t.typeLabel }}<span v-if="t.remark || t.refNo" class="tx-remark"> · {{ t.remark || t.refNo }}</span>
                </div>
                <div class="ap-row-sub">{{ t.createTime }}</div>
              </div>
              <span class="ap-row-amount" :class="Number(t.amount) >= 0 ? 'pos' : 'neg'">
                {{ Number(t.amount) >= 0 ? '+' : '' }}{{ fmt(t.amount) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 账号安全 -->
        <div v-show="activeMenu === 'security'" class="panel-section">
          <p class="panel-lead">修改登录密码，查看当前绑定的邮箱与电话。</p>
          <div class="ap-list">
            <div class="ap-row">
              <div class="ap-row-main">
                <div class="ap-row-title">登录密码</div>
                <div class="ap-row-sub">定期更换密码可以让账号更安全</div>
              </div>
              <el-button type="primary" plain @click="openPwdDialog">修改密码</el-button>
            </div>
            <div class="ap-row">
              <div class="ap-row-main">
                <div class="ap-row-title">绑定邮箱</div>
                <div class="ap-row-sub">{{ form.email ? '已绑定：' + form.email : '未绑定，绑定后可用于安全验证' }}</div>
              </div>
              <div class="sec-actions">
                <span class="ap-tag" :class="{ 'ap-tag--green': form.email }">{{ form.email ? '已绑定' : '未绑定' }}</span>
                <el-button link @click="activeMenu = 'base'">{{ form.email ? '修改邮箱' : '去绑定' }}</el-button>
              </div>
            </div>
            <div class="ap-row">
              <div class="ap-row-main">
                <div class="ap-row-title">绑定电话</div>
                <div class="ap-row-sub">{{ form.phone ? '已绑定：' + form.phone : '未绑定，绑定后可用于安全验证' }}</div>
              </div>
              <span class="ap-tag" :class="{ 'ap-tag--green': form.phone }">{{ form.phone ? '已绑定' : '未绑定' }}</span>
            </div>
          </div>
        </div>

        <!-- 我的证照（商家） -->
        <div v-show="activeMenu === 'cert'" class="panel-section">
          <p class="panel-lead">证照仅用于食堂管理处审核，不会对外展示。</p>
          <div class="cert-grid">
            <div class="cert-item">
              <div class="cert-title">健康证</div>
              <el-upload :show-file-list="false" :http-request="opt => doUploadCert(opt, 'health')" accept="image/*">
                <div class="cert-box">
                  <img v-if="cert.healthCertUrl" :src="cert.healthCertUrl" class="cert-img" alt="健康证" />
                  <div v-else class="cert-empty">
                    <div class="cert-upload-icon">
                      <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                        <polyline points="17 8 12 3 7 8"/>
                        <line x1="12" y1="3" x2="12" y2="15"/>
                      </svg>
                    </div>
                    <span class="cert-upload-text">上传健康证</span>
                    <span class="cert-upload-hint">支持 JPG / PNG，建议清晰完整</span>
                  </div>
                  <div class="cert-mask">点击更换</div>
                </div>
              </el-upload>
            </div>
            <div class="cert-item">
              <div class="cert-title">营业执照</div>
              <el-upload :show-file-list="false" :http-request="opt => doUploadCert(opt, 'license')" accept="image/*">
                <div class="cert-box">
                  <img v-if="cert.licenseUrl" :src="cert.licenseUrl" class="cert-img" alt="营业执照" />
                  <div v-else class="cert-empty">
                    <div class="cert-upload-icon">
                      <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                        <polyline points="17 8 12 3 7 8"/>
                        <line x1="12" y1="3" x2="12" y2="15"/>
                      </svg>
                    </div>
                    <span class="cert-upload-text">上传营业执照</span>
                    <span class="cert-upload-hint">支持 JPG / PNG，建议清晰完整</span>
                  </div>
                  <div class="cert-mask">点击更换</div>
                </div>
              </el-upload>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="440px">
      <el-form label-position="top">
        <el-form-item label="原密码">
          <el-input v-model="pwd.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码（6-20位）">
          <el-input v-model="pwd.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwd.confirm" type="password" show-password />
        </el-form-item>
        <el-form-item :label="`验证码（发送至 ${verifyTarget || '绑定的邮箱/电话'}）`">
          <div class="code-row">
            <el-input v-model="pwd.verifyCode" maxlength="6" placeholder="6 位验证码" />
            <el-button :disabled="countdown > 0" :loading="sending" class="code-btn" @click="sendCode">
              {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingPwd" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 余额充值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="余额充值" width="420px">
      <div class="recharge-box">
        <div class="recharge-amount-label">充值金额（元）</div>
        <el-input v-model="rechargeAmount" type="number" :min="0" placeholder="请输入充值金额" size="large">
          <template #prefix>¥</template>
        </el-input>
        <div class="recharge-quick">
          <el-button v-for="n in [10, 20, 50, 100]" :key="n" @click="rechargeAmount = String(n)">¥{{ n }}</el-button>
        </div>
        <p class="recharge-tip">点击确认后将打开支付宝沙盒收银台，用沙盒买家账号付款后余额自动到账。</p>
      </div>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { updateProfile, changePassword as changePwdApi, getAccountSummary } from '@/api/auth'
import { rechargeBalance, getRechargeStatus, listTransactions } from '@/api/balance'
import { uploadImage } from '@/api/sales'
import request from '@/api/request'

const router = useRouter()
const userStore = useUserStore()
const isMerchant = computed(() => userStore.isMerchant)
const roleKey = computed(() => (isMerchant.value ? 'merchant' : userStore.isAdmin ? 'admin' : 'student'))
const avatarText = computed(() => (userStore.nickname || 'U').trim().slice(0, 1).toUpperCase())

const activeMenu = ref('base')
const stats = reactive({ balance: 0, orderCount: 0, totalSpent: 0, completedCount: 0 })

const rechargeVisible = ref(false)
const rechargeAmount = ref('')
const recharging = ref(false)
const transactions = ref([])

// 充值支付结果轮询（沙盒付款后主动查单确认到账）
let rechargeTimer = null
let rechargeTicks = 0
const RECHARGE_POLL_MAX = 30

const menuBase = [
  { key: 'base', label: '编辑资料' },
  { key: 'balance', label: '余额管理' },
  { key: 'orders', label: '我的订单' },
  { key: 'security', label: '修改密码' },
  { key: 'logout', label: '退出登录' }
]

const sideMenus = computed(() => {
  const menus = [...menuBase]
  if (isMerchant.value) {
    menus.splice(menus.length - 1, 0, { key: 'cert', label: '我的证照' })
  }
  return menus
})

const saving = ref(false)
const changingPwd = ref(false)
const form = reactive({
  avatar: '',
  realName: '',
  email: '',
  phone: '',
  roleLabel: '',
  studentNo: ''
})
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '', verifyCode: '' })
const cert = reactive({ healthCertUrl: '', licenseUrl: '' })
const pwdVisible = ref(false)
const countdown = ref(0)
const sending = ref(false)
const verifyTarget = computed(() => form.email || form.phone || '')

function fmt(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

function fillForm(profile) {
  Object.assign(form, {
    avatar: profile.avatar || '',
    realName: profile.realName || '',
    email: profile.email || '',
    phone: profile.phone || '',
    roleLabel: profile.roleLabel || '',
    studentNo: profile.studentNo || ''
  })
}

async function loadProfile() {
  const res = await request.get('/auth/profile')
  fillForm(res.data)
  userStore.userInfo = res.data
  localStorage.setItem('canteen_user', JSON.stringify(res.data))
}

async function loadSummary() {
  try {
    const res = await getAccountSummary()
    Object.assign(stats, res.data || {})
  } catch {
    /* 忽略统计加载失败 */
  }
}

async function loadCert() {
  if (!isMerchant.value) return
  const res = await request.get('/merchant/certificates')
  cert.healthCertUrl = res.data.healthCertUrl || ''
  cert.licenseUrl = res.data.licenseUrl || ''
}

async function loadTransactions() {
  try {
    const res = await listTransactions(100)
    transactions.value = res.data || []
  } catch {
    transactions.value = []
  }
}

function openRecharge() {
  rechargeAmount.value = ''
  rechargeVisible.value = true
}

async function submitRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!amount || amount <= 0) {
    ElMessage.warning('请输入有效的充值金额')
    return
  }
  if (amount > 10000) {
    ElMessage.warning('单次充值不能超过 10000 元')
    return
  }
  recharging.value = true
  try {
    const res = await rechargeBalance(amount)
    const vo = res.data || {}
    if (vo.paid) {
      ElMessage.success(`充值成功，余额已到账 ¥${Number(vo.amount).toFixed(2)}`)
    } else if (vo.payUrl) {
      window.open(vo.payUrl, '_blank', 'noopener')
      ElMessage.info('已打开支付宝沙盒收银台，付款完成后余额将自动到账')
      pollRecharge(vo.payNo)
    }
    rechargeVisible.value = false
    loadSummary()
    loadTransactions()
  } catch {
    // 请求层已统一提示
  } finally {
    recharging.value = false
  }
}

/** 轮询充值单状态：本地开发收不到支付宝异步通知，靠主动查单确认付款结果 */
function pollRecharge(payNo) {
  clearRechargePoll()
  rechargeTicks = 0
  rechargeTimer = setInterval(async () => {
    rechargeTicks += 1
    if (rechargeTicks > RECHARGE_POLL_MAX) {
      clearRechargePoll()
      ElMessage.info('支付结果确认超时，可稍后刷新查看余额')
      loadSummary()
      loadTransactions()
      return
    }
    try {
      const res = await getRechargeStatus(payNo)
      const v = res.data || {}
      if (v.paid) {
        clearRechargePoll()
        ElMessage.success(`充值成功，余额已到账 ¥${Number(v.amount).toFixed(2)}`)
        loadSummary()
        loadTransactions()
      }
    } catch {
      clearRechargePoll()
    }
  }, 3000)
}

function clearRechargePoll() {
  if (rechargeTimer) {
    clearInterval(rechargeTimer)
    rechargeTimer = null
  }
  rechargeTicks = 0
}

function onMenuClick(m) {
  if (m.key === 'orders') {
    goOrders()
    return
  }
  if (m.key === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '退出确认', { type: 'warning' })
      .then(() => {
        userStore.logout()
        router.push('/login')
      })
      .catch(() => {})
    return
  }
  activeMenu.value = m.key
}

function goOrders() {
  router.push('/my/orders')
}

async function doUploadAvatar(option) {
  const res = await uploadImage(option.file)
  form.avatar = res.data
  await updateProfile({ avatar: form.avatar })
  ElMessage.success('头像已更新')
  loadProfile()
}

async function saveProfile() {
  if (!form.realName.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  saving.value = true
  try {
    const body = { realName: form.realName.trim(), email: form.email, phone: form.phone }
    const res = await updateProfile(body)
    fillForm(res.data)
    userStore.userInfo = res.data
    localStorage.setItem('canteen_user', JSON.stringify(res.data))
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

function openPwdDialog() {
  pwd.oldPassword = pwd.newPassword = pwd.confirm = pwd.verifyCode = ''
  pwdVisible.value = true
}

async function sendCode() {
  if (!verifyTarget.value) {
    ElMessage.warning('请先在「基本资料」里绑定邮箱或电话')
    return
  }
  sending.value = true
  try {
    const res = await request.post('/auth/verify-code', { target: verifyTarget.value })
    ElMessage.success('验证码已发送（演示模式）：' + res.data)
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally {
    sending.value = false
  }
}

async function savePassword() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwd.newPassword.length < 6 || pwd.newPassword.length > 20) {
    ElMessage.warning('新密码长度为 6-20 位')
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (!pwd.verifyCode.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  changingPwd.value = true
  try {
    await changePwdApi({
      oldPassword: pwd.oldPassword,
      newPassword: pwd.newPassword,
      verifyCode: pwd.verifyCode.trim()
    })
    ElMessage.success('密码已修改')
    pwdVisible.value = false
  } finally {
    changingPwd.value = false
  }
}

async function doUploadCert(option, type) {
  const res = await uploadImage(option.file)
  if (type === 'health') cert.healthCertUrl = res.data
  else cert.licenseUrl = res.data
  await request.put('/merchant/certificates', {
    healthCertUrl: cert.healthCertUrl,
    licenseUrl: cert.licenseUrl
  })
  ElMessage.success('证照已更新')
}

onMounted(() => {
  loadProfile()
  loadSummary()
  loadCert()
  loadTransactions()
})

// keep-alive 缓存后回访刷新个人资料
onActivated(() => {
  loadProfile()
  loadSummary()
  loadCert()
  loadTransactions()
})

onBeforeUnmount(clearRechargePoll)
</script>

<style scoped>
.profile-page {
  max-width: 1240px;
}

/* 身份摘要 */
.summary-card {
  padding: 24px 28px;
}

.summary-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.identity {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.profile-avatar {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: #f5f5f7;
  cursor: pointer;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1d1d1f;
  color: #fff;
  font-size: 24px;
  font-weight: 600;
}

.avatar-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 2px 0;
  font-size: 10.5px;
  color: #fff;
  text-align: center;
  background: rgba(0, 0, 0, 0.52);
  opacity: 0;
  transition: opacity 0.18s var(--ease-apple);
}

.profile-avatar:hover .avatar-mask {
  opacity: 1;
}

.identity-name {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-1);
}

.identity-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.identity-no {
  font-size: 12px;
  color: var(--text-3);
}

.balance-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.balance-summary-label {
  font-size: 12.5px;
  color: var(--text-2);
}

.balance-summary-value {
  font-size: 32px;
  font-weight: 650;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.balance-summary .ap-btn {
  margin-top: 4px;
}

.summary-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 28px 48px;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

.stat-group {
  flex: 1;
  min-width: 240px;
}

.stat-group--biz {
  flex: 1.6;
}

.stat-group-title {
  margin-bottom: 10px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-3);
}

.mini-stats {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px 30px;
}

.mini-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 72px;
}

.mini-stat span {
  font-size: 13px;
  color: var(--text-2);
}

.mini-stat b {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

/* 两栏布局 */
.profile-layout {
  display: grid;
  grid-template-columns: 228px minmax(0, 1fr);
  align-items: start;
  gap: 16px;
  margin-top: 16px;
}

.nav-card {
  padding: 8px;
  display: flex;
  flex-direction: column;
}

.nav-row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 11px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-2);
  text-align: left;
  cursor: pointer;
  transition: background 0.16s var(--ease-apple), color 0.16s var(--ease-apple);
}

.nav-row:hover {
  background: #f7f7f9;
  color: var(--text-1);
}

.nav-row.active {
  background: #f5f5f7;
  color: var(--text-1);
  font-weight: 600;
}

.nav-row.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 2px;
  height: 16px;
  border-radius: 2px;
  background: var(--brand);
  transform: translateY(-50%);
}

.nav-row--logout {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  border-radius: 0 0 10px 10px;
}

.nav-arrow {
  color: var(--text-3);
}

.panel-card {
  min-height: 420px;
  padding: 26px 28px;
}

.panel-section {
  max-width: 820px;
}

.panel-lead {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}

.profile-form {
  max-width: 440px;
}

/* 余额 */
.balance-overview {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
}

.balance-copy {
  min-width: 0;
}

.panel-label {
  font-size: 12.5px;
  color: var(--text-2);
}

.balance-big {
  margin-top: 8px;
  font-size: 34px;
  font-weight: 650;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.balance-desc {
  max-width: 420px;
  margin-top: 8px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-3);
}

.balance-meta {
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-2);
}

.balance-meta b {
  color: var(--text-1);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.ap-section-head {
  margin-top: 26px;
}

.tx-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-3);
}

.tx-remark {
  font-weight: 400;
  color: var(--text-3);
}

.ap-row-amount {
  font-size: 14px;
}

/* 账号安全 */
.sec-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 证照 */
.cert-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  max-width: 640px;
}

.cert-item {
  min-width: 0;
}

.cert-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}

.cert-box {
  position: relative;
  width: 100%;
  height: 190px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--surface-2);
  cursor: pointer;
  transition: border-color 0.18s var(--ease-apple), box-shadow 0.18s var(--ease-apple);
}

.cert-box:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.cert-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cert-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-3);
}

.cert-upload-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #f5f5f7;
  color: var(--text-2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cert-upload-text {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-2);
}

.cert-upload-hint {
  font-size: 11.5px;
  color: var(--text-3);
}

.cert-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 5px 0;
  font-size: 12px;
  color: #fff;
  text-align: center;
  background: rgba(0, 0, 0, 0.5);
  opacity: 0;
  transition: opacity 0.18s var(--ease-apple);
}

.cert-box:hover .cert-mask {
  opacity: 1;
}

/* 弹窗 */
.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-btn {
  width: 120px;
  flex-shrink: 0;
}

.recharge-box {
  padding: 4px 2px;
}

.recharge-amount-label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-2);
}

.recharge-quick {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.recharge-quick .el-button {
  flex: 1;
}

.recharge-tip {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-3);
}

@media (max-width: 980px) {
  .summary-top {
    flex-direction: column;
    align-items: flex-start;
  }

  .balance-summary {
    align-items: flex-start;
    text-align: left;
  }

  .profile-layout {
    grid-template-columns: 1fr;
  }

  .nav-card {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 2px;
  }

  .nav-row {
    width: auto;
    padding: 8px 14px;
  }

  .nav-row.active::before {
    display: none;
  }

  .nav-row--logout {
    margin-top: 0;
    padding-top: 8px;
    border-top: none;
    border-radius: 10px;
  }

  .panel-card {
    min-height: 0;
  }
}

@media (max-width: 720px) {
  .summary-card {
    padding: 20px;
  }

  .panel-card {
    padding: 20px;
  }

  .cert-grid {
    grid-template-columns: 1fr;
  }

  .summary-stats {
    flex-direction: column;
    gap: 20px;
  }
}
</style>
