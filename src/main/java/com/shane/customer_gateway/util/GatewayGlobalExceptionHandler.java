package com.shane.customer_gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;


@Component
@Order(-1)
@RequiredArgsConstructor
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 获取确定的HTTP状态码
        HttpStatusCode statusCode = determineHttpStatusCode(ex);
        response.setStatusCode(statusCode);

        // 构建统一响应对象
        R<?> result = buildErrorResult(ex);

        // 设置响应头
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    private R<?> buildErrorResult(Throwable ex) {
        HttpStatusCode statusCode = determineHttpStatusCode(ex);
        // 新增业务异常处理分支
        if (ex instanceof BusinessException) {
            BusinessException be = (BusinessException) ex;
            return new R<>(
                be.getCode(),  // 使用业务异常代码
                be.getMessage(), // 直接使用异常消息
                null,
                System.currentTimeMillis()
            );
        }
        return new R<>(
            statusCode.value(),
            getErrorMessage(ex, statusCode),
            null,
            System.currentTimeMillis()
        );
    }

    private HttpStatusCode determineHttpStatusCode(Throwable ex) {
        if (ex instanceof BusinessException) {
            // 统一返回200状态码，业务状态通过R对象传递
            return HttpStatus.OK;
        }
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode();
        }
        // 新增对连接相关异常的识别
        else if (ex instanceof ConnectException || ex instanceof TimeoutException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String getErrorMessage(Throwable ex, HttpStatusCode statusCode) {
        // 新增对服务不可用状态的错误描述
        if (statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
            return "下游服务暂时不可用，请稍后重试";
        }
        if (ex instanceof BusinessException) {
            return ex.getMessage();
        } else if (ex instanceof ResponseStatusException rse) {
            return rse.getReason();
        }
        return statusCode.toString();
    }
}
