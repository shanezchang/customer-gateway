package com.shane.customer_gateway.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R<T> {

    private final int code;

    private final String msg;

    private final T data;

    private final long timestamp;

}
