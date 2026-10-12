package com.mpassaseo.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpassaseo.config.RabbitMQConfig;
import com.mpassaseo.events.CommerceEvent;
import com.mpassaseo.service.OrderService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class OrderEventListener {

    private final ObjectMapper objectMapper;

    private final OrderService orderService;

    @RabbitListener(
            queues = RabbitMQConfig.ORDER_EVENTS_QUEUE
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

                case CommerceEvent.INVENTORY_RESERVED ->
                        orderService.handleInventoryReserved(
                                event.orderId()
                        );

                case CommerceEvent.INVENTORY_REJECTED ->
                        orderService.handleInventoryRejected(
                                event.orderId(),
                                event.reason()
                        );

                case CommerceEvent.PAYMENT_APPROVED ->
                        orderService.handlePaymentApproved(
                                event.orderId()
                        );

                case CommerceEvent.PAYMENT_REJECTED ->
                        orderService.handlePaymentRejected(
                                event.orderId(),
                                event.reason()
                        );

                default ->
                        log.warn(
                                "Unknown routing key: {}",
                                routingKey
                        );
            }

        } catch (Exception e) {

            log.error(
                    "Error processing event",
                    e
            );

            throw new IllegalStateException(
                    "Unable to process RabbitMQ event",
                    e
            );
        }
    }
}