package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_experience_hit")
public class AiExperienceHit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;
    private String queryText;
    private Long experienceId;
    private BigDecimal score;
    private LocalDateTime createTime;
}
