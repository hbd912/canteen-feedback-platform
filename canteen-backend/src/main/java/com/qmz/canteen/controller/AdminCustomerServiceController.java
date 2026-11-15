package com.qmz.canteen.controller;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.CustomerServiceRecordQuery;
import com.qmz.canteen.dto.KnowledgeQuery;
import com.qmz.canteen.service.CustomerServiceCenterService;
import com.qmz.canteen.service.CustomerServiceService;
import com.qmz.canteen.vo.CsStatsVO;
import com.qmz.canteen.vo.CustomerServiceMessageVO;
import com.qmz.canteen.vo.CustomerServiceRecordVO;
import com.qmz.canteen.vo.CustomerServiceSessionVO;
import com.qmz.canteen.vo.KnowledgeItemVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理员客服质检：全量会话与统计。
 */
@Validated
@RestController
@RequestMapping("/admin/customer-service")
@RequiredArgsConstructor
public class AdminCustomerServiceController {

    private final CustomerServiceService customerServiceService;
    private final CustomerServiceCenterService customerServiceCenterService;

    @GetMapping("/knowledge")
    public Result<PageResult<KnowledgeItemVO>> knowledge(@Valid KnowledgeQuery query) {
        return Result.success(customerServiceCenterService.knowledge(query));
    }

    @GetMapping("/records")
    public Result<PageResult<CustomerServiceRecordVO>> records(@Valid CustomerServiceRecordQuery query) {
        return Result.success(customerServiceCenterService.records(query));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<CustomerServiceMessageVO>> messages(
            @PathVariable @Positive(message = "会话ID不合法") Long id) {
        return Result.success(customerServiceService.adminGetMessages(id));
    }

    @GetMapping("/sessions")
    public Result<Map<String, Object>> sessions(
            @RequestParam(defaultValue = "1") @Min(1) int current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        List<CustomerServiceSessionVO> records = customerServiceService.adminListSessions(current, size);
        long total = customerServiceService.adminSessionTotal();
        return Result.success(Map.of("records", records, "total", total));
    }

    @GetMapping("/stats")
    public Result<CsStatsVO> stats() {
        return Result.success(customerServiceService.adminStats());
    }
}
