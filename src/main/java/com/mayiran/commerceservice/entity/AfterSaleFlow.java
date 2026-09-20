package com.mayiran.commerceservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSaleFlow implements Serializable {
    private Long id;
    //工单号
    private String ticketNo;
    //来源状态
    private String fromStatus;
    //去向状态
    private String toStatus;
    //实施者Id
    private Long operatorId;
    //实施者类型
    private String operatorType;
    //备注
    private String remark;
    //创建时间
    private LocalDateTime createTime;
}
