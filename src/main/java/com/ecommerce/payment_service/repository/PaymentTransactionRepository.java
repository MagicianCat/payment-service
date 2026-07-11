package com.ecommerce.payment_service.repository;

import com.ecommerce.payment_service.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    // Full audit trail for a given payment, oldest first — reconstructs the exact
    // sequence of status transitions
    List<PaymentTransaction> findByPaymentIdOrderByTimestampAsc(Long paymentId);
}