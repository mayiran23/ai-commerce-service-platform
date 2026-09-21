package com.mayiran.commerceservice.enums;

import java.util.List;
import java.util.Map;

/**
 * 工单状态枚举
 */
public enum AfterSaleStatus {
    PENDING("待审核"),
    MANUAL_REVIEW("待人工"),
    APPROVED("已通过"),
    REFUNDING("退款中"),
    COMPLETED("已完成"),
    REJECTED("已驳回"),
    CANCELLED("已撤销");

    private final String text;

    AfterSaleStatus(String text){
        this.text = text;
    }
    public String getText() {
        return text;
    }

    public static String textOf(String code){
        if(code==null){
            return null;
        }
        for(AfterSaleStatus status : AfterSaleStatus.values()){
            if(status.name().equals(code)){
                return status.text;
            }
        }
        return code;
    }


    /* ==================== 状态机：合法流转表 ==================== */

    /**
     * 这张表必须和 frontend/assets/ui.js 里的 TRANSITIONS 逐字一致。
     * 前端用它决定"显示哪几个按钮"，后端用它在流转接口里做真正校验 —— 后端才是权威。
     *
     * ⚠️ 为什么写在 static 块里，而不是写成枚举的实例字段？
     * 枚举常量按声明顺序初始化，在构造器/实例字段里引用别的常量会触发
     * "illegal reference to static field from initializer" 编译错误。
     * static 块在所有常量初始化完成后才执行，所以安全。
     */
    private static final Map<String, List<String>> TRANSITIONS;

    static {
        TRANSITIONS = Map.of(
                PENDING.name(),       List.of(APPROVED.name(), REJECTED.name(), MANUAL_REVIEW.name(), CANCELLED.name()),
                MANUAL_REVIEW.name(), List.of(APPROVED.name(), REJECTED.name(), CANCELLED.name()),
                APPROVED.name(),      List.of(REFUNDING.name()),
                REFUNDING.name(),     List.of(COMPLETED.name()),
                COMPLETED.name(),     List.of(),
                REJECTED.name(),      List.of(),
                CANCELLED.name(),     List.of()
        );
    }

    /**
     * 当前状态允许流转到的目标状态。终态返回空列表，认不出的状态也返回空列表。
     */
    public static List<String> allowedTransitionsOf(String status) {
        if (status == null) {
            return List.of();
        }
        return TRANSITIONS.getOrDefault(status, List.of());
    }
}
