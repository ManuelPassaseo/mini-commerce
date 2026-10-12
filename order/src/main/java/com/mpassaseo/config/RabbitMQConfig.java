package com.mpassaseo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENT_EXCHANGE = "commerce.events";

    public static final String ORDER_EVENTS_QUEUE =
            "order.events.queue";

    @Bean
    public TopicExchange commerceEventsExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderEventsQueue() {
        return new Queue(ORDER_EVENTS_QUEUE, true);
    }

    @Bean
    public Binding inventoryReservedBinding() {
        return BindingBuilder
                .bind(orderEventsQueue())
                .to(commerceEventsExchange())
                .with("inventory.reserved");
    }

    @Bean
    public Binding inventoryRejectedBinding() {
        return BindingBuilder
                .bind(orderEventsQueue())
                .to(commerceEventsExchange())
                .with("inventory.rejected");
    }

    @Bean
    public Binding paymentApprovedBinding() {
        return BindingBuilder
                .bind(orderEventsQueue())
                .to(commerceEventsExchange())
                .with("payment.approved");
    }

    @Bean
    public Binding paymentRejectedBinding() {
        return BindingBuilder
                .bind(orderEventsQueue())
                .to(commerceEventsExchange())
                .with("payment.rejected");
    }
}