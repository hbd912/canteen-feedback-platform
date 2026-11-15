package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.Coupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CouponMapper extends BaseMapper<Coupon> {

    /** 原子占用一张券：库存为 0 表示不限量；返回 0 表示已抢完或已停用 */
    @Update("UPDATE coupon SET issued_qty = issued_qty + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND status = 1 "
            + "AND (total_qty = 0 OR issued_qty < total_qty)")
    int incrIssued(@Param("id") Long id);
}
