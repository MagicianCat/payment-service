package com.ecommerce.payment_service.dto;

import com.ecommerce.payment_service.entity.PaymentMethod;
import com.ecommerce.payment_service.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;
    private String gatewayReferenceId;
    private String failureReason; // null unless status == FAILED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}