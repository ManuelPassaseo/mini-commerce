package com.mpassaseo.controller;

import com.mpassaseo.persistence.model.Order;
import com.mpassaseo.service.OrderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public Order createOrder(@RequestBody Order order) {
        return orderService.placeOrder(order);
    }

    @GetMapping("/orders")
    public Iterable<Order> getOrders() {
        return orderService.getOrders();
    }

    @GetMapping("/orders/{orderId}")
    public Order getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId);
    }

    @DeleteMapping("/orders/{orderId}")
    public void deleteOrder(@PathVariable String orderId) {
        orderService.deleteOrder(orderId);
    }

    /*
     * Endpoint mantenuto per compatibilità con il frontend attuale.
     * Anche questo usa esclusivamente RabbitMQ per la comunicazione interna.
     */
    @PostMapping("/order")
    public Order triggerOrderFlow(@RequestBody Order order) {
        return orderService.placeOrder(order);
    }
}