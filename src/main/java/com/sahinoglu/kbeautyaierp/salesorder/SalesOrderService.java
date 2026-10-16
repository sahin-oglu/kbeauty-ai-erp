package com.sahinoglu.kbeautyaierp.salesorder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sahinoglu.kbeautyaierp.customer.Customer;
import com.sahinoglu.kbeautyaierp.customer.CustomerRepository;
import com.sahinoglu.kbeautyaierp.inventory.Inventory;
import com.sahinoglu.kbeautyaierp.inventory.InventoryRepository;
import com.sahinoglu.kbeautyaierp.product.Product;
import com.sahinoglu.kbeautyaierp.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesOrderService {
    private final SalesOrderRepository repository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public SalesOrderService(SalesOrderRepository repository, CustomerRepository customerRepository,
            ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse findById(Long id) {
        return toResponse(requireById(id));
    }

    @Transactional(readOnly = true)
    public List<SalesOrderResponse> findByCustomerId(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return repository.findByCustomerIdOrderByOrderDateDescIdDesc(customerId).stream().map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BrandSalesResponse> findBrandSales(String brand) {
        if (brand == null || brand.isBlank() || brand.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Specify a brand name of at most 120 characters");
        }
        if (!productRepository.existsByBrandIgnoreCase(brand.strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown brand: " + brand.strip());
        }
        return repository.findBrandSales(brand.strip(), SalesOrderStatus.SHIPPED);
    }

    @Transactional(readOnly = true)
    public List<TopSellingProductResponse> findTopSellingProducts() {
        return repository.findTopSellingProducts(SalesOrderStatus.SHIPPED, PageRequest.of(0, 10));
    }

    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request) {
        if (request.customerId() == null || request.customerId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer ID must be positive");
        }
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!customer.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer is inactive");
        }
        Map<Long, Integer> quantities = combineItems(request.items());
        SalesOrder order = new SalesOrder();
        order.setCustomer(customer);
        order.setOrderDate(LocalDate.now());
        order.setStatus(SalesOrderStatus.CREATED);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            Product product = productRepository.findById(productId).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId));
            if (!product.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is inactive: " + productId);
            }
            requireStock(productId, entry.getValue());
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(order);
            item.setProduct(product);
            item.setQuantity(entry.getValue());
            item.setUnitPrice(product.getUnitPrice());
            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            order.getItems().add(item);
            total = total.add(item.getLineTotal());
        }
        order.setTotalAmount(total);
        return toResponse(repository.save(order));
    }

    @Transactional
    public SalesOrderResponse confirm(Long id) {
        SalesOrder order = requireById(id);
        requireStatus(order, SalesOrderStatus.CREATED);
        order.setStatus(SalesOrderStatus.CONFIRMED);
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse ship(Long id) {
        SalesOrder order = requireById(id);
        requireStatus(order, SalesOrderStatus.CONFIRMED);
        Map<Inventory, Integer> stockChanges = new LinkedHashMap<>();
        // Check every line before changing stock; the transaction also makes shipment
        // atomic.
        for (SalesOrderItem item : order.getItems()) {
            Inventory inventory = requireStock(item.getProduct().getId(), item.getQuantity());
            stockChanges.put(inventory, item.getQuantity());
        }
        stockChanges.forEach((inventory, quantity) -> inventory.setQuantity(inventory.getQuantity() - quantity));
        order.setStatus(SalesOrderStatus.SHIPPED);
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrder order = requireById(id);
        if (order.getStatus() != SalesOrderStatus.CREATED && order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only created or confirmed orders can be cancelled");
        }
        order.setStatus(SalesOrderStatus.CANCELLED);
        return toResponse(order);
    }

    private Map<Long, Integer> combineItems(List<SalesOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (SalesOrderItemRequest item : items) {
            if (item == null || item.productId() == null || item.productId() <= 0 || item.quantity() == null
                    || item.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID and quantity must be positive");
            }
            long combined = (long) quantities.getOrDefault(item.productId(), 0) + item.quantity();
            if (combined > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combined quantity is too large");
            }
            quantities.put(item.productId(), (int) combined);
        }
        return quantities;
    }

    private Inventory requireStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "Inventory missing for product " + productId));
        if (inventory.getQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for product " + productId);
        }
        return inventory;
    }

    private SalesOrder requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order not found"));
    }

    private void requireStatus(SalesOrder order, SalesOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order must be " + expected + " but is " + order.getStatus());
        }
    }

    private SalesOrderResponse toResponse(SalesOrder order) {
        List<SalesOrderItemResponse> items = order.getItems().stream()
                .map(item -> new SalesOrderItemResponse(item.getId(), item.getProduct().getId(),
                        item.getProduct().getName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
                .toList();
        return new SalesOrderResponse(order.getId(), order.getCustomer().getId(), order.getCustomer().getCompanyName(),
                order.getOrderDate(), order.getStatus(), order.getTotalAmount(), items);
    }
}
