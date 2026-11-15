package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.MerchantWithdrawal;
import com.qmz.canteen.mapper.MerchantWithdrawalMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.service.MerchantFinanceService;
import com.qmz.canteen.vo.FinanceLedgerVO;
import com.qmz.canteen.vo.MerchantFinanceSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 商家财务。收益口径与经营分析/销售情况一致：已支付订单金额之和。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantFinanceServiceImpl implements MerchantFinanceService {

    private final OrderMapper orderMapper;
    private final MerchantWithdrawalMapper withdrawalMapper;
    private final MultiModelRouter router;

    @Override
    public MerchantFinanceSummaryVO summary(Long stallId) {
        List<DiningOrder> orders = ordersOf(stallId);
        MerchantFinanceSummaryVO vo = new MerchantFinanceSummaryVO();
        vo.setOrderCount(orders.size());
        vo.setCustomerCount(orders.stream().map(DiningOrder::getUserId)
                .filter(Objects::nonNull).distinct().count());

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal todayIncome = BigDecimal.ZERO;
        BigDecimal monthIncome = BigDecimal.ZERO;
        long paidCount = 0;
        for (DiningOrder o : orders) {
            if (!"PAID".equals(o.getPayStatus()) || o.getTotalAmount() == null) {
                continue;
            }
            paidCount++;
            BigDecimal amount = o.getTotalAmount();
            total = total.add(amount);
            LocalDateTime paidAt = o.getPaidTime() == null ? o.getCreateTime() : o.getPaidTime();
            if (paidAt != null && !paidAt.isBefore(todayStart)) {
                todayIncome = todayIncome.add(amount);
            }
            if (paidAt != null && !paidAt.isBefore(monthStart)) {
                monthIncome = monthIncome.add(amount);
            }
        }
        BigDecimal withdrawn = withdrawalMapper.sumWithdrawn(stallId);
        if (withdrawn == null) {
            withdrawn = BigDecimal.ZERO;
        }
        BigDecimal available = total.subtract(withdrawn);
        if (available.signum() < 0) {
            available = BigDecimal.ZERO;
        }
        vo.setTotalIncome(money(total));
        vo.setTodayIncome(money(todayIncome));
        vo.setMonthIncome(money(monthIncome));
        vo.setWithdrawn(money(withdrawn));
        vo.setAvailable(money(available));
        vo.setPaidOrderCount(paidCount);
        vo.setAvgOrderAmount(paidCount == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP));
        return vo;
    }

    @Override
    public List<FinanceLedgerVO> ledger(Long stallId) {
        List<FinanceLedgerVO> list = new ArrayList<>();
        for (DiningOrder o : ordersOf(stallId)) {
            if (!"PAID".equals(o.getPayStatus()) || o.getTotalAmount() == null) {
                continue;
            }
            FinanceLedgerVO item = new FinanceLedgerVO();
            item.setType(FinanceLedgerVO.TYPE_INCOME);
            item.setRefId(o.getId());
            item.setTitle("订单收入");
            item.setSubtitle(o.getOrderNo());
            item.setAmount(o.getTotalAmount());
            item.setTime(o.getPaidTime() == null ? o.getCreateTime() : o.getPaidTime());
            item.setStatus(o.getStatus());
            list.add(item);
        }
        List<MerchantWithdrawal> withdrawals = withdrawalMapper.selectList(
                new LambdaQueryWrapper<MerchantWithdrawal>()
                        .eq(MerchantWithdrawal::getStallId, stallId)
                        .orderByDesc(MerchantWithdrawal::getCreateTime)
                        .last("LIMIT 200"));
        for (MerchantWithdrawal w : withdrawals) {
            FinanceLedgerVO item = new FinanceLedgerVO();
            item.setType(FinanceLedgerVO.TYPE_WITHDRAW);
            item.setRefId(w.getId());
            item.setTitle("提现");
            item.setSubtitle(w.getRemark() == null || w.getRemark().isBlank() ? "提现到账户" : w.getRemark());
            item.setAmount(w.getAmount() == null ? BigDecimal.ZERO : w.getAmount().negate());
            item.setTime(w.getCreateTime());
            item.setStatus(w.getStatus());
            list.add(item);
        }
        list.sort(Comparator.comparing(FinanceLedgerVO::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return list.size() > 200 ? new ArrayList<>(list.subList(0, 200)) : list;
    }

    @Override
    @Transactional
    public MerchantFinanceSummaryVO withdraw(Long stallId, BigDecimal amount, String remark) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "提现金额必须大于 0");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        MerchantFinanceSummaryVO before = summary(stallId);
        if (amount.compareTo(before.getAvailable()) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "可提现余额不足，当前可提现 ¥" + before.getAvailable().toPlainString());
        }
        MerchantWithdrawal w = new MerchantWithdrawal();
        w.setStallId(stallId);
        w.setAmount(amount);
        w.setStatus(MerchantWithdrawal.STATUS_DONE);
        w.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        w.setCreateTime(LocalDateTime.now());
        withdrawalMapper.insert(w);
        return summary(stallId);
    }

    @Override
    public String aiReport(Long stallId) {
        MerchantFinanceSummaryVO s = summary(stallId);
        String data = "累计收益 " + s.getTotalIncome() + " 元，可提现 " + s.getAvailable()
                + " 元，本月收益 " + s.getMonthIncome() + " 元，今日收益 " + s.getTodayIncome()
                + " 元；累计订单 " + s.getOrderCount() + " 笔（已支付 " + s.getPaidOrderCount()
                + " 笔），客户 " + s.getCustomerCount() + " 位，客单价 " + s.getAvgOrderAmount() + " 元。";
        String system = "你是校园食堂档口的经营助理。只根据给出的真实数据写一段 150 字以内的中文经营简报："
                + "先一句话总结，再指出最多 2 个值得关注的点，最后给 2 条可执行建议。不要编造任何数字，不要使用 markdown 标题。";
        try {
            AiResult ai = router.complete(ModelRole.REPORT, system, data, 600, 0.4);
            if (ai != null && ai.content() != null && !ai.content().isBlank()) {
                return ai.content().trim();
            }
        } catch (Exception e) {
            log.warn("AI 经营简报生成失败，返回数据摘要：{}", e.getMessage());
        }
        return "累计收益 " + s.getTotalIncome() + " 元，可提现 " + s.getAvailable()
                + " 元，本月 " + s.getMonthIncome() + " 元；共 " + s.getOrderCount()
                + " 笔订单、" + s.getCustomerCount() + " 位客户。建议关注差评与出餐效率。";
    }

    private List<DiningOrder> ordersOf(Long stallId) {
        return orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getStallId, stallId));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}