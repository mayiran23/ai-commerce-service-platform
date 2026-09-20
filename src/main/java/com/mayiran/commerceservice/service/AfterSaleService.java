package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.result.PageResult;

public interface AfterSaleService {
    PageResult pageAfterSales(AfterSalePageDTO afterSalePageDTO);
}
