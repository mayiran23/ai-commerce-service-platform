package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderLogisticsVO implements Serializable {
    //运单号
    private String trackNo;
    //当前状态
    private String currentStatus;
    //当前所在节点
    private String currentNode;
    //预估到达时间
    private LocalDate estimatedArrival;
}
