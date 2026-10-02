package com.dentalcare.api.config.drive;

import com.google.api.services.drive.Drive;
import com.google.auth.oauth2.UserCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDriveConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void doesNotCreateDriveBeansWhenIntegrationIsDisabled() {
        contextRunner
                .withPropertyValues("dentalcare.google-drive.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(GoogleDriveProperties.class);
                    assertThat(context).doesNotHaveBean(Drive.class);
                    assertThat(context).doesNotHaveBean(UserCredentials.class);

                    GoogleDriveProperties properties = context.getBean(GoogleDriveProperties.class);
                    properties.validateRequiredWhenEnabled();
                });
    }

    @Test
    void createsReusableDriveClientWhenConfigurationIsComplete() {
        contextRunner
                .withPropertyValues(
                        "dentalcare.google-drive.enabled=true",
                        "dentalcare.google-drive.folder-id=test-folder-id",
                        "dentalcare.google-drive.client-id=test-client-id",
                        "dentalcare.google-drive.client-secret=test-client-secret",
                        "dentalcare.google-drive.refresh-token=test-refresh-token",
                        "dentalcare.google-drive.application-name=DentalCare API Test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(GoogleDriveProperties.class);
                    assertThat(context).hasSingleBean(UserCredentials.class);
                    assertThat(context).hasSingleBean(Drive.class);

                    GoogleDriveProperties properties = context.getBean(GoogleDriveProperties.class);
                    assertThat(properties.getFolderId()).isEqualTo("test-folder-id");

                    UserCredentials credentials = context.getBean(UserCredentials.class);
                    assertThat(credentials.getRefreshToken()).isEqualTo("test-refresh-token");
                });
    }

    @Test
    void failsClearlyWhenDriveIsEnabledWithIncompleteConfiguration() {
        contextRunner
                .withPropertyValues(
                        "dentalcare.google-drive.enabled=true",
                        "dentalcare.google-drive.client-id=test-client-id"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Google Drive is enabled but required configuration is missing: "
                                            + "GOOGLE_DRIVE_FOLDER_ID, GOOGLE_DRIVE_CLIENT_SECRET, "
                                            + "GOOGLE_DRIVE_REFRESH_TOKEN"
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GoogleDriveProperties.class)
    @Import(GoogleDriveConfig.class)
    static class TestConfiguration {
    }
}
