package com.qmz.canteen.controller;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.UserQuery;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.StudentProfileService;
import com.qmz.canteen.service.UserService;
import com.qmz.canteen.vo.StudentProfileVO;
import com.qmz.canteen.vo.UserInfoVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StudentProfileService studentProfileService;

    @GetMapping
    public Result<PageResult<UserInfoVO>> page(@Valid UserQuery query) {
        UserContext.requireAdmin();
        return Result.success(userService.pageUsers(query));
    }

    @GetMapping("/merchants")
    public Result<PageResult<UserInfoVO>> pageMerchants(@Valid UserQuery query) {
        UserContext.requireAdmin();
        return Result.success(userService.pageMerchants(query));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Positive(message = "用户ID不合法") Long id,
                                     @RequestParam @Min(value = 0, message = "状态取值不合法")
                                     @Max(value = 1, message = "状态取值不合法") Integer status) {
        UserContext.requireAdmin();
        userService.updateStatus(id, status, UserContext.getUserId());
        return Result.success();
    }

    /**
     * 学生 AI 画像（管理端「用户与商家」抽屉）：
     * 口味/常点/消费/时段/情绪记录 + AI 总结。打开时顺带把统计刷新到最新。
     */
    @GetMapping("/{id}/ai-profile")
    public Result<StudentProfileVO> aiProfile(@PathVariable @Positive(message = "用户ID不合法") Long id) {
        UserContext.requireAdmin();
        return Result.success(studentProfileService.adminGetProfile(id));
    }
}
