package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.AfterSaleCheckDTO;
import com.mayiran.commerceservice.dto.AfterSaleCreateDTO;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.dto.StatusFlowDTO;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.vo.*;

public interface AfterSaleService {
    AfterSalePageVO pageAfterSales(AfterSalePageDTO afterSalePageDTO);

    AfterSaleDetailVO getDetail(String ticketNo);

    AfterSaleCreateVO createAfterSale(AfterSaleCreateDTO afterSaleCreateDTO);

    StatusFlowVO transition(String ticketNo, StatusFlowDTO statusFlowDTO);

    AfterSaleEligibilityVO checkEligibleForInternal(AfterSaleCheckDTO dto);
}
