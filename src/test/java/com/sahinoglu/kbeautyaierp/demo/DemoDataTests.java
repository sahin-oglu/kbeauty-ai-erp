package com.sahinoglu.kbeautyaierp.demo;

import com.sahinoglu.kbeautyaierp.product.*;
import com.sahinoglu.kbeautyaierp.customer.*;
import com.sahinoglu.kbeautyaierp.inventory.InventoryService;
import com.sahinoglu.kbeautyaierp.salesorder.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;

class DemoDataTests {
    @ParameterizedTest
    @ValueSource(strings = {"products", "customers", "orders"})
    void existingRecordsPreventDemoWrites(String existing) {
        var productRepository = mock(ProductRepository.class);
        var customerRepository = mock(CustomerRepository.class);
        var orderRepository = mock(SalesOrderRepository.class);
        var products = mock(ProductService.class);
        var customers = mock(CustomerService.class);
        var inventory = mock(InventoryService.class);
        var orders = mock(SalesOrderService.class);
        switch (existing) {
            case "products" -> when(productRepository.count()).thenReturn(1L);
            case "customers" -> when(customerRepository.count()).thenReturn(1L);
            case "orders" -> when(orderRepository.count()).thenReturn(1L);
        }
        new DemoData(productRepository, customerRepository, orderRepository,
                products, customers, inventory, orders).run(null);
        verifyNoInteractions(products, customers, inventory, orders);
    }
}
