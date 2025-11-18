package com.homesweet.homesweetback.common.config;

import com.homesweet.homesweetback.common.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMVC 설정
 *
 * - Rate Limiting 인터셉터 등록
 * - CORS 설정
 *
 * @author ohhalim777@gmail.com
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/health",
                        "/api/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
