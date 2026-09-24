package com.mayiran.commerceservice.dto;

import lombok.Data;

import java.io.Serializable;
@Data
public class AfterSaleCreateDTO implements Serializable {

    //订单号
    private String orderNo;
    //商品id
    private Long productId;
    //工单类型
    private String type;
    //申请原因
    private String reason;
}
