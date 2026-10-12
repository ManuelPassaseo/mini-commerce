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

    public static final String PRODUCT_ORDER_QUEUE =
            "product.order.queue";

    @Bean
    public TopicExchange commerceEventsExchange() {
        return new TopicExchange(
                EVENT_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue productOrderQueue() {
        return new Queue(
                PRODUCT_ORDER_QUEUE,
                true
        );
    }

    @Bean
    public Binding orderCreatedBinding() {

        return BindingBuilder
                .bind(productOrderQueue())
                .to(commerceEventsExchange())
                .with("order.created");
    }

    @Bean
    public Binding orderRejectedBinding() {

        return BindingBuilder
                .bind(productOrderQueue())
                .to(commerceEventsExchange())
                .with("order.rejected");
    }
}