package com.ecommerce.payment_service.service;

import com.ecommerce.payment_service.dto.PaymentInitiateRequest;
import com.ecommerce.payment_service.dto.PaymentResponse;
import com.ecommerce.payment_service.entity.Payment;
import com.ecommerce.payment_service.entity.PaymentMethod;
import com.ecommerce.payment_service.entity.PaymentStatus;
import com.ecommerce.payment_service.entity.PaymentTransaction;
import com.ecommerce.payment_service.exception.DuplicatePaymentException;
import com.ecommerce.payment_service.exception.InvalidPaymentMethodException;
import com.ecommerce.payment_service.exception.PaymentNotFoundException;
import com.ecommerce.payment_service.mapper.PaymentMapper;
import com.ecommerce.payment_service.repository.PaymentRepository;
import com.ecommerce.payment_service.repository.PaymentTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentMapper paymentMapper;

    private static final List<PaymentStatus> ACTIVE_STATUSES =
            List.of(PaymentStatus.INITIATED, PaymentStatus.PROCESSING, PaymentStatus.SUCCESS);

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentTransactionRepository transactionRepository,
            PaymentMapper paymentMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public PaymentResponse initiatePayment(PaymentInitiateRequest request) {

        // 1. Idempotency check — if this exact request was already submitted
        // (e.g. client retried after a network timeout), return the existing
        // payment instead of creating a duplicate.
        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingByKey.isPresent()) {
            log.info("Idempotency key {} already exists — returning existing payment", request.getIdempotencyKey());
            return paymentMapper.toResponse(existingByKey.get());
        }

        // 2. Active-payment check — prevent a second payment attempt while one
        // is already in flight or already succeeded for this order. FAILED
        // payments are excluded on purpose, since they must be retryable.
        List<Payment> activePayments = paymentRepository.findActivePaymentsByOrderId(
                request.getOrderId(), ACTIVE_STATUSES
        );
        if (!activePayments.isEmpty()) {
            throw new DuplicatePaymentException(
                    "An active payment already exists for orderId=" + request.getOrderId()
            );
        }

        // 3. Validate and parse payment method (String -> enum) with a clean
        // custom error instead of a generic deserialization failure.
        PaymentMethod method = parseMethod(request.getMethod());

        // 4. Create the payment in INITIATED state — the PaymentProcessor's
        // scheduled job will pick it up asynchronously from here.
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .method(method)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Payment saved = paymentRepository.save(payment);

        PaymentTransaction ledgerEntry = PaymentTransaction.builder()
                .paymentId(saved.getId())
                .previousStatus(null)
                .newStatus(PaymentStatus.INITIATED)
                .notes("Payment initiated by client")
                .build();
        transactionRepository.save(ledgerEntry);

        log.info("Payment initiated: id={}, orderId={}", saved.getId(), saved.getOrderId());

        return paymentMapper.toResponse(saved);
    }

    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
        return paymentMapper.toResponse(payment);
    }

    public List<PaymentResponse> getPaymentHistoryByOrderId(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    private PaymentMethod parseMethod(String method) {
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidPaymentMethodException(
                    "Invalid payment method: " + method + ". Allowed values: CARD, WALLET"
            );
        }
    }
}