package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderStatsVO implements Serializable {
    // 总订单数
    private Long total;
    //各状态的订单数
    private Map<String,Long> byStatus;
}
