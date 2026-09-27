package com.mayiran.commerceservice.dto;

import lombok.Data;

import java.io.Serializable;
@Data
public class AfterSaleSearchDTO implements Serializable {
    private Long userId;

    private String ticketNo;

    private int limit=5;
}
