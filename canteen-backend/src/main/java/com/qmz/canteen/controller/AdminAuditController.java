package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AuditFeedService;
import com.qmz.canteen.vo.AuditFeedVO;
import com.qmz.canteen.vo.AuditStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 超级后台日志：AI 决策、经验命中、广告审核、退款统一时间线。
 */
@Validated
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditFeedService auditFeedService;

    @GetMapping("/feed")
    public Result<AuditFeedVO> feed(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "20") Integer size,
                                    @RequestParam(defaultValue = "7") Integer days) {
        UserContext.requireAdmin();
        return Result.success(auditFeedService.feed(type, status,
                current == null ? 1 : current,
                size == null ? 20 : size,
                days == null ? 7 : days));
    }

    @GetMapping("/stats")
    public Result<AuditStatsVO> stats(@RequestParam(defaultValue = "7") Integer days) {
        UserContext.requireAdmin();
        return Result.success(auditFeedService.stats(days == null ? 7 : days));
    }
}
