package com.sahinoglu.kbeautyaierp.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventoryRequest(
        @NotNull @PositiveOrZero Integer quantity,
        @NotNull @PositiveOrZero Integer minimumStockLevel) {
}
