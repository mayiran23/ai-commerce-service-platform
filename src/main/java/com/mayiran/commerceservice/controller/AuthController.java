package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.LoginDTO;
import com.mayiran.commerceservice.entity.User;
import com.mayiran.commerceservice.enums.RoleEnum;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.AuthService;
import com.mayiran.commerceservice.utils.JwtUtil;
import com.mayiran.commerceservice.vo.LoginVO;
import com.mayiran.commerceservice.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO){
        log.info("用户登录:{}", loginDTO.getUsername());
        User user = authService.login(loginDTO);

        //登录成功后,生成jwt令牌
        String token=jwtUtil.generate(user.getId(), user.getRole());

        //组装响应给前端的用户信息
        UserVO userVO=UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .role(user.getRole())
                .roleText(RoleEnum.of(user.getRole()).getText())
                .build();

        return Result.success(new LoginVO(token, userVO));

    }

    @GetMapping("/me")
    public Result<UserVO> me(){
        Long userId = UserContext.getUserId();
        log.info("查询当前登录用户:{}",userId);

        return Result.success(authService.me(userId));
    }
}
