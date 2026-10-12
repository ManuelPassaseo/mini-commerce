package com.mpassaseo.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor

public class OrderCreatedEvent {

    private String orderId;
    private String productIds;
    private String customerId;
    private double totalPrice;
}
