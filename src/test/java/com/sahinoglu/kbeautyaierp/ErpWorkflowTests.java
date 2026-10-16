package com.sahinoglu.kbeautyaierp;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.sahinoglu.kbeautyaierp.customer.*;
import com.sahinoglu.kbeautyaierp.product.*;
import com.sahinoglu.kbeautyaierp.inventory.*;
import com.sahinoglu.kbeautyaierp.salesorder.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ErpWorkflowTests {
    @Autowired ProductService products;
    @Autowired CustomerService customers;
    @Autowired InventoryService inventory;
    @Autowired SalesOrderService orders;
    @Autowired EntityManager entityManager;

    private Long product() {
        return products.create(new ProductRequest("TEST-" + UUID.randomUUID(), "Test cream",
                "Test brand", ProductCategory.SKINCARE, new BigDecimal("125.50"), true)).id();
    }

    @Test
    void orderAndItemsPersistAndShipmentReducesStock() {
        Long productId = product();
        Long customerId = customers.create(new CustomerRequest("Test retailer", null,
                "Istanbul", null, null, true)).id();
        inventory.update(productId, new InventoryRequest(10, 3));
        var created = orders.create(new CreateSalesOrderRequest(customerId,
                List.of(new SalesOrderItemRequest(productId, 3))));
        entityManager.flush();
        entityManager.clear();

        var reloaded = orders.findById(created.id());
        assertThat(reloaded.items()).hasSize(1);
        assertThat(reloaded.items().getFirst().id()).isNotNull();
        assertThat(reloaded.totalAmount()).isEqualByComparingTo("376.50");
        assertThat(inventory.findByProductId(productId).quantity()).isEqualTo(10);
        orders.confirm(created.id());
        entityManager.flush();
        entityManager.clear();
        orders.ship(created.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(orders.findById(created.id()).status()).isEqualTo(SalesOrderStatus.SHIPPED);
        assertThat(inventory.findByProductId(productId).quantity()).isEqualTo(7);
        assertThat(orders.findAll()).extracting(SalesOrderResponse::id).contains(created.id());
    }

    @Test
    void lowStockIncludesEqualityAndExcludesStockAboveThreshold() {
        Long productId = product();
        inventory.update(productId, new InventoryRequest(3, 3));
        entityManager.flush();
        entityManager.clear();
        assertThat(inventory.findLowStock()).extracting(InventoryResponse::productId).contains(productId);
        inventory.update(productId, new InventoryRequest(4, 3));
        entityManager.flush();
        entityManager.clear();
        assertThat(inventory.findLowStock()).extracting(InventoryResponse::productId).doesNotContain(productId);
        inventory.update(productId, new InventoryRequest(0, 3));
        entityManager.flush();
        assertThat(inventory.findLowStock()).extracting(InventoryResponse::productId).contains(productId);
    }

    @Test
    void updatingInventoryKeepsOneRecordPerProduct() {
        Long productId = product();
        var first = inventory.update(productId, new InventoryRequest(10, 3));
        entityManager.flush();
        entityManager.clear();
        var second = inventory.update(productId, new InventoryRequest(5, 2));
        entityManager.flush();
        entityManager.clear();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(inventory.findAll().stream().filter(row -> row.productId().equals(productId))).hasSize(1);
        assertThat(inventory.findByProductId(productId).quantity()).isEqualTo(5);
    }
}
