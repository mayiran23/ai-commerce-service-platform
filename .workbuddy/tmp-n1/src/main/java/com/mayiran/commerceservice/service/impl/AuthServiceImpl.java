package com.mayiran.commerceservice.service.impl;

import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.constant.StatusConstant;
import com.mayiran.commerceservice.dto.LoginDTO;
import com.mayiran.commerceservice.entity.User;
import com.mayiran.commerceservice.exception.AccountLockedException;
import com.mayiran.commerceservice.exception.AccountNotFoundException;
import com.mayiran.commerceservice.exception.PasswordErrorException;
import com.mayiran.commerceservice.mapper.UserMapper;
import com.mayiran.commerceservice.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Autowired
    private UserMapper userMapper;
    @Override
    public User login(LoginDTO loginDTO){
        String username=loginDTO.getUsername();
        String password=loginDTO.getPassword();

        //1:根据用户名查询数据库中的数据
        User user = userMapper.getByUsername(username);
        //2:处理各种异常情况(用户不存在,密码不对,账号被锁定)
        if(user==null){
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.LOGIN_FAILURE);
        }
        //密码比对
        if(!passwordEncoder.matches(password, user.getPassword())){
            //密码不匹配
            throw new PasswordErrorException(MessageConstant.LOGIN_FAILURE);
        }
        if(StatusConstant.DISABLE.equals(user.getStatus())){
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }
        //3:返回实体对象
        return user;

    }
}
