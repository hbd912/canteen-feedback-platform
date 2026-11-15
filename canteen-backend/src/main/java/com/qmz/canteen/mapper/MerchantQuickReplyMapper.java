package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.MerchantQuickReply;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MerchantQuickReplyMapper extends BaseMapper<MerchantQuickReply> {

    @Select("SELECT * FROM merchant_quick_reply WHERE stall_id = #{stallId} AND deleted = 0 "
            + "ORDER BY sort ASC, id ASC")
    List<MerchantQuickReply> selectByStall(@Param("stallId") Long stallId);
}
