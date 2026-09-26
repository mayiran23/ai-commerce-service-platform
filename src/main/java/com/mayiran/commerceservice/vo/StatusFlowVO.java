package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatusFlowVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String ticketNo;

    private String status;

    private String statusText;
}
