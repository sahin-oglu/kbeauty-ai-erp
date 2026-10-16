package com.sahinoglu.kbeautyaierp.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    @Query("select i from Inventory i where i.quantity <= i.minimumStockLevel")
    List<Inventory> findLowStock();
}
