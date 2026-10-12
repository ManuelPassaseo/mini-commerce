package com.mpassaseo.service;

import com.mpassaseo.events.CommerceEvent;
import com.mpassaseo.messaging.EventPublisher;
import com.mpassaseo.persistence.model.Order;
import com.mpassaseo.persistence.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    private final EventPublisher eventPublisher;

    public Order placeOrder(Order order) {

        log.info("Creating order {}...", order.getOrderId());

        Optional<Order> existingOrder =
                orderRepository.findByOrderId(order.getOrderId());

        if (existingOrder.isPresent()) {
            log.info(
                    "Order {} already exists",
                    order.getOrderId()
            );
            return existingOrder.get();
        }

        order.setStatus(Order.OrderStatus.PENDING);
        order.setInventoryReserved(false);
        order.setPaymentApproved(false);

        Order savedOrder =
                orderRepository.save(order);

        log.info(
                "Order {} persisted with PENDING status",
                savedOrder.getOrderId()
        );

        CommerceEvent event =
                CommerceEvent.orderCreated(
                        savedOrder.getOrderId(),
                        savedOrder.getProductIds(),
                        savedOrder.getCustomerId(),
                        savedOrder.getTotalPrice(),
                        savedOrder.getCreditCardNumber()
                );

        eventPublisher.publish(
                CommerceEvent.ORDER_CREATED,
                event
        );

        log.info(
                "OrderCreated event published for {}",
                savedOrder.getOrderId()
        );

        return savedOrder;
    }

    public void handleInventoryReserved(String orderId) {

        Optional<Order> optionalOrder =
                orderRepository.findByOrderId(orderId);

        if (optionalOrder.isEmpty()) {
            log.warn(
                    "Order {} not found while processing InventoryReserved",
                    orderId
            );
            return;
        }

        Order order = optionalOrder.get();

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            return;
        }

        order.setInventoryReserved(true);

        orderRepository.save(order);

        log.info(
                "Inventory reserved for order {}",
                orderId
        );

        tryApproveOrder(order);
    }

    public void handleInventoryRejected(
            String orderId,
            String reason) {

        rejectOrder(
                orderId,
                "Inventory rejected: " + reason
        );
    }

    public void handlePaymentApproved(String orderId) {

        Optional<Order> optionalOrder =
                orderRepository.findByOrderId(orderId);

        if (optionalOrder.isEmpty()) {
            log.warn(
                    "Order {} not found while processing PaymentApproved",
                    orderId
            );
            return;
        }

        Order order = optionalOrder.get();

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            return;
        }

        order.setPaymentApproved(true);

        orderRepository.save(order);

        log.info(
                "Payment approved for order {}",
                orderId
        );

        tryApproveOrder(order);
    }

    public void handlePaymentRejected(
            String orderId,
            String reason) {

        rejectOrder(
                orderId,
                "Payment rejected: " + reason
        );
    }

    private void tryApproveOrder(Order order) {

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            return;
        }

        if (!order.isInventoryReserved()) {
            return;
        }

        if (!order.isPaymentApproved()) {
            return;
        }

        order.setStatus(Order.OrderStatus.APPROVED);

        orderRepository.save(order);

        log.info(
                "Order {} APPROVED",
                order.getOrderId()
        );

        eventPublisher.publish(
                CommerceEvent.ORDER_APPROVED,
                CommerceEvent.orderApproved(
                        order.getOrderId()
                )
        );
    }

    private void rejectOrder(
            String orderId,
            String reason) {

        Optional<Order> optionalOrder =
                orderRepository.findByOrderId(orderId);

        if (optionalOrder.isEmpty()) {
            log.warn(
                    "Order {} not found while rejecting",
                    orderId
            );
            return;
        }

        Order order = optionalOrder.get();

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            return;
        }

        order.setStatus(Order.OrderStatus.REJECTED);

        orderRepository.save(order);

        log.info(
                "Order {} REJECTED: {}",
                orderId,
                reason
        );

        eventPublisher.publish(
                CommerceEvent.ORDER_REJECTED,
                CommerceEvent.orderRejected(
                        orderId,
                        reason
                )
        );
    }

    public Order save(Order order) {
        return orderRepository.save(order);
    }

    public Iterable<Order> getOrders() {
        return orderRepository.findAll();
    }

    public Order getOrder(String orderId) {

        return orderRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new RuntimeException(
                                "Order not found: " + orderId
                        )
                );
    }

    public void deleteOrder(String orderId) {

        log.info(
                "Deleting order {}...",
                orderId
        );

        orderRepository
                .findByOrderId(orderId)
                .ifPresent(orderRepository::delete);
    }
}