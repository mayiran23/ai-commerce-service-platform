package com.mayiran.commerceservice.handler;

import com.mayiran.commerceservice.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部接口专用异常处理器
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.mayiran.commerceservice.internal")
public class InternalExceptionHandler {
    /**
     * 资源不存在：订单 / 工单 / 会话查不到 → 404
     * 注意：这里沿用业务层"查不到"和"不是本人"抛同一个异常的设计，不要把两者拆开
     */
    @ExceptionHandler({OrderNotFoundException.class,
            AfterSaleNotFoundException.class,
            ChatSessionNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(BaseException e) {
        log.warn("内部接口-资源不存在:{}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 无权限：userId 与订单归属不一致 → 403 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(BaseException e) {
        log.warn("内部接口-无权访问:{}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** 其余业务异常：参数不完整、状态不合法…… → 400 */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BaseException e) {
        log.warn("内部接口-业务异常:{}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 兜底：任何没预料到的异常 → 500（堆栈只进日志，不外泄） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("内部接口-未知异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "内部接口处理失败");
    }

    /** 统一错误体：code 直接用 HTTP 状态码，调用方读 msg 就知道原因 */
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", status.value());
        body.put("msg", msg);
        return ResponseEntity.status(status).body(body);
    }
}
