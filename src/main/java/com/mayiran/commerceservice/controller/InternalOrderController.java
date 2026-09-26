package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.dto.SearchDTO;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内部接口:给Python AI服务调用
 */
@RestController
@RequestMapping("/internal/orders")
@Slf4j
public class InternalOrderController {
    @Autowired
    private OrderService orderService;

    /**
     * 根据订单号查询订单
     * @param orderNo
     * @param userId
     * @return
     */
    @GetMapping("/{orderNo}")
    public OrderVO getOrder(@PathVariable String orderNo, @RequestParam Long userId){
        log.info("内部接口查询订单:orderNo:{}, userId:{}", orderNo, userId);
        return orderService.getOrderForInternal(orderNo,userId);
    }

    @GetMapping("/search")
    public List<OrderVO> searchOrder(SearchDTO searchDTO){
        log.info("内部接口模糊查询订单:userId:{},status:{}", searchDTO.getUserId(), searchDTO.getStatus());

        return orderService.searchOrderForInternal(searchDTO);
    }
}
