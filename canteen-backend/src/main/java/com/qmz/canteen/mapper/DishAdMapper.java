package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.DishAd;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DishAdMapper extends BaseMapper<DishAd> {

    /** 当前生效的广告（用于下单归因） */
    @Select("SELECT id FROM dish_ad WHERE dish_id = #{dishId} AND deleted = 0 AND status = 'ACTIVE' "
            + "AND (start_time IS NULL OR start_time <= NOW()) "
            + "AND (end_time IS NULL OR end_time >= NOW()) ORDER BY id DESC LIMIT 1")
    Long findActiveAdId(@Param("dishId") Long dishId);

    /** 定时把过期的投放置为 EXPIRED */
    @Update("UPDATE dish_ad SET status = 'EXPIRED' "
            + "WHERE deleted = 0 AND status = 'ACTIVE' "
            + "AND end_time IS NOT NULL AND end_time < NOW()")
    int expireActive();
}
