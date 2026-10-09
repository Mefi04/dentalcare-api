package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.ServiceUnavailableException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AppointmentOutboxCrypto {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final AppointmentOutboxProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AppointmentOutboxCrypto(AppointmentOutboxProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plainText) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceUnavailableException("Appointment notification encryption is unavailable");
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(ciphertext);
            ByteBuffer input = ByteBuffer.wrap(packed);
            byte[] nonce = new byte[NONCE_BYTES];
            input.get(nonce);
            byte[] encrypted = new byte[input.remaining()];
            input.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("OUTBOX_PAYLOAD_DECRYPTION_FAILED");
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] raw = Base64.getDecoder().decode(properties.encryptionKey());
            if (raw.length != 32) throw new IllegalArgumentException("Expected 32 bytes");
            return new SecretKeySpec(raw, "AES");
        } catch (Exception exception) {
            throw new ServiceUnavailableException("Appointment notification encryption is not configured");
        }
    }
}
