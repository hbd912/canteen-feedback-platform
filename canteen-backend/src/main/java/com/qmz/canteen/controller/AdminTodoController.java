package com.qmz.canteen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.common.enums.FeedbackStatus;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.Feedback;
import com.qmz.canteen.entity.MerchantMessage;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.FeedbackMapper;
import com.qmz.canteen.mapper.MerchantMessageMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.vo.AdminTodoVO;
import com.qmz.canteen.vo.AdminWorkbenchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端工作台：今日经营指标 + 待办聚合（资质 / 退款 / 评价 / 留言 / 学生反馈）。
 */
@Validated
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminTodoController {

    private final RefundRequestMapper refundRequestMapper;
    private final DishReviewMapper dishReviewMapper;
    private final FeedbackMapper feedbackMapper;
    private final MerchantMessageMapper merchantMessageMapper;
    private final OrderMapper orderMapper;
    private final DishMapper dishMapper;

    @GetMapping("/workbench")
    public Result<AdminWorkbenchVO> workbench() {
        UserContext.requireAdmin();

        long dishes = countPendingDishes();
        long reviews = countPendingReviews();
        long feedbacks = countPendingFeedbacks();
        long messages = countPendingMessages();
        long lowReviews = countLowRatingReviews();
        long refundRecords = countRefundRecords();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<DiningOrder> todayPaid = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .select(DiningOrder::getTotalAmount)
                .eq(DiningOrder::getPayStatus, "PAID")
                .ge(DiningOrder::getPaidTime, todayStart));
        BigDecimal revenue = todayPaid.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AdminWorkbenchVO vo = new AdminWorkbenchVO();
        vo.setTodayOrders(todayPaid.size());
        vo.setTodayRevenue(revenue);
        vo.setPendingDishes(dishes);
        vo.setPendingReviews(reviews + lowReviews);
        vo.setPendingFeedbacks(feedbacks);
        vo.setPendingMessages(messages);
        vo.setRefundRecords(refundRecords);
        vo.setTodos(buildTodos(dishes, reviews, feedbacks, messages, lowReviews));
        return Result.success(vo);
    }

    /** 兼容旧的待办接口 */
    @GetMapping("/todos/summary")
    public Result<List<AdminTodoVO>> summary() {
        UserContext.requireAdmin();
        return Result.success(buildTodos(countPendingDishes(),
                countPendingReviews(), countPendingFeedbacks(), countPendingMessages(),
                countLowRatingReviews()));
    }

    private List<AdminTodoVO> buildTodos(long dishes, long reviews, long feedbacks, long messages,
                                         long lowReviews) {
        List<AdminTodoVO> todos = new ArrayList<>();
        if (dishes > 0) {
            todos.add(todo("DISH_AUDIT", "菜品待审核",
                    dishes + " 道菜品等待上架审核",
                    dishes, "/admin/dish-audits"));
        }
        if (reviews > 0) {
            todos.add(todo("REVIEW", "评价待审核",
                    reviews + " 条学生评价等待审核",
                    reviews, "/admin/feedback-center?tab=review"));
        }
        if (lowReviews > 0) {
            // 与值班助理口径一致：未处理的低分评价也算待办
            todos.add(todo("REVIEW", "低分评价待处理",
                    lowReviews + " 条低分评价待跟进回复",
                    lowReviews, "/admin/feedback-center?tab=review"));
        }
        if (feedbacks > 0) {
            todos.add(todo("FEEDBACK", "学生留言待处理",
                    feedbacks + " 条学生留言等待处理",
                    feedbacks, "/admin/feedback-center?tab=student"));
        }
        if (messages > 0) {
            todos.add(todo("MESSAGE", "商家留言待回复",
                    messages + " 条商家留言还没有回复",
                    messages, "/admin/feedback-center?tab=merchant"));
        }
        return todos;
    }

    private long countPendingDishes() {
        // 使用列名而不是实体 getter，避免工作台与菜品审核实现互相依赖
        return dishMapper.selectCount(new QueryWrapper<Dish>()
                .eq("audit_status", "PENDING"));
    }

    private long countRefundRecords() {
        return refundRequestMapper.selectCount(null);
    }

    private long countPendingReviews() {
        return dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getStatus, DishReview.STATUS_PENDING));
    }

    /** 低分评价待处理：与值班助理口径一致（已发布、未处理、≤3 星） */
    private long countLowRatingReviews() {
        return dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                .eq(DishReview::getHandled, 0)
                .le(DishReview::getRating, 3));
    }

    private long countPendingFeedbacks() {
        return feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .in(Feedback::getStatus, FeedbackStatus.PENDING.name(), FeedbackStatus.PROCESSING.name()));
    }

    private long countPendingMessages() {
        return merchantMessageMapper.selectCount(new LambdaQueryWrapper<MerchantMessage>()
                .eq(MerchantMessage::getStatus, 0)
                .eq(MerchantMessage::getDirection, MerchantMessage.DIR_IN));
    }

    private static AdminTodoVO todo(String type, String title, String content, long count, String url) {
        AdminTodoVO vo = new AdminTodoVO();
        vo.setType(type);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setCount(count);
        vo.setTargetUrl(url);
        return vo;
    }
}
