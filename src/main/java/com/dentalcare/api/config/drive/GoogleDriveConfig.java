package com.dentalcare.api.config.drive;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dentalcare.google-drive", name = "enabled", havingValue = "true")
public class GoogleDriveConfig {

    @Bean
    public UserCredentials googleDriveCredentials(GoogleDriveProperties properties) {
        properties.validateRequiredWhenEnabled();

        return UserCredentials.newBuilder()
                .setClientId(properties.getClientId())
                .setClientSecret(properties.getClientSecret())
                .setRefreshToken(properties.getRefreshToken())
                .build();
    }

    @Bean
    public Drive googleDriveClient(
            GoogleDriveProperties properties,
            UserCredentials googleDriveCredentials
    ) throws GeneralSecurityException, IOException {
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(googleDriveCredentials)
        )
                .setApplicationName(properties.getApplicationName())
                .build();
    }
}
