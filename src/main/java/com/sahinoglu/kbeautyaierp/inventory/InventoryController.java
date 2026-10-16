package com.sahinoglu.kbeautyaierp.inventory;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<InventoryResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/low-stock")
    public List<InventoryResponse> findLowStock() {
        return service.findLowStock();
    }

    @GetMapping("/{productId}")
    public InventoryResponse findByProductId(@PathVariable Long productId) {
        return service.findByProductId(productId);
    }

    @PutMapping("/{productId}")
    public InventoryResponse update(@PathVariable Long productId, @Valid @RequestBody InventoryRequest request) {
        return service.update(productId, request);
    }
}
