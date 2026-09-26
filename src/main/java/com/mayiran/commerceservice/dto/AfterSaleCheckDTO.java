package com.mayiran.commerceservice.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AfterSaleCheckDTO implements Serializable {

    private String orderNo;

    private Long productId;
}
