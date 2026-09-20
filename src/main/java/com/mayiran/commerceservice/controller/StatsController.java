package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.StatsService;
import com.mayiran.commerceservice.vo.OrderStatsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Slf4j
public class StatsController {
    @Autowired
    private StatsService statsService;

    /**
     * 订单统计接口
     * @return
     */
    @GetMapping("/orders")
    public Result<OrderStatsVO> getStats(){
        log.info("进行订单的统计");
        OrderStatsVO stats = statsService.getStats();
        return Result.success(stats);

    }
}
