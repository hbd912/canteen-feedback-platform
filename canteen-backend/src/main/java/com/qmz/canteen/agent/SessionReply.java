package com.qmz.canteen.agent;

import java.math.BigDecimal;

/**
 * 一条客服 AI 回复的结果：正文文本 + 可选附带的一道菜品ID + 可选安抚折扣。
 * <p>
 * dishId 非空时，前端会把该菜品渲染成一张菜品卡（含图/名/价），并支持点「+」直接加入购物车。
 * couponRate 非空时，表示 AI 检测到老顾客心情不佳、想给个实在安慰，系统会据此发一张档口真实优惠券
 * （见 {@code CustomerServiceServiceImpl.maybeGrantVeteranDiscount}），避免「空头承诺不发券」。
 *
 * @param text        回复正文（已经过去除「推荐菜：」「给券：」标记行的清洗）
 * @param dishId      推荐的那道菜（本档口在售），没有推荐则为 null
 * @param couponRate  安抚折扣率（0.9=九折；0=免单），AI 没提就不发券为 null
 */
public record SessionReply(String text, Long dishId, BigDecimal couponRate) {

    /** 纯文本回复，不带菜品卡、不发券 */
    public static SessionReply plain(String text) {
        return new SessionReply(text, null, null);
    }
}