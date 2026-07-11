package com.ecommerce.payment_service.repository;

import com.ecommerce.payment_service.entity.Payment;
import com.ecommerce.payment_service.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Idempotency check — retrieve existing payment by key instead of creating a duplicate
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // Used before creating a new payment: is there already an active (non-terminal-failed)
    // payment for this order? Prevents duplicate SUCCESS/INITIATED/PROCESSING payments
    // on the same order, while still allowing retries after a FAILED attempt.
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status IN :activeStatuses")
    List<Payment> findActivePaymentsByOrderId(
            @Param("orderId") Long orderId,
            @Param("activeStatuses") List<PaymentStatus> activeStatuses
    );

    // Used by the scheduled PaymentProcessor to pick up payments awaiting processing
    List<Payment> findByStatus(PaymentStatus status);

    // Full payment history for an order (all attempts, including failed ones) — useful for
    // support/audit lookups
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}