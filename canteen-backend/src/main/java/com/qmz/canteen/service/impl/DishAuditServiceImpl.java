package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.service.DishAuditService;
import com.qmz.canteen.service.NotificationService;
import com.qmz.canteen.vo.DishAuditVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DishAuditServiceImpl implements DishAuditService {

    private static final Set<String> AUDIT_STATUSES =
            Set.of(Dish.AUDIT_PENDING, Dish.AUDIT_APPROVED, Dish.AUDIT_REJECTED);

    private final DishMapper dishMapper;
    private final NotificationService notificationService;

    @Override
    public PageResult<DishAuditVO> pageAudits(String status, Long current, Long size) {
        String normalizedStatus = normalizeStatus(status);
        long pageNo = current == null || current < 1 ? 1 : current;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        IPage<DishAuditVO> result = dishMapper.selectAuditPage(new Page<>(pageNo, pageSize), normalizedStatus);
        return PageResult.of(result);
    }

    @Override
    @Transactional
    public void approve(Long dishId, Long adminId) {
        Dish dish = requireDish(dishId);
        LocalDateTime now = LocalDateTime.now();
        int updated = dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, dishId)
                .eq(Dish::getAuditStatus, Dish.AUDIT_PENDING)
                .set(Dish::getAuditStatus, Dish.AUDIT_APPROVED)
                .set(Dish::getStatus, 1)
                .set(Dish::getAuditBy, adminId)
                .set(Dish::getAuditTime, now)
                .set(Dish::getAuditRemark, null)
                .set(Dish::getUpdateTime, now));
        if (updated == 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(),
                    "该菜品已被其他管理员审核，请刷新后重试");
        }
        notificationService.notifyMerchant(dish.getStallId(), Notification.TYPE_ADMIN_NOTICE,
                "菜品「" + dish.getName() + "」审核通过，已上架", "/merchant/dishes", dishId);
    }

    @Override
    @Transactional
    public void reject(Long dishId, Long adminId, String reason) {
        String cleanReason = reason == null ? null : reason.trim();
        if (!StringUtils.hasText(cleanReason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写驳回原因");
        }
        if (cleanReason.length() > 300) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "驳回原因不能超过300字");
        }

        Dish dish = requireDish(dishId);
        LocalDateTime now = LocalDateTime.now();
        int updated = dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, dishId)
                .eq(Dish::getAuditStatus, Dish.AUDIT_PENDING)
                .set(Dish::getAuditStatus, Dish.AUDIT_REJECTED)
                .set(Dish::getStatus, 0)
                .set(Dish::getAuditRemark, cleanReason)
                .set(Dish::getAuditBy, adminId)
                .set(Dish::getAuditTime, now)
                .set(Dish::getUpdateTime, now));
        if (updated == 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(),
                    "该菜品已被其他管理员审核，请刷新后重试");
        }
        notificationService.notifyMerchant(dish.getStallId(), Notification.TYPE_ADMIN_NOTICE,
                "菜品「" + dish.getName() + "」审核未通过：" + cleanReason, "/merchant/dishes", dishId);
    }

    private Dish requireDish(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "菜品不存在");
        }
        return dish;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!AUDIT_STATUSES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "审核状态不合法");
        }
        return normalized;
    }
}
