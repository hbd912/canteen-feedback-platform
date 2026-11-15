package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.CustomerServiceDiscount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface CustomerServiceDiscountMapper extends BaseMapper<CustomerServiceDiscount> {

    /**
     * 该学生在该档口最近一次拿到安抚券的时间（用于冷却）。
     * 命中当前仍在 30 天冷却期则返回该行，无记录或已过期冷却返回 null。
     */
    @Select("SELECT * FROM customer_service_discount "
            + "WHERE stall_id = #{stallId} AND user_id = #{userId} AND deleted = 0 "
            + "AND grant_time >= #{since} ORDER BY grant_time DESC LIMIT 1")
    CustomerServiceDiscount selectRecent(@Param("stallId") Long stallId,
                                         @Param("userId") Long userId,
                                         @Param("since") LocalDateTime since);
}