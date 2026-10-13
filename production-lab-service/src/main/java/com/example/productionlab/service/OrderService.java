package com.example.productionlab.service;

import com.example.productionlab.model.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Fixed, in-memory sample data. Deterministic so that responses are identical
 * across replicas and restarts, which keeps lab observations comparable.
 */
@Service
public class OrderService {

    private static final List<Order> ORDERS = List.of(
            new Order("1001", "cust-001", "CREATED", new BigDecimal("49.99"), "EUR"),
            new Order("1002", "cust-002", "PAID", new BigDecimal("120.00"), "EUR"),
            new Order("1003", "cust-001", "SHIPPED", new BigDecimal("15.50"), "EUR"));

    public List<Order> findAll() {
        return ORDERS;
    }

    public Optional<Order> findById(String id) {
        return ORDERS.stream().filter(order -> order.id().equals(id)).findFirst();
    }
}
