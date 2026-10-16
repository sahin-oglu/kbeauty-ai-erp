package com.sahinoglu.kbeautyaierp;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.sahinoglu.kbeautyaierp.customer.*;
import com.sahinoglu.kbeautyaierp.product.*;
import com.sahinoglu.kbeautyaierp.inventory.*;
import com.sahinoglu.kbeautyaierp.salesorder.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ErpReportingTests {
    @Autowired ProductService products;
    @Autowired CustomerService customers;
    @Autowired InventoryService inventory;
    @Autowired SalesOrderService orders;
    @Autowired SalesOrderRepository orderRepository;

    private Long product() {
        Long id = products.create(new ProductRequest("TEST-" + UUID.randomUUID(), "Reporting cream",
                "Test", ProductCategory.SKINCARE, new BigDecimal("12.50"), true)).id();
        inventory.update(id, new InventoryRequest(100, 3));
        return id;
    }

    private Long customer() {
        return customers.create(new CustomerRequest("Test retailer", null, "Istanbul", null, null, true)).id();
    }

    private SalesOrderResponse order(Long customerId, Long productId, int quantity) {
        return orders.create(new CreateSalesOrderRequest(customerId,
                List.of(new SalesOrderItemRequest(productId, quantity))));
    }

    @Test
    void customerQueryIncludesOnlyTheirOrdersNewestFirst() {
        Long productId = product();
        Long firstCustomer = customer();
        Long secondCustomer = customer();
        var first = order(firstCustomer, productId, 1);
        var second = order(firstCustomer, productId, 2);
        order(secondCustomer, productId, 3);
        assertThat(orders.findByCustomerId(firstCustomer)).extracting(SalesOrderResponse::id)
                .containsExactly(second.id(), first.id());
    }

    @Test
    void customerWithNoOrdersIsDifferentFromMissingCustomer() {
        assertThat(orders.findByCustomerId(customer())).isEmpty();
        assertThatThrownBy(() -> orders.findByCustomerId(Long.MAX_VALUE))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Customer not found");
    }

    @Test
    void salesRankingCountsOnlyShippedOrdersAndUsesCopiedPrices() {
        Long customerId = customer();
        Long productId = product();
        var shipped = order(customerId, productId, 2);
        orders.confirm(shipped.id());
        orders.ship(shipped.id());
        var anotherShipped = order(customerId, productId, 3);
        orders.confirm(anotherShipped.id());
        orders.ship(anotherShipped.id());
        order(customerId, productId, 4);
        var confirmed = order(customerId, productId, 6);
        orders.confirm(confirmed.id());
        var cancelled = order(customerId, productId, 8);
        orders.cancel(cancelled.id());
        var originalProduct = products.findById(productId);
        products.update(productId, new ProductRequest(originalProduct.sku(), originalProduct.name(),
                originalProduct.brand(), originalProduct.category(), new BigDecimal("100.00"), true));

        // Query the report before test data is rolled back.
        var ranking = orderRepository.findTopSellingProducts(SalesOrderStatus.SHIPPED,
                org.springframework.data.domain.Pageable.unpaged());
        assertThat(ranking).extracting(TopSellingProductResponse::quantitySold)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(orders.findTopSellingProducts()).hasSizeLessThanOrEqualTo(10);
        var row = ranking.stream()
                .filter(result -> result.productId().equals(productId)).findFirst().orElseThrow();
        assertThat(row.quantitySold()).isEqualTo(5);
        assertThat(row.totalSales()).isEqualByComparingTo("62.50");
        var brandRows = orders.findBrandSales("test");
        assertThat(brandRows).allMatch(item -> item.brand().equalsIgnoreCase("Test"));
        var brandRow = brandRows.stream().filter(item -> item.productId().equals(productId)).findFirst().orElseThrow();
        assertThat(brandRow.quantitySold()).isEqualTo(5);
        assertThat(brandRow.totalSales()).isEqualByComparingTo("62.50");
    }
    @Test
    void brandReportExcludesOtherBrandsAndDoesNotLimitToTen() {
        String brand = "Brand-" + UUID.randomUUID();
        Long customerId = customer();
        for (int i = 0; i < 11; i++) {
            var p = products.create(new ProductRequest("TEST-" + UUID.randomUUID(), "Cream " + i,
                    brand, ProductCategory.SKINCARE, new BigDecimal("2.00"), true));
            inventory.update(p.id(), new InventoryRequest(10, 0));
            var sale = order(customerId, p.id(), 1);
            orders.confirm(sale.id());
            orders.ship(sale.id());
        }
        var other = order(customerId, product(), 2);
        orders.confirm(other.id());
        orders.ship(other.id());
        var rows = orders.findBrandSales(brand.toLowerCase(java.util.Locale.ROOT));
        assertThat(rows).hasSize(11).allMatch(row -> row.brand().equals(brand));
        assertThat(rows.stream().map(BrandSalesResponse::totalSales).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("22.00");
        assertThatThrownBy(() -> orders.findBrandSales("Missing-" + UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unknown brand");
    }

}
