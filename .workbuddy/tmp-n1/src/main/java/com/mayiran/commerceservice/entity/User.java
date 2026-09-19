package com.mayiran.commerceservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {
    private Long id;
    //登录名
    private String username;
    //密码.默认123456
    private String password;
    //姓名
    private String nickname;
    //电话号码
    private String phone;
    //角色
    private String role;
    //状态
    private Integer status;
    //创建时间
    private LocalDateTime createTime;
    //更新时间
    private LocalDateTime updateTime;
}
