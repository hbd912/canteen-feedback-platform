package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.CouponRequest;
import com.qmz.canteen.entity.Coupon;
import com.qmz.canteen.entity.CouponOffer;
import com.qmz.canteen.entity.DiningOrderItem;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.entity.UserCoupon;
import com.qmz.canteen.mapper.CouponMapper;
import com.qmz.canteen.mapper.CouponOfferMapper;
import com.qmz.canteen.mapper.DiningOrderItemMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.mapper.UserCouponMapper;
import com.qmz.canteen.service.CouponService;
import com.qmz.canteen.vo.CouponVO;
import com.qmz.canteen.vo.UserCouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final StallMapper stallMapper;
    private final DishMapper dishMapper;
    private final DiningOrderItemMapper orderItemMapper;
    private final CouponOfferMapper couponOfferMapper;

    @Override
    public List<CouponVO> listAvailable(Long stallId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Coupon> query = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .eq(Coupon::getPrivateGift, 0)
                .and(w -> w.isNull(Coupon::getStartTime).or().le(Coupon::getStartTime, now))
                .and(w -> w.isNull(Coupon::getEndTime).or().ge(Coupon::getEndTime, now))
                .and(w -> w.eq(Coupon::getTotalQty, 0).or().apply("issued_qty < total_qty"));
        if (stallId != null) {
            query.and(w -> w.isNull(Coupon::getStallId).or().eq(Coupon::getStallId, stallId));
        }
        // 不传档口（领券中心）时返回全平台券 + 所有档口券，按新到旧排序
        query.orderByDesc(Coupon::getId);

        List<Coupon> coupons = couponMapper.selectList(query);
        // 填充档口名，供领券中心展示
        List<Long> stallIds = coupons.stream().map(Coupon::getStallId).filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> stallNames = stallIds.isEmpty() ? java.util.Map.of()
                : stallMapper.selectBatchIds(stallIds).stream()
                        .collect(java.util.stream.Collectors.toMap(com.qmz.canteen.entity.Stall::getId,
                                s -> s.getName() == null ? "" : s.getName()));
        List<CouponVO> result = new ArrayList<>();
        for (Coupon c : coupons) {
            CouponVO vo = toVO(c, false);
            if (c.getStallId() != null) {
                vo.setStallName(stallNames.getOrDefault(c.getStallId(), ""));
            }
            if (userId != null && c.getPerUserLimit() != null) {
                long owned = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getCouponId, c.getId())
                        .eq(UserCoupon::getUserId, userId));
                vo.setClaimed(owned >= c.getPerUserLimit());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<UserCouponVO> myCoupons(Long userId) {
        return userCouponMapper.selectByUser(userId);
    }

    @Override
    @Transactional
    public UserCouponVO claim(Long couponId, Long userId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券还没到领取时间");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券已过期");
        }
        long owned = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId)
                .eq(UserCoupon::getUserId, userId));
        int limit = coupon.getPerUserLimit() == null ? 1 : coupon.getPerUserLimit();
        if (owned >= limit) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "你已领取过这张券");
        }
        // 原子扣减库存，防止并发超发
        int affected = couponMapper.incrIssued(couponId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "优惠券已被抢完");
        }
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        uc.setStatus(UserCoupon.STATUS_UNUSED);
        uc.setReceiveTime(now);
        userCouponMapper.insert(uc);
        return toUserVO(uc, coupon);
    }

    @Override
    @Transactional
    public UserCouponVO grantToUser(Long couponId, Long userId, Long createdBy) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券还没到领取时间");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券已过期");
        }
        // 原子扣减库存，防止并发超发；为 0 表示不限量
        int affected = couponMapper.incrIssued(couponId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "优惠券已被抢完");
        }
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        uc.setStatus(UserCoupon.STATUS_UNUSED);
        uc.setReceiveTime(now);
        userCouponMapper.insert(uc);
        return toUserVO(uc, coupon);
    }

    @Override
    @Transactional
    public CouponUseResult useCoupon(Long userCouponId, Long userId, Long stallId,
                                     BigDecimal subtotal, Long orderId) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在");
        }
        if (!UserCoupon.STATUS_UNUSED.equals(uc.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "优惠券已使用或已过期");
        }
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券已过期");
        }
        if (coupon.getStallId() != null && !coupon.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "该券不能用于其他档口");
        }
        BigDecimal min = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
        if (subtotal.compareTo(min) < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "满 " + min.stripTrailingZeros().toPlainString() + " 元才能使用该券");
        }

        // 单份菜券：只按「一道菜的一份」抵扣，不与其他菜/份数累加
        BigDecimal base = subtotal;
        if (Coupon.SCOPE_ITEM.equals(coupon.getScope())) {
            List<DiningOrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<DiningOrderItem>().eq(DiningOrderItem::getOrderId, orderId));
            DiningOrderItem target;
            if (coupon.getDishId() != null) {
                target = items.stream()
                        .filter(i -> coupon.getDishId().equals(i.getDishId()))
                        .findFirst().orElse(null);
                if (target == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "该券限指定菜品，本单未包含");
                }
            } else {
                target = items.stream()
                        .filter(i -> i.getPrice() != null)
                        .max(java.util.Comparator.comparing(DiningOrderItem::getPrice))
                        .orElse(null);
            }
            if (target == null || target.getPrice() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "本单没有可使用该券的菜品");
            }
            base = target.getPrice();
        }

        BigDecimal discount;
        if (Coupon.TYPE_DISCOUNT.equals(coupon.getType())) {
            BigDecimal rate = coupon.getValue();
            discount = base.multiply(BigDecimal.ONE.subtract(rate));
        } else {
            discount = coupon.getValue() == null ? BigDecimal.ZERO : coupon.getValue();
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(base) > 0) {
            discount = base;
        }
        // 抵扣不超过整单金额
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        uc.setStatus(UserCoupon.STATUS_USED);
        uc.setUseTime(LocalDateTime.now());
        uc.setOrderId(orderId);
        userCouponMapper.updateById(uc);
        return new CouponUseResult(coupon.getId(), discount);
    }

    @Override
    @Transactional
    public void restoreByOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        List<UserCoupon> used = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, orderId)
                .eq(UserCoupon::getStatus, UserCoupon.STATUS_USED));
        for (UserCoupon uc : used) {
            // updateById 会忽略 null，必须用 UpdateWrapper 才能把 orderId/useTime 清空
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, uc.getId())
                    .set(UserCoupon::getStatus, UserCoupon.STATUS_UNUSED)
                    .set(UserCoupon::getOrderId, null)
                    .set(UserCoupon::getUseTime, null));
        }
    }

    @Override
    @Transactional
    public CouponVO createCoupon(Long stallId, Long createdBy, CouponRequest request) {
        validateRequest(request);
        Coupon c = new Coupon();
        c.setStallId(stallId);
        c.setName(request.getName().trim());
        c.setType(request.getType());
        c.setScope(Coupon.SCOPE_ITEM.equals(request.getScope()) ? Coupon.SCOPE_ITEM : Coupon.SCOPE_ORDER);
        c.setDishId(Coupon.SCOPE_ITEM.equals(c.getScope()) ? request.getDishId() : null);
        c.setValue(request.getValue());
        c.setMinAmount(request.getMinAmount() == null ? BigDecimal.ZERO : request.getMinAmount());
        c.setTotalQty(request.getTotalQty() == null ? 0 : request.getTotalQty());
        c.setIssuedQty(0);
        c.setPerUserLimit(request.getPerUserLimit() == null ? 1 : request.getPerUserLimit());
        c.setStartTime(request.getStartTime());
        c.setEndTime(request.getEndTime());
        c.setStatus(Integer.valueOf(1).equals(request.getStatus()) ? 1 : 0);
        c.setPrivateGift(Integer.valueOf(1).equals(request.getPrivateGift()) ? 1 : 0);
        c.setCreatedBy(createdBy);
        couponMapper.insert(c);
        return toVO(c, false);
    }

    @Override
    public List<CouponVO> listMerchantCoupons(Long stallId) {
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStallId, stallId)
                .orderByDesc(Coupon::getId));
        List<CouponVO> result = new ArrayList<>();
        for (Coupon c : coupons) {
            result.add(toVO(c, false));
        }
        return result;
    }

    @Override
    @Transactional
    public void updateStatus(Long couponId, Long stallId, Integer status) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || !stallId.equals(c.getStallId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在");
        }
        c.setStatus(Integer.valueOf(1).equals(status) ? 1 : 0);
        couponMapper.updateById(c);
    }

    private void validateRequest(CouponRequest request) {
        if (Coupon.TYPE_DISCOUNT.equals(request.getType())) {
            if (request.getValue().compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "折扣必须小于1（如0.9表示九折）");
            }
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "结束时间不能早于开始时间");
        }
    }

    @Override
    @Transactional
    public CouponOffer createOffer(Long stallId, Long userId, Long couponId, Long sessionId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || stallId == null || !stallId.equals(coupon.getStallId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在或不属于本档口");
        }
        CouponOffer offer = new CouponOffer();
        offer.setStallId(stallId);
        offer.setUserId(userId);
        offer.setCouponId(couponId);
        offer.setSessionId(sessionId);
        offer.setStatus(CouponOffer.STATUS_UNCLAIMED);
        offer.setCreateTime(LocalDateTime.now());
        couponOfferMapper.insert(offer);
        return offer;
    }

    @Override
    @Transactional
    public UserCouponVO claimOffer(Long offerId, Long userId) {
        CouponOffer offer = couponOfferMapper.selectById(offerId);
        if (offer == null || userId == null || !userId.equals(offer.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在");
        }
        if (CouponOffer.STATUS_CLAIMED.equals(offer.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该券已领取过了");
        }
        Coupon coupon = couponMapper.selectById(offer.getCouponId());
        if (coupon == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券还没到领取时间");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "优惠券已过期");
        }
        UserCouponVO vo = grantToUser(offer.getCouponId(), userId, offer.getStallId());
        offer.setStatus(CouponOffer.STATUS_CLAIMED);
        offer.setClaimTime(now);
        couponOfferMapper.updateById(offer);
        return vo;
    }

    @Override
    public CouponOffer getOffer(Long offerId) {
        return offerId == null ? null : couponOfferMapper.selectById(offerId);
    }

    private CouponVO toVO(Coupon c, boolean claimed) {
        CouponVO vo = new CouponVO();
        vo.setId(c.getId());
        vo.setStallId(c.getStallId());
        vo.setName(c.getName());
        vo.setType(c.getType());
        vo.setScope(c.getScope() == null ? Coupon.SCOPE_ORDER : c.getScope());
        vo.setDishId(c.getDishId());
        if (c.getDishId() != null) {
            Dish dish = dishMapper.selectById(c.getDishId());
            vo.setDishName(dish == null ? null : dish.getName());
        }
        vo.setValue(c.getValue());
        vo.setMinAmount(c.getMinAmount());
        vo.setTotalQty(c.getTotalQty());
        vo.setIssuedQty(c.getIssuedQty());
        vo.setPerUserLimit(c.getPerUserLimit());
        vo.setStartTime(c.getStartTime());
        vo.setEndTime(c.getEndTime());
        vo.setStatus(c.getStatus());
        vo.setClaimed(claimed);
        if (c.getStallId() != null) {
            Stall stall = stallMapper.selectById(c.getStallId());
            vo.setStallName(stall == null ? null : stall.getName());
        } else {
            vo.setStallName("全场通用");
        }
        return vo;
    }

    private UserCouponVO toUserVO(UserCoupon uc, Coupon c) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setCouponId(c.getId());
        vo.setName(c.getName());
        vo.setType(c.getType());
        vo.setValue(c.getValue());
        vo.setMinAmount(c.getMinAmount());
        vo.setStallId(c.getStallId());
        vo.setEndTime(c.getEndTime());
        vo.setStatus(uc.getStatus());
        vo.setReceiveTime(uc.getReceiveTime());
        if (c.getStallId() != null) {
            Stall stall = stallMapper.selectById(c.getStallId());
            vo.setStallName(stall == null ? null : stall.getName());
        } else {
            vo.setStallName("全场通用");
        }
        return vo;
    }
}
