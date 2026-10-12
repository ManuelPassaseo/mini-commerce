package com.mpassaseo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENT_EXCHANGE =
            "commerce.events";

    public static final String PAYMENT_ORDER_QUEUE =
            "payment.order.queue";

    @Bean
    public TopicExchange commerceEventsExchange() {
        return new TopicExchange(
                EVENT_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue paymentOrderQueue() {
        return new Queue(
                PAYMENT_ORDER_QUEUE,
                true
        );
    }

    @Bean
    public Binding orderCreatedBinding() {

        return BindingBuilder
                .bind(paymentOrderQueue())
                .to(commerceEventsExchange())
                .with("order.created");
    }
}