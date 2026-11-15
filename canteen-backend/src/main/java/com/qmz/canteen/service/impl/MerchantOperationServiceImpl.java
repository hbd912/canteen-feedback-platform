package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.agent.MerchantOperationAdvisor;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.MerchantOperationAction;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.MerchantOperationActionMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.service.MerchantOperationService;
import com.qmz.canteen.vo.MerchantDiagnosisVO;
import com.qmz.canteen.vo.MerchantOperationActionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantOperationServiceImpl implements MerchantOperationService {

    private final MerchantOperationAdvisor advisor;
    private final MerchantOperationActionMapper actionMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;

    @Override
    public MerchantDiagnosisVO diagnose(Long stallId, int days) {
        int d = Math.min(Math.max(days, 7), 90);
        List<MerchantOperationAction> actions = advisor.diagnose(stallId, d);
        int totalDishes = countOnSale(stallId);
        String stallName = stallName(stallId);
        String summary = advisor.summarize(stallName, d, totalDishes, actions.size(), actions);

        MerchantDiagnosisVO vo = new MerchantDiagnosisVO();
        vo.setSummary(summary);
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setTotalDishes(totalDishes);
        vo.setWeakDishCount(actions.size());
        List<MerchantOperationActionVO> actionVOs = new ArrayList<>();
        for (MerchantOperationAction a : actions) {
            actionVOs.add(toVO(a));
        }
        vo.setActions(actionVOs);
        return vo;
    }

    @Override
    public List<MerchantOperationActionVO> listActions(Long stallId) {
        List<MerchantOperationAction> actions = actionMapper.selectList(new LambdaQueryWrapper<MerchantOperationAction>()
                .eq(MerchantOperationAction::getStallId, stallId)
                .eq(MerchantOperationAction::getStatus, MerchantOperationAction.STATUS_PENDING)
                .orderByDesc(MerchantOperationAction::getId));
        List<MerchantOperationActionVO> result = new ArrayList<>();
        for (MerchantOperationAction a : actions) {
            result.add(toVO(a));
        }
        return result;
    }

    @Override
    @Transactional
    public MerchantOperationActionVO approve(Long stallId, Long actionId) {
        MerchantOperationAction action = actionMapper.selectById(actionId);
        if (action == null || !action.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "建议不存在或不属于本档口");
        }
        if (!MerchantOperationAction.STATUS_PENDING.equals(action.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该建议已处理，不能重复执行");
        }
        if (!MerchantOperationAction.ACTION_OFF_SHELF.equals(action.getActionType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "该动作类型不支持自动执行");
        }
        // 重新校验菜品仍属于本档口，且状态与建议时一致，避免执行过期建议
        Dish dish = dishMapper.selectById(action.getDishId());
        if (dish == null || !dish.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "菜品不存在或已不属于本档口");
        }
        if (!Integer.valueOf(1).equals(dish.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "菜品已是下架状态，无需重复下架");
        }

        Dish update = new Dish();
        update.setId(dish.getId());
        update.setStatus(0);
        dishMapper.updateById(update);

        action.setStatus(MerchantOperationAction.STATUS_EXECUTED);
        action.setExecutedTime(LocalDateTime.now());
        actionMapper.updateById(action);
        return toVO(action);
    }

    @Override
    @Transactional
    public void reject(Long stallId, Long actionId) {
        MerchantOperationAction action = actionMapper.selectById(actionId);
        if (action == null || !action.getStallId().equals(stallId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "建议不存在或不属于本档口");
        }
        if (!MerchantOperationAction.STATUS_PENDING.equals(action.getStatus())) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "该建议已处理");
        }
        action.setStatus(MerchantOperationAction.STATUS_REJECTED);
        actionMapper.updateById(action);
    }

    private int countOnSale(Long stallId) {
        return Math.toIntExact(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId)
                .eq(Dish::getStatus, 1)));
    }

    private String stallName(Long stallId) {
        Stall stall = stallMapper.selectById(stallId);
        return stall == null ? "本档口" : stall.getName();
    }

    private MerchantOperationActionVO toVO(MerchantOperationAction a) {
        MerchantOperationActionVO vo = new MerchantOperationActionVO();
        vo.setId(a.getId());
        vo.setActionType(a.getActionType());
        vo.setDishId(a.getDishId());
        vo.setDishName(a.getDishName());
        vo.setExpectedStatus(a.getExpectedStatus());
        vo.setTargetStatus(a.getTargetStatus());
        vo.setReason(a.getReason());
        vo.setStatus(a.getStatus());
        vo.setCreateTime(a.getCreateTime());

        Dish dish = a.getDishId() == null ? null : dishMapper.selectById(a.getDishId());
        if (dish != null) {
            vo.setDishImage(dish.getImage());
            vo.setPrice(dish.getPrice());
        }
        // 解析证据快照
        String evidence = a.getEvidenceJson();
        if (evidence != null && !evidence.isBlank()) {
            vo.setSalesQty(parseLong(evidence, "salesQty"));
            vo.setReviewCount(parseLong(evidence, "reviewCount"));
            vo.setBadCount(parseLong(evidence, "badCount"));
            vo.setAvgRating(parseBigDecimal(evidence, "avgRating"));
        }
        return vo;
    }

    private Long parseLong(String json, String key) {
        String value = extract(json, key);
        if (value == null) {
            return 0L;
        }
        try {
            return (long) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal parseBigDecimal(String json, String key) {
        String value = extract(json, key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String extract(String json, String key) {
        String token = "\"" + key + "\":";
        int idx = json.indexOf(token);
        if (idx < 0) {
            return null;
        }
        int start = idx + token.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        return end == start ? null : json.substring(start, end);
    }
}
