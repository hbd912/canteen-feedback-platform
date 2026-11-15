package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MerchantDiagnosisVO {

    private String summary;
    private LocalDateTime generatedAt;
    private Integer totalDishes;
    private Integer weakDishCount;
    private List<MerchantOperationActionVO> actions;
}
