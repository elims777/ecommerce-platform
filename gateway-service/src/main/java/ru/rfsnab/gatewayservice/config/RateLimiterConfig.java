package ru.rfsnab.gatewayservice.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

/**
 * Конфигурация rate limiting.
 * Определяет по какому ключу считать запросы — по IP адресу клиента.
 */
@Configuration
public class RateLimiterConfig {

    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String UNKNOWN_KEY = "unknown";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    /**
     * X-Real-IP выставляет nginx (proxy_set_header X-Real-IP $remote_addr — перезаписывает клиентское значение).
     * X-Forwarded-For не читаем: nginx дописывает его к клиентскому заголовку, ключ подделывается.
     * Значение проверяем на формат IP, чтобы мусорный заголовок не становился ключом Redis.
     * Корректность ключа держится на сетевом инварианте: gateway слушает только 127.0.0.1, снаружи его нет.
     */
    @Bean
    public KeyResolver ipKeyResolver(){
        return exchange -> {
            String realIp = exchange.getRequest().getHeaders().getFirst(REAL_IP_HEADER);
            if (realIp != null && isIpLiteral(realIp.trim())) {
                return Mono.just(realIp.trim());
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }
            return Mono.just(UNKNOWN_KEY);
        };
    }

    private static boolean isIpLiteral(String value) {
        return IPV4.matcher(value).matches() || IPV6.matcher(value).matches();
    }
}
