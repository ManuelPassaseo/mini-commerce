package com.mpassaseo.events;

public record CommerceEvent(
        String type,
        String orderId,
        String productIds,
        String customerId,
        Double totalPrice,
        String creditCardNumber,
        String reason
) {

    public static final String ORDER_CREATED = "order.created";

    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_REJECTED = "inventory.rejected";

    public static final String PAYMENT_APPROVED = "payment.approved";
    public static final String PAYMENT_REJECTED = "payment.rejected";

    public static final String ORDER_APPROVED = "order.approved";
    public static final String ORDER_REJECTED = "order.rejected";

    public static CommerceEvent orderCreated(
            String orderId,
            String productIds,
            String customerId,
            double totalPrice,
            String creditCardNumber) {

        return new CommerceEvent(
                ORDER_CREATED,
                orderId,
                productIds,
                customerId,
                totalPrice,
                creditCardNumber,
                null
        );
    }

    public static CommerceEvent inventoryReserved(String orderId) {

        return new CommerceEvent(
                INVENTORY_RESERVED,
                orderId,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static CommerceEvent inventoryRejected(
            String orderId,
            String reason) {

        return new CommerceEvent(
                INVENTORY_REJECTED,
                orderId,
                null,
                null,
                null,
                null,
                reason
        );
    }

    public static CommerceEvent paymentApproved(String orderId) {

        return new CommerceEvent(
                PAYMENT_APPROVED,
                orderId,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static CommerceEvent paymentRejected(
            String orderId,
            String reason) {

        return new CommerceEvent(
                PAYMENT_REJECTED,
                orderId,
                null,
                null,
                null,
                null,
                reason
        );
    }

    public static CommerceEvent orderApproved(String orderId) {

        return new CommerceEvent(
                ORDER_APPROVED,
                orderId,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static CommerceEvent orderRejected(
            String orderId,
            String reason) {

        return new CommerceEvent(
                ORDER_REJECTED,
                orderId,
                null,
                null,
                null,
                null,
                reason
        );
    }
}