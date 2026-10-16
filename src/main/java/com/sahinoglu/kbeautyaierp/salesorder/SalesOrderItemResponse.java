package com.sahinoglu.kbeautyaierp.salesorder;

import java.math.BigDecimal;

public record SalesOrderItemResponse(
        Long id, Long productId, String productName, Integer quantity,
        BigDecimal unitPrice, BigDecimal lineTotal) {
}
