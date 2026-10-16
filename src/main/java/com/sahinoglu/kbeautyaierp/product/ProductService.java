package com.sahinoglu.kbeautyaierp.product;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<ProductResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public ProductResponse findById(Long id) {
        return toResponse(requireById(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (repository.existsBySku(request.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        Product entity = new Product();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product entity = requireById(id);
        if (repository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        applyRequest(entity, request);
        return toResponse(entity);
    }

    @Transactional
    public ProductResponse deactivate(Long id) {
        Product entity = requireById(id);
        entity.setActive(false);
        return toResponse(entity);
    }

    private Product requireById(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private void applyRequest(Product entity, ProductRequest request) {
        entity.setSku(request.sku());
        entity.setName(request.name());
        entity.setBrand(request.brand());
        entity.setCategory(request.category());
        entity.setUnitPrice(request.unitPrice());
        entity.setActive(request.active());
    }

    private ProductResponse toResponse(Product entity) {
        return new ProductResponse(entity.getId(),
                entity.getSku(), entity.getName(), entity.getBrand(), entity.getCategory(), entity.getUnitPrice(), entity.isActive());
    }
}
