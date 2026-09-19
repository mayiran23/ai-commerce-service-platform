package com.mayiran.commerceservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderLogistics implements Serializable {
    private Long id;
    //订单号
    private String orderNo;
    //运单号
    private String trackNo;
    //当前状态
    private String currentStatus;
    //当前所在节点
    private String currentNode;
    //预估到达时间
    private LocalDate estimatedArrival;
    //更新时间
    private LocalDateTime updateTime;
}
