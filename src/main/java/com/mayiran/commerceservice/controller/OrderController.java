package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.entity.Order;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.service.impl.OrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    //分页查询
    @GetMapping
    public Result<PageResult> listOrders(OrderPageDTO orderPageDTO) {
        log.info("商品的分页查询:{}",orderPageDTO);
        PageResult page= orderService.pageOrders(orderPageDTO);
        return Result.success(page);
    }
}
