package com.sahinoglu.kbeautyaierp.salesorder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.sahinoglu.kbeautyaierp.customer.Customer;
import com.sahinoglu.kbeautyaierp.customer.CustomerRepository;
import com.sahinoglu.kbeautyaierp.inventory.Inventory;
import com.sahinoglu.kbeautyaierp.inventory.InventoryRepository;
import com.sahinoglu.kbeautyaierp.product.Product;
import com.sahinoglu.kbeautyaierp.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOrderServiceTests {
    private final SalesOrderRepository orders = mock(SalesOrderRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final SalesOrderService service = new SalesOrderService(orders, customers, products, inventories);
    private Customer customer;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setActive(true);
        ReflectionTestUtils.setField(customer, "id", 1L);
        product = new Product();
        product.setActive(true);
        product.setUnitPrice(new BigDecimal("125.50"));
        ReflectionTestUtils.setField(product, "id", 2L);
        inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(10);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(products.findById(2L)).thenReturn(Optional.of(product));
        when(inventories.findByProductId(2L)).thenReturn(Optional.of(inventory));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateSalesOrderRequest request(Integer quantity) {
        return new CreateSalesOrderRequest(1L, List.of(new SalesOrderItemRequest(2L, quantity)));
    }

    private SalesOrder savedOrder() {
        var captor = org.mockito.ArgumentCaptor.forClass(SalesOrder.class);
        verify(orders).save(captor.capture());
        SalesOrder order = captor.getValue();
        when(orders.findById(3L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void creationCalculatesTotalsAndCopiesPriceWithoutReducingStock() {
        SalesOrderResponse response = service.create(request(3));
        SalesOrder order = savedOrder();
        product.setUnitPrice(new BigDecimal("200.00"));
        assertThat(response.status()).isEqualTo(SalesOrderStatus.CREATED);
        assertThat(response.totalAmount()).isEqualByComparingTo("376.50");
        assertThat(response.items().getFirst().lineTotal()).isEqualByComparingTo("376.50");
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("125.50");
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void totalsIncludeEveryProduct() {
        Product second = new Product();
        second.setActive(true);
        second.setUnitPrice(new BigDecimal("10.25"));
        ReflectionTestUtils.setField(second, "id", 4L);
        Inventory secondStock = new Inventory();
        secondStock.setQuantity(5);
        when(products.findById(4L)).thenReturn(Optional.of(second));
        when(inventories.findByProductId(4L)).thenReturn(Optional.of(secondStock));
        var response = service.create(new CreateSalesOrderRequest(1L, List.of(
                new SalesOrderItemRequest(2L, 2), new SalesOrderItemRequest(4L, 3))));
        assertThat(response.totalAmount()).isEqualByComparingTo("281.75");
    }

    @Test
    void inactiveCustomerCannotCreateOrder() {
        customer.setActive(false);
        assertThatThrownBy(() -> service.create(request(1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Customer is inactive");
        verify(orders, never()).save(any());
    }

    @Test
    void inactiveProductCannotBeOrdered() {
        product.setActive(false);
        assertThatThrownBy(() -> service.create(request(1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Product is inactive");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void quantityMustBePositive(Integer quantity) {
        assertThatThrownBy(() -> service.create(request(quantity)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("quantity must be positive");
    }

    @Test
    void emptyOrderIsRejected() {
        assertThatThrownBy(() -> service.create(new CreateSalesOrderRequest(1L, List.of())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("at least one item");
    }

    @Test
    void insufficientStockPreventsCreation() {
        assertThatThrownBy(() -> service.create(request(11)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Insufficient stock");
        verify(orders, never()).save(any());
    }

    @Test
    void repeatedProductsAreCombinedBeforeCheckingStock() {
        var request = new CreateSalesOrderRequest(1L, List.of(
                new SalesOrderItemRequest(2L, 6), new SalesOrderItemRequest(2L, 5)));
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Insufficient stock");
    }

    @Test
    void repeatedProductsBecomeOneLine() {
        var response = service.create(new CreateSalesOrderRequest(1L, List.of(
                new SalesOrderItemRequest(2L, 2), new SalesOrderItemRequest(2L, 3))));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualTo(5);
        assertThat(response.totalAmount()).isEqualByComparingTo("627.50");
    }

    @Test
    void missingInventoryPreventsCreation() {
        when(inventories.findByProductId(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Inventory missing");
    }

    @Test
    void shippingReducesStockExactlyOnce() {
        service.create(request(3));
        savedOrder();
        service.confirm(3L);
        assertThat(inventory.getQuantity()).isEqualTo(10);
        assertThat(service.ship(3L).status()).isEqualTo(SalesOrderStatus.SHIPPED);
        assertThat(inventory.getQuantity()).isEqualTo(7);
        assertThatThrownBy(() -> service.ship(3L)).isInstanceOf(ResponseStatusException.class);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void shippingChecksCurrentStock() {
        service.create(request(3));
        SalesOrder order = savedOrder();
        service.confirm(3L);
        inventory.setQuantity(2);
        assertThatThrownBy(() -> service.ship(3L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Insufficient stock");
        assertThat(inventory.getQuantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.CONFIRMED);
    }

    @Test
    void unavailableLaterLineDoesNotReduceEarlierStock() {
        service.create(request(3));
        SalesOrder order = savedOrder();
        Product second = new Product();
        ReflectionTestUtils.setField(second, "id", 4L);
        SalesOrderItem secondItem = new SalesOrderItem();
        secondItem.setProduct(second);
        secondItem.setQuantity(1);
        order.getItems().add(secondItem);
        order.setStatus(SalesOrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.ship(3L)).isInstanceOf(ResponseStatusException.class);
        assertThat(inventory.getQuantity()).isEqualTo(10);
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(value = SalesOrderStatus.class, names = {"CREATED", "CONFIRMED"})
    void cancellationLeavesStockUnchanged(SalesOrderStatus status) {
        service.create(request(3));
        savedOrder().setStatus(status);
        assertThat(service.cancel(3L).status()).isEqualTo(SalesOrderStatus.CANCELLED);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(value = SalesOrderStatus.class, names = {"SHIPPED", "CANCELLED"})
    void terminalOrdersCannotBeCancelled(SalesOrderStatus status) {
        service.create(request(3));
        savedOrder().setStatus(status);
        assertThatThrownBy(() -> service.cancel(3L)).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SalesOrderStatus.class, names = {"CONFIRMED", "SHIPPED", "CANCELLED"})
    void onlyCreatedOrdersCanBeConfirmed(SalesOrderStatus status) {
        service.create(request(1));
        savedOrder().setStatus(status);
        assertThatThrownBy(() -> service.confirm(3L)).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SalesOrderStatus.class, names = {"CREATED", "CANCELLED"})
    void onlyConfirmedOrdersCanBeShipped(SalesOrderStatus status) {
        service.create(request(1));
        savedOrder().setStatus(status);
        assertThatThrownBy(() -> service.ship(3L)).isInstanceOf(ResponseStatusException.class);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }
}
