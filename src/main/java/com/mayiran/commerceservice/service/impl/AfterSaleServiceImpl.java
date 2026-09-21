package com.mayiran.commerceservice.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.enums.AfterSaleStatus;
import com.mayiran.commerceservice.enums.RoleEnum;
import com.mayiran.commerceservice.exception.AfterSaleNotFoundException;
import com.mayiran.commerceservice.mapper.AfterSaleMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {
    @Autowired
    private AfterSaleMapper afterSaleMapper;
    //复用订单详情拿订单快照
    @Autowired
    private OrderService orderService;

    @Override
    public AfterSalePageVO pageAfterSales(AfterSalePageDTO afterSalePageDTO) {
        //越权控制
        String role= UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);

        if (!canSeeAll) {
            afterSalePageDTO.setUserId(UserContext.getUserId());
        } else {
            // 工单接口的参数表里没有 userId，客服也不该按用户筛 → 清掉前端可能塞进来的脏值
            afterSalePageDTO.setUserId(null);
        }
        //设置分页码和每页的记录数
        PageHelper.startPage(afterSalePageDTO.getPage(),afterSalePageDTO.getLimit());
        Page<AfterSaleVO> page=afterSaleMapper.pageAfterSales(afterSalePageDTO);
        PageInfo<AfterSaleVO> pageInfo=new PageInfo<>(page);
        List<AfterSaleVO> records = pageInfo.getList();

        //补statusText
        for (AfterSaleVO vo : records){
            vo.setStatusText(AfterSaleStatus.textOf(vo.getStatus()));
        }

        //查状态统计
        List<Map<String,Long>> rows = afterSaleMapper.countByStatus(canSeeAll?null:UserContext.getUserId());
        //先将7个状态全摆成0,再用SQL结果覆盖
        Map<String,Long> stats =new LinkedHashMap<>();
        for (AfterSaleStatus s : AfterSaleStatus.values()){
            stats.put(s.name(),0L);
        }

        for(Map<String,Long> row : rows){
            //设置状态
            String status= String.valueOf(row.get("status"));
            //设置状态对应的工单数
            Long n=row.get("n");
            stats.put(status,n);
        }

        //组装返回
        return AfterSalePageVO.builder()
                .total(pageInfo.getTotal())
                .records(records)
                .stats(stats)
                .build();
    }

    @Override
    public AfterSaleDetailVO getDetail(String ticketNo) {
        //1:查工单主体
        AfterSaleVO ticket=afterSaleMapper.getByTicketNo(ticketNo);
        //查不到抛异常
        if(ticket==null){
            log.warn("工单不存在:{}",ticketNo);
            throw new AfterSaleNotFoundException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }

        //2:越权检查
        String role = UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);
        if (!canSeeAll && !ticket.getUserId().equals(UserContext.getUserId())) {
            log.warn("越权访问工单: ticketNo={}, 工单归属={}, 当前用户={}",
                    ticketNo, ticket.getUserId(), UserContext.getUserId());
            throw new AfterSaleNotFoundException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }
        //3:流转记录
        List<AfterSaleFlowVO> flows =afterSaleMapper.getFlowsByTicketNo(ticketNo);

        //4:订单快照
        OrderVO order=orderService.getOrdersByorderNo(ticket.getOrderNo());

        //5:组装
        return AfterSaleDetailVO.builder()
                .ticketNo(ticket.getTicketNo())
                .orderNo(ticket.getOrderNo())
                .userId(ticket.getUserId())
                .type(ticket.getType())
                .reason(ticket.getReason())
                .status(ticket.getStatus())
                .statusText(AfterSaleStatus.textOf(ticket.getStatus()))
                .source(ticket.getSource())
                .handlerId(ticket.getHandlerId())
                .handleRemark(ticket.getHandleRemark())
                .aiGenerated(ticket.getAiGenerated())
                .aiConfidence(ticket.getAiConfidence())
                .createTime(ticket.getCreateTime())
                .updateTime(ticket.getUpdateTime())
                .productId(ticket.getProductId())
                .productName(ticket.getProductName())
                .userName(ticket.getUserName())
                .handlerName(ticket.getHandlerName())
                .order(order)
                .flows(flows)
                .allowedTransitions(AfterSaleStatus.allowedTransitionsOf(ticket.getStatus()))
                .build();
    }
}
