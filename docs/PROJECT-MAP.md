# 校园食堂反馈系统 · 项目地图（Agent 交接版）

> **生成时间**：2026-11-13
> **当前分支**：`feature/ai-agent-ops`（最新提交 `f65a4fe`）
> **运行状态**：后端 8080 ✅ / 前端 5173 ✅ / 工作区干净（0 未提交）
> **用途**：给下一个接手的 Agent 快速掌握「做了什么 / 没做什么 / 有什么坑」，避免重复调研。

---

## 0. 30 秒速览

校园食堂「点餐 + 评价 + 反馈 + 客服 + AI 运营」一体化系统，三角色（学生 / 商家 / 管理员）。

```
canteen-feedback/
├── canteen-backend/    Spring Boot 3.5.3 + MyBatis-Plus + MySQL8(canteen_feedback) + JWT，API 前缀 /api
├── canteen-frontend/   Vue3 <script setup> + Vite + Element Plus + Pinia + ECharts，端口 5173
├── db/                 手工 SQL 补丁（非 Flyway）
└── docs/               设计/交接文档
```

**AI 基础设施已相当完整**（不是从零开始）：
| 能力 | 类 | 模型 |
|---|---|---|
| 多模型路由 | `agent/MultiModelRouter` | BRAIN→智谱 glm-4-flash；FAST→硅基 Qwen2.5-7B；REASONING→硅基 DeepSeek-R1-8B；VISION→Qwen3-VL-8B |
| 食堂小助手（工具调用） | `agent/AgentOrchestrator` + `AgentTools` | glm-4-flash |
| 商家客服/评价回复 | `agent/MerchantReplyGenerator` | 硅基 Qwen2.5-7B，temp 0.9 |
| 商家待办 AI 代理 | `agent/MerchantTodoAgent` | 规则执行 + LLM 总结 |
| 经营诊断（下架建议） | `agent/MerchantOperationAdvisor` | 规则判定 + LLM 总结 |
| 内容安全审核 | `agent/ContentModerationService` | 本地词库 + 硅基语义审核 |
| 大脑事件路由 | `service/AiBrainService` | 规则优先，模糊才 LLM |

---

## 1. 启动与验证（照抄即可）

### 后端（**必须清掉端口环境变量**，否则 WorkBuddy 的 `SERVER__PORT` 会劫持 Spring 端口）

```bash
cd canteen-backend
env -u SERVER__PORT -u SERVER_PORT mvn -o spring-boot:run
```

- 端口：8080，context-path `/api`
- 迁移：启动时 `DatabaseMigrator` 自动执行 `src/main/resources/db/migration/V*.sql` 中未跑过的
- 当前已执行到 **V23**

### 前端

```bash
cd canteen-frontend
npm run dev     # 5173，HMR
```

### 账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 学生 | `student01` ~ `student60`（id 3~62） | 123456 |
| 老商家 | `100012` ~ `100023` | 123456 |
| 新商家 | `100024` ~ `100031`（档口 13~20） | 123456 |
| 管理员 | `admin` / `manager` | admin123 |

### 编译验证（**坑**）

```bash
mvn -o clean compile > /tmp/compile.log 2>&1; echo "EXIT=$?"; grep -a "BUILD" /tmp/compile.log
```

> ⚠️ 不要用 `mvn ... | tail` 判断成功，管道会把退出码吞掉，曾因此漏掉 import 错误。

---

## 2. Git 状态（重要）

```
feature/ai-agent-ops     ← 当前分支，领先 main 1 个提交（f65a4fe）
main                     ← 已合并 feature/merchant-customer-service（ed14c2b）
```

- `main` 已包含：余额支付体系、部分退款、商家待办聚合、消息通知重设计（b2dc18b）
- `feature/ai-agent-ops` 已包含：客服回复优化、商家待办 AI 代理、后台 AI、内容审核（f65a4fe）
- **未 push 到 origin**（origin/main 落后）
- 工作区干净，可直接在 `feature/ai-agent-ops` 继续开发

---

## 3. 已完成功能地图（按端）

### 学生端
- 浏览菜品/档口/美食探索、购物车（跨档口）、下单
- **下单直接扣账户余额**（不走收银台）；余额不足 400「账户余额不足，请先充值」
- 个人中心：余额卡（白色）、**支付明细**（充值紫/消费灰/退款绿）
- 充值：走**支付宝沙盒收银台**（`CANTEEN_PAY_CHANNEL` 未设置=alipay），前端 3 秒轮询状态 + 后端主动查单到账
- 我的订单：状态机 待支付→待取餐→已完成；**部分退款**（按菜品勾选，可全选）
- 评价：提交后过内容审核，违规自动屏蔽 + 警告
- 客服：与档口会话（SSE 秒回）
- 消息铃铛：出餐完成/退款结果/商家回复/点赞回复/文明提醒

### 商家端
- 待办：实时聚合（新订单/退款/客服未读/待回复评价/整改/差评预警），点击跳转并**闪烁高亮一次**
- **待办页「✨ AI 一键处理」**：批量完成出餐 + AI 回复客服 + AI 回复评价（退款跳过）
- 订单处理（待取餐→已完成）
- 退款处理（按菜品部分退款，列表显示退的是哪几道）
- 客服中心（AI 自动代回可开关、人设、快捷回复、AI 帮我想）
- 学生评价（AI 回复 / 手动回复）
- 经营 AI（诊断下架建议、AI 助手问答）
- 菜品/档口管理、证照、销售看板

### 管理员端
- 轮播/广告、商家管理、评价审核、留言（学生留言/商家留言/评价审核三合一页）
- **商家留言页每行「提醒商家」**：AI 生成提醒文案 → 发 ADMIN_NOTICE 通知给商家
- **公告管理「✨ AI 生成」**：输入主题 → AI 生成标题+正文
- 订单/退款/用户/客服质检/AI 运营/审计日志
- 内容审核：评价/留言自动屏蔽 + 警告（在提交时触发，不在后台页）

### AI 基础设施（已有，可复用）
- `MultiModelRouter.complete(role, system, user, maxTokens[, temperature])` — 纯文本补全，跨供应商降级
- `MultiModelRouter.completeVision(system, user, imageDataUrl, maxTokens)` — 视觉
- `LlmClient.chat / chatStream` — 原生 OpenAI 协议（工具调用/流式）
- `AgentOrchestrator` — 多轮工具调用编排 + `sanitize()`（package-private，仅 agent 包可用）
- `AgentTools` — search_dishes / list_stalls / get_rankings / add_to_cart / my_orders / my_balance 等
- `NotificationService` — `notify / notifyMerchant / notifyAdmins`，已支持 targetUrl + targetId

---

## 4. 本轮 AI 接入改动清单（f65a4fe，文件级）

### 后端新增
| 文件 | 作用 |
|---|---|
| `agent/MerchantTodoAgent.java` | 商家待办 AI 一键代理：订单 DONE / 客服 sendAiReply / 评价 replyReview("AI") / 退款计数跳过，返回报告 |
| `agent/ContentModerationService.java` | 内容审核：本地敏感词库（约 30 词）+ 硅基语义审核，模型失败放行 |
| `agent/ModerationResult.java` | 审核结果 record（flagged/category/reason） |
| `vo/AiProcessReportVO.java` | 代理报告（orderDone/csReplied/reviewReplied/refundSkipped/summary） |
| `controller/AdminAiController.java` | `POST /admin/ai/merchant-notify`、`POST /admin/ai/notice-generate` |

### 后端修改
| 文件 | 改动 |
|---|---|
| `agent/MultiModelRouter.java` | 新增 `complete(..., double temperature)` 重载；`call` 链透传温度 |
| `agent/MerchantReplyGenerator.java` | 改走 `MultiModelRouter.FAST`（原 LlmClient/glm-4-flash）；SESSION prompt 加防重复/答非所问/抱怨道歉；温度 0.9 |
| `service/CustomerServiceService(+Impl)` | 新增 `boolean sendAiReply(sessionId)`：AI 代回不沉淀人工经验 |
| `service/impl/ReviewServiceImpl.java` | `submitReview` 接内容审核，违规 `STATUS_BLOCKED` + `TYPE_WARN` 通知 |
| `service/impl/FeedbackServiceImpl.java` | `createFeedback` 接审核，违规发 `TYPE_WARN`（留言保留供管理员查看） |
| `entity/Notification.java` | 加 `TYPE_WARN`、`TYPE_ADMIN_NOTICE` |
| `controller/MerchantController.java` | 注入 `MerchantTodoAgent`，加 `POST /merchant/todos/ai-process` |

### 前端新增/修改
| 文件 | 改动 |
|---|---|
| `views/merchant/MerchantTodos.vue` | 「✨ AI 一键处理」按钮 + 结果弹窗（summary + 4 项统计） |
| `views/merchant/MerchantCustomerService.vue` | 客服气泡 `.msg-self .msg-bubble` 橙色渐变→白色，文字改深色 |
| `views/CustomerService.vue` | 同上（学生端） |
| `views/admin/MerchantMessageManage.vue` | 新增「提醒商家」列 + 弹窗（主题 → AI 生成并发送） |
| `views/admin/NoticeManage.vue` | 公告弹窗加「✨ AI 生成」区（主题输入 → 填充标题正文） |
| `views/Layout.vue` | 铃铛 typeIcon/typeLabel 补 WARN(⚠️文明提醒) / ADMIN_NOTICE(📣管理员提醒) |
| `api/admin.js` | `merchantNotify(stallId, topic)`、`noticeGenerate(topic)` |

### 实测记录（已验证通过）
- 脏话评价（dishId=77）→ `dish_review.status=2` 屏蔽 + 学生收 `WARN 你的评价因「包含不当用语」被系统屏蔽…`
- `POST /admin/ai/merchant-notify?stallId=1&topic=学生反馈出餐慢` → 生成「尊敬的「川味小炒」商家：我们收到部分学生反馈出餐速度较慢，建议优化备餐流程…」，商家收到 `ADMIN_NOTICE`
- `POST /admin/ai/notice-generate?topic=食堂开展饮食卫生检查` → 标题「食堂将进行饮食卫生专项检查」+ 正文
- `POST /merchant/todos/ai-process`（100012）→ `orderDone=73, csReplied=0, reviewReplied=0, refundSkipped=0`，总结「今天AI帮我们处理了73单出餐…」

---

## 5. 未完成 / 待办（按优先级）

### P0 — 用户明确提过、但还没做
1. **「管理员提醒 → 超时未处理 → 自动升级警告 → 处理后自动发公告」完整闭环**
   - 现状：`AdminAiController.merchantNotify` 只发一次通知，**没有任何状态跟踪**
   - 缺：`admin_merchant_notice` 表（stallId/content/status/提醒次数/最后提醒时间/是否升级警告）
   - 缺：定时任务扫描「超过 N 小时未处理」→ 再发警告 / 升级管理员
   - 缺：商家「已处理」按钮 → 回写状态 → 触发 AI 生成公告（可选）
   - 相关入口：`AdminAiController`、`MerchantController`、`Layout.vue`(铃铛)

2. **AI 一键代理无二次确认 / 无撤销**
   - 现状：点一下直接批量改 73 笔订单为 DONE 并给学生发「出餐完成」通知
   - 缺：前端二次确认弹窗（显示将处理多少单）+ 后端限制（如只处理 24h 内 / 单次上限）
   - 建议：`MerchantTodos.vue` 加 `ElMessageBox.confirm`，后端 `MerchantTodoAgent` 加 `since` 过滤

### P1 — 明显的体验/一致性问题
3. **内容审核同步跑在事务里**：`ReviewServiceImpl.submitReview` 是 `@Transactional`，内部同步调模型（1~2s，超时 30s），会拖长事务。建议审核移到事务外或异步（`@Async` + 事件）。
4. **反馈违规只警告不标记**：`Feedback` 表没有 `blocked` 字段，违规留言仍进管理员列表。建议加字段或至少在 VO 里带 `moderationFlag`。
5. **客服消息未审核**：`CustomerServiceMessage` 提交时没有过 `ContentModerationService`，学生可在客服里骂人不被拦。
6. **公告 AI 生成无审核**：AI 生成的标题/正文直接填表单，管理员可能不看就发布。建议前端标「AI 生成，请核对」。

### P2 — 可延后
7. 管理员端缺「AI 处理学生留言」按钮（生成回复建议），目前只有手动回复。
8. 商家经营 AI（`MerchantOperationAdvisor`）只做下架建议，未接入"一键采纳全部"。
9. 消息铃铛无分类筛选 / 无「全部已读」之外的批量操作。
10. `docs/` 下旧文档（`智能体接入进度.md` 等）与现状有出入，可合并。

---

## 6. 风险清单（接手必读）

### 🔴 高风险
| # | 风险 | 位置 | 建议 |
|---|---|---|---|
| R1 | **AI 一键代理会真实批量改订单状态**，误点即 73 单变「已完成」并通知学生，不可撤销 | `MerchantTodoAgent.process()` | 加二次确认 + 时间窗过滤 + 单次上限 |
| R2 | **API Key 明文写在配置里**（硅基 `sk-qjtmmx...`、智谱 `4bd1576d...`），仓库公开即泄露 | `application.yml` 第 53/73 行 | 上线前改环境变量，轮换 key |
| R3 | **内容审核误判直接屏蔽评价**，用户只收到警告，无申诉入口 | `ReviewServiceImpl.submitReview` | 加「误判申诉」或先待审后发布 |
| R4 | **免费模型额度耗尽会静默降级**：硅基 402 → 智谱；两家都挂 → 规则兜底/放行 | `MultiModelRouter` | 监控日志 `模型调用失败`，额度不足时 UI 提示 |

### 🟡 中风险
| # | 风险 | 位置 | 建议 |
|---|---|---|---|
| R5 | 敏感词库仅约 30 词，谐音/拼音/缩写拦不住，全靠大模型兜底 | `ContentModerationService.BLOCK_WORDS` | 词库外置到表/文件，支持热更新 |
| R6 | 订单排序 `ORDER BY o.id DESC`，历史 6 万单时深分页慢 | `mapper/OrderMapper.xml` | 加时间范围 / 游标分页 |
| R7 | `dish_sales` 是已聚合表，**禁止再 UNION dining_order_item**（会翻倍 + 4.6s 卡顿） | 统计类 Service | 所有销量/收入只查 `dish_sales` |
| R8 | keep-alive 跨账号缓存曾导致「看到别人数据」，已用 `用户ID+路径` key 修，但新增路由要检查 | `Layout.vue` | 新页面记得 `onActivated(loadData)` |
| R9 | 退款跳过 AI 代理，若商家误以为全自动会漏审 | `MerchantTodoAgent` | 前端结果弹窗已标「退款待人工」，可加强提示 |

### 🟢 低风险 / 技术债
- `AgentOrchestrator.sanitize()` 是 package-private，controller 包调不到（已在 `AdminAiController` 自写 `clean`），后续如复用建议改 public。
- `AdminAiController.parseNotice` 用内联 `ObjectMapper`，可提到静态常量。
- 支付宝沙盒 `notify-base-url` 为空，靠前端轮询 + 后端查单兜底，生产需配回调。
- 前端 build 有 chunk > 500kB 警告（ECharts），不影响功能。
- 测试数据遗留：商家 100012 曾有 73 笔历史待出餐订单（已被 AI 一键代理清掉），其他档口可能有类似脏数据。

---

## 7. 下一个 Agent 的上手清单

**如果要做「管理员提醒闭环」**（最可能的下一步）：
1. 新建 `V24__admin_merchant_notice.sql`：`id, stall_id, admin_id, content, level(REMIND/WARN), status(PENDING/HANDLED), remind_count, last_remind_time, handle_time, create_time`
2. 新建 `entity/AdminMerchantNotice` + `mapper` + `service`
3. 改 `AdminAiController.merchantNotify`：落库 + 发通知
4. 加定时任务（参考 `pay-job` 的 `@Scheduled`）：每小时扫 `status=PENDING 且 now-last_remind_time > 24h` → 升级 WARN 通知；`>72h` → 通知管理员
5. 商家端加「已处理」按钮 → `PUT /merchant/admin-notices/{id}/handle` → 可选触发 AI 生成公告
6. 前端：商家待办页加「管理员提醒」卡片；管理员端加提醒记录列表

**如果要做「AI 审核异步化」**：
1. 把 `moderationService.check()` 从 `submitReview` 移到事务外（`TransactionSynchronization.afterCommit` 或 `@Async`）
2. 违规后再 `updateReviewStatus(BLOCKED)` + 发通知
3. 注意：`@Async` 需要 `@EnableAsync`（检查是否已开）

**改任何 AI 相关代码前先看**：
- `agent/MultiModelRouter.java`（模型路由与降级）
- `agent/MerchantReplyGenerator.java`（prompt 风格）
- `service/impl/CustomerServiceServiceImpl.java`（客服两阶段提交：先落库推送，再事务外调模型）
- `application.yml` 第 41~81 行（agent + ai 配置）

**验证习惯**：
- 后端改完 `mvn -o clean compile`，看 `EXIT=0` + `BUILD SUCCESS`
- 前端改完 `npm run build`，看 `✓ built`
- 接口验证用 `curl + python -c "json.load"`，别只看 HTTP 200
- 改数据前先 `SELECT` 确认影响行数

---

## 8. 关键文件索引

### 后端 AI
```
agent/MultiModelRouter.java          模型路由（含 temperature 重载）
agent/MerchantReplyGenerator.java    客服/评价话术
agent/MerchantTodoAgent.java         待办一键代理
agent/ContentModerationService.java  内容审核
agent/AgentOrchestrator.java         食堂小助手编排（sanitize 在此）
agent/AgentTools.java                小助手工具集
service/AiBrainService.java          事件大脑（规则优先）
```

### 后端业务
```
service/impl/CartServiceImpl.java       下单/余额扣款 balancePay
service/impl/RefundServiceImpl.java     部分退款/余额回补/库存回补
service/impl/OrderServiceImpl.java      订单状态机 updateStatus
service/impl/CustomerServiceServiceImpl.java  客服（sendMessage/sendAiReply）
service/impl/NotificationServiceImpl.java     通知（notify/notifyMerchant）
controller/AdminAiController.java        后台 AI 接口
controller/MerchantController.java      商家接口（含 todos/ai-process）
```

### 前端
```
views/Layout.vue                        布局/铃铛/通知面板
views/merchant/MerchantTodos.vue       待办 + AI 一键处理
views/merchant/MerchantCustomerService.vue  商家客服（白气泡）
views/CustomerService.vue               学生客服（白气泡）
views/admin/MerchantMessageManage.vue  商家留言 + 提醒商家
views/admin/NoticeManage.vue           公告 + AI 生成
views/MyOrders.vue                     我的订单 + 部分退款
views/Profile.vue                      余额 + 支付明细
```

### 数据库
```
db/migration/V20__balance_recharge.sql
db/migration/V21__account_transaction.sql
db/migration/V22__refund_partial_items.sql
db/migration/V23__notification_target.sql
```

---

## 9. 一句话交接

> 系统功能已完整（点餐/评价/反馈/客服/支付/退款/通知/AI 运营），**AI 不是摆设**：客服走硅基 Qwen2.5-7B、待办可一键代理、后台可 AI 生成提醒与公告、评价/留言有内容审核。
> **最该补的是「管理员提醒的超时升级闭环」和「AI 批量操作的安全闸门」**，其余是体验优化。
> 改代码前先读第 6 节风险清单，尤其 R1（批量改单不可撤销）和 R2（Key 明文）。
