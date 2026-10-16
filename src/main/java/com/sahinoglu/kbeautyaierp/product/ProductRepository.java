package com.sahinoglu.kbeautyaierp.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByBrandIgnoreCase(String brand);
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
}
