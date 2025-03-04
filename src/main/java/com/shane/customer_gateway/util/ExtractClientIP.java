package com.shane.customer_gateway.util;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

public class ExtractClientIP {
    // 新增代理头常量定义
    private static final String[] PROXY_HEADERS = {
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "X-Real-IP"
    };

    public static String extractClientIp(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return "unknown"; // 空指针保护
        }

        HttpHeaders headers = exchange.getRequest().getHeaders();

        // 优化代理头检查顺序
        for (String header : PROXY_HEADERS) {
            String ipValue = headers.getFirst(header);
            if (isValidIp(ipValue)) {
                return cleanIp(ipValue.split(",")[0].trim());
            }
        }

        // 优化远程地址处理
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    // 新增IP有效性验证方法
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        // 简单验证IPv4/IPv6格式
        return ip.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") || 
               ip.matches("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    }

    // 新增IP清理方法（处理端口号）
    private static String cleanIp(String ip) {
        if (ip.contains(":")) {
            int colonIndex = ip.lastIndexOf(":");
            if (ip.substring(0, colonIndex).contains(".")) { // 带端口号的IPv4
                return ip.substring(0, colonIndex);
            }
        }
        return ip;
    }
}
