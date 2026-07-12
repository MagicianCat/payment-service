package com.ecommerce.payment_service.processor;

import com.ecommerce.payment_service.client.OrderClient;
import com.ecommerce.payment_service.entity.Payment;
import com.ecommerce.payment_service.entity.PaymentStatus;
import com.ecommerce.payment_service.entity.PaymentTransaction;
import com.ecommerce.payment_service.gateway.GatewayResponse;
import com.ecommerce.payment_service.gateway.PaymentGatewayService;
import com.ecommerce.payment_service.repository.PaymentRepository;
import com.ecommerce.payment_service.repository.PaymentTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Polls for payments awaiting processing and drives them through the gateway.
// This is intentionally a standalone component (not routed through PaymentService)
// because it represents the "processing engine" layer, distinct from the
// request-driven service layer that handles controller-initiated actions.
//
// Designed to be queue-ready: if this project later migrates to a message
// broker (RabbitMQ/Kafka), only this class's trigger mechanism changes
// (@Scheduled -> @RabbitListener) — the processing logic itself stays identical.
@Slf4j
@Component
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final OrderClient orderClient;

    public PaymentProcessor(
            PaymentRepository paymentRepository,
            PaymentTransactionRepository transactionRepository,
            PaymentGatewayService paymentGatewayService,
            OrderClient orderClient
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.orderClient = orderClient;
    }

    @Scheduled(fixedDelayString = "${payment.processor.poll-interval-ms}")
    public void processPendingPayments() {
        List<Payment> pending = paymentRepository.findByStatus(PaymentStatus.INITIATED);

        if (pending.isEmpty()) {
            return;
        }

        log.info("PaymentProcessor picked up {} pending payment(s)", pending.size());

        for (Payment payment : pending) {
            processSinglePayment(payment);
        }
    }

    @Transactional
    public void processSinglePayment(Payment payment) {
        // Move INITIATED -> PROCESSING immediately so a concurrent poll cycle
        // (or a future multi-instance deployment) never picks up the same
        // payment twice.
        transitionStatus(payment, PaymentStatus.PROCESSING, "Picked up by scheduled processor");

        GatewayResponse gatewayResponse = paymentGatewayService.processPayment(payment);

        if (gatewayResponse.isSuccess()) {
            payment.setGatewayReferenceId(gatewayResponse.getGatewayReferenceId());
            transitionStatus(payment, PaymentStatus.SUCCESS, "Gateway approved transaction");
            notifyOrderService(payment, "CONFIRMED");
        } else {
            payment.setGatewayReferenceId(gatewayResponse.getGatewayReferenceId());
            payment.setFailureReason(gatewayResponse.getFailureReason());
            transitionStatus(payment, PaymentStatus.FAILED, gatewayResponse.getFailureReason());
            notifyOrderService(payment, "CANCELLED");
        }
    }

    private void transitionStatus(Payment payment, PaymentStatus newStatus, String notes) {
        PaymentStatus previousStatus = payment.getStatus();
        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        PaymentTransaction ledgerEntry = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .notes(notes)
                .build();
        transactionRepository.save(ledgerEntry);
    }

    private void notifyOrderService(Payment payment, String orderStatus) {
        try {
            orderClient.updateOrderStatus(payment.getOrderId(), orderStatus);
        } catch (Exception e) {
            // Deliberately swallowed (but logged) here: a failed callback must
            // NOT roll back the payment's own SUCCESS/FAILED status, since the
            // gateway outcome already happened and is final. In a production
            // system this would instead push to a retry queue / dead-letter
            // queue so the order-service notification is retried independently.
            log.error(
                    "Failed to notify order-service for orderId={} (payment status={}): {}",
                    payment.getOrderId(), payment.getStatus(), e.getMessage()
            );
        }
    }
}