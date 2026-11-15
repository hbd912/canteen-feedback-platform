package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家快捷回复。
 */
@Data
@TableName("merchant_quick_reply")
public class MerchantQuickReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 档口ID */
    private Long stallId;

    private String content;

    /** 排序权重 */
    private Integer sort;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
