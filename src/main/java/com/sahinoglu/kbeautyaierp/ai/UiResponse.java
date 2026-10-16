package com.sahinoglu.kbeautyaierp.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiResponse(
        UiType type,
        String title,
        UiDataSource source,
        List<String> columns,
        List<Map<String, Object>> rows,
        Chart chart,
        BigDecimal value,
        String unit,
        List<StockForm> forms) {

    public record Chart(String kind, String xKey, String yKey, String yLabel) {
    }

    public record StockForm(Long productId, String productName, String helpText, List<Field> fields) {
    }

    public record Field(String name, String label, Integer value) {
    }
}
