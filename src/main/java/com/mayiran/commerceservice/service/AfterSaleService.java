package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.vo.AfterSalePageVO;

public interface AfterSaleService {
    AfterSalePageVO pageAfterSales(AfterSalePageDTO afterSalePageDTO);
}
