package com.sahinoglu.kbeautyaierp.salesorder;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    @Query("""
            select new com.sahinoglu.kbeautyaierp.salesorder.BrandSalesResponse(
                p.id, p.sku, p.name, p.brand, sum(i.quantity), sum(i.lineTotal))
            from SalesOrderItem i join i.product p
            where i.salesOrder.status = :status and lower(p.brand) = lower(:brand)
            group by p.id, p.sku, p.name, p.brand
            order by sum(i.quantity) desc, p.id asc
            """)
    List<BrandSalesResponse> findBrandSales(String brand, SalesOrderStatus status);

    List<SalesOrder> findByCustomerIdOrderByOrderDateDescIdDesc(Long customerId);

    @Query("""
            select new com.sahinoglu.kbeautyaierp.salesorder.TopSellingProductResponse(
                p.id, p.sku, p.name, sum(i.quantity), sum(i.lineTotal))
            from SalesOrderItem i join i.product p
            where i.salesOrder.status = :status
            group by p.id, p.sku, p.name
            order by sum(i.quantity) desc, p.id asc
            """)
    List<TopSellingProductResponse> findTopSellingProducts(SalesOrderStatus status, Pageable pageable);
}
