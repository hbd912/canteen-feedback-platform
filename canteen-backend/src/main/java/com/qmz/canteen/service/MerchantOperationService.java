package com.qmz.canteen.service;

import com.qmz.canteen.vo.MerchantDiagnosisVO;
import com.qmz.canteen.vo.MerchantOperationActionVO;

import java.util.List;

public interface MerchantOperationService {

    /** 对档口做一次 AI 经营诊断，生成并持久化待审批建议，返回诊断总结与建议列表 */
    MerchantDiagnosisVO diagnose(Long stallId, int days);

    /** 本档口待审批的经营建议 */
    List<MerchantOperationActionVO> listActions(Long stallId);

    /** 审批通过并执行（仅下架这类低风险动作；服务端重新校验归属与状态） */
    MerchantOperationActionVO approve(Long stallId, Long actionId);

    /** 驳回建议 */
    void reject(Long stallId, Long actionId);
}
