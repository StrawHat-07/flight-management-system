package com.flightmanagement.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfiguration {

    @Value("${http.client.connect-timeout-ms:5000}")
    private int connectTimeout;

    @Value("${http.client.read-timeout-ms:10000}")
    private int readTimeout;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .additionalMessageConverters(converter)
                .build();
    }
}
