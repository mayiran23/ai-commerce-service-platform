package com.mayiran.commerceservice.dto;

import lombok.Data;

@Data
public class SearchDTO {
    private Long userId;

    private String status;

    private String keyword;

    private int limit=5;
}
