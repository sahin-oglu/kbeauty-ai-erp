package com.sahinoglu.kbeautyaierp.ai;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sahinoglu.kbeautyaierp.inventory.InventoryResponse;
import com.sahinoglu.kbeautyaierp.salesorder.SalesOrderResponse;
import com.sahinoglu.kbeautyaierp.salesorder.TopSellingProductResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UiViewService {
    private final ErpTools tools;

    public UiViewService(ErpTools tools) {
        this.tools = tools;
    }

    public UiResponse render(UiPlan plan) {
        validate(plan);
        return switch (plan.source()) {
            case BRAND_SALES -> brandSalesView(plan);
            case LOW_STOCK -> stockView(plan, tools.getLowStockProducts());
            case PRODUCT_STOCK -> stockView(plan, List.of(tools.getProductStock(plan.id())));
            case CUSTOMER_ORDERS -> customerOrdersView(plan, tools.getCustomerOrders(plan.id()));
            case SALES_ORDER -> orderView(plan, tools.getSalesOrder(plan.id()));
            case TOP_SELLING -> topSellingView(plan, tools.getTopSellingProducts());
            case UNSUPPORTED -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported UI request");
        };
    }

    private String title(UiPlan plan) {
        return switch (plan.source()) {
            case BRAND_SALES -> "Brand: " + plan.brand().strip() + " · Shipped orders · All dates · TRY";
            case TOP_SELLING -> "Top 10 products · All brands · Shipped orders · All dates · TRY";
            case LOW_STOCK -> "Low stock products";
            case PRODUCT_STOCK -> "Stock for product " + plan.id();
            case CUSTOMER_ORDERS -> "Orders for customer " + plan.id() + " · All statuses · All dates · TRY";
            case SALES_ORDER -> "Sales order " + plan.id() + " · TRY";
            case UNSUPPORTED -> "Unsupported request";
        };
    }

    private void validate(UiPlan plan) {
        if (plan == null || plan.type() == null || plan.source() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned an invalid UI description");
        }
        if (plan.source() == UiDataSource.UNSUPPORTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Specify a complete request for low stock, product stock, customer orders, a sales order, or top-selling products");
        }
        if (plan.source() == UiDataSource.BRAND_SALES && (plan.brand() == null
                || plan.brand().isBlank() || plan.brand().length() > 120)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Specify a brand name");
        }
        if (plan.brand() != null && plan.source() != UiDataSource.BRAND_SALES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brand filters are supported only for brand sales reports");
        }
        boolean needsId = plan.source() == UiDataSource.PRODUCT_STOCK
                || plan.source() == UiDataSource.CUSTOMER_ORDERS || plan.source() == UiDataSource.SALES_ORDER;
        if (needsId && (plan.id() == null || plan.id() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Include a positive product, customer, or sales order ID in the question");
        }
        if (!needsId && plan.id() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned an unexpected ID");
        }
        if (plan.type() == UiType.FORM && plan.source() != UiDataSource.LOW_STOCK
                && plan.source() != UiDataSource.PRODUCT_STOCK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Forms currently support stock updates for low-stock products or one product ID");
        }
    }

    private UiResponse stockView(UiPlan plan, List<InventoryResponse> stock) {
        if (plan.type() == UiType.FORM) {
            List<UiResponse.StockForm> forms = stock.stream().map(item -> new UiResponse.StockForm(
                    item.productId(), item.productName(),
                    "Set the total stock quantity, not the amount to add. This form has not been submitted.",
                    List.of(new UiResponse.Field("quantity", "Total stock quantity", item.quantity()),
                            new UiResponse.Field("minimumStockLevel", "Minimum stock level", item.minimumStockLevel())))).toList();
            return new UiResponse(plan.type(), title(plan), plan.source(), null, null, null, null, null, forms);
        }
        if (plan.type() == UiType.METRIC) {
            return metric(plan, plan.source() == UiDataSource.LOW_STOCK ? BigDecimal.valueOf(stock.size())
                    : BigDecimal.valueOf(stock.getFirst().quantity()),
                    plan.source() == UiDataSource.LOW_STOCK ? "products" : "units");
        }
        List<Map<String, Object>> rows = stock.stream().map(item -> row(
                "productId", item.productId(), "sku", item.sku(), "name", item.productName(),
                "quantity", item.quantity(), "minimumStockLevel", item.minimumStockLevel())).toList();
        return rowsView(plan, List.of("productId", "sku", "name", "quantity", "minimumStockLevel"),
                rows, "sku", "quantity", "Stock quantity");
    }

    private UiResponse customerOrdersView(UiPlan plan, List<SalesOrderResponse> orders) {
        if (plan.type() == UiType.METRIC) {
            return metric(plan, BigDecimal.valueOf(orders.size()), "orders");
        }
        List<Map<String, Object>> rows = orders.stream().map(order -> row(
                "orderId", order.id(), "orderDate", order.orderDate(), "status", order.status(),
                "totalAmount", order.totalAmount())).toList();
        return rowsView(plan, List.of("orderId", "orderDate", "status", "totalAmount"), rows,
                "orderId", "totalAmount", "Order total (TRY)");
    }

    private UiResponse orderView(UiPlan plan, SalesOrderResponse order) {
        if (plan.type() == UiType.METRIC) {
            return metric(plan, order.totalAmount(), "TRY");
        }
        List<Map<String, Object>> rows = order.items().stream().map(item -> row(
                "productId", item.productId(), "name", item.productName(), "quantity", item.quantity(),
                "unitPrice", item.unitPrice(), "lineTotal", item.lineTotal())).toList();
        return rowsView(plan, List.of("productId", "name", "quantity", "unitPrice", "lineTotal"), rows,
                "name", "lineTotal", "Line total (TRY)");
    }

    private UiResponse brandSalesView(UiPlan plan) {
        var products = tools.getBrandSales(plan.brand());
        if (plan.type() == UiType.METRIC) {
            return metric(plan, products.stream().map(p -> p.totalSales())
                    .reduce(BigDecimal.ZERO, BigDecimal::add), "TRY");
        }
        var rows = products.stream().map(p -> row("productId", p.productId(), "sku", p.sku(),
                "name", p.productName(), "brand", p.brand(), "quantitySold", p.quantitySold(),
                "totalSales", p.totalSales())).toList();
        return rowsView(plan, List.of("productId", "sku", "name", "brand", "quantitySold", "totalSales"),
                rows, "name", "quantitySold", "Units shipped");
    }

    private UiResponse topSellingView(UiPlan plan, List<TopSellingProductResponse> products) {
        if (plan.type() == UiType.METRIC) {
            BigDecimal units = products.stream().map(product -> BigDecimal.valueOf(product.quantitySold()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return metric(plan, units, "units shipped across the top 10 products");
        }
        List<Map<String, Object>> rows = products.stream().map(product -> row(
                "productId", product.productId(), "sku", product.sku(), "name", product.productName(),
                "quantitySold", product.quantitySold(), "totalSales", product.totalSales())).toList();
        return rowsView(plan, List.of("productId", "sku", "name", "quantitySold", "totalSales"), rows,
                "sku", "quantitySold", "Units shipped (all dates)");
    }

    private UiResponse rowsView(UiPlan plan, List<String> columns, List<Map<String, Object>> rows,
            String xKey, String yKey, String yLabel) {
        if (plan.type() == UiType.CHART) {
            return new UiResponse(plan.type(), title(plan), plan.source(), null, rows,
                    new UiResponse.Chart("bar", xKey, yKey, yLabel), null, null, null);
        }
        return new UiResponse(plan.type(), title(plan), plan.source(), columns, rows, null, null, null, null);
    }

    private UiResponse metric(UiPlan plan, BigDecimal value, String unit) {
        return new UiResponse(plan.type(), title(plan), plan.source(), null, null, null, value, unit, null);
    }

    private Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            row.put((String) entries[i], entries[i + 1]);
        }
        return row;
    }
}
