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
@AllArgsConstructor
@NoArgsConstructor
public class AfterSale implements Serializable {
    private Long id;
    //工单号
    private String ticketNo;
    //订单号
    private String OrderNo;
    //订单中商品的Id
    private Long OrderItemId;
    //用户id
    private Long userId;
    //工单类型
    private String type;
    //原因
    private String reason;
    //工单状态
    private String status;
    //是否AI创建
    private Integer aiGenerated;
    //AI置信度
    private BigDecimal aiConfidence;
    //来源
    private String source;
    //处理人的Id
    private Long handlerId;
    //处理备注
    private String handleRemark;
    //创建时间
    private LocalDateTime createTime;
    //更新时间
    private LocalDateTime updateTime;
}
