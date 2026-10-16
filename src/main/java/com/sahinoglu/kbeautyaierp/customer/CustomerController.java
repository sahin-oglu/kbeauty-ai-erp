package com.sahinoglu.kbeautyaierp.customer;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
	private final CustomerService service;

	public CustomerController(CustomerService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
		CustomerResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/api/customers/" + response.id())).body(response);
	}

	@GetMapping
	public List<CustomerResponse> findAll() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public CustomerResponse findById(@PathVariable Long id) {
		return service.findById(id);
	}

	@PutMapping("/{id}")
	public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
		return service.update(id, request);
	}

	@PatchMapping("/{id}/deactivate")
	public CustomerResponse deactivate(@PathVariable Long id) {
		return service.deactivate(id);
	}
}
