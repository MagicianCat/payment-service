package com.ecommerce.payment_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Append-only audit ledger — rows are never updated or deleted.
// Every status transition of a Payment gets its own immutable record here.
@Entity
@Table(
    name = "payment_transactions",
    indexes = {
        @Index(name = "idx_txn_payment_id", columnList = "paymentId")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus previousStatus; // nullable — null for the very first entry

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus newStatus;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}