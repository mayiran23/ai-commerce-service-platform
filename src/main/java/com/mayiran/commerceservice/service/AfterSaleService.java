package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.*;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.vo.*;

import java.util.List;

public interface AfterSaleService {
    AfterSalePageVO pageAfterSales(AfterSalePageDTO afterSalePageDTO);

    AfterSaleDetailVO getDetail(String ticketNo);

    AfterSaleCreateVO createAfterSale(AfterSaleCreateDTO afterSaleCreateDTO);

    StatusFlowVO transition(String ticketNo, StatusFlowDTO statusFlowDTO);

    AfterSaleEligibilityVO checkEligibleForInternal(AfterSaleCheckDTO dto);

    AfterSaleCreateVO createByAiForInternal(AfterSaleAICreateDTO dto);

    List<AfterSaleVO> searchForInternal(AfterSaleSearchDTO dto);
}
