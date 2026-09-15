package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.mapper.OrderMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.vo.OrderVO;
import org.springframework.beans.factory.annotation.Autowired;

public interface OrderService {

    /*
    商品的分页查询
     */
    PageResult<OrderVO> pageOrders(OrderPageDTO orderPageDTO);
}
