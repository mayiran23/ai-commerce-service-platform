package com.mayiran.commerceservice.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.mapper.OrderMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;

    @Override
    public PageResult<OrderVO> pageOrders(OrderPageDTO orderPageDTO) {
        //设置分页码和每页记录数
        PageHelper.startPage(orderPageDTO.getPage(),orderPageDTO.getLimit());
        Page<OrderVO> page=orderMapper.pageOrders(orderPageDTO);
        PageInfo<OrderVO> pageInfo=new PageInfo<>(page);
        return new PageResult<>(pageInfo.getTotal(),pageInfo.getList());
    }
}
