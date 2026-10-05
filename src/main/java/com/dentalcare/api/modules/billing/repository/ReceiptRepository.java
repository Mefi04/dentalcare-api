package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByPaymentId(UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);

    @Query(value = "SELECT nextval('billing_receipt_number_seq')", nativeQuery = true)
    Number nextReceiptNumber();
}
