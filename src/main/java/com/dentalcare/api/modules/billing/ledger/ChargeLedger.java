package com.dentalcare.api.modules.billing.ledger;

import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ChargeLedger {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    public BigDecimal netPaid(BigDecimal grossPaid, BigDecimal refunded) {
        return scale(grossPaid).subtract(scale(refunded));
    }

    public ChargePosition position(BigDecimal amount, BigDecimal grossPaid, BigDecimal discount,
                                   BigDecimal refunded, boolean voided) {
        BigDecimal scaledDiscount = scale(discount);
        BigDecimal scaledRefunded = scale(refunded);
        BigDecimal net = scale(grossPaid).subtract(scaledRefunded);
        BigDecimal pending = voided ? ZERO : scale(amount).subtract(scaledDiscount).subtract(net);
        return new ChargePosition(net, scaledDiscount, scaledRefunded, pending, statusOf(net, pending, voided), voided);
    }

    private ChargeStatus statusOf(BigDecimal netPaid, BigDecimal pending, boolean voided) {
        if (voided) {
            return ChargeStatus.VOIDED;
        }
        if (pending.signum() <= 0) {
            return ChargeStatus.PAID;
        }
        return netPaid.signum() > 0 ? ChargeStatus.PARTIALLY_PAID : ChargeStatus.PENDING;
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
