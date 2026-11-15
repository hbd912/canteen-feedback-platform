package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 商家沟通中心 —— 一个档口的信息摘要（含权重档位与最近往来）。 */
@Data
public class MerchantCommStallVO {

    private Long id;
    private String name;
    private String windowNo;
    private String area;
    private Integer floor;
    /** 区域楼层，如「南区 1 楼」 */
    private String zone;
    private String category;
    private String coverImage;
    private String merchantAvatar;
    private String managerName;

    /** 评分/评论/好评率/销量（来自档口统计） */
    private Double avgRating;
    private Long reviewCount;
    private Double goodRate;
    private Long salesCount;

    /** 权重档位：HIGH 重点 / NORMAL 常规 */
    private String weight;
    private String weightLabel;
    /** 权重摘要，如「低分 2.6 · 评价 18」 */
    private String weightHint;

    /** 来自商家未读留言数（IN status=0） */
    private long unreadFromMerchant;
    /** 最近一条往来（含方向、作者、内容、时间） */
    private String lastDirection;
    private String lastAuthor;
    private String lastContent;
    private LocalDateTime lastTime;
}