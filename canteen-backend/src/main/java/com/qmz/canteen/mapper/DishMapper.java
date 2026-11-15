package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qmz.canteen.dto.DishQuery;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.vo.DishAuditVO;
import com.qmz.canteen.vo.DishVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface DishMapper extends BaseMapper<Dish> {

    IPage<DishAuditVO> selectAuditPage(IPage<?> page, @Param("status") String status);

    IPage<DishVO> selectDishPage(IPage<?> page,
                                 @Param("query") DishQuery query,
                                 @Param("userId") Long userId,
                                 @Param("goodThreshold") Integer goodThreshold,
                                 @Param("orderSql") String orderSql);

    DishVO selectDishDetail(@Param("dishId") Long dishId,
                            @Param("userId") Long userId,
                            @Param("goodThreshold") Integer goodThreshold);

    List<DishVO> selectRecommendDishes(@Param("dishId") Long dishId,
                                       @Param("limit") Long limit,
                                       @Param("goodThreshold") Integer goodThreshold);

    List<DishVO> selectFavoriteDishes(@Param("userId") Long userId,
                                      @Param("goodThreshold") Integer goodThreshold);

    List<DishVO> selectSignatureDishes(@Param("stallId") Long stallId,
                                       @Param("limit") Long limit,
                                       @Param("goodThreshold") Integer goodThreshold);

    /** 某档口评分最高的若干菜品（美食探索档口排序页，右侧展示用） */
    List<DishVO> selectTopRatedDishes(@Param("stallId") Long stallId,
                                      @Param("limit") Long limit,
                                      @Param("goodThreshold") Integer goodThreshold);

    /**
     * 原子扣减库存：库存为 NULL 表示不限量，不扣；返回 0 表示库存不足或菜品不可售。
     * sold_out 的判断引用的是「已扣减后的 stock_qty」，MySQL 的 SET 是左到右顺序求值。
     */
    @Update("UPDATE dish SET "
            + "stock_qty = IF(stock_qty IS NULL, NULL, stock_qty - #{qty}), "
            + "sold_out = IF(stock_qty IS NULL, 0, IF(stock_qty <= 0, 1, 0)) "
            + "WHERE id = #{dishId} AND deleted = 0 AND status = 1 "
            + "AND (stock_qty IS NULL OR stock_qty >= #{qty})")
    int deductStock(@Param("dishId") Long dishId, @Param("qty") int qty);

    /** 取消订单时回补库存 */
    @Update("UPDATE dish SET stock_qty = stock_qty + #{qty}, sold_out = 0 "
            + "WHERE id = #{dishId} AND stock_qty IS NOT NULL AND deleted = 0")
    int restoreStock(@Param("dishId") Long dishId, @Param("qty") int qty);
}
