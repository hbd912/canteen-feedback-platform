package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.agent.MerchantReplyGenerator;
import com.qmz.canteen.agent.SessionReply;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.CsConfigRequest;
import com.qmz.canteen.dto.QuickReplyRequest;
import com.qmz.canteen.entity.Coupon;
import com.qmz.canteen.entity.CouponOffer;
import com.qmz.canteen.entity.CustomerServiceDiscount;
import com.qmz.canteen.entity.CustomerServiceMessage;
import com.qmz.canteen.entity.CustomerServiceSession;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.MerchantCsConfig;
import com.qmz.canteen.entity.MerchantQuickReply;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.CouponMapper;
import com.qmz.canteen.mapper.CouponOfferMapper;
import com.qmz.canteen.mapper.CustomerServiceDiscountMapper;
import com.qmz.canteen.mapper.CustomerServiceMessageMapper;
import com.qmz.canteen.mapper.CustomerServiceSessionMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.MerchantCsConfigMapper;
import com.qmz.canteen.mapper.MerchantQuickReplyMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.CouponService;
import com.qmz.canteen.service.CustomerServiceService;
import com.qmz.canteen.service.CustomerServiceStreamService;
import com.qmz.canteen.service.ExperienceService;
import com.qmz.canteen.vo.CouponVO;
import com.qmz.canteen.vo.UserCouponVO;
import com.qmz.canteen.vo.CsStatsVO;
import com.qmz.canteen.vo.CustomerServiceMessageVO;
import com.qmz.canteen.vo.CustomerServiceSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceServiceImpl implements CustomerServiceService {

    /**
     * 智能代回专用线程池（同 {@link com.qmz.canteen.controller.ChatStreamController}）。
     * 一次生成 8~15 秒，绝不能跑在 Tomcat 工作线程上——否则学生发消息会同步卡住，
     * 输入框不清、sending 锁死、并发几个就占满线程。
     */
    private static final ExecutorService AI_REPLY_EXECUTOR = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "cs-ai-reply");
        t.setDaemon(true);
        return t;
    });

    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final MerchantCsConfigMapper configMapper;
    private final MerchantQuickReplyMapper quickReplyMapper;
    private final MerchantReplyGenerator replyGenerator;
    private final StallMapper stallMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final CustomerServiceDiscountMapper discountMapper;
    private final CouponMapper couponMapper;
    private final CouponOfferMapper couponOfferMapper;
    private final CouponService couponService;
    private final TransactionTemplate transactionTemplate;
    private final CustomerServiceStreamService streamService;
    private final ExperienceService experienceService;

    @Override
    public List<CustomerServiceSessionVO> listSessions() {
        List<CustomerServiceSession> sessions;
        if (UserContext.isMerchant()) {
            sessions = sessionMapper.selectByStall(UserContext.requireMerchant());
        } else if (UserContext.getUserId() != null && !UserContext.isAdmin()) {
            sessions = sessionMapper.selectByUser(UserContext.getUserId());
        } else {
            sessions = List.of();
        }
        List<CustomerServiceSessionVO> result = new ArrayList<>();
        for (CustomerServiceSession s : sessions) {
            result.add(toVO(s));
        }
        return result;
    }

    @Override
    public CustomerServiceSessionVO createOrGetSession(Long stallId, Long orderId, boolean newSession) {
        Long userId = UserContext.getUserId();
        if (userId == null || UserContext.isMerchant() || UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有学生能发起客服会话");
        }
        Stall stall = stallMapper.selectById(stallId);
        if (stall == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "档口不存在");
        }
        validateOrder(stallId, userId, orderId);

        CustomerServiceSession session = sessionMapper.selectOpenByStallAndUser(stallId, userId);
        if (session != null) {
            // 普通进入：复用当前活跃会话（多会话模式下不再强制新建）。
            // 从订单进入时更新上下文，避免一直停留在第一次咨询的旧订单。
            if (orderId != null && !orderId.equals(session.getOrderId())) {
                session.setOrderId(orderId);
                sessionMapper.updateById(session);
            }
            if (!newSession) {
                return toVO(session);
            }
            // 强制新建：把当前活跃会话归档，再开一个干净的新会话（实现「开新窗口」）。
            sessionMapper.close(session.getId());
        }

        session = new CustomerServiceSession();
        session.setStallId(stallId);
        session.setUserId(userId);
        session.setOrderId(orderId);
        session.setStudentUnread(0);
        session.setMerchantUnread(0);
        session.setStatus(CustomerServiceSession.STATUS_OPEN);
        try {
            sessionMapper.insert(session);
        } catch (DuplicateKeyException e) {
            // 两个入口同时点击时，兜底复用已创建的新会话。
            CustomerServiceSession existing = sessionMapper.selectOpenByStallAndUser(stallId, userId);
            if (existing != null) {
                return toVO(existing);
            }
            throw e;
        }

        MerchantCsConfig config = configMapper.selectByStall(stallId);
        String greeting = config == null ? null : config.getGreeting();
        if (greeting != null && !greeting.isBlank()) {
            CustomerServiceMessage greetingMessage = saveAiMessage(session.getId(), greeting.trim());
            sessionMapper.incrStudentUnread(session.getId());
            sessionMapper.updateLastMessage(session.getId(), truncate(greeting), LocalDateTime.now());
            publishSaved(session.getId(), greetingMessage, userId);
        }
        return toVO(sessionMapper.selectById(session.getId()));
    }

    @Override
    public List<CustomerServiceMessageVO> getMessages(Long sessionId) {
        requireSessionOwned(sessionId);
        List<CustomerServiceMessage> messages = messageMapper.listBySession(sessionId);
        if (UserContext.isMerchant()) {
            sessionMapper.resetMerchantUnread(sessionId);
        } else {
            sessionMapper.resetStudentUnread(sessionId);
        }
        return toMessageVOs(messages, UserContext.getUserId());
    }

    @Override
    public SseEmitter subscribe(Long sessionId) {
        requireSessionOwned(sessionId);
        return streamService.subscribe(sessionId);
    }

    @Override
    public List<CustomerServiceMessageVO> sendMessage(Long sessionId, String content, Long dishId) {
        CustomerServiceSession session = requireSessionOwned(sessionId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "消息内容不能为空");
        }
        if (text.length() > 1000) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "消息内容不能超过1000字");
        }
        validateDish(session.getStallId(), dishId);

        boolean merchant = UserContext.isMerchant();
        Long userId = UserContext.getUserId();
        Long finalDishId = dishId;

        // 第一阶段只写用户消息并立即提交，绝不在数据库事务里等待 LLM。
        CustomerServiceMessage[] savedUserMessage = new CustomerServiceMessage[1];
        transactionTemplate.executeWithoutResult(status -> {
            CustomerServiceMessage msg = new CustomerServiceMessage();
            msg.setSessionId(sessionId);
            msg.setSenderType(merchant ? CustomerServiceMessage.SENDER_MERCHANT : CustomerServiceMessage.SENDER_STUDENT);
            msg.setSenderId(userId);
            msg.setContent(text);
            msg.setDishId(finalDishId);
            msg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(msg);
            savedUserMessage[0] = msg;

            if (merchant) {
                sessionMapper.incrStudentUnread(sessionId);
                // 商家回复时，学生即便删过这个会话也应重新出现
                sessionMapper.restoreForStudent(sessionId);
            } else {
                sessionMapper.incrMerchantUnread(sessionId);
                // 学生发新消息时，商家即使删过这个会话也要重新出现（否则商家收不到）
                sessionMapper.restoreForMerchant(sessionId);
            }
            String summary = finalDishId == null ? text : "【菜品咨询】" + text;
            sessionMapper.updateLastMessage(sessionId, truncate(summary), LocalDateTime.now());
        });
        // 消息一落库就推给双方，学生消息无需等 AI 生成即可「秒回」
        publishSaved(sessionId, savedUserMessage[0], userId);
        if (merchant) {
            // 商家人工回复是最可靠的「正确样本」，沉淀成经验供下次检索
            saveMerchantReplyExperience(session, text);
        }

        // 第二阶段：AI 代回改到后台线程池生成，绝不阻塞本次发送。
        // 学生消息此时已落库并经 SSE 推给双方 → sendMessage 立即返回，前端秒清输入框、可连续发。
        // 新 AI 回复生成完通过 publishSaved 走 SSE 推回来，学生无需刷新即可看到。
        if (!merchant) {
            final Long sid = sessionId;
            final Long stallId = session.getStallId();
            // 熟客记忆：在调用线程(能读 UserContext 的线程)上预取学生档案，再安全交给执行器线程
            final MerchantReplyGenerator.StudentProfile profile = studentProfile(session);
            AI_REPLY_EXECUTOR.execute(() -> {
                try {
                    MerchantCsConfig config = configMapper.selectByStall(stallId);
                    if (config != null && Integer.valueOf(1).equals(config.getAiEnabled())) {
                        List<CustomerServiceMessage> history = messageMapper.listBySession(sid);
                        SessionReply sr = replyGenerator.generateSessionReply(stallId,
                                stallName(stallId), config.getPersona(), profile, history);
                        if (sr != null && sr.text() != null && !sr.text().isBlank()) {
                            String[] replyHolder = {sr.text().trim()};
                            CustomerServiceMessage[] savedAi = new CustomerServiceMessage[1];
                            transactionTemplate.executeWithoutResult(status -> {
                                // 老顾客安抚券与 AI 回复同事务：审核通过才生成可领取卡片，并把卡片挂到这条 AI 消息上
                                CouponGrant grant = grantVeteranDiscount(session, sr, stallId);
                                if (grant != null) {
                                    replyHolder[0] = replyHolder[0] + "\n" + grant.line();
                                }
                                savedAi[0] = saveAiMessage(sid, replyHolder[0], sr.dishId());
                                if (grant != null && grant.offer() != null) {
                                    savedAi[0].setCouponOfferId(grant.offer().getId());
                                    messageMapper.updateById(savedAi[0]);
                                }
                                sessionMapper.incrStudentUnread(sid);
                                sessionMapper.restoreForStudent(sid);
                                sessionMapper.updateLastMessage(sid, truncate(replyHolder[0]), LocalDateTime.now());
                            });
                            publishSaved(sid, savedAi[0], null);
                        }
                    }
                } catch (Exception e) {
                    log.warn("会话 {} 智能代回失败：{}", sid, e.getMessage());
                }
            });
        }

        transactionTemplate.executeWithoutResult(status -> {
            if (merchant) {
                sessionMapper.resetMerchantUnread(sessionId);
            } else {
                sessionMapper.resetStudentUnread(sessionId);
            }
        });
        return toMessageVOs(messageMapper.listBySession(sessionId), userId);
    }

    @Override
    public String suggestAiReply(Long sessionId) {
        Long stallId = UserContext.requireMerchant();
        CustomerServiceSession session = requireSession(sessionId);
        if (!session.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "会话不属于本档口");
        }
        MerchantCsConfig config = configMapper.selectByStall(stallId);
        List<CustomerServiceMessage> history = messageMapper.listBySession(sessionId);
        return replyGenerator.generateSessionReply(stallId, stallName(stallId),
                config == null ? null : config.getPersona(), studentProfile(session), history).text();
    }

    @Override
    public boolean sendAiReply(Long sessionId) {
        Long stallId = UserContext.requireMerchant();
        CustomerServiceSession session = requireSession(sessionId);
        if (!session.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "会话不属于本档口");
        }
        if (session.getMerchantUnread() == null || session.getMerchantUnread() <= 0) {
            return false;
        }
        MerchantCsConfig config = configMapper.selectByStall(stallId);
        List<CustomerServiceMessage> history = messageMapper.listBySession(sessionId);
        SessionReply sr = replyGenerator.generateSessionReply(stallId, stallName(stallId),
                config == null ? null : config.getPersona(), studentProfile(session), history);
        if (sr == null || sr.text() == null || sr.text().isBlank()) {
            return false;
        }
        String[] textHolder = {sr.text().trim()};
        // AI 代理回复：直接落库 + 推送，不沉淀为人工经验，避免「AI 回复 → 存经验 → 下次复读」的循环
        CustomerServiceMessage[] saved = new CustomerServiceMessage[1];
        transactionTemplate.executeWithoutResult(status -> {
            // 老顾客安抚券与回复同事务，生成可领取卡片并挂到这条 AI 消息上
            CouponGrant grant = grantVeteranDiscount(session, sr, stallId);
            if (grant != null) {
                textHolder[0] = textHolder[0] + "\n" + grant.line();
            }
            saved[0] = saveAiMessage(sessionId, textHolder[0], sr.dishId());
            if (grant != null && grant.offer() != null) {
                saved[0].setCouponOfferId(grant.offer().getId());
                messageMapper.updateById(saved[0]);
            }
            sessionMapper.incrStudentUnread(sessionId);
            sessionMapper.restoreForStudent(sessionId);
            sessionMapper.updateLastMessage(sessionId, truncate(textHolder[0]), LocalDateTime.now());
            sessionMapper.resetMerchantUnread(sessionId);
        });
        publishSaved(sessionId, saved[0], UserContext.getUserId());
        return true;
    }

    @Override
    public MerchantCsConfig getConfig() {
        Long stallId = UserContext.requireMerchant();
        MerchantCsConfig config = configMapper.selectByStall(stallId);
        if (config == null) {
            config = new MerchantCsConfig();
            config.setStallId(stallId);
            config.setAiEnabled(0);
            config.setReviewAiEnabled(0);
            config.setDiscountEnabled(1);
            config.setManualGrantEnabled(1);
            config.setMinRate(new BigDecimal("0.50"));
            config.setMaxRate(new BigDecimal("0.95"));
            config.setAllowFree(1);
            config.setCooldownDays(30);
            config.setVeteranMinOrders(10);
        }
        return config;
    }

    @Override
    @Transactional
    public MerchantCsConfig updateConfig(CsConfigRequest request) {
        Long stallId = UserContext.requireMerchant();
        MerchantCsConfig config = configMapper.selectByStall(stallId);
        if (config == null) {
            config = new MerchantCsConfig();
            config.setStallId(stallId);
        }
        config.setAiEnabled(normalizeSwitch(request.getAiEnabled()));
        config.setReviewAiEnabled(normalizeSwitch(request.getReviewAiEnabled()));
        config.setPersona(trimToNull(request.getPersona()));
        config.setGreeting(trimToNull(request.getGreeting()));
        if (request.getDiscountEnabled() != null) {
            config.setDiscountEnabled(normalizeSwitch(request.getDiscountEnabled()));
        }
        if (request.getManualGrantEnabled() != null) {
            config.setManualGrantEnabled(normalizeSwitch(request.getManualGrantEnabled()));
        }
        if (request.getMinRate() != null) {
            config.setMinRate(request.getMinRate());
        }
        if (request.getMaxRate() != null) {
            config.setMaxRate(request.getMaxRate());
        }
        if (request.getAllowFree() != null) {
            config.setAllowFree(normalizeSwitch(request.getAllowFree()));
        }
        if (request.getCooldownDays() != null) {
            config.setCooldownDays(request.getCooldownDays());
        }
        if (request.getVeteranMinOrders() != null) {
            config.setVeteranMinOrders(request.getVeteranMinOrders());
        }
        // 折扣区间兜底：最低不得高于最高，缺省补齐默认值
        BigDecimal min = config.getMinRate() == null ? new BigDecimal("0.50") : config.getMinRate();
        BigDecimal max = config.getMaxRate() == null ? new BigDecimal("0.95") : config.getMaxRate();
        if (min.compareTo(max) > 0) {
            BigDecimal tmp = min;
            min = max;
            max = tmp;
        }
        config.setMinRate(min);
        config.setMaxRate(max);
        if (config.getCooldownDays() == null) {
            config.setCooldownDays(30);
        }
        if (config.getVeteranMinOrders() == null) {
            config.setVeteranMinOrders(10);
        }
        if (config.getManualGrantEnabled() == null) {
            config.setManualGrantEnabled(1);
        }
        if (config.getAllowFree() == null) {
            config.setAllowFree(1);
        }
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config;
    }

    @Override
    public List<MerchantQuickReply> listQuickReplies() {
        return quickReplyMapper.selectByStall(UserContext.requireMerchant());
    }

    @Override
    @Transactional
    public MerchantQuickReply addQuickReply(QuickReplyRequest request) {
        Long stallId = UserContext.requireMerchant();
        MerchantQuickReply q = new MerchantQuickReply();
        q.setStallId(stallId);
        q.setContent(request.getContent().trim());
        q.setSort(request.getSort() == null ? 0 : request.getSort());
        quickReplyMapper.insert(q);
        return q;
    }

    @Override
    @Transactional
    public MerchantQuickReply updateQuickReply(Long id, QuickReplyRequest request) {
        Long stallId = UserContext.requireMerchant();
        MerchantQuickReply q = quickReplyMapper.selectById(id);
        if (q == null || !q.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "快捷回复不存在");
        }
        q.setContent(request.getContent().trim());
        if (request.getSort() != null) {
            q.setSort(request.getSort());
        }
        quickReplyMapper.updateById(q);
        return q;
    }

    @Override
    @Transactional
    public void deleteQuickReply(Long id) {
        Long stallId = UserContext.requireMerchant();
        MerchantQuickReply q = quickReplyMapper.selectById(id);
        if (q == null || !q.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "快捷回复不存在");
        }
        quickReplyMapper.deleteById(id);
    }

    @Override
    public long unreadCount() {
        List<CustomerServiceSession> sessions;
        boolean merchant = UserContext.isMerchant();
        if (merchant) {
            sessions = sessionMapper.selectByStall(UserContext.requireMerchant());
        } else if (UserContext.getUserId() != null && !UserContext.isAdmin()) {
            sessions = sessionMapper.selectByUser(UserContext.getUserId());
        } else {
            return 0;
        }
        long total = 0;
        for (CustomerServiceSession s : sessions) {
            Integer count = merchant ? s.getMerchantUnread() : s.getStudentUnread();
            total += count == null ? 0 : count;
        }
        return total;
    }

    @Override
    public void deleteSession(Long sessionId) {
        if (UserContext.isMerchant()) {
            int rows = sessionMapper.deleteForMerchant(sessionId, UserContext.requireMerchant());
            if (rows == 0) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在或不属于本档口");
            }
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null || UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权删除该会话");
        }
        int rows = sessionMapper.deleteForStudent(sessionId, userId);
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在");
        }
    }

    @Override
    public void readAll() {
        if (UserContext.isMerchant()) {
            sessionMapper.resetAllMerchantUnread(UserContext.requireMerchant());
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null || UserContext.isAdmin()) {
            return;
        }
        sessionMapper.resetAllStudentUnread(userId);
    }

    @Override
    public void readSession(Long sessionId) {
        requireSessionOwned(sessionId);
        if (UserContext.isMerchant()) {
            sessionMapper.resetMerchantUnread(sessionId);
        } else {
            sessionMapper.resetStudentUnread(sessionId);
        }
    }

    /**
     * 商家手动发一张折扣券给会话里的学生：受「商家可手动发券」开关控制，
     * 折扣统一按商家配置的区间收敛（免单需开启 allow_free），并留一条商家消息说明。
     */
    @Override
    @Transactional
    public Map<String, Object> grantCouponManually(Long sessionId, BigDecimal rate, String scope, Long dishId) {
        Long stallId = UserContext.requireMerchant();
        Long merchantUserId = UserContext.getUserId();
        CustomerServiceSession session = requireSession(sessionId);
        if (!session.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "会话不属于本档口");
        }
        if (session.getUserId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "会话缺少学生信息");
        }
        MerchantCsConfig config = configMapper.selectByStall(stallId);
        if (config == null || !Integer.valueOf(1).equals(config.getManualGrantEnabled())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "商家手动发券已在客服设置里关闭");
        }
        String finalScope = Coupon.SCOPE_ITEM.equals(scope) ? Coupon.SCOPE_ITEM : Coupon.SCOPE_ORDER;
        Long finalDishId = null;
        if (Coupon.SCOPE_ITEM.equals(finalScope) && dishId != null) {
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null || !stallId.equals(dish.getStallId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "所选菜品不属于本档口");
            }
            finalDishId = dishId;
        }
        BigDecimal finalRate = normalizeManualRate(rate, config);
        final boolean free = finalRate.signum() == 0;
        com.qmz.canteen.dto.CouponRequest req = buildCouponRequest(stallId, finalRate, true, finalScope, finalDishId);
        req.setPrivateGift(1);
        CouponVO coupon = couponService.createCoupon(stallId, merchantUserId, req);
        // 关键：不直接进学生券包，先生成可领取凭证；学生点「领取」后才真正到账
        CouponOffer offer = couponService.createOffer(stallId, session.getUserId(), coupon.getId(), sessionId);

        CustomerServiceDiscount rec = new CustomerServiceDiscount();
        rec.setStallId(stallId);
        rec.setUserId(session.getUserId());
        rec.setSessionId(sessionId);
        rec.setCouponId(coupon.getId());
        rec.setUserCouponId(null);
        rec.setTier(CustomerServiceDiscount.TIER_MANUAL);
        rec.setDiscountValue(finalRate);
        rec.setGrantTime(LocalDateTime.now());
        discountMapper.insert(rec);

        String face = free ? "免单券"
                : finalRate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + " 折券";
        String text = "老板给你发了一张" + face + "，点下面的券卡片领取，领取后可在「我的优惠券」里使用～";
        CustomerServiceMessage msg = new CustomerServiceMessage();
        msg.setSessionId(sessionId);
        msg.setSenderType(CustomerServiceMessage.SENDER_MERCHANT);
        msg.setSenderId(merchantUserId);
        msg.setContent(text);
        msg.setCouponOfferId(offer.getId());
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
        sessionMapper.incrStudentUnread(sessionId);
        sessionMapper.restoreForStudent(sessionId);
        sessionMapper.updateLastMessage(sessionId, truncate(text), LocalDateTime.now());
        publishSaved(sessionId, msg, merchantUserId);

        log.info("商家发券(待领取)：stall={} user={} rate={} scope={} dish={}",
                stallId, session.getUserId(), finalRate, finalScope, finalDishId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("couponId", coupon.getId());
        result.put("couponName", coupon.getName());
        result.put("offerId", offer.getId());
        result.put("rate", finalRate);
        result.put("scope", finalScope);
        result.put("free", free);
        result.put("message", text);
        return result;
    }

    /** AI 发券折扣收敛：免单需 allow_free，其余夹到商家配置区间；不允许则返回 null（静默不发） */
    private BigDecimal clampAiRate(BigDecimal rate, MerchantCsConfig config) {
        if (rate == null) {
            return null;
        }
        BigDecimal min = config.getMinRate() == null ? new BigDecimal("0.50") : config.getMinRate();
        BigDecimal max = config.getMaxRate() == null ? new BigDecimal("0.95") : config.getMaxRate();
        if (rate.signum() <= 0) {
            return Integer.valueOf(1).equals(config.getAllowFree()) ? BigDecimal.ZERO.setScale(2) : null;
        }
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            return null;
        }
        if (rate.compareTo(min) < 0) {
            return min.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (rate.compareTo(max) > 0) {
            return max.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return rate.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 手动发券的折扣收敛：0=免单需 allow_free，其余夹在 [minRate, maxRate] 之间 */
    private BigDecimal normalizeManualRate(BigDecimal rate, MerchantCsConfig config) {
        BigDecimal min = config.getMinRate() == null ? new BigDecimal("0.50") : config.getMinRate();
        BigDecimal max = config.getMaxRate() == null ? new BigDecimal("0.95") : config.getMaxRate();
        if (rate == null) {
            return max;
        }
        if (rate.signum() <= 0) {
            if (!Integer.valueOf(1).equals(config.getAllowFree())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "当前客服设置不允许发免单券");
            }
            return BigDecimal.ZERO.setScale(2);
        }
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "折扣必须小于 1（如 0.9=九折）");
        }
        if (rate.compareTo(min) < 0) {
            return min;
        }
        if (rate.compareTo(max) > 0) {
            return max;
        }
        return rate.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerServiceMessageVO> adminGetMessages(Long sessionId) {
        UserContext.requireAdmin();
        requireSession(sessionId);
        return toMessageVOs(messageMapper.listBySession(sessionId), UserContext.getUserId());
    }

    @Override
    public List<CustomerServiceSessionVO> adminListSessions(int current, int size) {
        UserContext.requireAdmin();
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * pageSize;
        return sessionMapper.adminList(offset, pageSize);
    }

    @Override
    public long adminSessionTotal() {
        UserContext.requireAdmin();
        return sessionMapper.countAll();
    }

    @Override
    public CsStatsVO adminStats() {
        UserContext.requireAdmin();
        CsStatsVO vo = new CsStatsVO();
        vo.setSessionTotal(sessionMapper.countAll());
        vo.setOpenTotal(sessionMapper.countOpen());
        vo.setMessageTotal(messageMapper.countAll());
        vo.setAiMessageTotal(messageMapper.countAi());
        vo.setUnhandledTotal(sessionMapper.countUnhandled());
        return vo;
    }

    private void validateOrder(Long stallId, Long userId, Long orderId) {
        if (orderId == null) {
            return;
        }
        DiningOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId) || !Objects.equals(order.getStallId(), stallId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "订单与当前学生或档口不匹配");
        }
    }

    private void validateDish(Long stallId, Long dishId) {
        if (dishId == null) {
            return;
        }
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "咨询菜品不存在");
        }
        if (!Objects.equals(dish.getStallId(), stallId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "不能咨询其他档口的菜品");
        }
    }

    private List<CustomerServiceMessageVO> toMessageVOs(List<CustomerServiceMessage> messages, Long currentUserId) {
        List<Long> dishIds = messages.stream().map(CustomerServiceMessage::getDishId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Dish> dishes = new HashMap<>();
        if (!dishIds.isEmpty()) {
            for (Dish dish : dishMapper.selectBatchIds(dishIds)) {
                dishes.put(dish.getId(), dish);
            }
        }
        // 券卡片：批量加载 offer 与券模板
        List<Long> offerIds = messages.stream().map(CustomerServiceMessage::getCouponOfferId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CouponOffer> offers = new HashMap<>();
        Map<Long, Coupon> offerCoupons = new HashMap<>();
        if (!offerIds.isEmpty()) {
            for (CouponOffer o : couponOfferMapper.selectBatchIds(offerIds)) {
                offers.put(o.getId(), o);
            }
            List<Long> couponIds = offers.values().stream().map(CouponOffer::getCouponId)
                    .filter(Objects::nonNull).distinct().toList();
            if (!couponIds.isEmpty()) {
                for (Coupon c : couponMapper.selectBatchIds(couponIds)) {
                    offerCoupons.put(c.getId(), c);
                }
            }
        }
        List<CustomerServiceMessageVO> result = new ArrayList<>();
        for (CustomerServiceMessage m : messages) {
            CustomerServiceMessageVO vo = new CustomerServiceMessageVO();
            vo.setId(m.getId());
            vo.setSessionId(m.getSessionId());
            vo.setSenderType(m.getSenderType());
            vo.setSenderId(m.getSenderId());
            vo.setContent(m.getContent());
            vo.setDishId(m.getDishId());
            Dish dish = dishes.get(m.getDishId());
            if (dish != null) {
                vo.setDishName(dish.getName());
                vo.setDishImage(dish.getImage());
                vo.setDishPrice(dish.getPrice());
                vo.setDishStatus(dish.getStatus());
            }
            vo.setCouponOfferId(m.getCouponOfferId());
            CouponOffer offer = m.getCouponOfferId() == null ? null : offers.get(m.getCouponOfferId());
            if (offer != null) {
                vo.setCouponOfferStatus(offer.getStatus());
                Coupon oc = offerCoupons.get(offer.getCouponId());
                if (oc != null) {
                    vo.setCouponName(oc.getName());
                    vo.setCouponType(oc.getType());
                    vo.setCouponScope(oc.getScope() == null ? Coupon.SCOPE_ORDER : oc.getScope());
                    vo.setCouponValue(oc.getValue());
                    vo.setCouponEndTime(oc.getEndTime());
                    if (oc.getDishId() != null) {
                        Dish cd = dishMapper.selectById(oc.getDishId());
                        vo.setCouponDishName(cd == null ? null : cd.getName());
                    }
                }
            }
            vo.setCreateTime(m.getCreateTime());
            boolean self = !CustomerServiceMessage.SENDER_AI.equals(m.getSenderType())
                    && currentUserId != null && currentUserId.equals(m.getSenderId());
            vo.setSelf(self);
            result.add(vo);
        }
        return result;
    }

    private CustomerServiceMessage saveAiMessage(Long sessionId, String content) {
        return saveAiMessage(sessionId, content, null);
    }

    /** AI 回复落库，可选附带菜品ID（dishId 非空 → 前端渲染成可加购的菜品卡） */
    private CustomerServiceMessage saveAiMessage(Long sessionId, String content, Long dishId) {
        CustomerServiceMessage m = new CustomerServiceMessage();
        m.setSessionId(sessionId);
        m.setSenderType(CustomerServiceMessage.SENDER_AI);
        m.setSenderId(null);
        m.setContent(content);
        m.setDishId(dishId);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
        return m;
    }

    /** 把「学生问题 + 商家人工回复」沉淀进经验库；向量交给定时任务补，避免拖慢发消息 */
    private void saveMerchantReplyExperience(CustomerServiceSession session, String reply) {
        try {
            if (reply == null || reply.trim().length() < 4) {
                return;
            }
            List<CustomerServiceMessage> history = messageMapper.listBySession(session.getId());
            String question = null;
            // 跳过刚发的这条商家消息；遇到上一条商家消息说明是连发，不重复沉淀
            for (int i = history.size() - 2; i >= 0; i--) {
                CustomerServiceMessage m = history.get(i);
                if (CustomerServiceMessage.SENDER_MERCHANT.equals(m.getSenderType())) {
                    return;
                }
                if (CustomerServiceMessage.SENDER_STUDENT.equals(m.getSenderType())) {
                    question = m.getContent();
                    break;
                }
            }
            if (question == null || question.trim().length() < 4) {
                return;
            }
            experienceService.save("CUSTOMER_SERVICE", question, reply, "SUCCESS", 5,
                    "商家人工回复", session.getStallId(), "MERCHANT_REPLY", session.getId(), false);
        } catch (Exception e) {
            log.warn("商家人工回复沉淀经验失败：{}", e.getMessage());
        }
    }

    /** 消息落库后推送给 SSE 订阅者；推送失败不影响主流程 */
    private void publishSaved(Long sessionId, CustomerServiceMessage message, Long currentUserId) {
        if (message == null) {
            return;
        }
        try {
            List<CustomerServiceMessageVO> vos = toMessageVOs(List.of(message), currentUserId);
            if (!vos.isEmpty()) {
                streamService.publish(sessionId, vos.get(0));
            }
        } catch (Exception e) {
            log.warn("客服消息 SSE 推送失败：session={} err={}", sessionId, e.getMessage());
        }
    }

    private CustomerServiceSession requireSession(Long sessionId) {
        CustomerServiceSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return session;
    }

    private CustomerServiceSession requireSessionOwned(Long sessionId) {
        CustomerServiceSession session = requireSession(sessionId);
        if (UserContext.isMerchant()) {
            if (!session.getStallId().equals(UserContext.requireMerchant())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "会话不属于本档口");
            }
        } else if (UserContext.getUserId() != null && !UserContext.isAdmin()) {
            if (!session.getUserId().equals(UserContext.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "会话不属于你");
            }
        } else {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问");
        }
        return session;
    }

    private CustomerServiceSessionVO toVO(CustomerServiceSession s) {
        CustomerServiceSessionVO vo = new CustomerServiceSessionVO();
        vo.setId(s.getId());
        vo.setStallId(s.getStallId());
        vo.setUserId(s.getUserId());
        vo.setOrderId(s.getOrderId());
        vo.setLastMessage(s.getLastMessage());
        vo.setLastMessageTime(s.getLastMessageTime());
        vo.setStudentUnread(s.getStudentUnread());
        vo.setMerchantUnread(s.getMerchantUnread());
        vo.setStatus(s.getStatus());
        vo.setCreateTime(s.getCreateTime());
        vo.setStallName(stallName(s.getStallId()));
        User merchant = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "MERCHANT")
                .eq(User::getStallId, s.getStallId())
                .orderByAsc(User::getId)
                .last("LIMIT 1"));
        vo.setStallAvatar(merchant != null && merchant.getAvatar() != null && !merchant.getAvatar().isBlank()
                ? merchant.getAvatar() : "/ui/logo/stall-" + s.getStallId() + ".webp");
        User student = userMapper.selectById(s.getUserId());
        if (student != null) {
            vo.setStudentName(student.getRealName());
            vo.setStudentAvatar(student.getAvatar());
        }
        return vo;
    }

    private String stallName(Long stallId) {
        if (stallId == null) {
            return null;
        }
        Stall stall = stallMapper.selectById(stallId);
        return stall == null ? null : stall.getName();
    }

    /**
     * AI 想给老顾客实在安抚时，真正把一张本档口优惠券发到学生账户。
     * 只认 CLI 已在 {@link SessionReply#couponRate()} 解析出的合法折扣，且受冷却约束：
     * 同一学生同一档口 30 天内最多发 1 张。发现金券失败或冷却中则静默跳过（绝不让 AI 空头应承）。
     * 返回要追加在回复末尾的学生可见提示（无则 null）。
     * 由调用方（发 AI 回复的事务）内一并执行，保证发券与回复同生同灭。
     */
    public CouponGrant grantVeteranDiscount(CustomerServiceSession session, SessionReply reply, Long stallId) {
        if (session == null || reply == null || reply.couponRate() == null) {
            return null;
        }
        Long userId = session.getUserId();
        if (userId == null || stallId == null) {
            return null;
        }
        MerchantCsConfig config = configMapper.selectByStall(stallId);
        if (config == null || !Integer.valueOf(1).equals(config.getDiscountEnabled())) {
            return null;
        }
        // 冷却：按商家配置的天数（默认 30 天），期内已发过则不再发
        int cooldownDays = config.getCooldownDays() == null ? 30 : Math.max(config.getCooldownDays(), 0);
        LocalDateTime cooldownSince = LocalDateTime.now().minusDays(cooldownDays);
        if (cooldownDays > 0 && discountMapper.selectRecent(stallId, userId, cooldownSince) != null) {
            return null;
        }
        // 只有达到商家配置的老客门槛（默认本档口已完成 ≥10 单）才给真实券，防 AI 对生客随意发券
        long doneCount = orderMapper.countDoneByUserStall(userId, stallId);
        int veteranMin = config.getVeteranMinOrders() == null ? 10 : config.getVeteranMinOrders();
        if (doneCount < veteranMin) {
            return null;
        }
        BigDecimal rate = clampAiRate(reply.couponRate(), config);
        if (rate == null) {
            return null;
        }
        final boolean free = rate.signum() == 0;
        try {
            com.qmz.canteen.dto.CouponRequest aiReq =
                    buildCouponRequest(stallId, rate, false, Coupon.SCOPE_ORDER, null);
            // AI 安抚券是定向发给这位老客的，不在领券中心公开
            aiReq.setPrivateGift(1);
            CouponVO coupon = couponService.createCoupon(stallId, config.getStallId(), aiReq);
            // 与商家人工发券一致：生成可领取卡片，学生点「领取」后才到账
            CouponOffer offer = couponService.createOffer(stallId, userId, coupon.getId(), session.getId());
            CustomerServiceDiscount rec = new CustomerServiceDiscount();
            rec.setStallId(stallId);
            rec.setUserId(userId);
            rec.setSessionId(session.getId());
            rec.setCouponId(coupon.getId());
            rec.setUserCouponId(null);
            rec.setTier(CustomerServiceDiscount.TIER_VETERAN);
            rec.setDiscountValue(rate);
            rec.setGrantTime(LocalDateTime.now());
            discountMapper.insert(rec);
            log.info("客服AI为老顾客发安抚券卡片：stall={} user={} rate={}", stallId, userId, rate);
            String line = free
                    ? "（给你留了一张免单券卡片，点下面的卡片领取就能用～）"
                    : "（给你留了一张 " + rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString()
                            + " 折券卡片，点下面的卡片领取就能用～）";
            return new CouponGrant(offer, line);
        } catch (Exception e) {
            log.warn("客服AI发安抚券失败（忽略，仍有文字回复）：{}", e.getMessage());
            return null;
        }
    }

    /** AI 发券结果：可领取卡片 + 回复里追加的一句提示 */
    public record CouponGrant(CouponOffer offer, String line) {
    }

    private com.qmz.canteen.dto.CouponRequest buildCouponRequest(Long stallId, BigDecimal rate,
                                                                 boolean manual, String scope, Long dishId) {
        BigDecimal value = rate == null ? BigDecimal.valueOf(0.9) : rate;
        boolean free = value.signum() == 0;
        boolean item = Coupon.SCOPE_ITEM.equals(scope);
        String suffix = value.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + "折";
        com.qmz.canteen.dto.CouponRequest req = new com.qmz.canteen.dto.CouponRequest();
        req.setName(stallName(stallId) + (free
                ? (manual ? (item ? "老板专属单菜免单券" : "老板专属免单券") : "免单感谢券")
                : (manual ? (item ? "老板专属" + suffix + "单菜券" : "老板专属" + suffix + "券")
                        : "老顾客" + suffix + "感谢券")));
        req.setType(Coupon.TYPE_DISCOUNT);
        req.setScope(item ? Coupon.SCOPE_ITEM : Coupon.SCOPE_ORDER);
        req.setDishId(item ? dishId : null);
        req.setValue(value);
        req.setMinAmount(BigDecimal.ZERO);
        req.setTotalQty(0);
        req.setPerUserLimit(1);
        // 7 天内有效，避免僵尸券堆积。
        // 开始时间回退 1 秒并抹掉纳秒：MySQL DATETIME 会对小数秒四舍五入，
        // 否则可能存成「未来 1 秒」，发券时因「还没到领取时间」失败。
        LocalDateTime now = LocalDateTime.now().withNano(0).minusSeconds(1);
        req.setStartTime(now);
        req.setEndTime(now.plusDays(7));
        return req;
    }

    /** 学生熟客档案：在能读 UserContext 的调用线程上构建，供 AI 生成「记住老顾客」的亲近回复 */
    private MerchantReplyGenerator.StudentProfile studentProfile(CustomerServiceSession session) {
        Long userId = session.getUserId();
        if (userId == null) {
            return null;
        }
        User student = userMapper.selectById(userId);
        int doneCount = (int) Math.min(orderMapper.countDoneByUserStall(userId, session.getStallId()), Integer.MAX_VALUE);
        return new MerchantReplyGenerator.StudentProfile(userId,
                student == null ? null : student.getRealName(), doneCount);
    }

    private static int normalizeSwitch(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
