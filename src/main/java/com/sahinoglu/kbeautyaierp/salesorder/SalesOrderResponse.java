package com.sahinoglu.kbeautyaierp.salesorder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        Long id, Long customerId, String companyName, LocalDate orderDate,
        SalesOrderStatus status, BigDecimal totalAmount, List<SalesOrderItemResponse> items) {
}
