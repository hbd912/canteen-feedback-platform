package com.qmz.canteen.service;

import com.qmz.canteen.dto.CouponRequest;
import com.qmz.canteen.entity.CouponOffer;
import com.qmz.canteen.vo.CouponVO;
import com.qmz.canteen.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /** 学生：可领取的券（平台券 + 指定档口券） */
    List<CouponVO> listAvailable(Long stallId, Long userId);

    /** 学生：我的券 */
    List<UserCouponVO> myCoupons(Long userId);

    /** 学生：领取 */
    UserCouponVO claim(Long couponId, Long userId);

    /** 系统/客服主动发券给指定学生（跳过学生扫码领取，仍计入库存额度与领取数） */
    UserCouponVO grantToUser(Long couponId, Long userId, Long createdBy);

    /** 结算：核销并返回抵扣金额 */
    CouponUseResult useCoupon(Long userCouponId, Long userId, Long stallId,
                              BigDecimal subtotal, Long orderId);

    /** 订单取消/关单：把券退回未使用 */
    void restoreByOrder(Long orderId);

    /** 商家：创建本档口券 */
    CouponVO createCoupon(Long stallId, Long createdBy, CouponRequest request);

    /** 商家：本档口券列表 */
    List<CouponVO> listMerchantCoupons(Long stallId);

    /** 客服发券：生成一张待领取凭证（学生点「领取」后才真正到账） */
    CouponOffer createOffer(Long stallId, Long userId, Long couponId, Long sessionId);

    /** 学生领取客服发来的券 */
    UserCouponVO claimOffer(Long offerId, Long userId);

    CouponOffer getOffer(Long offerId);

    /** 商家：启用/停用 */
    void updateStatus(Long couponId, Long stallId, Integer status);

    record CouponUseResult(Long couponId, BigDecimal discount) {
    }
}
