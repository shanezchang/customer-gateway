package com.shane.customer_gateway.util;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthorizationValidationFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        List<String> noAuthPathList = new ArrayList<>();
        noAuthPathList.add("/customer/login");
        boolean isPass = noAuthPathList.stream().anyMatch(url -> pathMatcher.match(url, request.getPath().toString()));
        // 校验通过，继续执行后续过滤器链
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .header("auth_user_id", "")
                .header("auth_user_ip", "")
                .build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public int getOrder() {
        // 设置过滤器优先级（确保在路由转发前执行）
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
