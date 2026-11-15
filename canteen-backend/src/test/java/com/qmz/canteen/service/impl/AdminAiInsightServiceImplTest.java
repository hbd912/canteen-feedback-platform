package com.qmz.canteen.service.impl;

import com.qmz.canteen.vo.AdminAiFindingVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAiInsightServiceImplTest {

    private final AdminAiInsightServiceImpl service =
            new AdminAiInsightServiceImpl(null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void groupsByCategoryAndStallAndKeepsSeverityRulesInServer() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 14, 12, 0);
        List<AdminAiInsightServiceImpl.SourceItem> items = List.of(
                new AdminAiInsightServiceImpl.SourceItem("FEEDBACK", 1L, 10L, 1L,
                        "第一档口", null, null, "吃出异物", "饭里有头发", "COMPLAINT",
                        null, null, now.minusMinutes(1)),
                new AdminAiInsightServiceImpl.SourceItem("FEEDBACK", 2L, 11L, 1L,
                        "第一档口", null, null, "饭里有虫", "有虫子", "COMPLAINT",
                        null, null, now.minusMinutes(2)),
                new AdminAiInsightServiceImpl.SourceItem("FEEDBACK", 3L, 12L, 1L,
                        "第一档口", null, null, "出餐慢", "等了半小时", "COMPLAINT",
                        null, null, now.minusMinutes(3)),
                new AdminAiInsightServiceImpl.SourceItem("FEEDBACK", 4L, 13L, 1L,
                        "第一档口", null, null, "味道不错", "好吃", "PRAISE",
                        null, null, now.minusMinutes(4)),
                new AdminAiInsightServiceImpl.SourceItem("MERCHANT_MESSAGE", 5L, 20L, 2L,
                        "第二档口", null, null, "商家留言", "麻烦审核一下上架信息", null,
                        null, null, now.minusMinutes(5)),
                new AdminAiInsightServiceImpl.SourceItem("REFUND", 6L, 21L, 1L,
                        "第一档口", null, null, "退款申请", "菜里有头发，要求退款", null,
                        null, new BigDecimal("15.00"), now.minusMinutes(6))
        );

        List<AdminAiFindingVO> findings = service.analyze(items);

        AdminAiFindingVO foodSafety = find(findings, "FOOD_SAFETY");
        assertEquals("P0", foodSafety.getSeverity());
        assertEquals("立即处理", foodSafety.getSeverityLabel());
        assertEquals(2, foodSafety.getCount());
        assertEquals(2, foodSafety.getUsers());
        assertEquals("HANDLE", foodSafety.getSuggestedAction());
        assertEquals("第一档口 · 食品安全", foodSafety.getTitle());
        assertEquals("单档口", foodSafety.getScopeLabel());
        assertTrue(foodSafety.getEvidence().contains("学生留言 2 条"));
        assertNotNull(foodSafety.getSample());

        AdminAiFindingVO refund = find(findings, "REFUND");
        assertEquals("P0", refund.getSeverity());
        assertEquals("HANDLE", refund.getSuggestedAction());
        assertEquals("第一档口 · 退款申请", refund.getTitle());
        assertEquals("", refund.getSuggestedReply());

        AdminAiFindingVO serviceIssue = find(findings, "SERVICE");
        assertEquals("P1", serviceIssue.getSeverity());
        assertEquals("HANDLE", serviceIssue.getSuggestedAction());

        AdminAiFindingVO merchant = findings.stream()
                .filter(finding -> "MERCHANT_MESSAGE".equals(finding.getSourceType()))
                .findFirst().orElseThrow();
        assertEquals("P1", merchant.getSeverity());
        assertEquals("HANDLE", merchant.getSuggestedAction());
        assertTrue(merchant.getSuggestedReply().contains("收到你的反馈"));

        AdminAiFindingVO praise = findings.stream()
                .filter(finding -> "OTHER:1".equals(finding.getId()))
                .findFirst().orElseThrow();
        assertEquals("P2", praise.getSeverity());
        assertEquals("OBSERVE", praise.getSuggestedAction());
        assertEquals("", praise.getSuggestedReply());
    }

    private AdminAiFindingVO find(List<AdminAiFindingVO> findings, String category) {
        return findings.stream()
                .filter(finding -> category.equals(finding.getCategory()))
                .findFirst().orElseThrow();
    }
}
