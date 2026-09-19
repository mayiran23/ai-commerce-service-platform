package com.mayiran.commerceservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    private Long id;
    //订单号
    private String orderNo;
    //下单用户ID
    private Long userId;
    //订单状态
    private String status;
    //总金额
    private BigDecimal totalAmount;
    //付款金额
    private BigDecimal payAmount;
    //付款时间
    private LocalDateTime payTime;
    //发货时间
    private LocalDateTime shipTime;
    //收货时间
    private LocalDateTime receiveTime;
    //收货人姓名
    private String receiverName;
    //收货人手机号
    private String receiverPhone;
    //收货人地址
    private String receiverAddr;
    //创建时间
    private LocalDateTime createTime;
    //更新时间
    private LocalDateTime updateTime;


}
