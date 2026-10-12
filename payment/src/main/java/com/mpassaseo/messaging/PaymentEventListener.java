package com.mpassaseo.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpassaseo.config.RabbitMQConfig;
import com.mpassaseo.events.CommerceEvent;
import com.mpassaseo.service.CardValidatorService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class PaymentEventListener {

    private final ObjectMapper objectMapper;

    private final CardValidatorService
            cardValidatorService;

    private final EventPublisher eventPublisher;

    @RabbitListener(
            queues = RabbitMQConfig.PAYMENT_ORDER_QUEUE
    )
    public void handle(String payload) {

        try {

            CommerceEvent event =
                    objectMapper.readValue(
                            payload,
                            CommerceEvent.class
                    );

            if (!CommerceEvent.ORDER_CREATED.equals(
                    event.type())) {

                return;
            }

            log.info(
                    "Received OrderCreated for payment: {}",
                    event.orderId()
            );

            boolean approved =
                    cardValidatorService.processPayment(
                            event.orderId(),
                            event.creditCardNumber()
                    );

            if (approved) {

                eventPublisher.publish(
                        CommerceEvent.PAYMENT_APPROVED,
                        CommerceEvent.paymentApproved(
                                event.orderId()
                        )
                );

                log.info(
                        "PaymentApproved published for order {}",
                        event.orderId()
                );

            } else {

                eventPublisher.publish(
                        CommerceEvent.PAYMENT_REJECTED,
                        CommerceEvent.paymentRejected(
                                event.orderId(),
                                "Invalid credit card"
                        )
                );

                log.info(
                        "PaymentRejected published for order {}",
                        event.orderId()
                );
            }

        } catch (Exception e) {

            log.error(
                    "Error processing payment event",
                    e
            );

            throw new IllegalStateException(
                    "Unable to process payment event",
                    e
            );
        }
    }
}