package com.mpassaseo.persistence.repository;

import com.mpassaseo.persistence.model.InventoryReservation;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface InventoryReservationRepository
        extends CrudRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByOrderId(String orderId);
}