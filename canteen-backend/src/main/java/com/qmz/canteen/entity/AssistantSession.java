package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 小助手会话：学生与食堂小助手的一轮对话（历史持久化，刷新/换设备不丢）。 */
@Data
@TableName("assistant_session")
public class AssistantSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话标题（首条问题截断） */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
