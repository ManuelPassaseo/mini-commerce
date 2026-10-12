package com.mpassaseo.service;

import com.mpassaseo.persistence.model.InventoryReservation;
import com.mpassaseo.persistence.model.Product;
import com.mpassaseo.persistence.repository.InventoryReservationRepository;
import com.mpassaseo.persistence.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    private final InventoryReservationRepository
            reservationRepository;

    @Transactional
    public ReservationResult reserveInventory(
            String orderId,
            String productIds) {

        Optional<InventoryReservation> existingReservation =
                reservationRepository.findByOrderId(orderId);

        if (existingReservation.isPresent()) {

            InventoryReservation reservation =
                    existingReservation.get();

            if (reservation.getStatus()
                    == InventoryReservation.ReservationStatus.RESERVED) {

                log.info(
                        "Reservation already exists for order {}",
                        orderId
                );

                return new ReservationResult(
                        true,
                        null
                );
            }

            if (reservation.getStatus()
                    == InventoryReservation.ReservationStatus.REJECTED) {

                return new ReservationResult(
                        false,
                        reservation.getReason()
                );
            }

            return new ReservationResult(
                    false,
                    "Order already released"
            );
        }

        List<String> ids = parseProductIds(productIds);

        Map<String, Integer> requested =
                countRequestedProducts(ids);

        for (Map.Entry<String, Integer> entry :
                requested.entrySet()) {

            String productId = entry.getKey();
            int quantity = entry.getValue();

            Optional<Product> productOptional =
                    productRepository
                            .findProductsByProductId(productId);

            if (productOptional.isEmpty()) {

                String reason =
                        "Product not found: " + productId;

                saveRejectedReservation(
                        orderId,
                        productIds,
                        reason
                );

                return new ReservationResult(
                        false,
                        reason
                );
            }

            Product product =
                    productOptional.get();

            if (product.getQuantity() < quantity) {

                String reason =
                        "Insufficient inventory for product "
                                + productId
                                + ". Requested: "
                                + quantity
                                + ", available: "
                                + product.getQuantity();

                saveRejectedReservation(
                        orderId,
                        productIds,
                        reason
                );

                return new ReservationResult(
                        false,
                        reason
                );
            }
        }

        for (Map.Entry<String, Integer> entry :
                requested.entrySet()) {

            Product product =
                    productRepository
                            .findProductsByProductId(
                                    entry.getKey()
                            )
                            .orElseThrow();

            int newQuantity =
                    product.getQuantity()
                            - entry.getValue();

            product.setQuantity(newQuantity);

            productRepository.save(product);

            log.info(
                    "Reserved {} unit(s) of {} for order {}. New quantity: {}",
                    entry.getValue(),
                    entry.getKey(),
                    orderId,
                    newQuantity
            );
        }

        InventoryReservation reservation =
                new InventoryReservation(
                        orderId,
                        productIds,
                        InventoryReservation.ReservationStatus.RESERVED,
                        null
                );

        reservationRepository.save(reservation);

        return new ReservationResult(
                true,
                null
        );
    }

    @Transactional
    public void releaseReservation(String orderId) {

        Optional<InventoryReservation> optionalReservation =
                reservationRepository.findByOrderId(orderId);

        if (optionalReservation.isEmpty()) {
            log.info(
                    "No inventory reservation found for order {}",
                    orderId
            );
            return;
        }

        InventoryReservation reservation =
                optionalReservation.get();

        if (reservation.getStatus()
                != InventoryReservation.ReservationStatus.RESERVED) {

            return;
        }

        List<String> ids =
                parseProductIds(
                        reservation.getProductIds()
                );

        Map<String, Integer> requested =
                countRequestedProducts(ids);

        for (Map.Entry<String, Integer> entry :
                requested.entrySet()) {

            Product product =
                    productRepository
                            .findProductsByProductId(
                                    entry.getKey()
                            )
                            .orElseThrow();

            int newQuantity =
                    product.getQuantity()
                            + entry.getValue();

            product.setQuantity(newQuantity);

            productRepository.save(product);

            log.info(
                    "Released {} unit(s) of {} for order {}. New quantity: {}",
                    entry.getValue(),
                    entry.getKey(),
                    orderId,
                    newQuantity
            );
        }

        reservation.setStatus(
                InventoryReservation.ReservationStatus.RELEASED
        );

        reservationRepository.save(reservation);

        log.info(
                "Inventory reservation released for order {}",
                orderId
        );
    }

    private void saveRejectedReservation(
            String orderId,
            String productIds,
            String reason) {

        InventoryReservation reservation =
                new InventoryReservation(
                        orderId,
                        productIds,
                        InventoryReservation.ReservationStatus.REJECTED,
                        reason
                );

        reservationRepository.save(reservation);

        log.warn(
                "Inventory reservation rejected for order {}: {}",
                orderId,
                reason
        );
    }

    private List<String> parseProductIds(String productIds) {

        return Arrays.stream(
                        productIds.split(",")
                )
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .toList();
    }

    private Map<String, Integer> countRequestedProducts(
            List<String> ids) {

        Map<String, Integer> requested =
                new HashMap<>();

        for (String id : ids) {

            requested.put(
                    id,
                    requested.getOrDefault(id, 0) + 1
            );
        }

        return requested;
    }

    public Iterable<Product> getProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(
            String productId) {

        return productRepository
                .findProductsByProductId(productId);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    public Product update(
            String productId,
            Product product) {

        Optional<Product> existingProduct =
                productRepository
                        .findProductsByProductId(productId);

        if (existingProduct.isEmpty()) {
            throw new RuntimeException(
                    "Product not found: " + productId
            );
        }

        Product existing =
                existingProduct.get();

        existing.setProductId(
                product.getProductId()
        );

        existing.setQuantity(
                product.getQuantity()
        );

        existing.setName(
                product.getName()
        );

        existing.setDescription(
                product.getDescription()
        );

        existing.setPrice(
                product.getPrice()
        );

        return productRepository.save(existing);
    }

    public void delete(String productId) {

        Optional<Product> existingProduct =
                productRepository
                        .findProductsByProductId(productId);

        existingProduct.ifPresent(
                productRepository::delete
        );
    }

    public record ReservationResult(
            boolean reserved,
            String reason
    ) {
    }
}