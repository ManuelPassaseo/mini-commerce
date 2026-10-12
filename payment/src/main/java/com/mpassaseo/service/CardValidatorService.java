package com.mpassaseo.service;

import com.mpassaseo.persistence.model.Payment;
import com.mpassaseo.persistence.repository.PaymentRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@AllArgsConstructor
@Slf4j
@Service
public class CardValidatorService {

    private final PaymentRepository paymentRepository;

    public boolean processPayment(
            String orderId,
            String cardNumber) {

        log.info(
                "Processing payment for order {}...",
                orderId
        );

        Optional<Payment> existingPayment =
                paymentRepository.findByOrderId(orderId);

        if (existingPayment.isPresent()) {

            log.info(
                    "Payment already exists for order {}",
                    orderId
            );

            return Boolean.TRUE.equals(
                    existingPayment.get().getSuccess()
            );
        }

        Payment payment =
                new Payment(
                        orderId,
                        cardNumber
                );

        boolean valid =
                validateCard(cardNumber);

        payment.setSuccess(valid);

        paymentRepository.save(payment);

        if (valid) {

            log.info(
                    "Payment approved for order {}",
                    orderId
            );

        } else {

            log.info(
                    "Payment rejected for order {}",
                    orderId
            );
        }

        return valid;
    }

    private boolean validateCard(
            String cardNumber) {

        return cardNumber != null
                && cardNumber.startsWith("7777");
    }
}