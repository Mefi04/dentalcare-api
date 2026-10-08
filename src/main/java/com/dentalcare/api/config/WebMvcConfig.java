package com.dentalcare.api.config;

import com.dentalcare.api.security.ratelimit.ApiRateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<ApiRateLimitInterceptor> apiRateLimitInterceptor;

    public WebMvcConfig(ObjectProvider<ApiRateLimitInterceptor> apiRateLimitInterceptor) {
        this.apiRateLimitInterceptor = apiRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        apiRateLimitInterceptor.ifAvailable(interceptor ->
            registry.addInterceptor(interceptor).addPathPatterns("/api/**"));
    }
}
