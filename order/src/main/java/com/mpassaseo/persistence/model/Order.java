package com.mpassaseo.persistence.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Setter
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    public enum OrderStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private String orderId = UUID.randomUUID().toString();

    @Column(columnDefinition = "TEXT")
    private String productIds;

    private String customerId;

    private double totalPrice;

    private String creditCardNumber;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    private boolean inventoryReserved = false;

    private boolean paymentApproved = false;

    public Order(
            String orderId,
            String productIds,
            String customerId,
            double totalPrice,
            String creditCardNumber,
            OrderStatus status) {

        this.orderId = orderId;
        this.productIds = productIds;
        this.customerId = customerId;
        this.totalPrice = totalPrice;
        this.creditCardNumber = creditCardNumber;
        this.status = status;
    }
}