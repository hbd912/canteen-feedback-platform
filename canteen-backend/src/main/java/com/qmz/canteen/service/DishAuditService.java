package com.qmz.canteen.service;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.vo.DishAuditVO;

public interface DishAuditService {

    /** 管理员分页查看菜品审核列表；status 为空表示全部 */
    PageResult<DishAuditVO> pageAudits(String status, Long current, Long size);

    /** 审核通过：仅待审核菜品可操作，通过后立即上架并通知商家 */
    void approve(Long dishId, Long adminId);

    /** 审核驳回：仅待审核菜品可操作，记录原因、保持下架并通知商家 */
    void reject(Long dishId, Long adminId, String reason);
}
