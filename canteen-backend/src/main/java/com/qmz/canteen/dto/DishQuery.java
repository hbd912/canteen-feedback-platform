package com.qmz.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DishQuery {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码超出范围")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数最多100条")
    private Long size = 12L;

    private String keyword;
    private Long stallId;
    /** south-南区 north-北区，为空表示全部 */
    private String area;
    /** 楼层：1 或 2，为空表示全部 */
    private Integer floor;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minRating;

    /** rating-好评优先 sales-销量优先 comprehensive-综合（按档口窗口号） reviews-评价数 priceAsc/priceDesc/newest */
    private String sortBy;

    /** 商家管理时包含下架菜品；由服务端按身份注入，客户端不可随意传 */
    private Boolean includeOffShelf;

    /** 指定状态筛选，仅在 includeOffShelf=true 时生效（1在售 0下架） */
    private Integer status;

    /** 上架审核状态筛选：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回；空表示全部 */
    @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "审核状态不合法")
    private String auditStatus;
}
