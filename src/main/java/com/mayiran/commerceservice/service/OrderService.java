package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.dto.SearchDTO;
import com.mayiran.commerceservice.mapper.OrderMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.vo.OrderVO;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public interface OrderService {

    List<OrderVO> searchOrderForInternal(SearchDTO searchDTO);

    /*
        商品的分页查询
         */
    PageResult<OrderVO> pageOrders(OrderPageDTO orderPageDTO);

    /**
     * 根据订单号查询订单的详细信息
     * @param orderNo
     * @return
     */
    OrderVO getOrdersByorderNo(String orderNo);

    /**
     * 给AI内部接口用:显式传userId校验归属,不依赖登录态
     * @param orderNo
     * @param userId
     * @return
     */
    OrderVO getOrderForInternal(String orderNo, Long userId);
}
