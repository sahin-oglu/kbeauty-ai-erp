package com.sahinoglu.kbeautyaierp.customer;


public record CustomerResponse(
        Long id,
        String companyName,
        String taxNumber,
        String city,
        String email,
        String phone,
        boolean active) {
}
