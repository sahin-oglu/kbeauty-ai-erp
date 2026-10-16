package com.sahinoglu.kbeautyaierp.inventory;

import java.util.Optional;
import com.sahinoglu.kbeautyaierp.product.Product;
import com.sahinoglu.kbeautyaierp.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceTests {
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryService service = new InventoryService(inventory, products);

    @Test
    void firstUpdateCreatesStockForExistingProduct() {
        Product product = new Product();
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(inventory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.update(1L, new InventoryRequest(10, 3));
        assertThat(response.quantity()).isEqualTo(10);
        assertThat(response.minimumStockLevel()).isEqualTo(3);
    }

    @Test
    void updateReplacesQuantityOnExistingRecord() {
        Product product = new Product();
        Inventory existing = new Inventory();
        existing.setQuantity(10);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(inventory.findByProductId(1L)).thenReturn(Optional.of(existing));
        when(inventory.save(existing)).thenReturn(existing);
        var response = service.update(1L, new InventoryRequest(4, 2));
        assertThat(response.quantity()).isEqualTo(4);
        verify(inventory).save(existing);
    }

    @Test
    void missingProductCannotReceiveStock() {
        assertThatThrownBy(() -> service.update(1L, new InventoryRequest(1, 0)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Product not found");
        verify(inventory, never()).save(any());
    }

    @Test
    void negativeStockOrThresholdIsRejected() {
        assertThatThrownBy(() -> service.update(1L, new InventoryRequest(-1, 0)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.update(1L, new InventoryRequest(0, -1)))
                .isInstanceOf(ResponseStatusException.class);
        verify(inventory, never()).save(any());
    }
}
