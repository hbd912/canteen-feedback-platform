package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.entity.AccountTransaction;
import com.qmz.canteen.mapper.AccountTransactionMapper;
import com.qmz.canteen.service.AccountTransactionService;
import com.qmz.canteen.vo.AccountTransactionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountTransactionServiceImpl implements AccountTransactionService {

    private final AccountTransactionMapper mapper;

    @Override
    public void record(Long userId, String type, BigDecimal amount, BigDecimal balanceAfter,
                       String refNo, String remark) {
        AccountTransaction tx = new AccountTransaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalance(balanceAfter);
        tx.setRefNo(refNo);
        tx.setRemark(remark);
        tx.setCreateTime(LocalDateTime.now());
        mapper.insert(tx);
    }

    @Override
    public List<AccountTransactionVO> list(Long userId, Integer limit) {
        int n = limit == null ? 100 : Math.min(Math.max(limit, 1), 200);
        return mapper.selectList(new LambdaQueryWrapper<AccountTransaction>()
                        .eq(AccountTransaction::getUserId, userId)
                        .orderByDesc(AccountTransaction::getId)
                        .last("LIMIT " + n))
                .stream().map(this::toVO).toList();
    }

    private AccountTransactionVO toVO(AccountTransaction tx) {
        AccountTransactionVO vo = new AccountTransactionVO();
        vo.setId(tx.getId());
        vo.setType(tx.getType());
        vo.setTypeLabel(label(tx.getType()));
        vo.setAmount(tx.getAmount());
        vo.setBalance(tx.getBalance());
        vo.setRefNo(tx.getRefNo());
        vo.setRemark(tx.getRemark());
        vo.setCreateTime(tx.getCreateTime());
        return vo;
    }

    private static String label(String type) {
        return switch (type) {
            case AccountTransaction.TYPE_RECHARGE -> "充值";
            case AccountTransaction.TYPE_CONSUME -> "消费";
            case AccountTransaction.TYPE_REFUND -> "退款";
            default -> type;
        };
    }
}
