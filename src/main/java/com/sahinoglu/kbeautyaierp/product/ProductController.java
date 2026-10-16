package com.sahinoglu.kbeautyaierp.product;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + response.id())).body(response);
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    public ProductResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
