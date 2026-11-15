package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.SalesService;
import com.qmz.canteen.vo.SalesAnalysisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员全平台销售分析：聚合所有档口订单，含退款/净收入、档口排行、菜品销量 TOP10。
 */
@RestController
@RequestMapping("/admin/sales")
@RequiredArgsConstructor
public class AdminSalesController {

    private final SalesService salesService;

    /** 全平台 AI 销售分析报告（图表 + 可下载表格） */
    @GetMapping("/analysis")
    public Result<SalesAnalysisVO> analysis(@RequestParam(required = false) Integer days) {
        UserContext.requireAdmin();
        return Result.success(salesService.adminAnalysis(days));
    }
}
