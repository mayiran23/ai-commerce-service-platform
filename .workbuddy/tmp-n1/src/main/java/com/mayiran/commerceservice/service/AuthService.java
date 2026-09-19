package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.LoginDTO;
import com.mayiran.commerceservice.entity.User;

public interface AuthService {
    User login(LoginDTO loginDTO);
}
