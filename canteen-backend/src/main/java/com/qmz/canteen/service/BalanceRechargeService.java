package com.qmz.canteen.service;

import com.qmz.canteen.vo.RechargeVO;

import java.math.BigDecimal;
import java.util.List;

public interface BalanceRechargeService {

    /** 发起余额充值：创建充值单并触发支付网关，mock 通道自动成功并累加余额 */
    RechargeVO recharge(Long userId, BigDecimal amount);

    /** 查询充值单状态 */
    RechargeVO status(String payNo, Long userId);

    /** mock 通道手动确认充值成功（autoSuccess=false 时兜底） */
    RechargeVO mockSuccess(String payNo, Long userId);

    /** 我的充值记录 */
    List<RechargeVO> records(Long userId);
}
