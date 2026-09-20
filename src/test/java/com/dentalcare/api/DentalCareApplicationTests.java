package com.dentalcare.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "dentalcare.cors.allowed-origin=http://localhost:3000"
})
class DentalCareApplicationTests {

    @Test
    void contextLoads() {
    }
}
