package ru.rfsnab.userservice.services.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.userservice.exceptions.OrderServiceUnavailableException;

import java.util.Map;

@Slf4j
@Component
public class OrderServiceClient {

    private final RestClient restClient;
    private final String orderServiceUrl;

    public OrderServiceClient(RestClient restClient,
                              @Value("${services.order.url}") String orderServiceUrl) {
        this.restClient = restClient;
        this.orderServiceUrl = orderServiceUrl;
    }

    public long countActiveOrdersByUserId(Long userId, String callerUserId, String callerRole) {
        return fetchActiveCount("userId=" + userId, callerUserId, callerRole);
    }

    public long countActiveOrdersByInn(String inn, String callerUserId, String callerRole) {
        return fetchActiveCount("inn=" + inn, callerUserId, callerRole);
    }

    @SuppressWarnings("unchecked")
    private long fetchActiveCount(String query, String callerUserId, String callerRole) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(orderServiceUrl + "/api/v1/admin/orders/active-count?" + query)
                    .header("X-User-Id", callerUserId)
                    .header("X-User-Role", callerRole)
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("count") == null) {
                return 0L;
            }
            Object countValue = body.get("count");
            return countValue instanceof Number n ? n.longValue() : Long.parseLong(countValue.toString());
        } catch (RestClientException e) {
            log.error("Order service unavailable: {}", e.getMessage());
            throw new OrderServiceUnavailableException("Order service unavailable");
        }
    }
}
