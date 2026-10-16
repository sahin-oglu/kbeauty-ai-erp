package com.sahinoglu.kbeautyaierp;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.sahinoglu.kbeautyaierp.ai.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TableCsvTests {
    private UiResponse table(List<String> columns, List<Map<String, Object>> rows) {
        return new UiResponse(UiType.TABLE, "Report", UiDataSource.LOW_STOCK, columns, rows, null, null, null, null);
    }

    @Test
    void preservesUnicodeQuotesNewlinesColumnOrderAndPrecision() {
        var view = table(List.of("name", "amount", "missing"), List.of(Map.of(
                "name", "İstanbul, \"Krem\"\nŞişe", "amount", new BigDecimal("12.345"))));
        assertThat(TableCsv.write(view)).isEqualTo("\uFEFF\"name\",\"amount\",\"missing\"\r\n"
                + "\"İstanbul, \"\"Krem\"\"\nŞişe\",\"12.345\",\"\"\r\n");
    }

    @Test
    void neutralizesFormulaTextButKeepsNegativeNumbers() {
        for (String value : List.of("=1+1", "+cmd", "  @SUM(A1)", "-2")) {
            assertThat(TableCsv.write(table(List.of("value"), List.of(Map.of("value", value)))))
                    .contains("\"'" + value + "\"");
        }
        assertThat(TableCsv.write(table(List.of("value"), List.of(Map.of("value", -2)))))
                .endsWith("\"-2\"\r\n");
    }

    @Test
    void emptyTablesKeepHeaders() {
        assertThat(TableCsv.write(table(List.of("sku"), List.of()))).isEqualTo("\uFEFF\"sku\"\r\n");
    }
}
