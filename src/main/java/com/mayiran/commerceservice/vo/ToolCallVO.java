package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallVO implements Serializable {
    private String name;

    private Map<String,Object> args;

    private String status;

    private Integer ms;

    private String result;
}
