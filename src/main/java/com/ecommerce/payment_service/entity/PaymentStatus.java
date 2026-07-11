package com.ecommerce.payment_service.entity;

public enum PaymentStatus {
    INITIATED,   // Payment record created, not yet picked up for processing
    PROCESSING,  // Picked up by scheduled processor, simulated gateway call in progress
    SUCCESS,     // Payment completed successfully
    FAILED       // Payment declined/failed
}
