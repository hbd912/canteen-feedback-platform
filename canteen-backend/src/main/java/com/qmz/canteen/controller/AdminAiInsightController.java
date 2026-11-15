package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.AdminAiAskRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AdminAiInsightService;
import com.qmz.canteen.vo.AdminAiAnswerVO;
import com.qmz.canteen.vo.AdminAiInsightVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台 AI 值班助理：只读聚合，不执行退款、处罚或上架。
 */
@RestController
@RequestMapping("/admin/workbench/ai-insight")
@RequiredArgsConstructor
public class AdminAiInsightController {

    private final AdminAiInsightService service;

    @GetMapping
    public Result<AdminAiInsightVO> get() {
        UserContext.requireAdmin();
        return Result.success(service.getCached());
    }

    @PostMapping("/refresh")
    public Result<AdminAiInsightVO> refresh() {
        UserContext.requireAdmin();
        return Result.success(service.refresh());
    }

    @PostMapping("/handle-all")
    public Result<AdminAiInsightVO> handleAll() {
        UserContext.requireAdmin();
        return Result.success(service.handleAll());
    }

    @PostMapping("/handle-auto")
    public Result<AdminAiInsightVO> handleAuto() {
        UserContext.requireAdmin();
        return Result.success(service.handleAutoOnly());
    }

    @GetMapping("/greeting")
    public Result<Map<String, String>> greeting() {
        UserContext.requireAdmin();
        return Result.success(Map.of("text", service.greeting()));
    }

    @PostMapping("/ask")
    public Result<AdminAiAnswerVO> ask(@RequestBody @Valid AdminAiAskRequest request) {
        UserContext.requireAdmin();
        return Result.success(service.ask(request.getQuestion()));
    }
}
