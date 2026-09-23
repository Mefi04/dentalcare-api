package com.dentalcare.api.config;

import com.dentalcare.api.modules.users.service.InitialAdminBootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InitialAdminBootstrapRunner implements ApplicationRunner {

    private final InitialAdminBootstrapService initialAdminBootstrapService;

    public InitialAdminBootstrapRunner(InitialAdminBootstrapService initialAdminBootstrapService) {
        this.initialAdminBootstrapService = initialAdminBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialAdminBootstrapService.bootstrap();
    }
}
