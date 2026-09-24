package com.mayiran.commerceservice.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

/**
 * 内部接口拦截器,校验X-Internal-Token
 *
 * 为什么不用JWT,调用方是Python AI服务,它没有用户身份
 */
@Component
@Slf4j
public class InternalInterceptor implements HandlerInterceptor {
    private static final String HEADER="X-Internal-Token";

    private final String internalToken;

    public InternalInterceptor(@Value("${internal.token}") String internalToken) {
        this.internalToken = internalToken;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(!(handler instanceof HandlerMethod)){
            return true;
        }
        String token = request.getHeader(HEADER);
        if (token == null || !token.equals(internalToken)) {
            log.warn("内部接口非法调用:{}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            PrintWriter writer =response.getWriter();
            writer.write("{\"code\":401,\"msg\":\"内部接口未授权\",\"data\":null}");
            writer.flush();
            return false;
        }
        return true;
    }
}
