package com.mayiran.commerceservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSaleAICreateDTO implements Serializable {

    private String orderNo;

    private Long productId;

    private String type;

    private String reason;

    private Boolean aiGenerated;

    private BigDecimal aiConfidence;
}
