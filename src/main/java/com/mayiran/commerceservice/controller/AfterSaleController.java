package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.dto.AfterSaleCreateDTO;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.vo.AfterSaleCreateVO;
import com.mayiran.commerceservice.vo.AfterSaleDetailVO;
import com.mayiran.commerceservice.vo.AfterSalePageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/after-sales")
@Slf4j
public class AfterSaleController {
    @Autowired
    private AfterSaleService afterSaleService;

    /**
     * 分页查询售后订单
     * @param afterSalePageDTO
     * @return
     */
    @GetMapping
    public Result<AfterSalePageVO> pageAfterSales(AfterSalePageDTO afterSalePageDTO){
        log.info("分页查询售后订单:{}", afterSalePageDTO);
        AfterSalePageVO page=afterSaleService.pageAfterSales(afterSalePageDTO);
        return Result.success(page);
    }

    /**
     * 查询售后订单详情
     * @param ticketNo
     * @return
     */
    @GetMapping("/{ticketNo}")
    public Result<AfterSaleDetailVO> getDetail(@PathVariable String ticketNo){
        log.info("查询售后订单详情:{}", ticketNo);
        AfterSaleDetailVO detail=afterSaleService.getDetail(ticketNo);
        return Result.success(detail);
    }

    @PostMapping
    public Result<AfterSaleCreateVO> createAfterSale(@RequestBody AfterSaleCreateDTO afterSaleCreateDTO){
        log.info("创建售后订单:{}", afterSaleCreateDTO);
        AfterSaleCreateVO vo=afterSaleService.createAfterSale(afterSaleCreateDTO);
        return Result.success(vo);
    }
}
