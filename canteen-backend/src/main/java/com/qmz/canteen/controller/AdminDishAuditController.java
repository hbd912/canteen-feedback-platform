package com.qmz.canteen.controller;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.DishAuditRejectRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.DishAuditService;
import com.qmz.canteen.vo.DishAuditVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员菜品上架审核：商家提交的菜品需审核通过后才会上架到学生端。
 */
@Validated
@RestController
@RequestMapping("/admin/dish-audits")
@RequiredArgsConstructor
public class AdminDishAuditController {

    private final DishAuditService dishAuditService;

    @GetMapping
    public Result<PageResult<DishAuditVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") Long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 100, message = "每页条数最多100条") Long size) {
        UserContext.requireAdmin();
        return Result.success(dishAuditService.pageAudits(status, current, size));
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable @Positive(message = "菜品ID不合法") Long id) {
        UserContext.requireAdmin();
        dishAuditService.approve(id, UserContext.getUserId());
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable @Positive(message = "菜品ID不合法") Long id,
                               @RequestBody @Valid DishAuditRejectRequest request) {
        UserContext.requireAdmin();
        dishAuditService.reject(id, UserContext.getUserId(), request.getReason());
        return Result.success();
    }
}
