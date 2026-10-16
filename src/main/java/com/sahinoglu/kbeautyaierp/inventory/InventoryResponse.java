package com.sahinoglu.kbeautyaierp.inventory;

public record InventoryResponse(
        Long id, Long productId, String sku, String productName,
        Integer quantity, Integer minimumStockLevel) {
}
