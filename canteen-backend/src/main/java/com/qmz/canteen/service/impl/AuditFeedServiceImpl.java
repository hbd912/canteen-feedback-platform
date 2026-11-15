package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.entity.AiEventLog;
import com.qmz.canteen.entity.AiExperienceHit;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.RefundRequest;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.AiEventLogMapper;
import com.qmz.canteen.mapper.AiExperienceHitMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.service.AuditFeedService;
import com.qmz.canteen.vo.AuditFeedVO;
import com.qmz.canteen.vo.AuditItemVO;
import com.qmz.canteen.vo.AuditStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超级后台日志：把 AI 决策、经验命中、广告审核、退款拼成一条时间线。
 * 数据量不大时在内存合并即可；后续量大再改成 SQL UNION + 分页。
 */
@Service
@RequiredArgsConstructor
public class AuditFeedServiceImpl implements AuditFeedService {

    private final AiEventLogMapper eventLogMapper;
    private final AiExperienceHitMapper hitMapper;
    private final RefundRequestMapper refundMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final StallMapper stallMapper;

    private static final int MAX_PER_SOURCE = 300;

    @Override
    public AuditFeedVO feed(String type, String status, int current, int size, int days) {
        List<AuditItemVO> items = collect(days);
        if (type != null && !type.isBlank()) {
            items.removeIf(item -> !type.trim().equalsIgnoreCase(item.getType()));
        }
        if (status != null && !status.isBlank()) {
            items.removeIf(item -> !status.trim().equalsIgnoreCase(item.getStatus()));
        }
        long total = items.size();
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min((page - 1) * pageSize, items.size());
        int to = Math.min(from + pageSize, items.size());

        AuditFeedVO vo = new AuditFeedVO();
        vo.setRecords(items.subList(from, to));
        vo.setTotal(total);
        vo.setCurrent(page);
        vo.setSize(pageSize);
        return vo;
    }

    @Override
    public AuditStatsVO stats(int days) {
        List<AuditItemVO> items = collect(days);
        AuditStatsVO vo = new AuditStatsVO();
        vo.setTotal(items.size());
        for (AuditItemVO item : items) {
            switch (item.getType()) {
                case "AI" -> vo.setAi(vo.getAi() + 1);
                case "EXPERIENCE" -> vo.setExperienceHits(vo.getExperienceHits() + 1);
                case "REFUND" -> {
                    vo.setRefunds(vo.getRefunds() + 1);
                    if (RefundRequest.STATUS_FAILED.equals(item.getStatus())) {
                        vo.setRefundFailures(vo.getRefundFailures() + 1);
                    }
                }
                default -> {
                }
            }
        }
        return vo;
    }

    private List<AuditItemVO> collect(int days) {
        int range = Math.min(Math.max(days, 1), 90);
        LocalDateTime from = LocalDateTime.now().minusDays(range);
        List<AuditItemVO> items = new ArrayList<>();

        List<AiEventLog> events = eventLogMapper.selectList(new LambdaQueryWrapper<AiEventLog>()
                .ge(AiEventLog::getCreateTime, from)
                .orderByDesc(AiEventLog::getId)
                .last("LIMIT " + MAX_PER_SOURCE));
        for (AiEventLog event : events) {
            AuditItemVO item = new AuditItemVO();
            item.setId("AI:" + event.getId());
            item.setType("AI");
            item.setTypeLabel("AI 事件");
            item.setTitle("AI 事件 · " + safe(event.getEventType())
                    + (event.getAssignTo() == null ? "" : " → " + event.getAssignTo()));
            item.setDetail(shorten(event.getContent(), 200)
                    + (event.getResult() == null ? "" : " → " + shorten(event.getResult(), 140)));
            item.setStatus(event.getStatus());
            item.setStatusLabel(aiStatusLabel(event.getStatus()));
            item.setActor(event.getAssignTo());
            item.setTime(event.getCreateTime());
            item.setRefId(event.getId());
            items.add(item);
        }

        List<AiExperienceHit> hits = hitMapper.selectList(new LambdaQueryWrapper<AiExperienceHit>()
                .ge(AiExperienceHit::getCreateTime, from)
                .orderByDesc(AiExperienceHit::getId)
                .last("LIMIT " + MAX_PER_SOURCE));
        for (AiExperienceHit hit : hits) {
            AuditItemVO item = new AuditItemVO();
            item.setId("EXPERIENCE:" + hit.getId());
            item.setType("EXPERIENCE");
            item.setTypeLabel("经验命中");
            item.setTitle("经验命中 #" + hit.getExperienceId());
            item.setDetail("场景：" + sceneLabel(hit.getScene())
                    + " · 问题：" + shorten(hit.getQueryText(), 100)
                    + " · 相似度：" + (hit.getScore() == null ? "—" : hit.getScore().toPlainString()));
            item.setStatus("HIT");
            item.setStatusLabel("命中");
            item.setActor("RAG");
            item.setTime(hit.getCreateTime());
            item.setRefId(hit.getExperienceId());
            item.setTarget("/admin/cs-center?tab=experience");
            items.add(item);
        }

        List<RefundRequest> refunds = refundMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .ge(RefundRequest::getCreateTime, from)
                .orderByDesc(RefundRequest::getId)
                .last("LIMIT " + MAX_PER_SOURCE));
        Map<Long, DiningOrder> orders = loadOrders(refunds.stream().map(RefundRequest::getOrderId).toList());
        Map<Long, Stall> stalls = loadStalls(refunds.stream().map(RefundRequest::getStallId).toList());
        Map<Long, User> users = loadUsers(refunds.stream().map(RefundRequest::getUserId).toList());
        for (RefundRequest refund : refunds) {
            DiningOrder order = orders.get(refund.getOrderId());
            Stall stall = stalls.get(refund.getStallId());
            User user = users.get(refund.getUserId());
            AuditItemVO item = new AuditItemVO();
            item.setId("REFUND:" + refund.getId());
            item.setType("REFUND");
            item.setTypeLabel("退款");
            item.setTitle("退款 · " + (order == null ? "订单#" + refund.getOrderId() : order.getOrderNo()));
            item.setDetail("¥" + refund.getAmount()
                    + (stall == null ? "" : " · " + stall.getName())
                    + (user == null ? "" : " · " + user.getRealName())
                    + (refund.getReason() == null ? "" : " · " + shorten(refund.getReason(), 60))
                    + (refund.getFailReason() == null ? "" : " · 失败：" + shorten(refund.getFailReason(), 80)));
            item.setStatus(refund.getStatus());
            item.setStatusLabel(refundStatusLabel(refund.getStatus()));
            item.setActor(refund.getHandledBy() == null ? "—" : "操作人 #" + refund.getHandledBy());
            item.setTime(refund.getHandledTime() == null ? refund.getCreateTime() : refund.getHandledTime());
            item.setRefId(refund.getId());
            item.setTarget("/admin/refunds");
            items.add(item);
        }

        items.sort(Comparator.comparing(AuditItemVO::getTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    private Map<Long, DiningOrder> loadOrders(List<Long> ids) {
        Map<Long, DiningOrder> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (DiningOrder order : orderMapper.selectBatchIds(ids)) {
            map.put(order.getId(), order);
        }
        return map;
    }

    private Map<Long, Stall> loadStalls(List<Long> ids) {
        Map<Long, Stall> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Stall stall : stallMapper.selectBatchIds(ids)) {
            map.put(stall.getId(), stall);
        }
        return map;
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (User user : userMapper.selectBatchIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private static String safe(String text) {
        return text == null ? "—" : text;
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String sceneLabel(String scene) {
        if (scene == null) {
            return "—";
        }
        return switch (scene) {
            case "CUSTOMER_SERVICE" -> "客服会话";
            case "MERCHANT_ASSISTANT" -> "经营助理";
            case "REVIEW_REPLY" -> "评价回复";
            default -> scene;
        };
    }

    private static String aiStatusLabel(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "DONE" -> "已处理";
            case "ESCALATED" -> "已升级人工";
            default -> status;
        };
    }

    private static String refundStatusLabel(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case RefundRequest.STATUS_PENDING -> "待审核";
            case RefundRequest.STATUS_REFUNDED -> "已退款";
            case RefundRequest.STATUS_REJECTED -> "已驳回";
            case RefundRequest.STATUS_FAILED -> "退款失败";
            default -> status;
        };
    }
}
