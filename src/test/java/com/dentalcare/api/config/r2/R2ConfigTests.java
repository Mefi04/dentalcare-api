package com.dentalcare.api.config.r2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class R2ConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void doesNotCreateS3ClientWhenR2IsDisabled() {
        contextRunner
                .withPropertyValues("dentalcare.r2.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(R2Properties.class);
                    assertThat(context).doesNotHaveBean(S3Client.class);

                    R2Properties properties = context.getBean(R2Properties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getBucket()).isEqualTo("dentalcare-expedientes");
                });
    }

    @Test
    void createsS3ClientWhenR2ConfigurationIsComplete() {
        contextRunner
                .withPropertyValues(
                        "dentalcare.r2.enabled=true",
                        "dentalcare.r2.account-id=test-account-id",
                        "dentalcare.r2.bucket=dentalcare-expedientes",
                        "dentalcare.r2.endpoint=https://test-account-id.r2.cloudflarestorage.com",
                        "dentalcare.r2.access-key-id=test-access-key",
                        "dentalcare.r2.secret-access-key=test-secret-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(R2Properties.class);
                    assertThat(context).hasSingleBean(S3Client.class);

                    R2Properties properties = context.getBean(R2Properties.class);
                    assertThat(properties.getAccountId()).isEqualTo("test-account-id");
                    assertThat(properties.getBucket()).isEqualTo("dentalcare-expedientes");
                });
    }

    @Test
    void failsClearlyWhenR2IsEnabledWithIncompleteConfiguration() {
        contextRunner
                .withPropertyValues(
                        "dentalcare.r2.enabled=true",
                        "dentalcare.r2.account-id=test-account-id"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Cloudflare R2 is enabled but required configuration is missing: "
                                            + "R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY"
                            );
                });
    }

    @Test
    void failsClearlyWhenR2EndpointIsNotHttps() {
        contextRunner
                .withPropertyValues(
                        "dentalcare.r2.enabled=true",
                        "dentalcare.r2.account-id=test-account-id",
                        "dentalcare.r2.bucket=dentalcare-expedientes",
                        "dentalcare.r2.endpoint=http://test-account-id.r2.cloudflarestorage.com",
                        "dentalcare.r2.access-key-id=test-access-key",
                        "dentalcare.r2.secret-access-key=test-secret-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("R2_ENDPOINT must be a valid HTTPS URI");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(R2Properties.class)
    @Import(R2Config.class)
    static class TestConfiguration {
    }
}
