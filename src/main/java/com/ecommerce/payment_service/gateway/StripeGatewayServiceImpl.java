package com.ecommerce.payment_service.gateway;

// ============================================================================
// REFERENCE IMPLEMENTATION — NOT ACTIVE
// ============================================================================
// This class demonstrates that PaymentGatewayService is a genuine abstraction,
// not just decoration around a fake mock. Swapping the simulated gateway for
// a real one (Stripe, here) requires ONLY this one new class — no changes to
// PaymentService, PaymentController, PaymentProcessor, or any other layer.
//
// It is left commented out because:
//   1. It requires a real Stripe account + API secret key (not available/appropriate
//      for a portfolio project — see earlier discussion on why real gateway
//      integration is out of scope).
//   2. Real Stripe webhooks require a public HTTPS endpoint (e.g. via ngrok),
//      which adds infrastructure complexity unrelated to the microservices
//      architecture this project is meant to demonstrate.
//   3. It depends on the "stripe-java" SDK, which is not in this project's pom.xml.
//
// To actually activate this in the future:
//   1. Add dependency: com.stripe:stripe-java (pom.xml)
//   2. Set Stripe.apiKey from an env variable (never hardcode it)
//   3. Uncomment the class body below
//   4. Uncomment @Service, and either remove @Service from
//      SimulatedGatewayServiceImpl or use @Primary / @Qualifier to choose
//      which implementation Spring injects into PaymentService
// ============================================================================

/*
import com.ecommerce.paymentservice.entity.Payment;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripeGatewayServiceImpl implements PaymentGatewayService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Override
    public GatewayResponse processPayment(Payment payment) {
        try {
            // Stripe works in the smallest currency unit (e.g. cents), so the
            // amount must be converted — another real-world detail a "dummy"
            // implementation would never need to handle.
            long amountInSmallestUnit = payment.getAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInSmallestUnit)
                    .setCurrency(payment.getCurrency().toLowerCase())
                    .setDescription("Order #" + payment.getOrderId())
                    .putMetadata("orderId", String.valueOf(payment.getOrderId()))
                    .putMetadata("idempotencyKey", payment.getIdempotencyKey())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            // In a real integration, "success" here is just "PaymentIntent created" —
            // the ACTUAL success/failure confirmation would arrive later via a
            // Stripe webhook (asynchronously), which is exactly why our architecture
            // is already async/scheduled-based rather than synchronous. The
            // PaymentProcessor's polling loop would be replaced by a webhook
            // controller endpoint that Stripe calls directly.
            return GatewayResponse.builder()
                    .success(true)
                    .gatewayReferenceId(intent.getId())
                    .failureReason(null)
                    .build();

        } catch (StripeException e) {
            return GatewayResponse.builder()
                    .success(false)
                    .gatewayReferenceId(null)
                    .failureReason("Stripe error: " + e.getMessage())
                    .build();
        }
    }
}
*/