package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.CustomerServiceMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CustomerServiceMessageMapper extends BaseMapper<CustomerServiceMessage> {

    /** 某会话全部消息（按时间正序，聊天展示用） */
    @Select("SELECT * FROM customer_service_message WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY id ASC")
    List<CustomerServiceMessage> listBySession(@Param("sessionId") Long sessionId);

    @Select("SELECT COUNT(*) FROM customer_service_message WHERE deleted = 0")
    long countAll();

    @Select("SELECT COUNT(*) FROM customer_service_message WHERE deleted = 0 AND sender_type = 'AI'")
    long countAi();
}
