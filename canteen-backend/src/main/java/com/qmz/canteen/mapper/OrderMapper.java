package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qmz.canteen.dto.OrderQuery;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.vo.OrderItemVO;
import com.qmz.canteen.vo.OrderVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderMapper extends BaseMapper<DiningOrder> {

    IPage<OrderVO> selectOrderPage(IPage<?> page,
                                   @Param("query") OrderQuery query,
                                   @Param("userId") Long userId);

    OrderVO selectOrderById(@Param("id") Long id);

    List<OrderItemVO> selectItemsByOrderIds(@Param("orderIds") List<Long> orderIds);

    /** 批量查用户已评价的 (userId, dishId)，用于订单的评价状态标签 */
    List<String> selectReviewedPairs(@Param("userIds") List<Long> userIds);

    /** 真实销量聚合（已支付且未取消），返回 [{dishId, quantity, revenue}] */
    List<Map<String, Object>> aggregateStallDishSales(@Param("stallId") Long stallId,
                                                      @Param("since") LocalDateTime since);

    /** 某学生在该档口的已完成(DONE)订单数，用于「老顾客」分层 */
    @Select("SELECT COUNT(*) FROM dining_order WHERE user_id = #{userId} "
            + "AND stall_id = #{stallId} AND status = 'DONE' AND deleted = 0")
    long countDoneByUserStall(@Param("userId") Long userId, @Param("stallId") Long stallId);

    /** 该档口每个学生的已完成(DONE)单量按人数聚合，返回 [{userId, cnt}]，仅统计来过的学生 */
    @Select("SELECT user_id AS userId, COUNT(*) AS cnt FROM dining_order "
            + "WHERE stall_id = #{stallId} AND status = 'DONE' AND deleted = 0 AND user_id IS NOT NULL "
            + "GROUP BY user_id")
    List<Map<String, Object>> aggregateStallUserFrequency(@Param("stallId") Long stallId);

    /** 该档口已完成(DONE)订单总数（去重学生数与订单数分开算） */
    @Select("SELECT COUNT(*) FROM dining_order WHERE stall_id = #{stallId} "
            + "AND status = 'DONE' AND deleted = 0")
    long countDoneByStall(@Param("stallId") Long stallId);
}
