package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.MerchantWithdrawal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface MerchantWithdrawalMapper extends BaseMapper<MerchantWithdrawal> {

    @Select("SELECT IFNULL(SUM(amount), 0) FROM merchant_withdrawal "
            + "WHERE stall_id = #{stallId} AND status = 'DONE' AND deleted = 0")
    BigDecimal sumWithdrawn(@Param("stallId") Long stallId);
}