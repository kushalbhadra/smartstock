package com.smartstock.smartstock.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForecastingJsonConfig {
    @Bean
    public ObjectMapper forecastObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
