package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.agent.EmotionWords;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.agent.PromptProperties;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.DiningOrderItem;
import com.qmz.canteen.entity.DishFavorite;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.StudentProfile;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.DiningOrderItemMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.DishFavoriteMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.StudentProfileMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.service.StudentProfileService;
import com.qmz.canteen.vo.StudentProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学生 AI 画像实现。
 * <p>
 * 数据来源全部实时聚合：dining_order / dining_order_item（消费与常点）、
 * dish_review（评价口味倾向）、dish_favorite（收藏）、chat_log 的情绪记录
 * 由 recordChatTurn 显式写入 student_profile.mood_notes。
 * <p>
 * LLM 总结走 MultiModelRouter（FAST 角色、低温），失败不影响统计字段更新。
 * 刷新有节流：同一用户 10 分钟内不重复聚合，防止每轮对话都全表扫订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentProfileServiceImpl implements StudentProfileService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int REFRESH_EVERY_TURNS = 6;

    private final StudentProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final DiningOrderItemMapper orderItemMapper;
    private final DishFavoriteMapper favoriteMapper;
    private final DishReviewMapper reviewMapper;
    private final MultiModelRouter router;
    private final PromptProperties prompts;
    private final com.qmz.canteen.mapper.StallMapper stallMapper;
    private final com.qmz.canteen.mapper.DishMapper dishMapper;

    /** userId → 上次聚合时间（节流） */
    private final Map<Long, Long> lastRefresh = new ConcurrentHashMap<>();

    // ---------------- 对外接口 ----------------

    @Override
    public StudentProfileVO getProfile(Long userId) {
        StudentProfile profile = getOrCreate(userId);
        return toVO(profile, true);
    }

    @Override
    public int recordChatTurn(Long userId, boolean moodHit, String moodWord) {
        if (userId == null) {
            return 0;
        }
        StudentProfile profile = getOrCreate(userId);
        int turns = (profile.getMsgCount() == null ? 0 : profile.getMsgCount()) + 1;
        StudentProfile update = new StudentProfile();
        update.setId(profile.getId());
        update.setMsgCount(turns);
        if (moodHit) {
            String label = EmotionWords.labelOf(EmotionWords.categoryOf(moodWord == null ? "" : moodWord));
            String note = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd")) + " " + label;
            String old = profile.getMoodNotes() == null ? "" : profile.getMoodNotes();
            // mood_notes 只留最近 10 条，防止无限膨胀
            String[] notes = old.isEmpty() ? new String[0] : old.split("\\|");
            List<String> kept = new ArrayList<>();
            kept.add(note);
            for (String n : notes) {
                if (!n.isBlank() && kept.size() < 10) {
                    kept.add(n.trim());
                }
            }
            update.setMoodNotes(String.join(" | ", kept));
        }
        profileMapper.updateById(update);

        // 每 N 轮（或命中情绪）触发一次画像刷新，节流内不重复
        long now = System.currentTimeMillis();
        Long last = lastRefresh.get(userId);
        boolean due = last == null || now - last >= REFRESH_INTERVAL_MS;
        if (due && (turns % REFRESH_EVERY_TURNS == 0 || moodHit)) {
            try {
                refreshProfile(userId);
            } catch (Exception e) {
                log.warn("画像刷新失败 userId={}：{}", userId, e.getMessage());
            }
        }
        return turns;
    }

    @Override
    public void refreshProfile(Long userId) {
        StudentProfile profile = getOrCreate(userId);
        Aggregate agg = aggregate(userId);

        StudentProfile update = new StudentProfile();
        update.setId(profile.getId());
        update.setTasteTags(agg.tasteTags);
        update.setFavoriteStalls(agg.favoriteStalls);
        update.setFavoriteDishes(agg.favoriteDishes);
        update.setExpenseWeek(agg.expenseWeek);
        update.setExpenseMonth(agg.expenseMonth);
        update.setExpenseTotal(agg.expenseTotal);
        update.setOrderCount(agg.orderCount);
        update.setUpdateTime(LocalDateTime.now());

        // AI 总结：拿聚合结果 + 情绪记录让模型写画像；失败只跳过总结字段
        try {
            String summary = summarize(profile, agg);
            if (summary != null && !summary.isBlank()) {
                update.setAiSummary(summary);
                update.setAiSummaryAt(LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("画像 AI 总结失败 userId={}：{}", userId, e.getMessage());
        }
        profileMapper.updateById(update);
        lastRefresh.put(userId, System.currentTimeMillis());
    }

    /** 管理端画像刷新的线程池：点开抽屉先回旧数据，刷新在后台做，不卡界面 */
    private final java.util.concurrent.ExecutorService profileRefreshPool =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "profile-refresh");
                t.setDaemon(true);
                return t;
            });

    @Override
    public StudentProfileVO adminGetProfile(Long userId) {
        StudentProfile profile = getOrCreate(userId);
        StudentProfileVO vo = toVO(profile, false);
        // 管理端打开时先把当前落库的画像秒回给前端；统计+AI总结的刷新放到后台线程做，
        // 前端稍后自行再拉一次拿最新结果（抽屉里的「画像更新中」逻辑）
        long now = System.currentTimeMillis();
        Long last = lastRefresh.get(userId);
        if (last == null || now - last >= REFRESH_INTERVAL_MS) {
            lastRefresh.put(userId, now); // 先占位，防止后台还没跑完又被重复提交
            try {
                profileRefreshPool.submit(() -> {
                    try {
                        refreshProfile(userId);
                    } catch (Exception e) {
                        log.warn("后台画像刷新失败 userId={}：{}", userId, e.getMessage());
                        lastRefresh.remove(userId); // 失败后允许下次立刻重试
                    }
                });
            } catch (Exception e) {
                log.warn("提交画像后台刷新失败 userId={}：{}", userId, e.getMessage());
            }
        }
        return vo;
    }

    // ---------------- 聚合 ----------------

    private Aggregate aggregate(Long userId) {
        Aggregate agg = new Aggregate();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        List<DiningOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getUserId, userId)
                .orderByDesc(DiningOrder::getCreateTime));

        List<Long> paidOrderIds = new ArrayList<>();
        Map<Long, Integer> stallCount = new LinkedHashMap<>();
        Map<String, Integer> hourBuckets = new LinkedHashMap<>();
        BigDecimal week = BigDecimal.ZERO;
        BigDecimal month = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (DiningOrder o : orders) {
            boolean paid = "PAID".equals(o.getPayStatus());
            if (!paid || o.getTotalAmount() == null || o.getCreateTime() == null) {
                continue;
            }
            paidOrderIds.add(o.getId());
            total = total.add(o.getTotalAmount());
            if (o.getCreateTime().isAfter(weekAgo)) {
                week = week.add(o.getTotalAmount());
            }
            if (o.getCreateTime().isAfter(monthAgo)) {
                month = month.add(o.getTotalAmount());
            }
            if (o.getStallId() != null) {
                stallCount.merge(o.getStallId(), 1, Integer::sum);
            }
            int hour = o.getCreateTime().getHour();
            String bucket = hour >= 6 && hour < 10 ? "早餐" : hour >= 10 && hour < 14 ? "午餐"
                    : hour >= 16 && hour < 20 ? "晚餐" : hour >= 20 ? "夜宵" : "其他";
            hourBuckets.merge(bucket, 1, Integer::sum);
        }
        agg.expenseWeek = week;
        agg.expenseMonth = month;
        agg.expenseTotal = total;
        agg.orderCount = paidOrderIds.size();
        agg.favoriteStalls = joinStallNames(stallCount, 3);
        agg.activeHours = topHours(hourBuckets);

        // 常点菜品：从订单明细聚合
        if (!paidOrderIds.isEmpty()) {
            Map<String, Integer> dishCount = new LinkedHashMap<>();
            List<DiningOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                    .in(DiningOrderItem::getOrderId, paidOrderIds));
            for (DiningOrderItem it : items) {
                if (it.getDishName() != null) {
                    dishCount.merge(it.getDishName(), it.getQuantity() == null ? 1 : it.getQuantity(), Integer::sum);
                }
            }
            agg.favoriteDishes = joinTop(dishCount, 5);
        }

        // 口味标签：从评价内容里找常见口味词
        List<DishReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getUserId, userId)
                .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                .orderByDesc(DishReview::getId)
                .last("LIMIT 50"));
        Map<String, Integer> taste = new LinkedHashMap<>();
        String[][] tasteWords = {
                {"辣", "偏辣"}, {"甜", "爱甜"}, {"咸", "口重"}, {"清淡", "清淡"}, {"酸", "爱酸"},
                {"麻", "爱麻"}, {"油", "嫌油腻"}, {"香", "爱香口"}, {"烫", "爱吃烫"}, {"冰", "爱冰饮"}
        };
        for (DishReview r : reviews) {
            String c = r.getContent() == null ? "" : r.getContent();
            for (String[] tw : tasteWords) {
                if (c.contains(tw[0])) {
                    taste.merge(tw[1], 1, Integer::sum);
                }
            }
        }
        agg.tasteTags = joinTop(taste, 4);

        // 收藏菜名并入常点展示（收藏=强偏好）
        List<DishFavorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<DishFavorite>()
                .eq(DishFavorite::getUserId, userId)
                .orderByDesc(DishFavorite::getId)
                .last("LIMIT 5"));
        if (!favs.isEmpty()) {
            List<String> favNames = new ArrayList<>();
            for (DishFavorite f : favs) {
                com.qmz.canteen.entity.Dish d = dishMapperSafe(f.getDishId());
                if (d != null) {
                    favNames.add(d.getName());
                }
            }
            if (!favNames.isEmpty()) {
                String extra = String.join(",", favNames);
                agg.favoriteDishes = agg.favoriteDishes == null || agg.favoriteDishes.isBlank()
                        ? extra : agg.favoriteDishes + "," + extra;
            }
        }
        return agg;
    }


    private String summarize(StudentProfile profile, Aggregate agg) {
        if (!router.hasSiliconFlow() && !router.hasScnet()) {
            return null;
        }
        StringBuilder user = new StringBuilder();
        user.append("请根据以下数据为这位学生写一份「食堂画像」，60~120字，用第二人称（你），"
                + "语气像食堂阿姨认识你之后的评价，自然口语化。内容包含：口味偏好、常点的档口和菜、"
                + "消费水平、用餐时段习惯；有情绪记录时提一句最近的状态。不要罗列数据，要写成有温度的一段话。\n");
        user.append("口味标签：").append(agg.tasteTags == null || agg.tasteTags.isBlank() ? "暂无评价记录" : agg.tasteTags).append("\n");
        user.append("常点档口：").append(agg.favoriteStalls == null || agg.favoriteStalls.isBlank() ? "暂无" : agg.favoriteStalls).append("\n");
        user.append("常点菜品：").append(agg.favoriteDishes == null || agg.favoriteDishes.isBlank() ? "暂无" : agg.favoriteDishes).append("\n");
        user.append("最近7天消费：").append(agg.expenseWeek).append("元；最近30天：").append(agg.expenseMonth)
                .append("元；累计：").append(agg.expenseTotal).append("元（").append(agg.orderCount).append("单）\n");
        user.append("常去时段：").append(agg.activeHours == null ? "暂无" : agg.activeHours).append("\n");
        user.append("情绪记录：").append(profile.getMoodNotes() == null || profile.getMoodNotes().isBlank()
                ? "暂无" : profile.getMoodNotes()).append("\n");
        user.append("累计对话：").append(profile.getMsgCount() == null ? 0 : profile.getMsgCount()).append("轮");

        com.qmz.canteen.agent.AiResult r = router.complete(ModelRole.FAST,
                "你是校园食堂的学生画像分析助手，只输出画像正文，不要标题、不要列表符号、不要 Markdown。",
                user.toString(), 300, 0.6);
        String text = r == null ? null : r.content();
        if (text != null) {
            text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1").trim();
            if (text.length() > 900) {
                text = text.substring(0, 900);
            }
        }
        return text;
    }

    private StudentProfile getOrCreate(Long userId) {
        StudentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<StudentProfile>()
                .eq(StudentProfile::getUserId, userId).last("LIMIT 1"));
        if (profile != null) {
            return profile;
        }
        StudentProfile created = new StudentProfile();
        created.setUserId(userId);
        created.setMsgCount(0);
        created.setExpenseWeek(BigDecimal.ZERO);
        created.setExpenseMonth(BigDecimal.ZERO);
        created.setExpenseTotal(BigDecimal.ZERO);
        created.setOrderCount(0);
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        try {
            profileMapper.insert(created);
        } catch (Exception e) {
            // 并发插入撞唯一键：回读即可
            profile = profileMapper.selectOne(new LambdaQueryWrapper<StudentProfile>()
                    .eq(StudentProfile::getUserId, userId).last("LIMIT 1"));
            if (profile != null) {
                return profile;
            }
            throw e;
        }
        return created;
    }

    private StudentProfileVO toVO(StudentProfile profile, boolean withUser) {
        StudentProfileVO vo = new StudentProfileVO();
        vo.setUserId(profile.getUserId());
        vo.setTasteTags(profile.getTasteTags());
        vo.setFavoriteStalls(profile.getFavoriteStalls());
        vo.setFavoriteDishes(profile.getFavoriteDishes());
        vo.setExpenseWeek(profile.getExpenseWeek());
        vo.setExpenseMonth(profile.getExpenseMonth());
        vo.setExpenseTotal(profile.getExpenseTotal());
        vo.setOrderCount(profile.getOrderCount());
        vo.setMoodNotes(profile.getMoodNotes());
        vo.setAiSummary(profile.getAiSummary());
        vo.setAiSummaryAt(profile.getAiSummaryAt());
        vo.setMsgCount(profile.getMsgCount());
        if (withUser) {
            User u = userMapper.selectById(profile.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setUsername(u.getUsername());
                vo.setStudentNo(u.getStudentNo());
            }
        }
        return vo;
    }

    private String joinStallNames(Map<Long, Integer> stallCount, int top) {
        if (stallCount.isEmpty()) {
            return null;
        }
        List<Long> ids = stallCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(top).map(Map.Entry::getKey).toList();
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            com.qmz.canteen.entity.Stall s = stallMapperSafe(id);
            if (s != null) {
                names.add(s.getName());
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private com.qmz.canteen.entity.Stall stallMapperSafe(Long id) {
        try {
            return stallMapper.selectById(id);
        } catch (Exception e) {
            return null;
        }
    }

    private com.qmz.canteen.entity.Dish dishMapperSafe(Long id) {
        try {
            return dishMapper.selectById(id);
        } catch (Exception e) {
            return null;
        }
    }

    private String topHours(Map<String, Integer> buckets) {
        if (buckets.isEmpty()) {
            return null;
        }
        return buckets.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(2)
                .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .reduce((a, b) -> a + "、" + b).orElse(null);
    }

    private String joinTop(Map<String, Integer> counts, int top) {
        if (counts.isEmpty()) {
            return null;
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(top)
                .map(Map.Entry::getKey)
                .reduce((a, b) -> a + "," + b).orElse(null);
    }

    /** 聚合中间结果 */
    private static class Aggregate {
        String tasteTags;
        String favoriteStalls;
        String favoriteDishes;
        String activeHours;
        BigDecimal expenseWeek;
        BigDecimal expenseMonth;
        BigDecimal expenseTotal;
        int orderCount;
    }
}
