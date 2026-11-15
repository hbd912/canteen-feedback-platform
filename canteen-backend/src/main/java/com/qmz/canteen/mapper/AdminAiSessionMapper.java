package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.AdminAiSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AdminAiSessionMapper extends BaseMapper<AdminAiSession> {

    /** 某管理员的全部会话，按最近更新倒序 */
    @Select("SELECT * FROM admin_ai_session WHERE user_id = #{userId} AND deleted = 0 "
            + "ORDER BY update_time DESC, id DESC LIMIT 50")
    List<AdminAiSession> selectByUser(@Param("userId") Long userId);

    /** 更新最后一条消息摘要与时间 */
    @Update("UPDATE admin_ai_session SET last_message = #{content}, last_message_time = #{time} WHERE id = #{id}")
    int updateLastMessage(@Param("id") Long id, @Param("content") String content,
                          @Param("time") java.time.LocalDateTime time);
}
