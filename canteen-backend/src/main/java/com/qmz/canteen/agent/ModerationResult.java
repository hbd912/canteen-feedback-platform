package com.qmz.canteen.agent;

/**
 * 内容安全审核结果。
 */
public record ModerationResult(boolean flagged, String category, String reason, String studentReply) {

    public static ModerationResult pass() {
        return new ModerationResult(false, null, null, null);
    }

    public static ModerationResult block(String category, String reason, String studentReply) {
        return new ModerationResult(true, category, reason, studentReply);
    }
}
