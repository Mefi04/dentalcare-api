package com.dentalcare.api.config.r2;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dentalcare.r2", name = "enabled", havingValue = "true")
public class R2Config {

    @Bean(destroyMethod = "close")
    public S3Client r2S3Client(R2Properties properties) {
        properties.validateRequiredWhenEnabled();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        );

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        ClientOverrideConfiguration timeoutConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(properties.getApiCallTimeout())
                .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .overrideConfiguration(timeoutConfiguration)
                .build();
    }
}
