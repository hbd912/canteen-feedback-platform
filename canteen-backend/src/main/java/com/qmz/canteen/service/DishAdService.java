package com.qmz.canteen.service;

import com.qmz.canteen.dto.DishAdRequest;
import com.qmz.canteen.vo.DishAdVO;

import java.util.List;

public interface DishAdService {

    /** 商家：申请投放（PENDING + UNPAID） */
    DishAdVO apply(Long stallId, Long userId, DishAdRequest request);

    /** 商家：模拟支付（演示环境，不接真实支付网关） */
    DishAdVO pay(Long id, Long stallId);

    /** 商家：本档口投放列表 */
    List<DishAdVO> myAds(Long stallId);

    /** 商家：取消申请/投放 */
    void cancel(Long id, Long stallId);

    /** 管理员：投放列表（可按状态过滤） */
    List<DishAdVO> adminList(String status, int limit);

    /** 管理员：审核通过（必须已支付） */
    DishAdVO approve(Long id, Long adminId);

    /** 管理员：驳回 */
    DishAdVO reject(Long id, Long adminId, String reason);

    /** 定时清理过期投放 */
    int expire();
}
