package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.DishAdStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface DishAdStatMapper extends BaseMapper<DishAdStat> {

    @Insert("INSERT INTO dish_ad_stat(ad_id, stat_date, impressions) VALUES(#{adId}, CURDATE(), 1) "
            + "ON DUPLICATE KEY UPDATE impressions = impressions + 1")
    int incrImpression(@Param("adId") Long adId);

    @Insert("INSERT INTO dish_ad_stat(ad_id, stat_date, clicks) VALUES(#{adId}, CURDATE(), 1) "
            + "ON DUPLICATE KEY UPDATE clicks = clicks + 1")
    int incrClick(@Param("adId") Long adId);

    @Insert("INSERT INTO dish_ad_stat(ad_id, stat_date, conversions) VALUES(#{adId}, CURDATE(), 1) "
            + "ON DUPLICATE KEY UPDATE conversions = conversions + 1")
    int incrConversion(@Param("adId") Long adId);

    @Select("SELECT * FROM dish_ad_stat WHERE ad_id = #{adId} AND stat_date >= #{from} ORDER BY stat_date")
    List<DishAdStat> selectByAd(@Param("adId") Long adId, @Param("from") LocalDate from);

    @Select("SELECT ad_id AS adId, SUM(impressions) AS impressions, SUM(clicks) AS clicks, "
            + "SUM(conversions) AS conversions FROM dish_ad_stat "
            + "WHERE stat_date >= #{from} GROUP BY ad_id")
    List<java.util.Map<String, Object>> sumByAd(@Param("from") LocalDate from);
}
