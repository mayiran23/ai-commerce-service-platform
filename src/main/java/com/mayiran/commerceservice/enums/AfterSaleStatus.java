package com.mayiran.commerceservice.enums;

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
}
