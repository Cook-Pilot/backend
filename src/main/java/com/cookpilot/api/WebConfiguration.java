package com.cookpilot.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfiguration implements WebMvcConfigurer {
  private final String[] allowedOrigins;

  WebConfiguration(@Value("${cookpilot.cors.allowed-origin-patterns:*}") String allowedOrigins) {
    this.allowedOrigins = allowedOrigins.split(",");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOriginPatterns(allowedOrigins)
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600);
  }
}

