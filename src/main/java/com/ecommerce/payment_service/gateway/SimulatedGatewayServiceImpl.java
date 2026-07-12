package com.ecommerce.payment_service.gateway;

import com.ecommerce.payment_service.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Simulates a real payment gateway's behavior: a configurable success rate
// (like Stripe/PayPal sandbox modes), plus deterministic test triggers so
// specific outcomes can be reproduced on demand for demos and testing.
@Service
public class SimulatedGatewayServiceImpl implements PaymentGatewayService {

    @Value("${payment.simulation.success-rate}")
    private double successRate;

    // Deterministic test trigger: any amount ending in exactly .13 always fails.
    // Mirrors how Stripe's test card numbers guarantee specific outcomes
    // (e.g. 4000000000000002 always declines) instead of relying on randomness.
    private static final BigDecimal FORCED_FAILURE_CENTS = new BigDecimal("0.13");

    @Override
    public GatewayResponse processPayment(Payment payment) {
        String reference = "sim_" + UUID.randomUUID();

        if (isForcedFailure(payment.getAmount())) {
            return GatewayResponse.builder()
                    .success(false)
                    .gatewayReferenceId(reference)
                    .failureReason("Simulated decline — forced test failure trigger (amount ending in .13)")
                    .build();
        }

        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;

        if (success) {
            return GatewayResponse.builder()
                    .success(true)
                    .gatewayReferenceId(reference)
                    .failureReason(null)
                    .build();
        }

        return GatewayResponse.builder()
                .success(false)
                .gatewayReferenceId(reference)
                .failureReason("Simulated decline — insufficient funds")
                .build();
    }

    private boolean isForcedFailure(BigDecimal amount) {
        BigDecimal cents = amount.remainder(BigDecimal.ONE).setScale(2, java.math.RoundingMode.HALF_UP);
        return cents.compareTo(FORCED_FAILURE_CENTS) == 0;
    }
}