package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.AdminAiMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AdminAiMessageMapper extends BaseMapper<AdminAiMessage> {

    /** 某会话的全部消息，按 id 正序 */
    @Select("SELECT * FROM admin_ai_message WHERE session_id = #{sessionId} AND deleted = 0 "
            + "ORDER BY id ASC")
    List<AdminAiMessage> listBySession(@Param("sessionId") Long sessionId);
}
