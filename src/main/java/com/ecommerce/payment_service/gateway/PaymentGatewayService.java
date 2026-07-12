package com.ecommerce.payment_service.gateway;

import com.ecommerce.payment_service.entity.Payment;

// Abstraction over "a payment gateway". Today it's backed by a simulated
// implementation; tomorrow a real provider (Stripe, PayPal, etc.) could be
// plugged in by adding a new implementation of this interface — no changes
// needed anywhere else in the codebase (service layer, controllers, processor).
public interface PaymentGatewayService {

    // Processes a payment attempt against the gateway and returns the outcome.
    GatewayResponse processPayment(Payment payment);
}