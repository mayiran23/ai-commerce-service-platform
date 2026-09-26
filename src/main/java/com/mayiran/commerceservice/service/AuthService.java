package com.mayiran.commerceservice.service;


import com.mayiran.commerceservice.dto.LoginDTO;
import com.mayiran.commerceservice.entity.User;
import com.mayiran.commerceservice.vo.UserVO;

public interface AuthService {
    User login(LoginDTO loginDTO);

    UserVO me(Long userId);
}
