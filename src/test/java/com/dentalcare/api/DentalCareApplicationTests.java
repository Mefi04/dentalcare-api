package com.dentalcare.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.dentalcare.api.security.jwt.JwtService;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "dentalcare.cors.allowed-origin=http://localhost:3000"
})
class DentalCareApplicationTests {

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @Test
    void contextLoads() {
    }
}
