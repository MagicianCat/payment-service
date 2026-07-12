package com.ecommerce.payment_service.controller;

import com.ecommerce.payment_service.dto.PaymentInitiateRequest;
import com.ecommerce.payment_service.dto.PaymentResponse;
import com.ecommerce.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Called by order-service when an order moves to PAYMENT_PENDING.
    // Returns immediately with status=INITIATED — actual processing happens
    // asynchronously via PaymentProcessor.
    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiateRequest request
    ) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Allows polling a payment's current status (e.g. order-service or a
    // frontend could poll this while waiting for async processing to resolve).
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    // Full payment attempt history for an order — useful for support/audit
    // scenarios (e.g. "why did this order's payment fail the first time?").
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistoryByOrderId(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(paymentService.getPaymentHistoryByOrderId(orderId));
    }
}