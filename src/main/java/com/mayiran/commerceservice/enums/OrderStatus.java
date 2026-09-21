package com.mayiran.commerceservice.enums;
/**
 * 订单状态枚举（对应 t_order.status）
 *
 * 中文文案必须和前端 frontend/assets/ui.js 里的 ORDER_STATUS_TEXT
 * 以及 docs/API.md §2.1 的枚举字典完全一致，改一处就要三处一起改。
 */
public enum OrderStatus {

    PENDING_PAY("待付款"),
    PAID("已付款"),
    SHIPPED("已发货"),
    DELIVERING("运输中"),
    RECEIVED("已签收"),
    CANCELLED("已取消");

    private final String text;

    OrderStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * 状态码 → 中文。
     */
    public static String textOf(String code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equals(code)) {
                return status.text;
            }
        }
        return code;
    }
}