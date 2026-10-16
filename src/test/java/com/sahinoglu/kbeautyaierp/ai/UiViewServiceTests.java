package com.sahinoglu.kbeautyaierp.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.sahinoglu.kbeautyaierp.inventory.InventoryResponse;
import com.sahinoglu.kbeautyaierp.salesorder.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UiViewServiceTests {
    private final ErpTools tools = mock(ErpTools.class);
    private final UiViewService service = new UiViewService(tools);
    private final InventoryResponse stock = new InventoryResponse(1L, 27L, "TG-BIFIDA", "Bifida Serum", 0, 10);

    @Test
    void tableContainsRealStockWithNamedColumns() {
        when(tools.getLowStockProducts()).thenReturn(List.of(stock));
        var response = service.render(new UiPlan(UiType.TABLE, UiDataSource.LOW_STOCK, null));
        assertThat(response.columns()).containsExactly("productId", "sku", "name", "quantity", "minimumStockLevel");
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().getFirst()).containsEntry("quantity", 0).containsEntry("productId", 27L);
        assertThat(response.chart()).isNull();
    }

    @Test
    void chartAxesReferenceRealRowKeys() {
        when(tools.getLowStockProducts()).thenReturn(List.of(stock));
        var response = service.render(new UiPlan(UiType.CHART, UiDataSource.LOW_STOCK, null));
        assertThat(response.chart().kind()).isEqualTo("bar");
        assertThat(response.rows().getFirst()).containsKeys(response.chart().xKey(), response.chart().yKey());
        assertThat(response.rows().getFirst().get(response.chart().yKey())).isEqualTo(0);
        assertThat(response.columns()).isNull();
    }

    @Test
    void metricsCountProductsAndReadActualStock() {
        when(tools.getLowStockProducts()).thenReturn(List.of(stock));
        when(tools.getProductStock(27L)).thenReturn(stock);
        assertThat(service.render(new UiPlan(UiType.METRIC, UiDataSource.LOW_STOCK, null)).value())
                .isEqualByComparingTo("1");
        assertThat(service.render(new UiPlan(UiType.METRIC, UiDataSource.PRODUCT_STOCK, 27L)).value())
                .isEqualByComparingTo("0");
    }

    @Test
    void formPrefillsExistingValuesWithoutWritingAnything() {
        when(tools.getLowStockProducts()).thenReturn(List.of(stock));
        var response = service.render(new UiPlan(UiType.FORM, UiDataSource.LOW_STOCK, null));
        var form = response.forms().getFirst();
        assertThat(form.productId()).isEqualTo(27L);
        assertThat(form.fields()).extracting(UiResponse.Field::name).containsExactly("quantity", "minimumStockLevel");
        assertThat(form.fields()).extracting(UiResponse.Field::value).containsExactly(0, 10);
        verify(tools).getLowStockProducts();
        verifyNoMoreInteractions(tools);
    }

    @ParameterizedTest
    @EnumSource(UiType.class)
    void emptyInventoryRemainsEmpty(UiType type) {
        when(tools.getLowStockProducts()).thenReturn(List.of());
        var response = service.render(new UiPlan(type, UiDataSource.LOW_STOCK, null));
        if (type == UiType.METRIC) assertThat(response.value()).isEqualByComparingTo("0");
        else if (type == UiType.FORM) assertThat(response.forms()).isEmpty();
        else assertThat(response.rows()).isEmpty();
    }

    @Test
    void invalidFormAndMissingIdsAreRejectedBeforeFetchingData() {
        assertThatThrownBy(() -> service.render(new UiPlan(UiType.FORM, UiDataSource.SALES_ORDER, 38L)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.render(new UiPlan(UiType.TABLE, UiDataSource.CUSTOMER_ORDERS, null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(tools);
    }

    @Test
    void missingStockStaysNotFoundInsteadOfBecomingZero() {
        when(tools.getProductStock(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));
        assertThatThrownBy(() -> service.render(new UiPlan(UiType.METRIC, UiDataSource.PRODUCT_STOCK, 99L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void salesOrderMetricUsesStoredTotalAndTableUsesStoredLines() {
        var order = new SalesOrderResponse(38L, 22L, "Shop", LocalDate.of(2026, 10, 3), SalesOrderStatus.SHIPPED,
                new BigDecimal("125.50"), List.of(new SalesOrderItemResponse(1L, 27L, "Serum", 2,
                        new BigDecimal("62.75"), new BigDecimal("125.50"))));
        when(tools.getSalesOrder(38L)).thenReturn(order);
        when(tools.getCustomerOrders(22L)).thenReturn(List.of(order));
        assertThat(service.render(new UiPlan(UiType.METRIC, UiDataSource.SALES_ORDER, 38L)).value())
                .isEqualByComparingTo("125.50");
        assertThat(service.render(new UiPlan(UiType.TABLE, UiDataSource.SALES_ORDER, 38L)).rows().getFirst())
                .containsEntry("lineTotal", new BigDecimal("125.50"));
        assertThat(service.render(new UiPlan(UiType.METRIC, UiDataSource.CUSTOMER_ORDERS, 22L)).value())
                .isEqualByComparingTo("1");
        assertThat(service.render(new UiPlan(UiType.CHART, UiDataSource.CUSTOMER_ORDERS, 22L)).chart().yKey())
                .isEqualTo("totalAmount");
    }

    @Test
    void topSellingMetricExplicitlyCountsOnlyReturnedTopTen() {
        when(tools.getTopSellingProducts()).thenReturn(List.of(
                new TopSellingProductResponse(27L, "BIFIDA", "Serum", 9L, new BigDecimal("500.00"))));
        var response = service.render(new UiPlan(UiType.METRIC, UiDataSource.TOP_SELLING, null));
        assertThat(response.value()).isEqualByComparingTo("9");
        assertThat(response.unit()).contains("top 10");
    }
}
