package com.mayiran.commerceservice.handler;

import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.exception.BaseException;
import com.mayiran.commerceservice.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器,处理项目中抛出的业务异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 捕获业务异常
     * @param e 异常对象
     * @return 统一的异常响应结果
     */
    @ExceptionHandler(BaseException.class)
    public Result exceptionHandler(BaseException e){
        log.warn("异常信息:{}",e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     *兜底:捕获所有没被方法一处理的其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result exceptionHandlerAll(Exception e){
        log.error("异常信息:{}",e);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }


}
