package com.mayiran.commerceservice.exception;

public class OrderNotFoundException extends BaseException{
    public OrderNotFoundException() {

    }
    public OrderNotFoundException(String message) {
        super(message);
    }
}
