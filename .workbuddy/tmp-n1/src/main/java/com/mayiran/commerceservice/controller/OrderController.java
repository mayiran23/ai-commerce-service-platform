package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.entity.Order;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.service.impl.OrderServiceImpl;
import com.mayiran.commerceservice.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    //根据订单号查询详细信息
    @GetMapping("{orderNo}")
    public Result<OrderVO> getOrderDetail(@PathVariable String orderNo){
        log.info("根据订单号查询订单的详细信息:{}",orderNo);
        OrderVO orderVO = orderService.getOrdersByorderNo(orderNo);
        return Result.success(orderVO);
    }
}
