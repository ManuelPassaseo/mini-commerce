package com.mpassaseo.persistence.repository;

import com.mpassaseo.persistence.model.Payment;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PaymentRepository extends CrudRepository<Payment, Long>{
    Optional<Payment> findByOrderId(String orderId);
}
