package com.mayiran.commerceservice.dto;

import lombok.Data;

import java.io.Serializable;
@Data
public class LoginDTO implements Serializable {
    //账户名
    private String username;
    //密码
    private String password;
}
