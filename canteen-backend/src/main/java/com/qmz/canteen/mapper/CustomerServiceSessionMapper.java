package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.CustomerServiceSession;
import com.qmz.canteen.vo.CustomerServiceSessionVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CustomerServiceSessionMapper extends BaseMapper<CustomerServiceSession> {

    /** 某学生对某档口的最近一条「活跃(OPEN)」会话（用于「联系客服」时复用；仅匹配未关闭、未被他隐藏的会话） */
    @Select("SELECT * FROM customer_service_session "
            + "WHERE stall_id = #{stallId} AND user_id = #{userId} AND status = 'OPEN' AND deleted = 0 "
            + "AND student_deleted = 0 "
            + "ORDER BY update_time DESC, id DESC LIMIT 1")
    CustomerServiceSession selectOpenByStallAndUser(@Param("stallId") Long stallId, @Param("userId") Long userId);

    /** 关闭某会话（归档；多会话切换时把上一个会话置为 CLOSED） */
    @Update("UPDATE customer_service_session SET status = 'CLOSED' WHERE id = #{id}")
    int close(@Param("id") Long id);

    /** 某档口的全部会话（商家侧，按最近更新倒序；过滤商家自己删掉的） */
    @Select("SELECT * FROM customer_service_session WHERE stall_id = #{stallId} AND deleted = 0 "
            + "AND merchant_deleted = 0 ORDER BY update_time DESC, id DESC")
    List<CustomerServiceSession> selectByStall(@Param("stallId") Long stallId);

    /** 某学生的全部会话（学生侧，按最近更新倒序；过滤学生自己删掉的） */
    @Select("SELECT * FROM customer_service_session WHERE user_id = #{userId} AND deleted = 0 "
            + "AND student_deleted = 0 ORDER BY update_time DESC, id DESC")
    List<CustomerServiceSession> selectByUser(@Param("userId") Long userId);

    /** 学生侧删除/隐藏一个会话（不动商家侧与消息记录） */
    @Update("UPDATE customer_service_session SET student_deleted = 1 WHERE id = #{id} AND user_id = #{userId}")
    int deleteForStudent(@Param("id") Long id, @Param("userId") Long userId);

    /** 商家侧删除/隐藏一个会话（不动学生侧与消息记录） */
    @Update("UPDATE customer_service_session SET merchant_deleted = 1 WHERE id = #{id} AND stall_id = #{stallId}")
    int deleteForMerchant(@Param("id") Long id, @Param("stallId") Long stallId);

    /** 有新消息时恢复被商家隐藏的会话（否则学生再发消息商家收不到） */
    @Update("UPDATE customer_service_session SET merchant_deleted = 0 WHERE id = #{id}")
    int restoreForMerchant(@Param("id") Long id);

    /** 有新消息时恢复被学生隐藏的会话 */
    @Update("UPDATE customer_service_session SET student_deleted = 0 WHERE id = #{id}")
    int restoreForStudent(@Param("id") Long id);

    /** 学生「全部已读」：清零该学生所有会话的学生未读 */
    @Update("UPDATE customer_service_session SET student_unread = 0 "
            + "WHERE user_id = #{userId} AND deleted = 0 AND student_deleted = 0")
    int resetAllStudentUnread(@Param("userId") Long userId);

    /** 商家「全部已读」：清零该档口所有会话的商家未读 */
    @Update("UPDATE customer_service_session SET merchant_unread = 0 "
            + "WHERE stall_id = #{stallId} AND deleted = 0 AND merchant_deleted = 0")
    int resetAllMerchantUnread(@Param("stallId") Long stallId);

    /** 商家未读 +1（原子） */
    @Update("UPDATE customer_service_session SET merchant_unread = merchant_unread + 1 WHERE id = #{id}")
    int incrMerchantUnread(@Param("id") Long id);

    /** 学生未读 +1（原子） */
    @Update("UPDATE customer_service_session SET student_unread = student_unread + 1 WHERE id = #{id}")
    int incrStudentUnread(@Param("id") Long id);

    /** 商家未读清零 */
    @Update("UPDATE customer_service_session SET merchant_unread = 0 WHERE id = #{id}")
    int resetMerchantUnread(@Param("id") Long id);

    /** 学生未读清零 */
    @Update("UPDATE customer_service_session SET student_unread = 0 WHERE id = #{id}")
    int resetStudentUnread(@Param("id") Long id);

    /** 更新最后一条消息摘要与时间 */
    @Update("UPDATE customer_service_session SET last_message = #{content}, "
            + "last_message_time = #{time}, status = 'OPEN' WHERE id = #{id}")
    int updateLastMessage(@Param("id") Long id, @Param("content") String content,
                          @Param("time") java.time.LocalDateTime time);

    /** 管理员：全量会话（联档口名与学生名） */
    @Select("SELECT s.id, s.stall_id, s.user_id, s.order_id, s.last_message, s.last_message_time, "
            + "s.student_unread, s.merchant_unread, s.status, s.create_time, "
            + "st.name AS stall_name, IFNULL(u.real_name, '已注销') AS student_name, "
            + "IFNULL(u.avatar, '') AS student_avatar "
            + "FROM customer_service_session s "
            + "LEFT JOIN canteen_stall st ON st.id = s.stall_id "
            + "LEFT JOIN sys_user u ON u.id = s.user_id "
            + "WHERE s.deleted = 0 ORDER BY s.update_time DESC, s.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<CustomerServiceSessionVO> adminList(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM customer_service_session WHERE deleted = 0")
    long countAll();

    @Select("SELECT COUNT(*) FROM customer_service_session WHERE deleted = 0 AND status = 'OPEN'")
    long countOpen();

    @Select("SELECT COUNT(*) FROM customer_service_session WHERE deleted = 0 AND merchant_unread > 0")
    long countUnhandled();
}
