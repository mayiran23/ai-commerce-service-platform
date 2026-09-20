package com.mayiran.commerceservice.dto;

import lombok.Data;

import java.io.Serializable;
@Data
public class AfterSalePageDTO implements Serializable {
    //第几页,页数
    private int page=1;
    //每页条数,上限为100
    private int limit=10;
    //匹配订单号,用户昵称,商品名,任一命中
    private String keyword;
    //订单状态
    private String status;
    //来源
    private String source;
    //用户的ID
    private Long userId;
}
