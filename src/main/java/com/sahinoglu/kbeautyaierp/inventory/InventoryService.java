package com.sahinoglu.kbeautyaierp.inventory;

import java.util.List;
import com.sahinoglu.kbeautyaierp.product.Product;
import com.sahinoglu.kbeautyaierp.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private final InventoryRepository repository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository repository, ProductRepository productRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse findByProductId(Long productId) {
        return toResponse(repository.findByProductId(productId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for product " + productId)));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findLowStock() {
        return repository.findLowStock().stream().map(this::toResponse).toList();
    }

    @Transactional
    public InventoryResponse update(Long productId, InventoryRequest request) {
        if (request.quantity() == null || request.quantity() < 0
                || request.minimumStockLevel() == null || request.minimumStockLevel() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quantity and minimum stock level must be non-negative");
        }
        Product product = productRepository.findById(productId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        Inventory inventory = repository.findByProductId(productId).orElseGet(Inventory::new);
        inventory.setProduct(product);
        inventory.setQuantity(request.quantity());
        inventory.setMinimumStockLevel(request.minimumStockLevel());
        return toResponse(repository.save(inventory));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        Product product = inventory.getProduct();
        return new InventoryResponse(inventory.getId(), product.getId(), product.getSku(),
                product.getName(), inventory.getQuantity(), inventory.getMinimumStockLevel());
    }
}
