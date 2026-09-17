package com.mayiran.commerceservice.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具:负责签发和解析token
 * 密钥来自application.properties
 */

@Slf4j
@Component
public class JwtUtil {
    //签名密钥,jjwt按它的字节长度自动选HS256,HS384,HS512
    private final SecretKey key;
    //token 有效期,单位:小时
    private final long expireHours;


    public JwtUtil(@Value("${jwt.secret}")String secret,
                   @Value("${jwt.expire-hours}")long expireHours){
        this.key= Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours=expireHours;
        log.info("JwtUtil 初始化完成:签名算法={},有效期={}小时",key.getAlgorithm(),expireHours);
    }
    /**
     * 签发 token。
     *
     * @param userId 用户 id —— 拦截器要用它覆盖前端传来的 userId
     * @param role   角色 code，如 "USER" / "AGENT"；不要传中文的 roleText
     */
    public String generate(Long userId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireHours * 3600_000L))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token。签名不对或已过期都会抛异常，调用方负责捕获。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 取 userId。用带类型参数的 get 重载，避免 Integer 强转 Long 的异常。 */
    public Long getUserId(String token) {
        return parse(token).get("userId", Long.class);
    }

    /** 取 role。 */
    public String getRole(String token) {
        return parse(token).get("role", String.class);
    }


}

