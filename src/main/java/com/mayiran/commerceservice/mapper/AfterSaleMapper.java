package com.mayiran.commerceservice.mapper;

import com.github.pagehelper.Page;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.vo.AfterSaleFlowVO;
import com.mayiran.commerceservice.vo.AfterSaleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AfterSaleMapper {
    Page<AfterSaleVO> pageAfterSales(AfterSalePageDTO afterSalePageDTO);

    List<Map<String, Long>> countByStatus(@Param("userId") Long userId);

    AfterSaleVO getByTicketNo(String ticketNo);

    List<AfterSaleFlowVO> getFlowsByTicketNo(String ticketNo);
}
