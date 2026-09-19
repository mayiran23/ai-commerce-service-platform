package com.mayiran.commerceservice.exception;

/**
 * 业务异常
 */
public class BaseException extends RuntimeException{
    public BaseException(String message) {
        super(message);
    }
    public BaseException(){}
}
