package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.DishAdRequest;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishAd;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.DishAdMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.service.DishAdService;
import com.qmz.canteen.vo.DishAdVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishAdServiceImpl implements DishAdService {

    private final DishAdMapper adMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;

    @Override
    @Transactional
    public DishAdVO apply(Long stallId, Long userId, DishAdRequest request) {
        Dish dish = dishMapper.selectById(request.getDishId());
        if (dish == null || !stallId.equals(dish.getStallId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "菜品不存在或不属于本档口");
        }
        if (!Integer.valueOf(1).equals(dish.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "下架菜品不能投广告");
        }
        long running = adMapper.selectCount(new LambdaQueryWrapper<DishAd>()
                .eq(DishAd::getDishId, dish.getId())
                .in(DishAd::getStatus, List.of(DishAd.STATUS_PENDING, DishAd.STATUS_ACTIVE)));
        if (running > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该菜品已有待处理或投放中的广告");
        }
        DishAd ad = new DishAd();
        ad.setStallId(stallId);
        ad.setDishId(dish.getId());
        ad.setPlanName(request.getPlanName() == null ? null : request.getPlanName().trim());
        ad.setAmount(request.getAmount());
        ad.setDays(request.getDays());
        ad.setStatus(DishAd.STATUS_PENDING);
        ad.setPayStatus(DishAd.PAY_UNPAID);
        ad.setCreatedBy(userId);
        adMapper.insert(ad);
        return toVO(ad);
    }

    @Override
    @Transactional
    public DishAdVO pay(Long id, Long stallId) {
        DishAd ad = ownedAd(id, stallId);
        if (!DishAd.STATUS_PENDING.equals(ad.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "当前状态不能支付");
        }
        if (DishAd.PAY_PAID.equals(ad.getPayStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该广告已支付");
        }
        ad.setPayStatus(DishAd.PAY_PAID);
        adMapper.updateById(ad);
        return toVO(ad);
    }

    @Override
    public List<DishAdVO> myAds(Long stallId) {
        List<DishAd> ads = adMapper.selectList(new LambdaQueryWrapper<DishAd>()
                .eq(DishAd::getStallId, stallId)
                .orderByDesc(DishAd::getId));
        List<DishAdVO> result = new ArrayList<>();
        for (DishAd ad : ads) {
            result.add(toVO(ad));
        }
        return result;
    }

    @Override
    @Transactional
    public void cancel(Long id, Long stallId) {
        DishAd ad = ownedAd(id, stallId);
        if (!List.of(DishAd.STATUS_PENDING, DishAd.STATUS_ACTIVE).contains(ad.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "当前状态不能取消");
        }
        ad.setStatus(DishAd.STATUS_CANCELED);
        adMapper.updateById(ad);
    }

    @Override
    public List<DishAdVO> adminList(String status, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<DishAd> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(DishAd::getStatus, status.trim().toUpperCase());
        }
        query.orderByDesc(DishAd::getId).last("LIMIT " + size);
        List<DishAd> ads = adMapper.selectList(query);
        List<DishAdVO> result = new ArrayList<>();
        for (DishAd ad : ads) {
            result.add(toVO(ad));
        }
        return result;
    }

    @Override
    @Transactional
    public DishAdVO approve(Long id, Long adminId) {
        DishAd ad = adMapper.selectById(id);
        if (ad == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "投放申请不存在");
        }
        if (!DishAd.STATUS_PENDING.equals(ad.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "只有待审核的申请可以通过");
        }
        if (!DishAd.PAY_PAID.equals(ad.getPayStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "商家尚未支付广告费，不能通过审核");
        }
        LocalDateTime now = LocalDateTime.now();
        ad.setStatus(DishAd.STATUS_ACTIVE);
        ad.setStartTime(now);
        ad.setEndTime(now.plusDays(ad.getDays() == null ? 7 : ad.getDays()));
        ad.setAuditedBy(adminId);
        ad.setAuditedTime(now);
        ad.setRejectReason(null);
        adMapper.updateById(ad);
        return toVO(ad);
    }

    @Override
    @Transactional
    public DishAdVO reject(Long id, Long adminId, String reason) {
        DishAd ad = adMapper.selectById(id);
        if (ad == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "投放申请不存在");
        }
        if (!DishAd.STATUS_PENDING.equals(ad.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "只有待审核的申请可以驳回");
        }
        ad.setStatus(DishAd.STATUS_REJECTED);
        ad.setRejectReason(reason == null ? null : reason.trim());
        ad.setAuditedBy(adminId);
        ad.setAuditedTime(LocalDateTime.now());
        adMapper.updateById(ad);
        return toVO(ad);
    }

    @Override
    public int expire() {
        return adMapper.expireActive();
    }

    private DishAd ownedAd(Long id, Long stallId) {
        DishAd ad = adMapper.selectById(id);
        if (ad == null || !stallId.equals(ad.getStallId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "投放记录不存在或不属于本档口");
        }
        return ad;
    }

    private DishAdVO toVO(DishAd ad) {
        DishAdVO vo = new DishAdVO();
        vo.setId(ad.getId());
        vo.setStallId(ad.getStallId());
        vo.setDishId(ad.getDishId());
        vo.setPlanName(ad.getPlanName());
        vo.setAmount(ad.getAmount());
        vo.setDays(ad.getDays());
        vo.setStartTime(ad.getStartTime());
        vo.setEndTime(ad.getEndTime());
        vo.setStatus(ad.getStatus());
        vo.setStatusLabel(label(ad));
        vo.setPayStatus(ad.getPayStatus());
        vo.setRejectReason(ad.getRejectReason());
        vo.setCreateTime(ad.getCreateTime());
        vo.setAuditedTime(ad.getAuditedTime());

        Dish dish = dishMapper.selectById(ad.getDishId());
        if (dish != null) {
            vo.setDishName(dish.getName());
            vo.setDishImage(dish.getImage());
        }
        Stall stall = stallMapper.selectById(ad.getStallId());
        if (stall != null) {
            vo.setStallName(stall.getName());
        }
        return vo;
    }

    private String label(DishAd ad) {
        return switch (ad.getStatus()) {
            case DishAd.STATUS_PENDING -> DishAd.PAY_PAID.equals(ad.getPayStatus()) ? "待审核" : "待支付";
            case DishAd.STATUS_ACTIVE -> "投放中";
            case DishAd.STATUS_REJECTED -> "已驳回";
            case DishAd.STATUS_EXPIRED -> "已结束";
            case DishAd.STATUS_CANCELED -> "已取消";
            default -> ad.getStatus();
        };
    }
}
