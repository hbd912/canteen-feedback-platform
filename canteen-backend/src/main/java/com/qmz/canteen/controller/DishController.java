package com.qmz.canteen.controller;

import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.DishDraftRequest;
import com.qmz.canteen.dto.DishQuery;
import com.qmz.canteen.dto.DishRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.DishDraftService;
import com.qmz.canteen.service.DishService;
import com.qmz.canteen.vo.DishDetailVO;
import com.qmz.canteen.vo.DishDraftVO;
import com.qmz.canteen.vo.DishVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;
    private final DishDraftService dishDraftService;

    @GetMapping
    public Result<PageResult<DishVO>> page(@Valid DishQuery query) {
        // 商家查看自己档口时，允许包含下架菜品（用于重新上架）；学生/游客仍只见在售
        if (UserContext.isMerchant() && query.getStallId() != null
                && query.getStallId().equals(UserContext.requireMerchant())) {
            query.setIncludeOffShelf(true);
        }
        return Result.success(dishService.pageDishes(query, UserContext.getUserId()));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(dishService.listCategories());
    }

    /** 商家：图片/文字 → 菜品上架草稿（只生成草稿，不落库） */
    @PostMapping("/ai-draft")
    public Result<DishDraftVO> aiDraft(@RequestBody @Valid DishDraftRequest request) {
        if (!UserContext.isMerchant() && !UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有商家或管理员可以生成菜品草稿");
        }
        return Result.success(dishDraftService.draft(request));
    }

    @GetMapping("/{id}")
    public Result<DishDetailVO> detail(@PathVariable @Positive(message = "菜品ID不合法") Long id) {
        return Result.success(dishService.getDishDetail(id, UserContext.getUserId()));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid DishRequest request) {
        return Result.success(dishService.createDish(request, operatorStallId()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "菜品ID不合法") Long id,
                               @RequestBody @Valid DishRequest request) {
        dishService.updateDish(id, request, operatorStallId());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "菜品ID不合法") Long id) {
        dishService.deleteDish(id, operatorStallId());
        return Result.success();
    }

    /** 管理员返回 null（可管全部），商家返回自己档口ID */
    private Long operatorStallId() {
        if (UserContext.isAdmin()) {
            return null;
        }
        return UserContext.requireMerchant();
    }
}
