package com.mpassaseo.persistence.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "inventory_reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_reservation_order",
                        columnNames = "order_id"
                )
        }
)
public class InventoryReservation {

    public enum ReservationStatus {
        RESERVED,
        REJECTED,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "order_id",
            unique = true,
            nullable = false
    )
    private String orderId;

    @Column(columnDefinition = "TEXT")
    private String productIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public InventoryReservation(
            String orderId,
            String productIds,
            ReservationStatus status,
            String reason) {

        this.orderId = orderId;
        this.productIds = productIds;
        this.status = status;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
}