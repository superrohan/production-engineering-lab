package com.example.productionlab.model;

import java.math.BigDecimal;

public record Order(String id, String customerId, String status, BigDecimal totalAmount, String currency) {
}
