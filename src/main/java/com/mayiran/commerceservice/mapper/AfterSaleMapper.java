package com.mayiran.commerceservice.mapper;

import com.github.pagehelper.Page;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.vo.AfterSaleVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AfterSaleMapper {
    Page<AfterSaleVO> pageAfterSales(AfterSalePageDTO afterSalePageDTO);
}
