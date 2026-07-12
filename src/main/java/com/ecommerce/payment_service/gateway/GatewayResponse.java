package com.ecommerce.payment_service.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Represents the outcome of a gateway processing attempt — mirrors what a
// real gateway SDK response object would look like (e.g. Stripe's PaymentIntent response)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayResponse {
    private boolean success;
    private String gatewayReferenceId;
    private String failureReason; // null if success
}