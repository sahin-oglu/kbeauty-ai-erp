package com.sahinoglu.kbeautyaierp.customer;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerService {
    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<CustomerResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public CustomerResponse findById(Long id) {
        return toResponse(requireById(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer entity = new Customer();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer entity = requireById(id);
        applyRequest(entity, request);
        return toResponse(entity);
    }

    @Transactional
    public CustomerResponse deactivate(Long id) {
        Customer entity = requireById(id);
        entity.setActive(false);
        return toResponse(entity);
    }

    private Customer requireById(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private void applyRequest(Customer entity, CustomerRequest request) {
        entity.setCompanyName(request.companyName());
        entity.setTaxNumber(request.taxNumber());
        entity.setCity(request.city());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setActive(request.active());
    }

    private CustomerResponse toResponse(Customer entity) {
        return new CustomerResponse(entity.getId(),
                entity.getCompanyName(), entity.getTaxNumber(), entity.getCity(), entity.getEmail(), entity.getPhone(), entity.isActive());
    }
}
