package com.mpassaseo.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpassaseo.config.RabbitMQConfig;
import com.mpassaseo.events.CommerceEvent;
import com.mpassaseo.service.ProductService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class ProductEventListener {

    private final ObjectMapper objectMapper;

    private final ProductService productService;

    private final EventPublisher eventPublisher;

    @RabbitListener(
            queues = RabbitMQConfig.PRODUCT_ORDER_QUEUE
    )
    public void handle(
            String payload,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY)
            String routingKey) {

        try {

            CommerceEvent event =
                    objectMapper.readValue(
                            payload,
                            CommerceEvent.class
                    );

            log.info(
                    "Received event {} for order {}",
                    event.type(),
                    event.orderId()
            );

            switch (routingKey) {

                case CommerceEvent.ORDER_CREATED ->
                        handleOrderCreated(event);

                case CommerceEvent.ORDER_REJECTED ->
                        handleOrderRejected(event);

                default ->
                        log.warn(
                                "Unknown routing key: {}",
                                routingKey
                        );
            }

        } catch (Exception e) {

            log.error(
                    "Error processing product event",
                    e
            );

            throw new IllegalStateException(
                    "Unable to process product event",
                    e
            );
        }
    }

    private void handleOrderCreated(
            CommerceEvent event) {

        ProductService.ReservationResult result =
                productService.reserveInventory(
                        event.orderId(),
                        event.productIds()
                );

        if (result.reserved()) {

            eventPublisher.publish(
                    CommerceEvent.INVENTORY_RESERVED,
                    CommerceEvent.inventoryReserved(
                            event.orderId()
                    )
            );

            log.info(
                    "InventoryReserved published for order {}",
                    event.orderId()
            );

        } else {

            eventPublisher.publish(
                    CommerceEvent.INVENTORY_REJECTED,
                    CommerceEvent.inventoryRejected(
                            event.orderId(),
                            result.reason()
                    )
            );

            log.info(
                    "InventoryRejected published for order {}",
                    event.orderId()
            );
        }
    }

    private void handleOrderRejected(
            CommerceEvent event) {

        log.info(
                "Order {} rejected. Releasing inventory if reserved...",
                event.orderId()
        );

        productService.releaseReservation(
                event.orderId()
        );
    }
}