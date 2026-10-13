package com.example.productionlab.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceTest {

    private final OrderService orderService = new OrderService();

    @Test
    void findAllReturnsDeterministicSampleOrders() {
        assertThat(orderService.findAll())
                .extracting("id")
                .containsExactly("1001", "1002", "1003");
    }

    @Test
    void findByIdReturnsOrderWhenPresent() {
        assertThat(orderService.findById("1002"))
                .hasValueSatisfying(order -> assertThat(order.status()).isEqualTo("PAID"));
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        assertThat(orderService.findById("9999")).isEmpty();
    }
}
