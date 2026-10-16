package com.sahinoglu.kbeautyaierp.product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String brand,
        ProductCategory category,
        BigDecimal unitPrice,
        boolean active) {
}
