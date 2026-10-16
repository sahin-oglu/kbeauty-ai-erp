package com.sahinoglu.kbeautyaierp.product;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record ProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotBlank String brand,
        @NotNull ProductCategory category,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
        boolean active) {
}
