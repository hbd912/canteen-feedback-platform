package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.config.CommerceProperties;
import com.qmz.canteen.entity.AccountTransaction;
import com.qmz.canteen.entity.BalanceRecharge;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.BalanceRechargeMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.payment.PayGateway;
import com.qmz.canteen.payment.PayOrder;
import com.qmz.canteen.payment.PayTradeResult;
import com.qmz.canteen.payment.PayTradeState;
import com.qmz.canteen.service.AccountTransactionService;
import com.qmz.canteen.service.BalanceRechargeService;
import com.qmz.canteen.vo.RechargeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 余额充值：给学生账户充值，触发支付网关（mock 自动成功 / 支付宝沙箱），
 * 支付成功后原子累加 sys_user.balance。与「下单支付」共用同一套支付通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceRechargeServiceImpl implements BalanceRechargeService {

    private static final DateTimeFormatter PAYNO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BalanceRechargeMapper rechargeMapper;
    private final UserMapper userMapper;
    private final CommerceProperties props;
    private final List<PayGateway> gateways;
    private final AccountTransactionService accountTransactionService;

    @Override
    @Transactional
    public RechargeVO recharge(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "充值金额至少 0.01 元");
        }
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "单次充值不能超过 10000 元");
        }
        amount = props.getCheckout().money(amount);

        String channel = resolveChannel();
        PayGateway gw = gateway(channel);
        if (gw == null) {
            throw new BusinessException("支付通道不可用");
        }

        String payNo = "RC" + LocalDateTime.now().format(PAYNO_TIME)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        BalanceRecharge rec = new BalanceRecharge();
        rec.setUserId(userId);
        rec.setAmount(amount);
        rec.setPayNo(payNo);
        rec.setChannel(channel);
        rec.setStatus(BalanceRecharge.STATUS_CREATED);
        rec.setCreateTime(LocalDateTime.now());
        rechargeMapper.insert(rec);

        String payUrl = gw.payUrl(PayOrder.builder()
                .payNo(payNo)
                .amount(amount)
                .subject("食堂账户余额充值")
                .notifyUrl(props.getPay().notifyUrl())
                .returnUrl(props.getPay().getReturnUrl())
                .build());

        // mock 通道自动成功，模拟「已支付」后累加余额
        if ("mock".equals(channel) && props.getPay().getMock().isAutoSuccess()) {
            markPaid(rec, "MOCK" + System.currentTimeMillis());
        }

        return toVO(rec, payUrl);
    }

    @Override
    @Transactional
    public RechargeVO status(String payNo, Long userId) {
        BalanceRecharge rec = byPayNo(payNo);
        if (rec == null || !rec.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "充值单不存在");
        }
        // 待支付时向支付宝主动查单：本机开发收不到异步通知，只能靠这一步确认付款结果
        if (BalanceRecharge.STATUS_CREATED.equals(rec.getStatus())) {
            syncFromGateway(rec);
        }
        return toVO(rec, null);
    }

    /** 主动查单：付款成功后累加余额；交易关闭则置为已关闭 */
    private void syncFromGateway(BalanceRecharge rec) {
        if ("mock".equals(rec.getChannel())) {
            return;
        }
        PayGateway gw = gateway(rec.getChannel());
        if (gw == null || !gw.ready()) {
            return;
        }
        PayTradeResult result;
        try {
            result = gw.query(rec.getPayNo());
        } catch (Exception e) {
            log.warn("充值查单失败 payNo={}：{}", rec.getPayNo(), e.getMessage());
            return;
        }
        if (result == null) {
            return;
        }
        if (result.state() == PayTradeState.SUCCESS) {
            markPaid(rec, result.tradeNo());
        } else if (result.state() == PayTradeState.CLOSED) {
            rec.setStatus(BalanceRecharge.STATUS_CLOSED);
            rechargeMapper.updateById(rec);
        }
    }

    @Override
    @Transactional
    public RechargeVO mockSuccess(String payNo, Long userId) {
        BalanceRecharge rec = byPayNo(payNo);
        if (rec == null || !rec.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "充值单不存在");
        }
        if (!"mock".equals(rec.getChannel())) {
            throw new BusinessException("只有模拟支付通道可以手动确认");
        }
        if (BalanceRecharge.STATUS_PAID.equals(rec.getStatus())) {
            return toVO(rec, null);
        }
        if (BalanceRecharge.STATUS_CLOSED.equals(rec.getStatus())) {
            throw new BusinessException("该充值单已关闭，请重新充值");
        }
        markPaid(rec, "MOCK" + System.currentTimeMillis());
        return toVO(rec, null);
    }

    @Override
    public List<RechargeVO> records(Long userId) {
        return rechargeMapper.selectList(new LambdaQueryWrapper<BalanceRecharge>()
                        .eq(BalanceRecharge::getUserId, userId)
                        .orderByDesc(BalanceRecharge::getId)
                        .last("LIMIT 50"))
                .stream().map(r -> toVO(r, null)).toList();
    }

    /** 充值成功：更新充值单状态 + 原子累加账户余额 */
    private void markPaid(BalanceRecharge rec, String tradeNo) {
        if (BalanceRecharge.STATUS_PAID.equals(rec.getStatus())) {
            return;
        }
        rec.setStatus(BalanceRecharge.STATUS_PAID);
        rec.setTradeNo(tradeNo);
        rec.setPaidTime(LocalDateTime.now());
        rechargeMapper.updateById(rec);
        userMapper.incrBalance(rec.getUserId(), rec.getAmount());
        User user = userMapper.selectById(rec.getUserId());
        accountTransactionService.record(rec.getUserId(), AccountTransaction.TYPE_RECHARGE,
                rec.getAmount(), user.getBalance(), rec.getPayNo(), "余额充值");
        log.info("余额充值成功：payNo={} userId={} amount={}", rec.getPayNo(), rec.getUserId(), rec.getAmount());
    }

    private BalanceRecharge byPayNo(String payNo) {
        return rechargeMapper.selectOne(new LambdaQueryWrapper<BalanceRecharge>()
                .eq(BalanceRecharge::getPayNo, payNo)
                .last("LIMIT 1"));
    }

    /** 与下单支付一致的通道降级策略 */
    private String resolveChannel() {
        String configured = props.getPay().getChannel();
        if (configured != null && !configured.isBlank()) {
            PayGateway gw = gateway(configured);
            if (gw != null && gw.healthy()) {
                return gw.channel();
            }
        }
        PayGateway alipay = gateway("alipay");
        if (alipay != null && alipay.healthy()) {
            return "alipay";
        }
        return "mock";
    }

    private PayGateway gateway(String channel) {
        if (channel == null || gateways == null) {
            return null;
        }
        return gateways.stream()
                .filter(g -> g.channel().equalsIgnoreCase(channel))
                .findFirst()
                .orElse(null);
    }

    private RechargeVO toVO(BalanceRecharge rec, String payUrl) {
        RechargeVO vo = new RechargeVO();
        vo.setId(rec.getId());
        vo.setAmount(rec.getAmount());
        vo.setPayNo(rec.getPayNo());
        vo.setChannel(rec.getChannel());
        vo.setStatus(rec.getStatus());
        vo.setStatusLabel(label(rec.getStatus()));
        vo.setPayUrl(payUrl);
        vo.setPaid(BalanceRecharge.STATUS_PAID.equals(rec.getStatus()));
        vo.setCreateTime(rec.getCreateTime());
        vo.setPaidTime(rec.getPaidTime());
        return vo;
    }

    private static String label(String status) {
        return switch (status) {
            case BalanceRecharge.STATUS_PAID -> "已到账";
            case BalanceRecharge.STATUS_CLOSED -> "已关闭";
            default -> "待支付";
        };
    }
}
