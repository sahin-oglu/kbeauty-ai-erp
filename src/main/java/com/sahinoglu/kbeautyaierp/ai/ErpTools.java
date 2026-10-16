package com.sahinoglu.kbeautyaierp.ai;

import java.util.List;
import com.sahinoglu.kbeautyaierp.inventory.InventoryResponse;
import com.sahinoglu.kbeautyaierp.inventory.InventoryService;
import com.sahinoglu.kbeautyaierp.salesorder.SalesOrderResponse;
import com.sahinoglu.kbeautyaierp.salesorder.SalesOrderService;
import com.sahinoglu.kbeautyaierp.salesorder.TopSellingProductResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ErpTools {
	private final InventoryService inventoryService;
	private final SalesOrderService salesOrderService;

	public ErpTools(InventoryService inventoryService, SalesOrderService salesOrderService) {
		this.inventoryService = inventoryService;
		this.salesOrderService = salesOrderService;
	}

	@Tool(description = "List products whose recorded stock quantity is at or below their minimum stock level.")
	public List<InventoryResponse> getLowStockProducts() {
		return inventoryService.findLowStock();
	}

	@Tool(description = "Get the current recorded stock and minimum stock level for one product by its ID.")
	public InventoryResponse getProductStock(@ToolParam(description = "Positive product ID") Long productId) {
		requirePositiveId(productId);
		return inventoryService.findByProductId(productId);
	}

	@Tool(description = "List all orders for a customer by ID, newest first, including every order status.")
	public List<SalesOrderResponse> getCustomerOrders(
			@ToolParam(description = "Positive customer ID") Long customerId) {
		requirePositiveId(customerId);
		return salesOrderService.findByCustomerId(customerId);
	}

	@Tool(description = "Get a sales order by ID, including its status, items, copied prices, and calculated total.")
	public SalesOrderResponse getSalesOrder(@ToolParam(description = "Positive sales order ID") Long id) {
		requirePositiveId(id);
		return salesOrderService.findById(id);
	}

	@Tool(description = "Get the top 10 products by units sold across all dates. Only SHIPPED orders count. "
			+ "Includes units sold and sales totals from order prices. Ties are ordered by product ID.")
	public List<TopSellingProductResponse> getTopSellingProducts() {
		return salesOrderService.findTopSellingProducts();
	}

    @Tool(description = "Sales for one exact brand, ignoring case, across all dates and SHIPPED orders only. "
            + "One row per sold product, including brand, units and sales amount in TRY. No top-10 limit.")
    public List<com.sahinoglu.kbeautyaierp.salesorder.BrandSalesResponse> getBrandSales(String brand) {
        return salesOrderService.findBrandSales(brand);
    }

	private void requirePositiveId(Long id) {
		if (id == null || id <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID must be positive");
		}
	}
}
