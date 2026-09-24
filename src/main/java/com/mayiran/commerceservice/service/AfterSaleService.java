package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.AfterSaleCreateDTO;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.vo.AfterSaleCreateVO;
import com.mayiran.commerceservice.vo.AfterSaleDetailVO;
import com.mayiran.commerceservice.vo.AfterSalePageVO;

public interface AfterSaleService {
    AfterSalePageVO pageAfterSales(AfterSalePageDTO afterSalePageDTO);

    AfterSaleDetailVO getDetail(String ticketNo);

    AfterSaleCreateVO createAfterSale(AfterSaleCreateDTO afterSaleCreateDTO);
}
