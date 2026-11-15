package com.qmz.canteen.payment;

public record RefundResult(boolean success, String refundNo, String message) {

    public static RefundResult ok(String refundNo) {
        return new RefundResult(true, refundNo, "退款成功");
    }

    public static RefundResult fail(String message) {
        return new RefundResult(false, null, message == null ? "退款失败" : message);
    }
}
