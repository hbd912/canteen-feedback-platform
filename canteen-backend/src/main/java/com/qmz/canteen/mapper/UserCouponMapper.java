package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.UserCoupon;
import com.qmz.canteen.vo.UserCouponVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /** 我的优惠券（带券模板信息） */
    @Select("SELECT uc.id, uc.coupon_id AS couponId, c.name, c.type, c.scope, c.dish_id AS dishId, "
            + "d.name AS dishName, c.value, c.min_amount AS minAmount, "
            + "c.stall_id AS stallId, s.name AS stallName, c.end_time AS endTime, "
            + "uc.status, uc.receive_time AS receiveTime, uc.order_id AS orderId "
            + "FROM user_coupon uc "
            + "INNER JOIN coupon c ON c.id = uc.coupon_id AND c.deleted = 0 "
            + "LEFT JOIN canteen_stall s ON s.id = c.stall_id AND s.deleted = 0 "
            + "LEFT JOIN dish d ON d.id = c.dish_id AND d.deleted = 0 "
            + "WHERE uc.user_id = #{userId} AND uc.deleted = 0 "
            + "ORDER BY uc.id DESC")
    List<UserCouponVO> selectByUser(@Param("userId") Long userId);
}
