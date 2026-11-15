package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.MerchantTodoRequest;
import com.qmz.canteen.entity.CustomerServiceDiscount;
import com.qmz.canteen.entity.CustomerServiceSession;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.MerchantMessage;
import com.qmz.canteen.entity.MerchantTodo;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.entity.RefundRequest;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.CustomerServiceDiscountMapper;
import com.qmz.canteen.mapper.CustomerServiceSessionMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.MerchantMessageMapper;
import com.qmz.canteen.mapper.MerchantTodoMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.service.MerchantService;
import com.qmz.canteen.service.NotificationService;
import com.qmz.canteen.vo.MerchantCommStallVO;
import com.qmz.canteen.vo.MerchantMessageVO;
import com.qmz.canteen.vo.StallVO;
import com.qmz.canteen.vo.MerchantTodoVO;
import com.qmz.canteen.vo.StallCustomerStatsVO;
import com.qmz.canteen.vo.StallCustomerStatsVO.StudentFreqItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    /** 差评预警阈值：评价数达标且差评占比超过该值就预警 */
    private static final int MIN_REVIEWS = 3;
    private static final int BAD_REVIEW_PERCENT = 40;
    private static final int BAD_RATING_MAX = 2;

    private final MerchantTodoMapper todoMapper;
    private final MerchantMessageMapper messageMapper;
    private final DishMapper dishMapper;
    private final DishReviewMapper dishReviewMapper;
    private final StallMapper stallMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceDiscountMapper discountMapper;
    private final NotificationService notificationService;

    @Override
    public List<MerchantTodo> listTodos(Long stallId) {
        scanBadReviews(stallId);
        return todoMapper.selectList(new LambdaQueryWrapper<MerchantTodo>()
                .eq(MerchantTodo::getStallId, stallId)
                .eq(MerchantTodo::getStatus, 0)
                .orderByDesc(MerchantTodo::getCreateTime));
    }

    @Override
    public List<MerchantTodoVO> listTodoSummary(Long stallId) {
        scanBadReviews(stallId);
        List<MerchantTodoVO> result = new ArrayList<>();

        // 1. 新订单（待出餐：PENDING + 已支付）
        List<DiningOrder> pendingOrders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getStallId, stallId)
                .eq(DiningOrder::getStatus, "PENDING")
                .eq(DiningOrder::getPayStatus, "PAID")
                .orderByDesc(DiningOrder::getId)
                .last("LIMIT 1"));
        if (!pendingOrders.isEmpty()) {
            DiningOrder latest = pendingOrders.get(0);
            long count = orderMapper.selectCount(new LambdaQueryWrapper<DiningOrder>()
                    .eq(DiningOrder::getStallId, stallId)
                    .eq(DiningOrder::getStatus, "PENDING")
                    .eq(DiningOrder::getPayStatus, "PAID"));
            result.add(build(ORDER, "新订单", "有 " + count + " 笔新订单待出餐",
                    "最新 " + latest.getOrderNo() + " · 取餐 " + time(latest.getPickupTime()),
                    (int) count, "/merchant/orders", latest.getId(), latest.getCreateTime(), null));
        }

        // 2. 退款申请（待审核）
        List<RefundRequest> pendingRefunds = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getStallId, stallId)
                .eq(RefundRequest::getStatus, RefundRequest.STATUS_PENDING)
                .orderByDesc(RefundRequest::getId)
                .last("LIMIT 1"));
        if (!pendingRefunds.isEmpty()) {
            RefundRequest latest = pendingRefunds.get(0);
            long count = refundRequestMapper.selectCount(new LambdaQueryWrapper<RefundRequest>()
                    .eq(RefundRequest::getStallId, stallId)
                    .eq(RefundRequest::getStatus, RefundRequest.STATUS_PENDING));
            result.add(build(REFUND, "退款申请", "有 " + count + " 笔退款待审核",
                    truncate(latest.getReason(), 40), (int) count,
                    "/merchant/refunds", latest.getId(), latest.getCreateTime(), null));
        }

        // 3. 客服未读消息
        List<CustomerServiceSession> unreadSessions = sessionMapper.selectList(new LambdaQueryWrapper<CustomerServiceSession>()
                .eq(CustomerServiceSession::getStallId, stallId)
                .eq(CustomerServiceSession::getMerchantDeleted, 0)
                .gt(CustomerServiceSession::getMerchantUnread, 0)
                .orderByDesc(CustomerServiceSession::getLastMessageTime)
                .last("LIMIT 1"));
        if (!unreadSessions.isEmpty()) {
            CustomerServiceSession latest = unreadSessions.get(0);
            List<CustomerServiceSession> allUnread = sessionMapper.selectList(new LambdaQueryWrapper<CustomerServiceSession>()
                    .eq(CustomerServiceSession::getStallId, stallId)
                    .eq(CustomerServiceSession::getMerchantDeleted, 0)
                    .gt(CustomerServiceSession::getMerchantUnread, 0));
            int count = allUnread.stream()
                    .mapToInt(s -> s.getMerchantUnread() == null ? 0 : s.getMerchantUnread()).sum();
            result.add(build(MESSAGE, "客服消息", "有 " + count + " 条客服消息待回复",
                    truncate(latest.getLastMessage(), 40), count,
                    "/merchant/customer-service", latest.getId(), latest.getLastMessageTime(), null));
        }

        // 4. 待回复评价（已发布且商家未处理）
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId));
        List<Long> dishIds = dishes.stream().map(Dish::getId).toList();
        if (!dishIds.isEmpty()) {
            List<DishReview> pendingReviews = dishReviewMapper.selectList(new LambdaQueryWrapper<DishReview>()
                    .in(DishReview::getDishId, dishIds)
                    .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                    .eq(DishReview::getHandled, 0)
                    .orderByDesc(DishReview::getId)
                    .last("LIMIT 1"));
            if (!pendingReviews.isEmpty()) {
                DishReview latest = pendingReviews.get(0);
                long count = dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                        .in(DishReview::getDishId, dishIds)
                        .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                        .eq(DishReview::getHandled, 0));
                result.add(build(REVIEW, "评价回复", "有 " + count + " 条评价待回复",
                        truncate(latest.getContent(), 40), (int) count,
                        "/merchant/reviews", latest.getId(), latest.getCreateTime(), null));
            }
        }

        // 5. 整改敦促 + 差评预警（既有 merchant_todo，可「已处理」）
        List<MerchantTodo> manualTodos = todoMapper.selectList(new LambdaQueryWrapper<MerchantTodo>()
                .eq(MerchantTodo::getStallId, stallId)
                .eq(MerchantTodo::getStatus, 0)
                .orderByDesc(MerchantTodo::getCreateTime));
        for (MerchantTodo t : manualTodos) {
            boolean inspect = MerchantTodo.TYPE_INSPECT.equals(t.getType());
            result.add(build(inspect ? INSPECT : BAD_REVIEW,
                    inspect ? "整改敦促" : "差评预警",
                    t.getTitle(), truncate(t.getContent(), 40), 1,
                    BAD_REVIEW.equals(t.getType()) && t.getDishId() != null ? "/merchant/reviews" : null,
                    BAD_REVIEW.equals(t.getType()) ? t.getDishId() : null,
                    t.getCreateTime(), t.getId()));
        }

        return result;
    }

    private static final String ORDER = "ORDER";
    private static final String REFUND = "REFUND";
    private static final String MESSAGE = "MESSAGE";
    private static final String REVIEW = "REVIEW";
    private static final String INSPECT = "INSPECT";
    private static final String BAD_REVIEW = "BAD_REVIEW";

    private static MerchantTodoVO build(String type, String label, String title, String content,
                                        int count, String url, Long targetId,
                                        LocalDateTime time, Long doneId) {
        MerchantTodoVO vo = new MerchantTodoVO();
        vo.setType(type);
        vo.setTypeLabel(label);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setCount(count);
        vo.setTargetUrl(url);
        vo.setTargetId(targetId);
        vo.setLatestTime(time);
        vo.setDoneId(doneId);
        return vo;
    }

    private static String time(LocalDateTime t) {
        if (t == null) {
            return "";
        }
        return t.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    @Override
    public void doneTodo(Long id, Long stallId) {
        MerchantTodo todo = todoMapper.selectById(id);
        if (todo == null || !todo.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "待办不存在");
        }
        todo.setStatus(1);
        todo.setHandleTime(LocalDateTime.now());
        todoMapper.updateById(todo);
    }

    @Override
    public Long createInspectTodo(MerchantTodoRequest request) {
        if (stallMapper.selectById(request.getStallId()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "档口不存在");
        }
        MerchantTodo todo = new MerchantTodo();
        todo.setStallId(request.getStallId());
        todo.setType(MerchantTodo.TYPE_INSPECT);
        todo.setTitle(request.getTitle());
        todo.setContent(request.getContent());
        todo.setStatus(0);
        todoMapper.insert(todo);
        return todo.getId();
    }

    /** 差评预警：某菜评价数达标且差评占比过高，生成一条待办（已有未处理预警则不重复） */
    private void scanBadReviews(Long stallId) {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId)
                .eq(Dish::getStatus, 1));
        for (Dish dish : dishes) {
            long total = dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                    .eq(DishReview::getDishId, dish.getId())
                    .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED));
            if (total < MIN_REVIEWS) {
                continue;
            }
            long bad = dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                    .eq(DishReview::getDishId, dish.getId())
                    .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                    .le(DishReview::getRating, BAD_RATING_MAX));
            if (bad * 100.0 / total < BAD_REVIEW_PERCENT) {
                continue;
            }
            boolean exists = todoMapper.selectCount(new LambdaQueryWrapper<MerchantTodo>()
                    .eq(MerchantTodo::getStallId, stallId)
                    .eq(MerchantTodo::getType, MerchantTodo.TYPE_BAD_REVIEW)
                    .eq(MerchantTodo::getDishId, dish.getId())
                    .eq(MerchantTodo::getStatus, 0)) > 0;
            if (exists) {
                continue;
            }
            MerchantTodo todo = new MerchantTodo();
            todo.setStallId(stallId);
            todo.setType(MerchantTodo.TYPE_BAD_REVIEW);
            todo.setTitle("菜品「" + dish.getName() + "」差评偏多");
            todo.setContent("近 " + total + " 条评价中 " + bad + " 条为差评（" + (bad * 100 / total) + "%），请关注口味或出品。");
            todo.setDishId(dish.getId());
            todo.setStatus(0);
            todoMapper.insert(todo);
        }
    }

    @Override
    public void sendMessage(Long userId, Long stallId, String content) {
        MerchantMessage message = new MerchantMessage();
        message.setUserId(userId);
        message.setStallId(stallId);
        message.setDirection(MerchantMessage.DIR_IN);
        message.setAuthorRole(MerchantMessage.AUTHOR_MERCHANT);
        message.setNotice(0);
        message.setContent(content.trim());
        message.setStatus(0);
        messageMapper.insert(message);
    }

    @Override
    public void adminSendMessage(Long adminId, Long stallId, String title, String content, boolean sendNotify) {
        if (stallMapper.selectById(stallId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "档口不存在");
        }
        MerchantMessage message = new MerchantMessage();
        message.setUserId(adminId);
        message.setStallId(stallId);
        message.setDirection(MerchantMessage.DIR_OUT);
        message.setAuthorRole(MerchantMessage.AUTHOR_ADMIN);
        message.setNotice(0);
        message.setTitle(trimToNull(title));
        message.setContent(content.trim());
        message.setStatus(0);
        messageMapper.insert(message);
        if (sendNotify) {
            String notifyText = (title == null || title.isBlank()) ? content.trim()
                    : "【" + title.trim() + "】" + content.trim();
            notificationService.notifyMerchant(stallId, Notification.TYPE_ADMIN_NOTICE,
                    notifyText, "/merchant/messages", message.getId());
        }
    }

    @Override
    public List<MerchantMessageVO> threadByStall(Long stallId) {
        return messageMapper.selectList(new LambdaQueryWrapper<MerchantMessage>()
                        .eq(MerchantMessage::getStallId, stallId)
                        .orderByAsc(MerchantMessage::getCreateTime)
                        .orderByAsc(MerchantMessage::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<MerchantMessageVO> listMyMessages(Long stallId) {
        // 新消息在最前（最新往来第一条可见），置顶通知由前端按 notice 字段处理
        return messageMapper.selectList(new LambdaQueryWrapper<MerchantMessage>()
                .eq(MerchantMessage::getStallId, stallId)
                .orderByDesc(MerchantMessage::getCreateTime)
                .orderByDesc(MerchantMessage::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public PageResult<MerchantMessageVO> pageAllMessages(Long current, Long size) {
        IPage<MerchantMessage> page = messageMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<MerchantMessage>().orderByAsc(MerchantMessage::getStatus)
                        .orderByDesc(MerchantMessage::getCreateTime));
        return PageResult.of(page, this::toVO);
    }

    @Override
    public void replyMessage(Long id, String reply) {
        MerchantMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "留言不存在");
        }
        message.setReply(reply.trim());
        message.setStatus(2);
        message.setReplyTime(LocalDateTime.now());
        messageMapper.updateById(message);
    }

    @Override
    public Stall getCertificates(Long stallId) {
        return stallMapper.selectById(stallId);
    }

    @Override
    public StallCustomerStatsVO stallCustomerStats(Long stallId) {
        StallCustomerStatsVO vo = new StallCustomerStatsVO();
        // 每学生的已完成单量（仅统计来过的学生）
        List<Map<String, Object>> freq = orderMapper.aggregateStallUserFrequency(stallId);
        long totalDone = 0;
        long newCount = 0, regularCount = 0, veteranCount = 0;
        List<StudentFreqItem> students = new ArrayList<>();
        for (Map<String, Object> row : freq) {
            Number userIdNum = (Number) row.get("userId");
            Number cntNum = (Number) row.get("cnt");
            if (userIdNum == null || cntNum == null) {
                continue;
            }
            Long userId = userIdNum.longValue();
            long cnt = cntNum.longValue();
            totalDone += cnt;
            String tier = CustomerServiceDiscount.tierOf(cnt);
            if (CustomerServiceDiscount.TIER_NEW.equals(tier)) {
                newCount++;
            } else if (CustomerServiceDiscount.TIER_REGULAR.equals(tier)) {
                regularCount++;
            } else {
                veteranCount++;
            }
            StudentFreqItem item = new StudentFreqItem();
            item.setUserId(userId);
            item.setDoneCount(cnt);
            item.setTier(tier);
            User u = userMapper.selectById(userId);
            if (u != null) {
                item.setStudentName(u.getRealName());
                item.setStudentAvatar(u.getAvatar());
            }
            students.add(item);
        }
        // 按单量从高到低排
        students.sort(Comparator.comparingLong(StudentFreqItem::getDoneCount).reversed());

        vo.setStudents(students);
        vo.setTotalStudents(students.size());
        vo.setTotalDoneOrders(totalDone);
        vo.setNewCount(newCount);
        vo.setRegularCount(regularCount);
        vo.setVeteranCount(veteranCount);
        // 本档口累计发放的安抚券数
        vo.setDiscountsGranted(discountMapper.selectCount(
                new LambdaQueryWrapper<CustomerServiceDiscount>()
                        .eq(CustomerServiceDiscount::getStallId, stallId)));
        return vo;
    }

    @Override
    public List<MerchantCommStallVO> commStalls(Long adminId) {
        // 全部营业档口，按统计聚合销量/评分
        List<StallVO> raw = stallMapper.selectStallWithStats(null, null, null, 4);
        List<MerchantCommStallVO> result = new ArrayList<>();
        for (StallVO s : raw) {
            MerchantCommStallVO vo = new MerchantCommStallVO();
            vo.setId(s.getId());
            vo.setName(s.getName());
            vo.setWindowNo(s.getWindowNo());
            vo.setArea(s.getArea());
            vo.setFloor(s.getFloor());
            vo.setZone(formatZone2(s));
            vo.setCategory(s.getCategory());
            vo.setCoverImage(s.getCoverImage());
            vo.setMerchantAvatar(s.getMerchantAvatar());
            vo.setManagerName(s.getManagerName());
            vo.setAvgRating(s.getAvgRating());
            vo.setReviewCount(s.getReviewCount());
            vo.setGoodRate(s.getGoodRate());
            vo.setSalesCount(s.getSalesCount());
            vo.setWeightLabel("常规");
            vo.setWeightHint((s.getAvgRating() == null ? "—" : String.valueOf(s.getAvgRating()))
                    + " 分 · " + nvl(s.getReviewCount()) + " 条评价");
            // 权重：高权重档口优先提醒 —— 近7天有销量/差评集中/评分偏低 → 重点
            computeWeight(vo, s);
            // 未读留言数 + 最近往来
            List<MerchantMessage> thread = messageMapper.selectList(new LambdaQueryWrapper<MerchantMessage>()
                    .eq(MerchantMessage::getStallId, s.getId())
                    .orderByDesc(MerchantMessage::getCreateTime)
                    .orderByDesc(MerchantMessage::getId)
                    .last("LIMIT 5"));
            vo.setUnreadFromMerchant(messageMapper.selectCount(new LambdaQueryWrapper<MerchantMessage>()
                    .eq(MerchantMessage::getStallId, s.getId())
                    .eq(MerchantMessage::getDirection, MerchantMessage.DIR_IN)
                    .eq(MerchantMessage::getStatus, 0)));
            if (!thread.isEmpty()) {
                MerchantMessage last = thread.get(0);
                vo.setLastDirection(last.getDirection() == null ? MerchantMessage.DIR_IN : last.getDirection());
                vo.setLastAuthor(last.getAuthorRole() == null ? MerchantMessage.AUTHOR_MERCHANT : last.getAuthorRole());
                vo.setLastContent(truncate(last.getContent(), 60));
                vo.setLastTime(last.getCreateTime());
            }
            result.add(vo);
        }
        // 置顶：未读商家留言 > 高权重
        result.sort(Comparator
                .comparingLong(MerchantCommStallVO::getUnreadFromMerchant).reversed()
                .thenComparingInt(vo -> "HIGH".equals(vo.getWeight()) ? 0 : 1)
                .thenComparingInt(vo -> (int) Math.min(nvl(vo.getSalesCount()), Integer.MAX_VALUE)).reversed());
        return result;
    }

    private void computeWeight(MerchantCommStallVO vo, StallVO s) {
        boolean lowScore = s.getAvgRating() != null && s.getAvgRating() < 3.2 && nvl(s.getReviewCount()) >= 3;
        boolean highReview = nvl(s.getReviewCount()) >= 10;
        boolean highSales = nvl(s.getSalesCount()) >= 200;
        if (lowScore || (highReview && highSales)) {
            vo.setWeight("HIGH");
            vo.setWeightLabel("重点档口");
            if (lowScore) {
                vo.setWeightHint("平均分偏低 " + s.getAvgRating() + " · " + nvl(s.getReviewCount()) + " 条评价");
            } else {
                vo.setWeightHint("热度高 · 建议主动反馈");
            }
        } else {
            vo.setWeight("NORMAL");
        }
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static String formatZone2(StallVO s) {
        if (s.getArea() == null) {
            return "";
        }
        if ("north".equalsIgnoreCase(s.getArea())) {
            return "北区";
        }
        if ("south".equalsIgnoreCase(s.getArea())) {
            int floor = s.getFloor() == null ? 1 : s.getFloor();
            return "南区 " + floor + " 楼";
        }
        return s.getArea();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MerchantMessageVO toVO(MerchantMessage m) {
        MerchantMessageVO vo = new MerchantMessageVO();
        vo.setId(m.getId());
        vo.setStallId(m.getStallId());
        Stall stall = stallMapper.selectById(m.getStallId());
        vo.setStallName(stall == null ? "未知档口" : stall.getName());
        if (stall != null) {
            vo.setManagerName(stall.getManagerName());
            vo.setWindowNo(stall.getWindowNo());
            vo.setCoverImage(stall.getCoverImage());
            vo.setZone(formatZone(stall));
            // 优先展示商家账号头像（logo），而不是档口门面图
            User merchant = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getStallId, m.getStallId())
                    .eq(User::getRole, "MERCHANT")
                    .eq(User::getDeleted, 0)
                    .last("LIMIT 1"));
            if (merchant != null) {
                vo.setMerchantAvatar(merchant.getAvatar());
            }
        }
        vo.setDirection(m.getDirection() == null ? MerchantMessage.DIR_IN : m.getDirection());
        vo.setAuthorRole(m.getAuthorRole() == null ? MerchantMessage.AUTHOR_MERCHANT : m.getAuthorRole());
        vo.setNotice(m.getNotice() == null ? 0 : m.getNotice());
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setStatus(m.getStatus());
        vo.setStatusLabel(statusLabel(m.getStatus()));
        vo.setReply(m.getReply());
        vo.setReplyTime(m.getReplyTime());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }

    private static String formatZone(Stall stall) {
        if (stall.getArea() == null) {
            return "";
        }
        if ("north".equalsIgnoreCase(stall.getArea())) {
            return "北区";
        }
        if ("south".equalsIgnoreCase(stall.getArea())) {
            int floor = stall.getFloor() == null ? 1 : stall.getFloor();
            return "南区 " + floor + " 楼";
        }
        return stall.getArea();
    }

    private static String statusLabel(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "已读";
            case 2 -> "已回复";
            default -> "未读";
        };
    }
}
