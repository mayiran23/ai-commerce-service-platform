package com.mayiran.commerceservice.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.entity.OrderItem;
import com.mayiran.commerceservice.entity.OrderLogistics;
import com.mayiran.commerceservice.enums.RoleEnum;
import com.mayiran.commerceservice.exception.OrderNotFoundException;
import com.mayiran.commerceservice.mapper.OrderMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.vo.OrderItemVO;
import com.mayiran.commerceservice.vo.OrderLogisticsVO;
import com.mayiran.commerceservice.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public OrderVO getOrdersByorderNo(String orderNo) {
        //先查询出订单的信息,再查询订单的关联的商品信息和运送轨迹信息
        //1:查订单主体
        OrderVO order=orderMapper.getByOrderNo(orderNo);
        if(order==null){
            //订单号不存在
            log.warn("订单不存在:{}",orderNo);
            throw new OrderNotFoundException(MessageConstant.ORDER_NOT_FOUND);
        }
        // ---- 2. 越权检查：查出来再判断 ----
        // 为什么这里可以"先查再判断"，而列表接口必须把条件写进 SQL？
        // 列表是"批量返回"，漏判一次就是几百条数据泄露；详情只返回一条，
        // 而且下一行立刻 throw，数据走不出这个方法。两者的风险面不一样。
        String role = UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);
        if (!canSeeAll && !order.getUserId().equals(UserContext.getUserId())) {
            // ⚠️ 文案必须和"订单不存在"逐字相同，且不能返回 HTTP 403
            log.warn("越权访问订单: orderNo={}, 订单归属={}, 当前用户={}",
                    orderNo, order.getUserId(), UserContext.getUserId());
            throw new OrderNotFoundException(MessageConstant.ORDER_NOT_FOUND);
        }
        //查明细,并且需要计算退货的资格
        List<OrderItem> items = orderMapper.getItems(orderNo);
        order.setItems(toItemVOList(items));

        //查物流信息
        OrderLogistics logistics = orderMapper.getLogistics(orderNo);
        order.setLogistics(toLogisticsVO(logistics));

        return order;
    }
    private OrderLogisticsVO toLogisticsVO(OrderLogistics logistics){
        if(logistics==null){
            return null;
        }
        return OrderLogisticsVO.builder()
                .trackNo(logistics.getTrackNo())
                .currentNode(logistics.getCurrentNode())
                .currentStatus(logistics.getCurrentStatus())
                .estimatedArrival(logistics.getEstimatedArrival())
                .build();
    }


    private List<OrderItemVO> toItemVOList(List<OrderItem> items){
        //判断商品信息是否为空,为空返回空列表
        if(items==null||items.isEmpty()){
            //为空返回空列表
            return new ArrayList<>();
        }
        LocalDateTime now=LocalDateTime.now();
        List<OrderItemVO> result=new ArrayList<>(items.size());
        for(OrderItem it: items){
            //获取退货截止时间
            LocalDateTime deadLine=it.getRefundDeadline();
            //未签收的订单没有退货截止时间为null
            Boolean eligible =deadLine==null?null:deadLine.isAfter(now);
            //计算距离截止时间剩余的时间
            Long hoursLeft =deadLine==null?null: Duration.between(now, deadLine).toHours();
            //组装结果
            result.add(OrderItemVO.builder()
                    .productId(it.getProductId())
                    .productName(it.getProductName())
                    .price(it.getProductPrice())
                    .quantity(it.getQuantity())
                    .refundDeadline(deadLine)
                    .refundEligible(eligible)
                    .hoursLeft(hoursLeft)
                    .build());
        }
        return result;
    }
}
