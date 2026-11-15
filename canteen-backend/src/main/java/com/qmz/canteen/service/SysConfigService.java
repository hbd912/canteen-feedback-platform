package com.qmz.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.entity.SysConfig;
import com.qmz.canteen.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 系统开关（key-value），如敏感词自动屏蔽。 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    public static final String KEY_MODERATION_AUTO_BLOCK = "moderation.auto_block";

    private final SysConfigMapper sysConfigMapper;

    public String get(String key, String defaultValue) {
        SysConfig cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getCfgKey, key));
        return cfg == null || cfg.getCfgValue() == null || cfg.getCfgValue().isBlank()
                ? defaultValue : cfg.getCfgValue();
    }

    public boolean isModerationAutoBlock() {
        return "true".equalsIgnoreCase(get(KEY_MODERATION_AUTO_BLOCK, "true"));
    }

    public void setModerationAutoBlock(boolean enabled) {
        SysConfig cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getCfgKey, KEY_MODERATION_AUTO_BLOCK));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setCfgKey(KEY_MODERATION_AUTO_BLOCK);
            cfg.setCfgValue(String.valueOf(enabled));
            sysConfigMapper.insert(cfg);
        } else {
            cfg.setCfgValue(String.valueOf(enabled));
            sysConfigMapper.updateById(cfg);
        }
    }
}
