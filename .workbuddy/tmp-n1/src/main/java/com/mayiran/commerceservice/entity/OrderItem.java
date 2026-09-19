package com.mayiran.commerceservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem implements Serializable {
    private Long id;
    //订单的ID
    private Long orderId;
    //订单号
    private String orderNo;
    //产品Id
    private Long productId;
    //产品名称
    private String productName;
    //产品价格
    private BigDecimal productPrice;
    //数量
    private Integer quantity;
    //退货资格截止时间
    private LocalDateTime refundDeadline;

}
