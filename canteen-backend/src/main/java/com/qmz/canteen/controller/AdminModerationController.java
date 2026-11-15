package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端「敏感词自动屏蔽」开关。
 */
@RestController
@RequestMapping("/admin/moderation")
@RequiredArgsConstructor
public class AdminModerationController {

    private final SysConfigService sysConfigService;

    @GetMapping("/auto-block")
    public Result<Map<String, Object>> getAutoBlock() {
        UserContext.requireAdmin();
        return Result.success(Map.of("enabled", sysConfigService.isModerationAutoBlock()));
    }

    @PutMapping("/auto-block")
    public Result<Map<String, Object>> setAutoBlock(@RequestBody Map<String, Object> body) {
        UserContext.requireAdmin();
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        sysConfigService.setModerationAutoBlock(enabled);
        return Result.success(Map.of("enabled", enabled));
    }
}
