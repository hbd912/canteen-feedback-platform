package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private String userName;
    private Long stallId;
    private String stallName;
    private String windowNo;
    private LocalDateTime pickupTime;
    private String remark;
    private BigDecimal totalAmount;
    /** 优惠金额 */
    private BigDecimal discountAmount;
    private Long couponId;
    /** 优惠券适用范围：ORDER-整单券（券后只能整单退款）/ ITEM-单品券；未用券为 null */
    private String couponScope;
    private String status;
    private String statusLabel;
    private String payStatus;
    private String payStatusLabel;
    /** NONE/PENDING/REFUNDED/REJECTED/FAILED */
    private String refundStatus;
    /** 是否部分退款（整单退为 false；正常未涉及退款为 false；仅部分菜品退回为 true） */
    private Boolean partialRefund;
    /** 已退款的菜品ID集合（部分退款细分；整单退或未退时为 null/空） */
    private List<Long> refundedItemIds;
    private String payNo;
    private String payChannel;
    private LocalDateTime paidTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    /** NONE-未支付不参与评价 UNREVIEWED-待评价 REVIEWED-已评价 */
    private String reviewStatus;
    private String reviewStatusLabel;
    /** 订单中尚未被该用户评价的菜品，供前端「去评价」跳转 */
    private List<Long> unreviewedDishIds;
    private List<OrderItemVO> items;
}
