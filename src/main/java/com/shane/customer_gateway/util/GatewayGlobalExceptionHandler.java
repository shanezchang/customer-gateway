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

        // 构建统一响应对象
        R<?> result = this.buildErrorResult(ex);

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
        HttpStatusCode statusCode = this.determineHttpStatusCode(ex);
        return new R<>(
                statusCode.value(),
                this.getErrorMessage(ex, statusCode),
                null,
                System.currentTimeMillis()
        );
    }

    private HttpStatusCode determineHttpStatusCode(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String getErrorMessage(Throwable ex, HttpStatusCode statusCode) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getReason();
        }
        return statusCode.toString();
    }
}
