package com.mayiran.commerceservice.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.AfterSaleCheckDTO;
import com.mayiran.commerceservice.dto.AfterSaleCreateDTO;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.dto.StatusFlowDTO;
import com.mayiran.commerceservice.entity.AfterSale;
import com.mayiran.commerceservice.entity.AfterSaleFlow;
import com.mayiran.commerceservice.entity.OrderItem;
import com.mayiran.commerceservice.enums.AfterSaleStatus;
import com.mayiran.commerceservice.enums.EligibilityCode;
import com.mayiran.commerceservice.enums.OrderStatus;
import com.mayiran.commerceservice.enums.RoleEnum;
import com.mayiran.commerceservice.exception.*;
import com.mayiran.commerceservice.mapper.AfterSaleMapper;
import com.mayiran.commerceservice.mapper.OrderMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.service.OrderService;
import com.mayiran.commerceservice.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {
    /** 允许的工单类型（契约 §2.2：REFUND / EXCHANGE / REPAIR） */
    private static final Set<String> VALID_TYPES = Set.of("REFUND", "EXCHANGE", "REPAIR");

    @Autowired
    private AfterSaleMapper afterSaleMapper;
    //复用订单详情拿订单快照
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 分页查询工单
     * @param afterSalePageDTO
     * @return
     */
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

    /**
     * 查询工单的详细信息
     * @param ticketNo
     * @return
     */
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

    /**
     * 创建工单
     * @param afterSaleCreateDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleCreateVO createAfterSale(AfterSaleCreateDTO afterSaleCreateDTO) {
        //1参数校验
        if(afterSaleCreateDTO==null
                ||afterSaleCreateDTO.getOrderNo()==null
                ||afterSaleCreateDTO.getOrderNo().isBlank()
                ||afterSaleCreateDTO.getProductId()==null
                ||afterSaleCreateDTO.getType()==null
                ||afterSaleCreateDTO.getType().isBlank()){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_PARAM_INVALID);
        }
        if(!VALID_TYPES.contains(afterSaleCreateDTO.getType())){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_TYPE_INVALID);
        }
        //2查订单:不存在会抛订单不存在
        OrderVO order = orderService.getOrdersByorderNo(afterSaleCreateDTO.getOrderNo());

        //3越权检验
        String role = UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);
        if (!canSeeAll && !order.getUserId().equals(UserContext.getUserId())) {
            log.warn("越权建单: orderNo={}, 订单归属={}, 当前用户={}",
                    afterSaleCreateDTO.getOrderNo(), order.getUserId(), UserContext.getUserId());
            throw new OrderNotFoundException(MessageConstant.ORDER_NOT_FOUND);
        }
        //4商品Id->订单明细主键(表里没有product_id列)
        Long orderItemId=afterSaleMapper.getOrderItemId(afterSaleCreateDTO.getProductId(),afterSaleCreateDTO.getOrderNo());
        if(orderItemId==null){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_ITEM_NOT_FOUND);
        }
        //5幂等:已有活跃工单就直接返回原单号,不新建
        //同样的操作做一遍和做十遍,结果都是一样的
        AfterSale existing=afterSaleMapper.getActiveTicket(afterSaleCreateDTO.getOrderNo(),afterSaleCreateDTO.getProductId(),afterSaleCreateDTO.getType());
        if(existing!=null){
            log.info("已存在活跃工单: {}", existing.getTicketNo());
            return AfterSaleCreateVO.builder()
                    .ticketNo(existing.getTicketNo())
                    .status(existing.getStatus())
                    .message("该商品已有进行中的售后申请，已为您返回原工单")
                    .build();
        }
        //6建单
        LocalDateTime now=LocalDateTime.now();
        AfterSale ticket=new AfterSale();
        ticket.setOrderNo(afterSaleCreateDTO.getOrderNo());
        ticket.setOrderItemId(orderItemId);
        ticket.setUserId(order.getUserId());
        ticket.setType(afterSaleCreateDTO.getType());
        ticket.setReason(afterSaleCreateDTO.getReason());
        ticket.setStatus(AfterSaleStatus.PENDING.name());
        ticket.setSource("WEB");
        ticket.setAiGenerated(0);
        ticket.setAiConfidence(null);
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);

        String ticketNo=null;
        for(int attempt=1; attempt<=3; attempt++){
            ticketNo=generateTicketNo();
            ticket.setTicketNo(ticketNo);
            try{
                afterSaleMapper.insertAfterSale(ticket);
                break;
            }catch (DuplicateKeyException e){
                log.warn("工单号冲突,第{}次尝试:{}",attempt,ticketNo);
                if(attempt==3){
                    throw new AfterSaleParamException("工单号生成失败,请稍后重试");
                }
            }
        }
        //7同事务写一条初始流转记录
        afterSaleMapper.insertFlow(AfterSaleFlow.builder()
                .ticketNo(ticketNo)
                .fromStatus(null)
                .toStatus(AfterSaleStatus.PENDING.name())
                .operatorId(UserContext.getUserId())
                .operatorType(role)
                .remark("用户在前端自助提交售后申请")
                .createTime(now)
                .build());

        //返回
        return AfterSaleCreateVO.builder()
                .ticketNo(ticketNo)
                .status(AfterSaleStatus.PENDING.name())
                .message("工单已创建，等待客服审核")
                .build();
    }

    /**
     * 工单状态流转
     * @param ticketNo
     * @param statusFlowDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StatusFlowVO transition(String ticketNo, StatusFlowDTO statusFlowDTO) {
        //1:参数校验
        if(ticketNo==null||ticketNo.isBlank()
                ||statusFlowDTO==null
                ||statusFlowDTO.getToStatus()==null
                ||statusFlowDTO.getToStatus().isBlank()){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_PARAM_INVALID);
        }
        if(statusFlowDTO.getRemark()==null||statusFlowDTO.getRemark().isBlank()){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_REMARK_REQUIRED);
        }

        //2:越权检验
        String role=UserContext.getRole();
        boolean canHandle=RoleEnum.AGENT.name().equals(role)||RoleEnum.ADMIN.name().equals(role);
        if(!canHandle){
            log.warn("越权流转工单:ticket={},当前角色={},当前用户={}",ticketNo,role,UserContext.getUserId());
            throw new ForbiddenException(MessageConstant.AFTER_SALE_FORBIDDEN);
        }

        //3:查工单(顺便拿到当前的状态)
        AfterSaleVO ticket = afterSaleMapper.getByTicketNo(ticketNo);
        if(ticket==null){
            throw new AfterSaleNotFoundException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }
        String fromStatus = ticket.getStatus();
        String toStatus = statusFlowDTO.getToStatus();

        //4:状态机校验
        if(!AfterSaleStatus.allowedTransitionsOf(fromStatus).contains(toStatus)){
            throw new AfterSaleStatusException("不允许从"+AfterSaleStatus.textOf(fromStatus)
            +"流转到"+AfterSaleStatus.textOf(toStatus));
        }

        //真正更新流转状态
        //先update再insert
        int rows=afterSaleMapper.updateStatus(ticketNo, fromStatus, toStatus, UserContext.getUserId(),statusFlowDTO.getRemark());
        if(rows==0){
            throw new AfterSaleStatusException(MessageConstant.AFTER_SALE_STATUS_CHANGED);
        }

        //同事务写一条流转记录
        afterSaleMapper.insertFlow(AfterSaleFlow.builder()
                .ticketNo(ticketNo)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .operatorId(UserContext.getUserId())
                .operatorType(role)
                .remark(statusFlowDTO.getRemark())
                .createTime(LocalDateTime.now())
                .build());

        log.info("工单流转成功:ticketNo={},{}->{},操作人={}",ticketNo,fromStatus,toStatus,UserContext.getUserId());

        //7:返回流转后的状态
        return StatusFlowVO.builder()
                .ticketNo(ticketNo)
                .status(toStatus)
                .statusText(AfterSaleStatus.textOf(toStatus))
                .build();

    }

    @Override
    public AfterSaleEligibilityVO checkEligibleForInternal(AfterSaleCheckDTO dto) {
        //1:进行参数校验
        if(dto==null||
        dto.getOrderNo()==null||dto.getOrderNo().isBlank()||
        dto.getProductId()==null){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_PARAM_INVALID);
        }
        String orderNo = dto.getOrderNo();
        Long productId = dto.getProductId();

        //2:订单必须存在
        OrderVO order = orderMapper.getByOrderNo(orderNo);
        if(order==null){
            throw new OrderNotFoundException(MessageConstant.ORDER_NOT_FOUND);
        }

        //3:订单中的商品必须存在
        List<OrderItem> items = orderMapper.getItems(orderNo);
        OrderItem target=null;
        if(items!=null){
            for(OrderItem item:items){
                if(item.getProductId().equals(productId)){
                    target=item;
                    break;
                }
            }
        }
        if(target==null){
            throw new AfterSaleParamException(MessageConstant.AFTER_SALE_ITEM_NOT_FOUND);
        }
        //截止时间是按"签收时间"+7天算好的
        LocalDateTime deadline = target.getRefundDeadline();

        //4:未签收,退货的前提是已签收
        if(!OrderStatus.RECEIVED.name().equals(order.getStatus())){
            //订单状态不是已签收,不允许退货
            boolean cancelled=OrderStatus.CANCELLED.name().equals(order.getStatus());
            return AfterSaleEligibilityVO.builder()
                    .eligible(false)
                    .code(EligibilityCode.NOT_RECEIVED.name())
                    .message(cancelled?
                            "该订单已取消,无需申请售后"
                            :"该订单还没有签收,签收之后才能申请退货退款")
                    .suggestManualReview(false)
                    .refundDeadline(deadline)
                    .build();
        }

        //5:已签收
        LocalDateTime now=LocalDateTime.now();
        if(deadline!=null && deadline.isAfter(now)){
            return AfterSaleEligibilityVO.builder()
                    .eligible(true)
                    .code(EligibilityCode.OK.name())
                    .message("该商品在7天无理由退货期内(截止"+deadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))+")")
                    .suggestManualReview(false)
                    .refundDeadline(deadline)
                    .build();
        }

        //6:超期,这是人机协同设计的关键
        //eligible=false不代表这事办不了,而是代表这事AI不能自己办
        //所以这里要转人工
        return AfterSaleEligibilityVO.builder()
                .eligible(false)
                .code(EligibilityCode.OVER_DEADLINE.name())
                .message("该商品已超过7天无理由退货期"
                +(deadline==null?"":"("+deadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))+")")
                +",建议转人工审核")
                .suggestManualReview(true)
                .refundDeadline(deadline)
                .build();
        
    }

    /**
     * 生成工单号:AS+yyyyMMdd+3位当日流水,例如:AS20260922001
     * 取当前已有的最大号+1;并发撞车由ticket_no唯一索引兜底
     * @return
     */

    private String generateTicketNo() {
        String dayPrefix="AS"+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String max=afterSaleMapper.getMaxTicketNoOfDay(dayPrefix);
        int seq=1;
        if(max!=null&&max.length()>dayPrefix.length()){
            try{
                seq=Integer.parseInt(max.substring(dayPrefix.length()))+1;
            }catch (NumberFormatException e){
                log.warn("工单号格式异常: {}", max);
            }
        }
        return dayPrefix+String.format("%03d", seq);
    }

}
