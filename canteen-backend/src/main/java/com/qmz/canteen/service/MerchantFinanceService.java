package com.qmz.canteen.service;

import com.qmz.canteen.vo.FinanceLedgerVO;
import com.qmz.canteen.vo.MerchantFinanceSummaryVO;

import java.math.BigDecimal;
import java.util.List;

public interface MerchantFinanceService {

    /** 财务概览：累计/今日/本月收益、已提现、可提现余额 */
    MerchantFinanceSummaryVO summary(Long stallId);

    /** 财务流水（订单收益 + 提现），按时间倒序，最多 200 条 */
    List<FinanceLedgerVO> ledger(Long stallId);

    /** 提现：从可提现余额里扣一笔 */
    MerchantFinanceSummaryVO withdraw(Long stallId, BigDecimal amount, String remark);

    /** AI 经营简报：基于真实财务数据生成一段解读与建议 */
    String aiReport(Long stallId);
}