package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.dto.response.ReceiptClinicResponse;
import com.dentalcare.api.modules.billing.model.Receipt;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import org.springframework.stereotype.Component;

@Component
public class ReceiptMapper {

    public ReceiptResponse toResponse(Receipt receipt, ClinicSettingsResponse settings) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getPaymentId(),
                receipt.getPatientId(),
                receipt.getConcept(),
                receipt.getAmount(),
                receipt.getMethod(),
                receipt.getStatus(),
                receipt.getIssuedAt(),
                receipt.getIssuedByUserId(),
                receipt.getVoidedAt(),
                receipt.getVoidReason(),
                settings == null ? null : new ReceiptClinicResponse(
                        settings.tradeName(), settings.nit(), settings.phone(), settings.email(),
                        settings.address(), settings.city()));
    }
}
