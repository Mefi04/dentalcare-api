package com.dentalcare.api.modules.billing.dto.response;

public record ReceiptClinicResponse(
        String tradeName,
        String nit,
        String phone,
        String email,
        String address,
        String city) {
}
