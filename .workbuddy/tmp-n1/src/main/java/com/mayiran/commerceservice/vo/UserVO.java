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
public class UserVO implements Serializable {
    private Long id;
    //账号名
    private String username;
    //昵称
    private String nickname;
    //电话号码
    private String phone;
    //角色
    private String role;
    private String roleText;
}
