package com.qmz.canteen.service;

import com.qmz.canteen.vo.AccountTransactionVO;

import java.math.BigDecimal;
import java.util.List;

public interface AccountTransactionService {

    /** 记录一笔余额变动（amount 正=收入，负=支出） */
    void record(Long userId, String type, BigDecimal amount, BigDecimal balanceAfter,
                String refNo, String remark);

    /** 查询账户明细（最近 N 条，倒序） */
    List<AccountTransactionVO> list(Long userId, Integer limit);
}
