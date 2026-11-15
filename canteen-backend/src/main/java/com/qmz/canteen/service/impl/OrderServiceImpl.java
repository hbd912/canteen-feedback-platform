package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.common.enums.OrderStatus;
import com.qmz.canteen.dto.OrderItemRequest;
import com.qmz.canteen.dto.OrderQuery;
import com.qmz.canteen.dto.OrderRequest;
import com.qmz.canteen.entity.Coupon;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.DiningOrderItem;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.entity.RefundRequest;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.CouponMapper;
import com.qmz.canteen.mapper.DiningOrderItemMapper;
import com.qmz.canteen.mapper.DishAdMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.service.OrderService;
import com.qmz.canteen.service.CouponService;
import com.qmz.canteen.service.NotificationService;
import com.qmz.canteen.vo.OrderItemVO;
import com.qmz.canteen.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int MAX_ADVANCE_DAYS = 7;
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final DiningOrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final DishAdMapper dishAdMapper;
    private final StallMapper stallMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final CouponMapper couponMapper;
    private final CouponService couponService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Long createOrder(OrderRequest request, Long userId) {
        Stall stall = stallMapper.selectById(request.getStallId());
        if (stall == null || !Integer.valueOf(1).equals(stall.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "该档口不存在或已歇业");
        }
        if (request.getPickupTime().isAfter(LocalDateTime.now().plusDays(MAX_ADVANCE_DAYS))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "最多只能预约 " + MAX_ADVANCE_DAYS + " 天内的取餐时间");
        }
        if (request.getPickupTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "取餐时间不能早于当前时间");
        }

        List<Long> dishIds = request.getItems().stream().map(OrderItemRequest::getDishId).toList();
        if (dishIds.stream().distinct().count() != dishIds.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "同一菜品请不要重复选择");
        }
        Map<Long, Dish> dishMap = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .in(Dish::getId, dishIds))
                .stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        List<DiningOrderItem> items = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            Dish dish = dishMap.get(item.getDishId());
            if (dish == null || !Integer.valueOf(1).equals(dish.getStatus())) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "所选菜品不存在或已下架");
            }
            if (!dish.getStallId().equals(request.getStallId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "菜品「" + dish.getName() + "」不属于该档口");
            }
            if (Integer.valueOf(1).equals(dish.getSoldOut())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "「" + dish.getName() + "」已售罄");
            }
            // 原子扣库存：并发下单时由数据库保证不超卖
            int deducted = dishMapper.deductStock(dish.getId(), item.getQuantity());
            if (deducted == 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "「" + dish.getName() + "」库存不足");
            }
            DiningOrderItem entity = new DiningOrderItem();
            entity.setDishId(dish.getId());
            // 下单瞬间命中的广告才归因，避免投放结束后仍算转化
            entity.setAdId(dishAdMapper.findActiveAdId(dish.getId()));
            entity.setDishName(dish.getName());
            entity.setPrice(dish.getPrice());
            entity.setQuantity(item.getQuantity());
            items.add(entity);
            total = total.add(dish.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        DiningOrder order = new DiningOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setStallId(request.getStallId());
        order.setPickupTime(request.getPickupTime());
        order.setRemark(request.getRemark());
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.PENDING.name());
        orderMapper.insert(order);

        for (DiningOrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
        return order.getId();
    }

    @Override
    public PageResult<OrderVO> pageMyOrders(OrderQuery query, Long userId) {
        return queryPage(query, userId);
    }

    @Override
    public PageResult<OrderVO> pageAllOrders(OrderQuery query) {
        return queryPage(query, null);
    }

    @Override
    public OrderVO getOrderDetail(Long id, Long userId, boolean admin) {
        OrderVO order = orderMapper.selectOrderById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (!admin && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能查看自己的订单");
        }
        fillItems(List.of(order));
        return order;
    }

    @Override
    public PageResult<OrderVO> pageStallOrders(OrderQuery query, Long stallId) {
        query.setStallId(stallId);
        return queryPage(query, null);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, Long userId, boolean admin, Long merchantStallId) {
        OrderStatus target = OrderStatus.of(status);
        if (target == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "状态取值不合法");
        }
        DiningOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (merchantStallId != null) {
            // 商家：只能处理自己档口的订单，把待取餐改成已完成/已取消
            if (!merchantStallId.equals(order.getStallId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能处理自己档口的订单");
            }
            if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "只有待取餐的订单可以处理");
            }
            if (target == OrderStatus.PENDING) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能改回待取餐");
            }
            if (target == OrderStatus.DONE && !"PAID".equals(order.getPayStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "未支付订单不能出餐，请等待学生支付");
            }
            if (target == OrderStatus.CANCELED && "PAID".equals(order.getPayStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "已支付订单需走退款流程，不能直接取消");
            }
        } else if (!admin) {
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能操作自己的订单");
            }
            if (target != OrderStatus.CANCELED) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "只能取消自己的订单");
            }
            if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "只有待取餐的订单可以取消");
            }
            if ("PAID".equals(order.getPayStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "订单已支付，如需取消请联系商家退款");
            }
        } else {
            // 管理员：与商家同规则——只能处理「待取餐」订单，且支付状态必须匹配，
            // 防止「未支付就标记取餐」或「已支付直接取消不退钱」。
            if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该订单已处理，不能重复操作");
            }
            if (target == OrderStatus.PENDING) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能改回待取餐");
            }
            if (target == OrderStatus.DONE && !"PAID".equals(order.getPayStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "未支付订单不能标记取餐，请等待学生支付");
            }
            if (target == OrderStatus.CANCELED && "PAID".equals(order.getPayStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "已支付订单需走退款流程，不能直接取消");
            }
        }
        DiningOrder update = new DiningOrder();
        update.setId(order.getId());
        update.setStatus(target.name());
        orderMapper.updateById(update);

        // 出餐完成：通知学生取餐
        if (target == OrderStatus.DONE) {
            notificationService.notify(order.getUserId(), Notification.TYPE_ORDER_DONE,
                    "您的外卖已出餐完成，请前往档口取餐", "/my/orders", order.getId());
        }

        // 取消时回补库存与优惠券：只回补「未支付且原本待取餐」的订单，避免重复回补
        if (target == OrderStatus.CANCELED
                && OrderStatus.PENDING.name().equals(order.getStatus())
                && !"PAID".equals(order.getPayStatus())) {
            restoreStockAndCoupon(order.getId());
        }
    }

    private void restoreStockAndCoupon(Long orderId) {
        List<DiningOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<DiningOrderItem>().eq(DiningOrderItem::getOrderId, orderId));
        for (DiningOrderItem item : items) {
            if (item.getDishId() != null && item.getQuantity() != null) {
                dishMapper.restoreStock(item.getDishId(), item.getQuantity());
            }
        }
        couponService.restoreByOrder(orderId);
    }

    private PageResult<OrderVO> queryPage(OrderQuery query, Long userId) {
        Page<OrderVO> page = new Page<>(query.getCurrent(), query.getSize());
        IPage<OrderVO> result = orderMapper.selectOrderPage(page, query, userId);
        fillItems(result.getRecords());
        return PageResult.of(result);
    }

    private void fillItems(List<OrderVO> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(OrderVO::getId).toList();
        Map<Long, List<OrderItemVO>> grouped = orderMapper.selectItemsByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemVO::getOrderId));

        // 部分退款细分：一次性捞出这些订单已退款(REFUNDED)的退款单，按 orderId 分组。
        // 每单至多一条已退申请（RefundServiceImpl.apply 有约束），itemIds 逗号串 = 实际退掉的菜品项。
        Map<Long, List<RefundRequest>> refundedByOrder = refundRequestMapper.selectList(
                        new LambdaQueryWrapper<RefundRequest>()
                                .in(RefundRequest::getOrderId, orderIds)
                                .eq(RefundRequest::getStatus, RefundRequest.STATUS_REFUNDED))
                .stream()
                .collect(Collectors.groupingBy(RefundRequest::getOrderId));

        // 一次性取出这些用户已评价过的菜品，避免逐单查库
        List<Long> userIds = orders.stream().map(OrderVO::getUserId).distinct().toList();
        Set<String> reviewed = new HashSet<>(orderMapper.selectReviewedPairs(userIds));

        // 批量取订单所用券的适用范围：整单券(ORDER)订单只能整单退款，前端据此禁用部分退款
        List<Long> couponIds = orders.stream()
                .map(OrderVO::getCouponId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> couponScopes = couponIds.isEmpty()
                ? Map.of()
                : couponMapper.selectBatchIds(couponIds).stream()
                    .collect(Collectors.toMap(Coupon::getId,
                            c -> c.getScope() == null ? Coupon.SCOPE_ORDER : c.getScope()));

        orders.forEach(order -> {
            List<OrderItemVO> items = grouped.getOrDefault(order.getId(), List.of());
            order.setItems(items);
            order.setCouponScope(order.getCouponId() == null ? null : couponScopes.get(order.getCouponId()));
            OrderStatus status = OrderStatus.of(order.getStatus());
            String statusLabel = status == null ? order.getStatus() : status.getLabel();
            // 未支付却停在 PENDING 的订单，实际语义是「待支付」而非「待取餐」，
            // 否则学生下单后没付钱就看到「待取餐」，状态机混乱。
            if (status == OrderStatus.PENDING && !"PAID".equals(order.getPayStatus())) {
                statusLabel = "待支付";
            }
            order.setStatusLabel(statusLabel);
            order.setPayStatusLabel(payStatusLabel(order.getPayStatus()));

            // 标记每道菜是否已退、是否部分退款（仅展示层，不改状态机与库存）
            List<RefundRequest> refundedReqs = refundedByOrder.getOrDefault(order.getId(), List.of());
            if (!refundedReqs.isEmpty()) {
                RefundRequest first = refundedReqs.get(0);
                Set<Long> refundedIds = parseRefundedIds(first.getItemIds());
                boolean fullRefund = refundedIds.isEmpty(); // itemIds 为空 = 整单退
                List<Long> actualRefunded = new ArrayList<>();
                for (OrderItemVO item : items) {
                    boolean refunded = fullRefund || refundedIds.contains(item.getId());
                    item.setRefunded(refunded);
                    if (refunded) {
                        actualRefunded.add(item.getId());
                    }
                }
                boolean partial = !fullRefund && !actualRefunded.isEmpty() && actualRefunded.size() < items.size();
                order.setPartialRefund(partial);
                order.setRefundedItemIds(actualRefunded);
                if (partial) {
                    order.setStatusLabel("部分退款");
                }
            }

            List<Long> unreviewed = items.stream()
                    .map(OrderItemVO::getDishId)
                    .filter(dishId -> !reviewed.contains(order.getUserId() + ":" + dishId))
                    .distinct()
                    .toList();
            order.setUnreviewedDishIds(unreviewed);
            if (!"PAID".equals(order.getPayStatus())
                    || "CANCELED".equals(order.getStatus())
                    || "REFUNDED".equals(order.getStatus())) {
                order.setReviewStatus("NONE");
                order.setReviewStatusLabel(null);
            } else if (unreviewed.isEmpty()) {
                order.setReviewStatus("REVIEWED");
                order.setReviewStatusLabel("已评价");
            } else {
                order.setReviewStatus("UNREVIEWED");
                order.setReviewStatusLabel("待评价");
            }
        });
    }

    private static String payStatusLabel(String payStatus) {
        if (payStatus == null) {
            return null;
        }
        return switch (payStatus) {
            case "PAID" -> "已支付";
            case "CLOSED" -> "已关闭";
            case "REFUNDED" -> "已退款";
            default -> "待支付";
        };
    }

    private String generateOrderNo() {
        return "CT" + LocalDateTime.now().format(ORDER_NO_TIME)
                + (1000 + ThreadLocalRandom.current().nextInt(9000));
    }

    /** 解析退款单的 itemIds 逗号串为菜品项ID集合；null/空串 = 整单退款，返回空集合 */
    private Set<Long> parseRefundedIds(String itemIds) {
        Set<Long> ids = new HashSet<>();
        if (itemIds == null || itemIds.isBlank()) {
            return ids;
        }
        for (String part : itemIds.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 非数字片段直接跳过
            }
        }
        return ids;
    }
}
