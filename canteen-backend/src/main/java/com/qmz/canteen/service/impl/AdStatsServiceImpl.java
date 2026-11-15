package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.DiningOrderItem;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishAd;
import com.qmz.canteen.entity.DishAdStat;
import com.qmz.canteen.mapper.DiningOrderItemMapper;
import com.qmz.canteen.mapper.DishAdMapper;
import com.qmz.canteen.mapper.DishAdStatMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.service.AdStatsService;
import com.qmz.canteen.vo.AdStatsOverviewVO;
import com.qmz.canteen.vo.AdStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdStatsServiceImpl implements AdStatsService {

    private final DishAdStatMapper statMapper;
    private final DishAdMapper adMapper;
    private final DishMapper dishMapper;
    private final DiningOrderItemMapper orderItemMapper;

    @Override
    public void recordImpressions(List<Long> adIds) {
        if (adIds == null || adIds.isEmpty()) {
            return;
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : adIds) {
            if (id != null && unique.size() < 20) {
                unique.add(id);
            }
        }
        for (Long id : unique) {
            if (isActive(adMapper.selectById(id))) {
                statMapper.incrImpression(id);
            }
        }
    }

    @Override
    public boolean recordClick(Long adId) {
        if (adId == null || !isActive(adMapper.selectById(adId))) {
            return false;
        }
        statMapper.incrClick(adId);
        return true;
    }

    @Override
    public void recordConversion(Long orderId) {
        if (orderId == null) {
            return;
        }
        List<DiningOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                .eq(DiningOrderItem::getOrderId, orderId)
                .isNotNull(DiningOrderItem::getAdId));
        for (DiningOrderItem item : items) {
            if (item.getAdId() != null) {
                statMapper.incrConversion(item.getAdId());
            }
        }
        if (!items.isEmpty()) {
            log.info("广告转化归因：order={} 命中 {} 个广告项", orderId, items.size());
        }
    }

    @Override
    public AdStatsVO stats(Long adId, int days) {
        DishAd ad = adMapper.selectById(adId);
        if (ad == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "投放记录不存在");
        }
        int range = clampDays(days);
        LocalDate from = LocalDate.now().minusDays(range - 1L);
        List<DishAdStat> rows = statMapper.selectByAd(adId, from);
        Map<LocalDate, DishAdStat> byDate = new HashMap<>();
        for (DishAdStat row : rows) {
            byDate.put(row.getStatDate(), row);
        }

        AdStatsVO vo = new AdStatsVO();
        vo.setAdId(ad.getId());
        vo.setPlanName(ad.getPlanName());
        vo.setAmount(ad.getAmount());
        vo.setDays(range);
        Dish dish = dishMapper.selectById(ad.getDishId());
        vo.setDishName(dish == null ? null : dish.getName());

        List<AdStatsVO.Daily> daily = new ArrayList<>();
        long impressions = 0;
        long clicks = 0;
        long conversions = 0;
        for (int i = 0; i < range; i++) {
            LocalDate date = from.plusDays(i);
            DishAdStat row = byDate.get(date);
            AdStatsVO.Daily d = new AdStatsVO.Daily(date);
            if (row != null) {
                d.setImpressions(nz(row.getImpressions()));
                d.setClicks(nz(row.getClicks()));
                d.setConversions(nz(row.getConversions()));
                impressions += nz(row.getImpressions());
                clicks += nz(row.getClicks());
                conversions += nz(row.getConversions());
            }
            daily.add(d);
        }
        vo.setDaily(daily);
        vo.setImpressions(impressions);
        vo.setClicks(clicks);
        vo.setConversions(conversions);
        vo.setCtr(rate(clicks, impressions));
        vo.setCvr(rate(conversions, clicks));
        vo.setCostPerClick(costPerClick(ad.getAmount(), clicks));
        return vo;
    }

    @Override
    public AdStatsOverviewVO overview(int days) {
        int range = clampDays(days);
        LocalDate from = LocalDate.now().minusDays(range - 1L);
        Map<Long, long[]> sums = new HashMap<>();
        for (Map<String, Object> row : statMapper.sumByAd(from)) {
            Long adId = toLong(row.get("adId"));
            sums.put(adId, new long[]{
                    toLong(row.get("impressions")),
                    toLong(row.get("clicks")),
                    toLong(row.get("conversions"))
            });
        }

        List<DishAd> ads = adMapper.selectList(new LambdaQueryWrapper<DishAd>()
                .orderByDesc(DishAd::getId).last("LIMIT 100"));
        List<AdStatsVO> list = new ArrayList<>();
        long totalImp = 0;
        long totalClick = 0;
        long totalConv = 0;
        for (DishAd ad : ads) {
            long[] sum = sums.getOrDefault(ad.getId(), new long[]{0, 0, 0});
            AdStatsVO vo = new AdStatsVO();
            vo.setAdId(ad.getId());
            vo.setPlanName(ad.getPlanName());
            vo.setAmount(ad.getAmount());
            vo.setImpressions(sum[0]);
            vo.setClicks(sum[1]);
            vo.setConversions(sum[2]);
            vo.setCtr(rate(sum[1], sum[0]));
            vo.setCvr(rate(sum[2], sum[1]));
            vo.setCostPerClick(costPerClick(ad.getAmount(), sum[1]));
            Dish dish = dishMapper.selectById(ad.getDishId());
            vo.setDishName(dish == null ? null : dish.getName());
            list.add(vo);
            totalImp += sum[0];
            totalClick += sum[1];
            totalConv += sum[2];
        }
        list.sort(Comparator.comparingLong(AdStatsVO::getImpressions).reversed());

        AdStatsOverviewVO overview = new AdStatsOverviewVO();
        overview.setImpressions(totalImp);
        overview.setClicks(totalClick);
        overview.setConversions(totalConv);
        overview.setCtr(rate(totalClick, totalImp));
        overview.setCvr(rate(totalConv, totalClick));
        overview.setAds(list);
        return overview;
    }

    private boolean isActive(DishAd ad) {
        if (ad == null || !DishAd.STATUS_ACTIVE.equals(ad.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (ad.getStartTime() != null && ad.getStartTime().isAfter(now)) {
            return false;
        }
        return ad.getEndTime() == null || !ad.getEndTime().isBefore(now);
    }

    private static int clampDays(int days) {
        return Math.min(Math.max(days, 1), 90);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }

    private static BigDecimal costPerClick(BigDecimal amount, long clicks) {
        if (amount == null || clicks <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP);
    }
}
