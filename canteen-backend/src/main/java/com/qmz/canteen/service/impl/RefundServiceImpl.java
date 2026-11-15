package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.AccountTransaction;
import com.qmz.canteen.entity.Coupon;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.DiningOrderItem;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.entity.PayRecord;
import com.qmz.canteen.entity.RefundRequest;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.CouponMapper;
import com.qmz.canteen.mapper.DiningOrderItemMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.PayRecordMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.payment.PayGateway;
import com.qmz.canteen.payment.PayRefund;
import com.qmz.canteen.payment.RefundResult;
import com.qmz.canteen.service.AccountTransactionService;
import com.qmz.canteen.service.NotificationService;
import com.qmz.canteen.service.RefundService;
import com.qmz.canteen.vo.RefundVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 退款链路：学生申请 -> 商家/管理员审核 -> 调支付网关原路退回。
 * 退款成功与否以网关返回为准，本地只记录结果，不自行判定成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRequestMapper refundMapper;
    private final OrderMapper orderMapper;
    private final PayRecordMapper payRecordMapper;
    private final UserMapper userMapper;
    private final StallMapper stallMapper;
    private final DiningOrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final CouponMapper couponMapper;
    private final List<PayGateway> gateways;
    private final TransactionTemplate transactionTemplate;
    private final AccountTransactionService accountTransactionService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RefundVO apply(Long orderId, Long userId, String reason, List<Long> itemIds) {
        DiningOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "只有已支付订单可以申请退款");
        }
        if ("CANCELED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该订单当前状态不能退款");
        }
        // 已完成（已出餐/已取餐）的订单不允许自助退款，防止「吃完再退」刷羊毛；
        // 确有纠纷走客服人工处理。
        if ("DONE".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "订单已完成，不能申请退款，如有纠纷请联系客服");
        }
        long existing = refundMapper.selectCount(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .in(RefundRequest::getStatus,
                        List.of(RefundRequest.STATUS_PENDING, RefundRequest.STATUS_REFUNDED)));
        if (existing > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该订单已有处理中或已退款的申请");
        }

        // 整单券（scope=ORDER）：券是按整单打折的，菜品原价之和 ≠ 实付金额。
        // 此时只允许整单退款，且按券后实付金额退回，禁止部分退款按原价多退。
        boolean orderLevelCoupon = false;
        if (order.getCouponId() != null) {
            Coupon coupon = couponMapper.selectById(order.getCouponId());
            // 券记录缺失时保守按整单券处理（宁可只允许整单退，也不按原价超额退）
            orderLevelCoupon = coupon == null
                    || coupon.getScope() == null
                    || Coupon.SCOPE_ORDER.equals(coupon.getScope());
        }

        // 确定退款菜品项：itemIds 为空=整单退款，否则只退所选菜品
        List<DiningOrderItem> allItems = orderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                .eq(DiningOrderItem::getOrderId, orderId));
        List<DiningOrderItem> refundItems;
        if (itemIds == null || itemIds.isEmpty()) {
            refundItems = allItems;
        } else {
            Set<Long> picked = new HashSet<>(itemIds);
            refundItems = allItems.stream().filter(i -> picked.contains(i.getId())).toList();
            if (refundItems.isEmpty() || refundItems.size() != picked.size()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "退款菜品选择不合法，请重新选择");
            }
        }
        boolean fullRefund = refundItems.size() == allItems.size();
        if (orderLevelCoupon && !fullRefund) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "该订单使用了整单优惠券，只能整单退款，不支持部分退款");
        }
        BigDecimal amount;
        if (orderLevelCoupon) {
            // 整单券订单只能整单退：按实付金额（券后价）退回，避免按原价多退
            amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        } else {
            amount = refundItems.stream()
                    .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        String itemIdsStr = refundItems.stream().map(i -> String.valueOf(i.getId()))
                .collect(Collectors.joining(","));
        String itemSummary = refundItems.stream()
                .map(i -> i.getDishName() + "×" + i.getQuantity())
                .collect(Collectors.joining("、"));

        RefundRequest refund = new RefundRequest();
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setStallId(order.getStallId());
        refund.setAmount(amount);
        refund.setItemIds(itemIdsStr);
        refund.setItemSummary(itemSummary);
        refund.setReason(reason == null ? null : reason.trim());
        refund.setStatus(RefundRequest.STATUS_PENDING);
        refundMapper.insert(refund);

        DiningOrder update = new DiningOrder();
        update.setId(orderId);
        update.setRefundStatus(RefundRequest.STATUS_PENDING);
        orderMapper.updateById(update);

        // 退款审核归商家端；管理员只留档，通知措辞不能写成"请审核"，否则管理员会以为要自己批
        notificationService.notifyMerchant(refund.getStallId(), Notification.TYPE_REFUND_APPLY,
                "收到退款申请（" + order.getOrderNo() + "），请及时审核", "/merchant/refunds", refund.getId());
        notificationService.notifyAdmins(Notification.TYPE_REFUND_APPLY,
                "收到退款申请（" + order.getOrderNo() + "），已转交商家审核，后台仅作记录", "/admin/refunds", refund.getId());
        return toVO(refund);
    }

    @Override
    public List<RefundVO> myRefunds(Long userId) {
        return toVOs(refundMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getUserId, userId)
                .orderByDesc(RefundRequest::getId)
                .last("LIMIT 100")));
    }

    @Override
    public List<RefundVO> merchantList(Long stallId, String status) {
        LambdaQueryWrapper<RefundRequest> query = new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getStallId, stallId);
        if (status != null && !status.isBlank()) {
            query.eq(RefundRequest::getStatus, status.trim().toUpperCase());
        }
        query.orderByDesc(RefundRequest::getId).last("LIMIT 100");
        return toVOs(refundMapper.selectList(query));
    }

    @Override
    public List<RefundVO> adminList(String status) {
        LambdaQueryWrapper<RefundRequest> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(RefundRequest::getStatus, status.trim().toUpperCase());
        }
        query.orderByDesc(RefundRequest::getId).last("LIMIT 100");
        return toVOs(refundMapper.selectList(query));
    }

    @Override
    public RefundVO approve(Long refundId, Long operatorId, boolean admin, Long stallId) {
        RefundRequest refund = requireOwned(refundId, admin, stallId);
        if (!RefundRequest.STATUS_PENDING.equals(refund.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "只有待审核的退款可以处理");
        }
        DiningOrder order = orderMapper.selectById(refund.getOrderId());
        if (order == null || !"PAID".equals(order.getPayStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "订单不是已支付状态，无法退款");
        }
        String refundNo = "RF" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        transactionTemplate.executeWithoutResult(status -> {
            // 下单已改为余额扣款，退款直接回补账户余额（不再依赖支付网关）
            userMapper.incrBalance(refund.getUserId(), refund.getAmount());
            User user = userMapper.selectById(refund.getUserId());
            accountTransactionService.record(refund.getUserId(), AccountTransaction.TYPE_REFUND,
                    refund.getAmount(), user.getBalance(), order.getOrderNo(), "退款回补");

            refund.setStatus(RefundRequest.STATUS_REFUNDED);
            refund.setRefundNo(refundNo);
            refund.setHandledBy(operatorId);
            refund.setHandledTime(LocalDateTime.now());
            refund.setFailReason(null);
            refundMapper.updateById(refund);

            DiningOrder update = new DiningOrder();
            update.setId(order.getId());
            update.setPayStatus("REFUNDED");
            update.setStatus("REFUNDED");
            update.setRefundStatus(RefundRequest.STATUS_REFUNDED);
            orderMapper.updateById(update);

            // 退款发生在「待取餐」阶段（餐未出），菜品未售出，需回补库存避免永久占用；
            // 部分退款只回补所选菜品。
            restoreStock(order.getId(), refund.getItemIds());
        });
        // 通知学生退款已通过
        notificationService.notify(refund.getUserId(), Notification.TYPE_REFUND_RESULT,
                "您的退款申请已通过，¥" + refund.getAmount() + " 已退回余额", "/my/orders", refund.getOrderId());
        log.info("退款成功：refundId={} orderId={} refundNo={}", refundId, refund.getOrderId(), refundNo);
        return toVO(refund);
    }

    @Override
    @Transactional
    public RefundVO reject(Long refundId, Long operatorId, boolean admin, Long stallId, String remark) {
        RefundRequest refund = requireOwned(refundId, admin, stallId);
        if (!RefundRequest.STATUS_PENDING.equals(refund.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "只有待审核的退款可以驳回");
        }
        refund.setStatus(RefundRequest.STATUS_REJECTED);
        refund.setRemark(remark == null ? null : remark.trim());
        refund.setHandledBy(operatorId);
        refund.setHandledTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        DiningOrder update = new DiningOrder();
        update.setId(refund.getOrderId());
        update.setRefundStatus(RefundRequest.STATUS_REJECTED);
        orderMapper.updateById(update);

        // 通知学生退款被驳回
        notificationService.notify(refund.getUserId(), Notification.TYPE_REFUND_RESULT,
                "您的退款申请被驳回：" + (remark == null || remark.isBlank() ? "请联系商家" : remark.trim()),
                "/my/orders", refund.getOrderId());
        return toVO(refund);
    }

    private RefundVO markFailed(RefundRequest refund, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            refund.setStatus(RefundRequest.STATUS_FAILED);
            refund.setFailReason(reason == null ? "退款失败" : reason);
            refund.setHandledTime(LocalDateTime.now());
            refundMapper.updateById(refund);

            DiningOrder update = new DiningOrder();
            update.setId(refund.getOrderId());
            update.setRefundStatus(RefundRequest.STATUS_FAILED);
            orderMapper.updateById(update);
        });
        log.warn("退款失败：refundId={} reason={}", refund.getId(), reason);
        return toVO(refund);
    }

    /** 退款成功后回补订单菜品库存；itemIdsStr 非空则只回补所选菜品，空=整单回补 */
    private void restoreStock(Long orderId, String itemIdsStr) {
        Set<Long> ids = parseIds(itemIdsStr);
        List<DiningOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<DiningOrderItem>().eq(DiningOrderItem::getOrderId, orderId));
        for (DiningOrderItem item : items) {
            if (ids != null && !ids.isEmpty() && !ids.contains(item.getId())) {
                continue;
            }
            if (item.getDishId() != null && item.getQuantity() != null) {
                dishMapper.restoreStock(item.getDishId(), item.getQuantity());
            }
        }
    }

    private Set<Long> parseIds(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Arrays.stream(s.split(",")).map(String::trim)
                    .filter(x -> !x.isEmpty()).map(Long::valueOf).collect(Collectors.toSet());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RefundRequest requireOwned(Long refundId, boolean admin, Long stallId) {
        RefundRequest refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "退款申请不存在");
        }
        if (!admin && (stallId == null || !stallId.equals(refund.getStallId()))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "退款申请不属于本档口");
        }
        return refund;
    }

    private List<RefundVO> toVOs(List<RefundRequest> list) {
        List<RefundVO> result = new ArrayList<>();
        for (RefundRequest refund : list) {
            result.add(toVO(refund));
        }
        return result;
    }

    private RefundVO toVO(RefundRequest refund) {
        RefundVO vo = new RefundVO();
        vo.setId(refund.getId());
        vo.setOrderId(refund.getOrderId());
        vo.setUserId(refund.getUserId());
        vo.setStallId(refund.getStallId());
        vo.setAmount(refund.getAmount());
        vo.setReason(refund.getReason());
        vo.setItemSummary(refund.getItemSummary());
        vo.setStatus(refund.getStatus());
        vo.setStatusLabel(label(refund.getStatus()));
        vo.setRefundNo(refund.getRefundNo());
        vo.setFailReason(refund.getFailReason());
        vo.setRemark(refund.getRemark());
        vo.setCreateTime(refund.getCreateTime());
        vo.setHandledTime(refund.getHandledTime());

        DiningOrder order = orderMapper.selectById(refund.getOrderId());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
        }
        User user = userMapper.selectById(refund.getUserId());
        if (user != null) {
            vo.setUserName(user.getRealName());
        }
        Stall stall = stallMapper.selectById(refund.getStallId());
        if (stall != null) {
            vo.setStallName(stall.getName());
        }
        return vo;
    }

    private static String label(String status) {
        return switch (status) {
            case RefundRequest.STATUS_PENDING -> "待审核";
            case RefundRequest.STATUS_REFUNDED -> "已退款";
            case RefundRequest.STATUS_REJECTED -> "已驳回";
            case RefundRequest.STATUS_FAILED -> "退款失败";
            default -> status;
        };
    }
}
