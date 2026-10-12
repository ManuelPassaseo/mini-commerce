package com.mpassaseo.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpassaseo.config.RabbitMQConfig;
import com.mpassaseo.events.CommerceEvent;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    public void publish(
            String routingKey,
            CommerceEvent event) {

        try {

            String payload =
                    objectMapper.writeValueAsString(event);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EVENT_EXCHANGE,
                    routingKey,
                    payload
            );

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Unable to serialize event",
                    e
            );
        }
    }
}