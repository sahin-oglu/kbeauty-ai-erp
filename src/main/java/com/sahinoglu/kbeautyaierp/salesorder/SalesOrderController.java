package com.sahinoglu.kbeautyaierp.salesorder;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {
    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SalesOrderResponse> create(@Valid @RequestBody CreateSalesOrderRequest request) {
        SalesOrderResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/sales-orders/" + response.id())).body(response);
    }

    @GetMapping
    public List<SalesOrderResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public SalesOrderResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PatchMapping("/{id}/confirm")
    public SalesOrderResponse confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PatchMapping("/{id}/ship")
    public SalesOrderResponse ship(@PathVariable Long id) {
        return service.ship(id);
    }

    @PatchMapping("/{id}/cancel")
    public SalesOrderResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
