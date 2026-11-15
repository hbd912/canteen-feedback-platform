package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {
    @TableId(value = "cfg_key", type = IdType.INPUT)
    private String cfgKey;

    private String cfgValue;

    private LocalDateTime updateTime;
}
