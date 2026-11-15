package com.qmz.canteen.service;

import com.qmz.canteen.vo.RefundVO;

import java.util.List;

public interface RefundService {

    /** 学生：对已支付订单申请退款（itemIds 为空=整单退款，否则按菜品部分退款） */
    RefundVO apply(Long orderId, Long userId, String reason, List<Long> itemIds);

    /** 学生：我的退款记录 */
    List<RefundVO> myRefunds(Long userId);

    /** 商家：本档口退款列表 */
    List<RefundVO> merchantList(Long stallId, String status);

    /** 管理员：全部退款列表 */
    List<RefundVO> adminList(String status);

    /** 商家/管理员：同意并原路退款 */
    RefundVO approve(Long refundId, Long operatorId, boolean admin, Long stallId);

    /** 商家/管理员：驳回 */
    RefundVO reject(Long refundId, Long operatorId, boolean admin, Long stallId, String remark);
}
