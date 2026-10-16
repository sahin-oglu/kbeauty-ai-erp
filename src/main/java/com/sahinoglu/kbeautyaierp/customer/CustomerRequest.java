package com.sahinoglu.kbeautyaierp.customer;

import jakarta.validation.constraints.*;

public record CustomerRequest(
        @NotBlank String companyName,
        String taxNumber,
        String city,
        @Email String email,
        String phone,
        boolean active) {
}
