package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSalePageVO implements Serializable {
    //工单总数
    private long total;
    //当前页工单
    private List<AfterSaleVO> records;
    //各状态的工单数
    private Map<String,Long> stats;
}
