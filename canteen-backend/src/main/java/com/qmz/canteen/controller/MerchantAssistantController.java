package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.AssistantFeedbackRequest;
import com.qmz.canteen.dto.MerchantAssistantRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.MerchantAssistantService;
import com.qmz.canteen.vo.MerchantAssistantAnswer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家 ↔ 经营 Agent 对话窗口。商家只能问自己的档口。
 */
@Validated
@RestController
@RequestMapping("/merchant/ai-assistant")
@RequiredArgsConstructor
public class MerchantAssistantController {

    private final MerchantAssistantService assistantService;

    @PostMapping("/ask")
    public Result<MerchantAssistantAnswer> ask(@RequestBody @Valid MerchantAssistantRequest request) {
        Long stallId = UserContext.requireMerchant();
        return Result.success(assistantService.ask(stallId, UserContext.getUserId(),
                request.getQuestion(), request.getImage(), request.getSkill()));
    }

    /** 商家认为某条建议有用：写入经验库，下次同类问题可检索到 */
    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestBody @Valid AssistantFeedbackRequest request) {
        Long stallId = UserContext.requireMerchant();
        assistantService.feedback(stallId, request.getQuestion(), request.getAnswer(),
                Boolean.TRUE.equals(request.getHelpful()));
        return Result.success();
    }
}
