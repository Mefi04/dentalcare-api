package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BillingMapper {

    public ChargeResponse toChargeResponse(Charge charge, BigDecimal paid, BigDecimal pending, ChargeStatus status,
                                           BigDecimal discount, BigDecimal refunded) {
        return new ChargeResponse(
                charge.getId(),
                charge.getConcept(),
                charge.getAmount(),
                paid,
                pending,
                status,
                charge.getCreatedAt(),
                discount,
                refunded);
    }

    public PaymentResponse toPaymentResponse(Payment payment) {
        Charge charge = payment.getCharge();
        return new PaymentResponse(
                payment.getId(),
                charge == null ? null : charge.getId(),
                payment.getKind(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCreatedAt());
    }
}
