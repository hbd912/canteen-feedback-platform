package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dish_ad_stat")
public class DishAdStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adId;
    private LocalDate statDate;
    private Long impressions;
    private Long clicks;
    private Long conversions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
