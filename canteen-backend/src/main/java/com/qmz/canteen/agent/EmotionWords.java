package com.qmz.canteen.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 情绪词识别：供「情绪陪聊」分支与画像情绪记录共用。
 * <p>
 * 设计动机：此前「我心情不好」这类话在规则引擎里没有任何分支可命中，
 * 只会落到「这个问题我还没学会」——在用户看来就是「死机」。
 * 有了统一的情绪词表，Agent 提示词、强制查库判断（needsData）和
 * 规则引擎兜底（route）三处都能正确对待情绪表达。
 */
public final class EmotionWords {

    private EmotionWords() {
    }

    /** 情绪关键词（命中任意一个即视为情绪表达） */
    private static final String[] WORDS = {
            // 难过/低落
            "心情不好", "心情差", "心情很糟", "难过", "伤心", "悲伤", "沮丧", "失落", "低落", "郁闷",
            "不开心", "不高兴", "emo", "破防", "心态崩", "崩溃", "想哭", "委屈",
            // 压力/疲惫
            "压力大", "压力好大", "好累", "好疲惫", "疲惫", "心累", "焦虑", "紧张", "烦躁", "睡不着",
            "失眠", "考试没过", "挂科", "挂了", "考试考砸", "考砸", "面试挂", "卷不动", "撑不住",
            // 想家/孤独
            "想家", "想爸妈", "想妈妈", "想男朋友", "想女朋友", "孤独", "寂寞", "一个人好",
            // 生气
            "生气", "气死", "好气", "憋屈", "愤怒",
            // 开心（也要接住：分享喜悦同样是情绪陪伴）
            "好开心", "太开心", "高兴", "太好了", "太棒了", "好激动", "成功了", "通过啦", "上岸", "中奖"
    };

    /**
     * 返回命中的第一个情绪词；未命中返回 null。
     */
    public static String match(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String w : WORDS) {
            if (lower.contains(w)) {
                return w;
            }
        }
        return null;
    }

    /** 情绪归类：sad / stress / lonely / angry / happy，未命中返回 null */
    public static String categoryOf(String text) {
        String hit = match(text);
        if (hit == null) {
            return null;
        }
        if (containsAny(text, "好开心", "太开心", "高兴", "太好了", "太棒了", "好激动", "成功了", "通过啦", "上岸", "中奖")) {
            return "happy";
        }
        if (containsAny(text, "想家", "想爸妈", "想妈妈", "想男朋友", "想女朋友", "孤独", "寂寞", "一个人好")) {
            return "lonely";
        }
        if (containsAny(text, "压力大", "压力好大", "好累", "好疲惫", "疲惫", "心累", "焦虑", "紧张", "烦躁",
                "睡不着", "失眠", "考试没过", "挂科", "挂了", "考试考砸", "考砸", "面试挂", "卷不动", "撑不住")) {
            return "stress";
        }
        if (containsAny(text, "生气", "气死", "好气", "憋屈", "愤怒")) {
            return "angry";
        }
        return "sad";
    }

    /** 给画像用的中文情绪标签 */
    public static String labelOf(String category) {
        return switch (category) {
            case "happy" -> "开心";
            case "lonely" -> "想家孤独";
            case "stress" -> "压力大疲惫";
            case "angry" -> "生气憋屈";
            case "sad" -> "难过低落";
            default -> null;
        };
    }

    private static boolean containsAny(String text, String... words) {
        String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        for (String w : words) {
            if (lower.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 供日志/调试：整表只读视图 */
    public static Map<String, String> categories() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("sad", "难过低落");
        map.put("stress", "压力大疲惫");
        map.put("lonely", "想家孤独");
        map.put("angry", "生气憋屈");
        map.put("happy", "开心");
        return map;
    }
}
