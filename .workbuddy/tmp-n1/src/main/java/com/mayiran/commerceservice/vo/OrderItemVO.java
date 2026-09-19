package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemVO implements Serializable {
    private static final long serialVersionUID = 1L;
    //商品的id
    private Long productId;
    //商品的名称
    private String productName;
    //商品的价格
    private BigDecimal price;
    //商品的数量
    private Integer quantity;
    //退货资格截止时间
    private LocalDateTime refundDeadline;
    //是否还具备退货资格
    private Boolean refundEligible;
    //剩余时间
    private Long hoursLeft;
}
