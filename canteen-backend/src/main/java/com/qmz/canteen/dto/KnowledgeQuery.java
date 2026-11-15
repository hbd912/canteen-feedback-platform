package com.qmz.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class KnowledgeQuery {

    @Pattern(regexp = "(FAQ|EXPERIENCE)?", message = "知识类型不合法")
    private String kind;

    private String keyword;

    /** 指定分类时仅查询 FAQ；同时指定 scene 时无匹配记录。 */
    private String category;

    /** 指定场景时仅查询经验。 */
    private String scene;

    @Min(value = 1, message = "页码不能小于1")
    private int current = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数最多100条")
    private int size = 10;
}
