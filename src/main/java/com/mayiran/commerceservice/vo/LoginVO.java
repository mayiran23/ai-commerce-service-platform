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
public class LoginVO implements Serializable {
    private String token;
    private UserVO user;

}
