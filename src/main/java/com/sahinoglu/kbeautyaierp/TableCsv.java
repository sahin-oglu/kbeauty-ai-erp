package com.sahinoglu.kbeautyaierp;

import java.util.List;
import java.util.stream.Collectors;
import com.sahinoglu.kbeautyaierp.ai.UiResponse;

final class TableCsv {
    static String write(UiResponse view) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(line(view.columns()));
        for (var row : view.rows()) {
            csv.append(line(view.columns().stream().map(row::get).toList()));
        }
        return csv.toString();
    }

    private static String line(List<?> values) {
        return values.stream().map(TableCsv::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (value instanceof String && text.matches("(?s)^\\s*[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
