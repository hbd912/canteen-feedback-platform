package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.MerchantCsConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MerchantCsConfigMapper extends BaseMapper<MerchantCsConfig> {

    @Select("SELECT * FROM merchant_cs_config WHERE stall_id = #{stallId} AND deleted = 0 LIMIT 1")
    MerchantCsConfig selectByStall(@Param("stallId") Long stallId);
}
