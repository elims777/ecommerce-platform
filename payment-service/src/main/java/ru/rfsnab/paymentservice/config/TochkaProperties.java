package ru.rfsnab.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tochka")
@Getter
@Setter
public class TochkaProperties {
    private String baseUrl;
    private String token;
    private String customerCode;
    private String redirectUrl;
    private String failRedirectUrl;
}
