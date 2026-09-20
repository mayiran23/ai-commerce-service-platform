package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.vo.OrderStatsVO;
import org.springframework.stereotype.Service;


public interface StatsService {
    //订单的统计
    OrderStatsVO getStats();
}
