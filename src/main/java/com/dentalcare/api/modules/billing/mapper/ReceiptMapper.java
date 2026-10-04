package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.model.Receipt;
import org.springframework.stereotype.Component;

@Component
public class ReceiptMapper {

    public ReceiptResponse toResponse(Receipt receipt) {
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
                receipt.getIssuedByUserId());
    }
}
