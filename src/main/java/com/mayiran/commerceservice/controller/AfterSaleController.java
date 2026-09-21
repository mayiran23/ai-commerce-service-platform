package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.vo.AfterSalePageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/after-sales")
@Slf4j
public class AfterSaleController {
    @Autowired
    private AfterSaleService afterSaleService;

    @GetMapping
    public Result<AfterSalePageVO> pageAfterSales(AfterSalePageDTO afterSalePageDTO){
        log.info("分页查询售后订单:{}", afterSalePageDTO);
        AfterSalePageVO page=afterSaleService.pageAfterSales(afterSalePageDTO);

        return Result.success(page);
    }
}
