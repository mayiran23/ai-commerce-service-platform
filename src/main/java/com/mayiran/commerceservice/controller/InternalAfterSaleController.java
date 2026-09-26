package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.dto.AfterSaleCheckDTO;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.vo.AfterSaleEligibilityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/after-sale")
@Slf4j
public class InternalAfterSaleController {
    @Autowired
    private AfterSaleService afterSaleService;

    @PostMapping("/check-eligible")
    public AfterSaleEligibilityVO checkEligibility(@RequestBody AfterSaleCheckDTO dto){
        log.info("内部接口-退货资格校验:orderNo:{},productId:{}", dto.getOrderNo(), dto.getProductId());

        return afterSaleService.checkEligibleForInternal(dto);

    }
}
