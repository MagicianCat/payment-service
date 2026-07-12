package com.ecommerce.payment_service.config;

import com.ecommerce.payment_service.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    private final ServiceTokenProvider serviceTokenProvider;

    public FeignConfig(ServiceTokenProvider serviceTokenProvider) {
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            String token = serviceTokenProvider.generateServiceToken();
            requestTemplate.header("Authorization", "Bearer " + token);
        };
    }
}