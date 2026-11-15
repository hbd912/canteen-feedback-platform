package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {

    @NotBlank(message = "请输入你想问的问题")
    @Size(max = 200, message = "问题不能超过200字")
    private String question;

    /**
     * 多轮历史，前端回传最近若干轮对话。
     * 元素形如 {"role":"user","content":"宫保鸡丁多少钱"}，
     * 由后端做条数裁剪与角色校验，防止被塞入任意内容。
     */
    private List<Map<String, String>> history;

    /**
     * 小助手会话ID（历史持久化用）。为空时自动复用最近 24 小时内的会话或新建。
     */
    @Pattern(regexp = "\\d{1,18}", message = "会话ID不合法")
    private String sessionId;
}
