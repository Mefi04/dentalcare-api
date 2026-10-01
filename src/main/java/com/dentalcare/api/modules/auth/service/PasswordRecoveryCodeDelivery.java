package com.dentalcare.api.modules.auth.service;

import java.time.Duration;

public interface PasswordRecoveryCodeDelivery {

    void deliver(String email, String code, Duration validity);
}
