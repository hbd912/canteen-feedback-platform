package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qmz.canteen.dto.ReviewQuery;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.vo.ReviewVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface DishReviewMapper extends BaseMapper<DishReview> {

    IPage<ReviewVO> selectReviewPage(IPage<?> page, @Param("query") ReviewQuery query,
                                     @Param("currentUserId") Long currentUserId);

    /** 商家端：分页查询本档口菜品的学生评价 */
    IPage<ReviewVO> selectMerchantReviewPage(IPage<?> page, @Param("query") ReviewQuery query,
                                             @Param("stallId") Long stallId);

    /** 按评价ID + 档口ID 校验收属（评价对应菜品必须属于该档口） */
    @Select("SELECT r.* FROM dish_review r "
            + "INNER JOIN dish d ON r.dish_id = d.id AND d.deleted = 0 "
            + "WHERE r.id = #{reviewId} AND d.stall_id = #{stallId} AND r.deleted = 0 LIMIT 1")
    DishReview selectByIdAndStallId(@Param("reviewId") Long reviewId, @Param("stallId") Long stallId);

    /**
     * 查出该用户对该菜品的评价，包含已逻辑删除的记录。
     * dish_review 上 (dish_id, user_id) 是唯一键，逻辑删除的记录仍占位，
     * 只查未删除的会导致再次评价时 insert 撞唯一键。
     */
    @Select("SELECT * FROM dish_review WHERE dish_id = #{dishId} AND user_id = #{userId} LIMIT 1")
    DishReview selectOneIncludeDeleted(@Param("dishId") Long dishId, @Param("userId") Long userId);

    /** 更新并把逻辑删除标记复位为 0（复活历史评价） */
    @Update("UPDATE dish_review SET rating = #{rating}, content = #{content}, anonymous = #{anonymous}, "
            + "status = #{status}, deleted = 0, create_time = NOW() WHERE id = #{id}")
    int restoreAndUpdate(DishReview review);

    /** 点赞数 +1（原子） */
    @Update("UPDATE dish_review SET like_count = like_count + 1 WHERE id = #{id}")
    int incrLike(@Param("id") Long id);

    /** 点赞数 -1（原子，下限 0） */
    @Update("UPDATE dish_review SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id}")
    int decrLike(@Param("id") Long id);

    /** 学生回复数 +1（原子） */
    @Update("UPDATE dish_review SET reply_count = reply_count + 1 WHERE id = #{id}")
    int incrReply(@Param("id") Long id);

    /** 某菜品全部已发布评价浏览量 +1（进入详情页时批量） */
    @Update("UPDATE dish_review SET view_count = view_count + 1 "
            + "WHERE dish_id = #{dishId} AND deleted = 0 AND status = 1")
    int incrViewByDish(@Param("dishId") Long dishId);

    /** 判断用户是否购买过该菜品（已支付且未取消），避免未支付订单污染真实评价 */
    @Select("SELECT COUNT(*) FROM dining_order_item oi "
            + "INNER JOIN dining_order o ON o.id = oi.order_id AND o.deleted = 0 "
            + "WHERE oi.dish_id = #{dishId} AND o.user_id = #{userId} "
            + "AND o.pay_status = 'PAID' AND o.status <> 'CANCELED' AND oi.deleted = 0")
    int countPurchased(@Param("dishId") Long dishId, @Param("userId") Long userId);

    /** 找出开启评价 AI 托管档口里待处理的评价，返回 { reviewId, stallId }，供定时任务自动回复 */
    @Select("SELECT r.id AS reviewId, d.stall_id AS stallId FROM dish_review r "
            + "INNER JOIN dish d ON r.dish_id = d.id AND d.deleted = 0 "
            + "INNER JOIN merchant_cs_config c ON c.stall_id = d.stall_id AND c.deleted = 0 AND c.review_ai_enabled = 1 "
            + "WHERE r.deleted = 0 AND r.status = 1 AND r.handled = 0 "
            + "AND (r.merchant_reply IS NULL OR r.merchant_reply = '') "
            + "ORDER BY r.id ASC LIMIT #{limit}")
    List<Map<String, Object>> selectPendingForAutoReply(@Param("limit") int limit);

    /** 某档口各菜品的评价统计，返回 [{dishId, reviewCount, badCount, avgRating}]，供 AI 经营托管 */
    @Select("SELECT d.id AS dishId, "
            + "COUNT(r.id) AS reviewCount, "
            + "IFNULL(SUM(CASE WHEN r.rating <= 2 THEN 1 ELSE 0 END), 0) AS badCount, "
            + "IFNULL(ROUND(AVG(r.rating), 2), 0) AS avgRating "
            + "FROM dish d "
            + "LEFT JOIN dish_review r ON r.dish_id = d.id AND r.deleted = 0 AND r.status = 1 "
            + "WHERE d.stall_id = #{stallId} AND d.deleted = 0 "
            + "GROUP BY d.id")
    List<Map<String, Object>> aggregateStallReviewStats(@Param("stallId") Long stallId);
}
