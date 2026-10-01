package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.dto.request.ConfirmPasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.request.PasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.response.PasswordRecoveryResponse;

public interface PasswordRecoveryService {

    PasswordRecoveryResponse requestRecovery(PasswordRecoveryRequest request);

    PasswordRecoveryResponse confirmRecovery(ConfirmPasswordRecoveryRequest request);
}
