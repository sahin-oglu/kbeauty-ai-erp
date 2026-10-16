package com.sahinoglu.kbeautyaierp.salesorder;

import java.math.BigDecimal;

public record TopSellingProductResponse(Long productId, String sku, String productName, Long quantitySold,
		BigDecimal totalSales) {
}
