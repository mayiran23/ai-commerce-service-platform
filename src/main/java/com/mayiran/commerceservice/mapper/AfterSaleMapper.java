package com.mayiran.commerceservice.mapper;

import com.github.pagehelper.Page;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.dto.AfterSaleSearchDTO;
import com.mayiran.commerceservice.entity.AfterSale;
import com.mayiran.commerceservice.entity.AfterSaleFlow;
import com.mayiran.commerceservice.vo.AfterSaleFlowVO;
import com.mayiran.commerceservice.vo.AfterSaleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AfterSaleMapper {
    Page<AfterSaleVO> pageAfterSales(AfterSalePageDTO afterSalePageDTO);

    /**
     * 按工单状态分组统计数量。
     * ⚠️ 泛型值是 Object：Map 里 status 是 String、n 是 Long，别写成 Map<String,Long>（假的类型安全）。
     */
    List<Map<String, Object>> countByStatus(@Param("userId") Long userId);

    AfterSaleVO getByTicketNo(String ticketNo);

    List<AfterSaleFlowVO> getFlowsByTicketNo(String ticketNo);

    Long getOrderItemId(@Param("productId") Long productId,
                        @Param("orderNo") String orderNo);

    AfterSale getActiveTicket(@Param("orderNo") String orderNo,
                              @Param("productId") Long productId,
                              @Param("type") String type);

    String getMaxTicketNoOfDay(@Param("dayPrefix") String dayPrefix);

    void insertAfterSale(AfterSale ticket);

    void insertFlow(AfterSaleFlow flow);

    /**
     * 更新工单状态
     * @param ticketNo
     * @param fromStatus
     * @param toStatus
     * @param userId
     * @param remark
     * @return
     */
    int updateStatus(@Param("ticketNo") String ticketNo,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("userId") Long userId,
                     @Param("remark") String remark);

    List<AfterSaleVO> searchForInternal(AfterSaleSearchDTO dto);
}
