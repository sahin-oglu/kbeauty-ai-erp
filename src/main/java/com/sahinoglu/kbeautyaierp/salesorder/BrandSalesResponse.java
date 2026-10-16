package com.sahinoglu.kbeautyaierp.salesorder;

import java.math.BigDecimal;

public record BrandSalesResponse(Long productId, String sku, String productName, String brand,
        Long quantitySold, BigDecimal totalSales) {}
