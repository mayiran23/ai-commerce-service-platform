package com.mayiran.commerceservice.enums;

/**
 * 退后资格校验结果码
 * 不能只返回true,false
 * 因为AI需要具体原因才能决策下一步
 */
public enum EligibilityCode {

    /** 还在无理由退货期内，可以按正常流程申请 */
    OK("可以申请"),

    /** 已超过退货期限 —— AI 应引导转人工，而不是直接拒绝 */
    OVER_DEADLINE("已超过退货期限"),

    /** 订单还没签收（含已取消）—— 退货的前提是"已经收到货" */
    NOT_RECEIVED("订单尚未签收"),

    /** 非质量问题场景 —— 契约预留，当前请求体拿不到退货原因，暂时不会返回 */
    NOT_QUALITY("非质量问题场景");

    private final String text;

    EligibilityCode(String text) {
        this.text = text;
    }

    public String getText(){
        return text;
    }
}
