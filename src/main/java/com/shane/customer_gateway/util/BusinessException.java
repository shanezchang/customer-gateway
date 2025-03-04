package com.shane.customer_gateway.util;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    // 允许覆盖消息
    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(String msg) {
        super(msg);
        this.code = 1008;
    }

}