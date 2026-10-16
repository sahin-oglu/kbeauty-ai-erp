package com.sahinoglu.kbeautyaierp.demo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.sahinoglu.kbeautyaierp.product.*;
import com.sahinoglu.kbeautyaierp.customer.*;
import com.sahinoglu.kbeautyaierp.inventory.*;
import com.sahinoglu.kbeautyaierp.salesorder.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoData.class);
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SalesOrderRepository orderRepository;
    private final ProductService products;
    private final CustomerService customers;
    private final InventoryService inventory;
    private final SalesOrderService orders;

    public DemoData(ProductRepository productRepository, CustomerRepository customerRepository,
            SalesOrderRepository orderRepository, ProductService products, CustomerService customers,
            InventoryService inventory, SalesOrderService orders) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.products = products;
        this.customers = customers;
        this.inventory = inventory;
        this.orders = orders;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (productRepository.count() > 0 || customerRepository.count() > 0 || orderRepository.count() > 0) {
            log.info("Demo data skipped: database already contains records.");
            return;
        }
        String[] names = {"Snail Cream", "BB Cream", "Bifida Serum", "Skin Toner",
                "Daily Sunscreen", "Foam Cleanser", "Sheet Mask", "Body Lotion"};
        String[] prices = {"650.00", "450.00", "800.00", "350.00", "550.00", "300.00", "90.00", "400.00"};
        ProductCategory[] categories = {ProductCategory.SKINCARE, ProductCategory.MAKEUP,
                ProductCategory.SKINCARE, ProductCategory.SKINCARE, ProductCategory.SUNCARE,
                ProductCategory.SKINCARE, ProductCategory.SKINCARE, ProductCategory.BODYCARE};
        List<Long> productIds = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            var product = products.create(new ProductRequest("DEMO-" + (i + 1), names[i],
                    i < 4 ? "Example Beauty" : "Sample Seoul", categories[i], new BigDecimal(prices[i]), true));
            productIds.add(product.id());
            inventory.update(product.id(), new InventoryRequest(100, 10));
        }
        String[] cities = {"Istanbul", "Ankara", "Izmir", "Bursa"};
        List<Long> customerIds = new ArrayList<>();
        for (int i = 0; i < cities.length; i++) {
            customerIds.add(customers.create(new CustomerRequest("Demo Shop " + (i + 1), null,
                    cities[i], "shop" + (i + 1) + "@example.com", null, true)).id());
        }
        // Eight shipped orders give both brands sales; four others cover the remaining states.
        for (int i = 0; i < 12; i++) {
            var order = orders.create(new CreateSalesOrderRequest(customerIds.get(i % 4), List.of(
                    new SalesOrderItemRequest(productIds.get(i % 8), 2 + i % 5),
                    new SalesOrderItemRequest(productIds.get((i + 1) % 8), 1))));
            if (i < 8) {
                orders.confirm(order.id());
                orders.ship(order.id());
            } else if (i == 8 || i == 9) {
                orders.confirm(order.id());
            } else if (i == 11) {
                orders.cancel(order.id());
            }
        }
        // Set the demo's final balances after shipments, including zero and threshold cases.
        int[] balances = {4, 10, 0, 25, 3, 60, 10, 90};
        for (int i = 0; i < productIds.size(); i++) {
            inventory.update(productIds.get(i), new InventoryRequest(balances[i], 10));
        }
        customers.deactivate(customerIds.getLast());
        log.info("Demo data created: 8 products, 4 customers, 12 orders. All names and data are fictional.");
    }
}
