package com.one23.one23.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Allows the React frontend to call the backend REST APIs.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // From app.cors.allowed-origins (comma-separated). Spring splits it into an array.
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    // allowedOriginPatterns (not allowedOrigins) so patterns like
    // "http://localhost:*" work together with allowCredentials(true).
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
