package com.shane.customer_gateway.util;

import com.shane.customer_gateway.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;


@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationValidationFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final WebClient.Builder webClientBuilder;

    private final static Integer AUTH_TOKEN_TIMEOUT = 3;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 优化路径匹配逻辑，使用常量中的路径列表
        boolean isNeedToken = Constants.NEED_AUTH_PATH_LIST.stream()
                .anyMatch(url -> pathMatcher.match(url, request.getPath().toString()));

        // 提前初始化token变量，优化日志输出
        String token = isNeedToken ? request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) : "";
        String clientIp = ExtractClientIP.extractClientIp(exchange);
        log.info("Authorization check - IP:{}, Path: {}, NeedsAuth: {}", clientIp, request.getPath(), isNeedToken);

        if (isNeedToken) {
            return this.buildAuthRequest(token)
                    .retrieve()
                    // 增强HTTP状态码处理
                    .onStatus(status -> !status.is2xxSuccessful(), this::handleHttpError)
                    .bodyToMono(R.class)
                    .timeout(Duration.ofSeconds(AUTH_TOKEN_TIMEOUT),
                            Mono.error(new BusinessException(HttpStatus.GATEWAY_TIMEOUT.value(), "认证服务响应超时")))
                    // 添加网络异常处理
                    .onErrorResume(this::handleNetworkException)
                    .flatMap(response -> this.processAuthResponse(exchange, chain, response, clientIp));
        }

        // 实现 非认证路径添加用户IP头
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .header(Constants.USER_IP, clientIp)
                .build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    // 新增网络异常处理方法
    private Mono<R<?>> handleNetworkException(Throwable ex) {
        log.error("认证服务通信异常: {}", ex.getMessage());
        if (ex instanceof ConnectException) {
            return Mono.error(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE.value(), "认证服务不可用"));
        }
        if (ex instanceof TimeoutException) {
            return Mono.error(new BusinessException(HttpStatus.GATEWAY_TIMEOUT.value(), "认证服务响应超时"));
        }
        return Mono.error(new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "认证服务异常"));
    }

    private WebClient.RequestHeadersSpec<?> buildAuthRequest(String token) {
        // 修改为直接使用服务名称（去掉lb://前缀）
        String serviceUrl = "http://customer-web/user/auth_token?token={token}";
        log.debug("Building auth request to: {}", serviceUrl);
        return webClientBuilder.build()
                .get()
                .uri(serviceUrl, token);
    }

    private Mono<? extends Throwable> handleHttpError(ClientResponse response) {
        return response.bodyToMono(String.class) // 改为通用类型处理
                .defaultIfEmpty("")
                .flatMap(body -> {
                    String errorMsg = String.format("认证服务错误[%s] %s", response.statusCode(), body);
                    log.error("Auth service error: {}", errorMsg);
                    int code = response.statusCode().value();
                    String message = resolveErrorMessage(response.statusCode(), body);
                    return Mono.error(new BusinessException(code, message));
                });
    }

    // 新增错误信息解析方法
    private String resolveErrorMessage(HttpStatusCode status, String body) {
        if (status.is5xxServerError()) {
            return "认证服务内部错误";
        } else if (status == HttpStatus.UNAUTHORIZED) {
            return "令牌已失效";
        } else if (status == HttpStatus.BAD_REQUEST) {
            return "无效的令牌格式";
        }
        return "认证服务错误";
    }

    private Mono<Void> processAuthResponse(ServerWebExchange exchange,
                                           GatewayFilterChain chain,
                                           R<?> response, String clientIp) {
        // 新增空响应体检查
        if (response == null) {
            return Mono.error(new BusinessException(500, "认证服务响应格式异常"));
        }

        if (response.getCode() != 200) {
            // 精确传递后端服务返回的错误信息
            return Mono.error(new BusinessException(
                    response.getCode(),
                    StringUtils.hasText(response.getMsg()) ? response.getMsg() : "认证服务异常"
            ));
        }

        // 添加请求头并继续执行
        String userId = String.valueOf(response.getData());
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .header(Constants.USER_ID, userId)
                .header(Constants.USER_IP, clientIp)
                .build();

        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public int getOrder() {
        // 设置过滤器优先级（确保在路由转发前执行）
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
