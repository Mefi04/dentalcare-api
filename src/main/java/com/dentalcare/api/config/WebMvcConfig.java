package com.dentalcare.api.config;

import com.dentalcare.api.security.ratelimit.ApiRateLimitInterceptor;
import com.dentalcare.api.security.ratelimit.RateLimitService;
import com.dentalcare.api.security.ratelimit.ClinicalDocumentConcurrencyInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimitService> rateLimitServiceProvider;
    private final ClinicalDocumentConcurrencyInterceptor clinicalDocumentConcurrencyInterceptor;

    public WebMvcConfig(ObjectProvider<RateLimitService> rateLimitServiceProvider,
                        ClinicalDocumentConcurrencyInterceptor clinicalDocumentConcurrencyInterceptor) {
        this.rateLimitServiceProvider = rateLimitServiceProvider;
        this.clinicalDocumentConcurrencyInterceptor = clinicalDocumentConcurrencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        rateLimitServiceProvider.ifAvailable(rateLimitService ->
            registry.addInterceptor(new ApiRateLimitInterceptor(rateLimitService)).addPathPatterns("/api/**"));
        registry.addInterceptor(clinicalDocumentConcurrencyInterceptor).addPathPatterns("/api/v1/patients/**/documents/**");
    }
}
