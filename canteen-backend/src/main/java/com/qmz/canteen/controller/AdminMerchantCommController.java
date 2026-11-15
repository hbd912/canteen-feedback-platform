package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.MerchantService;
import com.qmz.canteen.vo.MerchantCommStallVO;
import com.qmz.canteen.vo.MerchantMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端「商家沟通中心」：全部档口列表（权重档位）→ 点进单个档口的双向往来线程 → 发反馈/通知。
 */
@RestController
@RequestMapping("/admin/merchant-comm")
@RequiredArgsConstructor
public class AdminMerchantCommController {

    private final MerchantService merchantService;

    /** 全部档口摘要（含未读、权重、最近往来） */
    @GetMapping("/stalls")
    public Result<List<MerchantCommStallVO>> stalls() {
        UserContext.requireAdmin();
        return Result.success(merchantService.commStalls(null));
    }

    /** 单个档口的完整双向线程 */
    @GetMapping("/stalls/{stallId}/thread")
    public Result<List<MerchantMessageVO>> thread(@PathVariable Long stallId) {
        UserContext.requireAdmin();
        return Result.success(merchantService.threadByStall(stallId));
    }

    /** 管理员向商家发反馈/通知（带标题，AI 会先编辑好文案） */
    @PostMapping("/stalls/{stallId}/send")
    public Result<Void> send(@PathVariable Long stallId, @RequestBody Map<String, String> body) {
        UserContext.requireAdmin();
        merchantService.adminSendMessage(UserContext.getUserId(), stallId,
                body.get("title"), body.get("content"), true);
        return Result.success();
    }
}