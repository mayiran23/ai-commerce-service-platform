package com.mayiran.commerceservice.internal;

import com.mayiran.commerceservice.dto.AfterSaleAICreateDTO;
import com.mayiran.commerceservice.dto.AfterSaleCheckDTO;
import com.mayiran.commerceservice.dto.AfterSaleSearchDTO;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.vo.AfterSaleCreateVO;
import com.mayiran.commerceservice.vo.AfterSaleEligibilityVO;
import com.mayiran.commerceservice.vo.AfterSaleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/after-sale")
@Slf4j
public class InternalAfterSaleController {
    @Autowired
    private AfterSaleService afterSaleService;

    /**
     * 内部接口-退货资格校验
     * @param dto
     * @return
     */
    @PostMapping("/check-eligible")
    public AfterSaleEligibilityVO checkEligibility(@RequestBody AfterSaleCheckDTO dto){
        log.info("内部接口-退货资格校验:orderNo:{},productId:{}", dto.getOrderNo(), dto.getProductId());

        return afterSaleService.checkEligibleForInternal(dto);

    }

    /**
     * 内部接口-AI建工单
     * @param dto
     * @return
     */
    @PostMapping("/create")
    public AfterSaleCreateVO AiCreate(@RequestBody AfterSaleAICreateDTO dto){
        log.info("内部接口-AI建工单:orderNo:{},productId:{}", dto.getOrderNo(), dto.getProductId());
        return afterSaleService.createByAiForInternal(dto);
    }

    @PostMapping("/search")
    public List<AfterSaleVO> search(@RequestBody AfterSaleSearchDTO dto){
        log.info("内部接口-工单搜索:userId:{},ticketNo:{}",dto.getUserId(), dto.getTicketNo());
        return afterSaleService.searchForInternal(dto);
    }
}
