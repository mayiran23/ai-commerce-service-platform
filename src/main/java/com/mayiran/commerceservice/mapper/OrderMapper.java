package com.mayiran.commerceservice.mapper;

import com.github.pagehelper.Page;
import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper {

    //订单的分页查询
    Page<OrderVO> pageOrders(OrderPageDTO orderPageDTO);
}
