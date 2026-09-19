package com.mayiran.commerceservice.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.enums.RoleEnum;
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
        // --- 越权控制：谁在看订单，由 token 决定，不由请求参数决定 ---
        // 客服和管理员本来就要看全站订单，不覆盖；其余角色一律锁成"只能看自己"
        String role = UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);
        if (!canSeeAll) {
            // OrderPageDTO.userId 是 String，UserContext.getUserId() 是 Long，用 String.valueOf 过桥
            orderPageDTO.setUserId(String.valueOf(UserContext.getUserId()));
        }
        //设置分页码和每页记录数
        PageHelper.startPage(orderPageDTO.getPage(),orderPageDTO.getLimit());
        Page<OrderVO> page=orderMapper.pageOrders(orderPageDTO);
        PageInfo<OrderVO> pageInfo=new PageInfo<>(page);
        return new PageResult<>(pageInfo.getTotal(),pageInfo.getList());
    }
}
