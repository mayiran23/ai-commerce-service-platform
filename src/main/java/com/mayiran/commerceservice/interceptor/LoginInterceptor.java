package com.mayiran.commerceservice.interceptor;

import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    private static final String HEADER="Authorization";
    private static final String PREFIX="Bearer ";
    @Autowired
    private JwtUtil jwtUtil;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if(!(handler instanceof HandlerMethod)){
            return true;
        }
        //从请求头中获取token
        String header = request.getHeader(HEADER);
        if(header==null||!header.startsWith(PREFIX)){
            log.warn("请求{}未携带合法的Authorization头", request.getRequestURI());
            return reject(response);
        }
        String token=header.substring(PREFIX.length());

        Claims claims;
        try{
            claims=jwtUtil.parse(token);
        }catch (JwtException e){
            log.warn("token无效,{}", e.getMessage());
            return reject(response);
        }

        UserContext.set(claims.get("userId", Long.class), claims.get("role", String.class));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.remove();
    }


    /**
     * 拒绝访问，返回401状态码
     * @param response
     * @return
     * @throws Exception
     */
    private boolean reject(HttpServletResponse response)throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        PrintWriter writer = response.getWriter();
        writer.write("{\"code\":401,\"msg\":\"" + MessageConstant.NOT_LOGIN + "\",\"data\":null}");
        writer.flush();
        return false;
    }

}
