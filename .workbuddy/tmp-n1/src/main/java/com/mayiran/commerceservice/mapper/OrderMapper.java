package com.mayiran.commerceservice.mapper;

import com.github.pagehelper.Page;
import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.entity.OrderItem;
import com.mayiran.commerceservice.entity.OrderLogistics;
import com.mayiran.commerceservice.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper {

    //订单的分页查询
    Page<OrderVO> pageOrders(OrderPageDTO orderPageDTO);

    //根据订单号查询订单的详细信息
    OrderVO getByOrderNo(@Param("orderNo") String orderNo);

    //根据订单号查询商品的信息,并且要计算退货资格
    List<OrderItem> getItems(@Param("orderNo") String orderNo);

    //根据订单号查询物流的信息
    OrderLogistics getLogistics(@Param("orderNo") String orderNo);

    //批量查询多个订单的商品明细（给列表接口用，避免每行订单都单独查一次数据库）
    //入参是一个订单号集合，比如 ["SO001","SO002","SO003"]
    List<OrderItem> getItemsByOrderNos(@Param("orderNos") List<String> orderNos);
}
