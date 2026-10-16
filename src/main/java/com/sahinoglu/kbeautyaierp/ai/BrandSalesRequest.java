package com.sahinoglu.kbeautyaierp.ai;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// A deliberately small grammar keeps sales filters verifiable without trusting
// a model to notice every unsupported qualifier. Other ERP questions still use AI.
final class BrandSalesRequest {
    private static final Pattern SIMPLE = Pattern.compile(
            "(?i)(?:please )?show(?: me)? ([\\p{L}\\p{N}][\\p{L}\\p{N} '&-]{0,119}) sales(?: as a (table|(?:bar )?chart|metric))?[.!?]?");

    static UiPlan parse(String message) {
        String text = message.strip().replaceAll("\\s+", " ");
        var match = SIMPLE.matcher(text);
        if (match.matches()) {
            String type = match.group(2);
            return new UiPlan(type == null || type.equalsIgnoreCase("table") ? UiType.TABLE
                    : type.equalsIgnoreCase("metric") ? UiType.METRIC : UiType.CHART,
                    UiDataSource.BRAND_SALES, null, match.group(1).strip());
        }
        // Fail closed for sales reports with additional conditions or columns.
        // Individual order lookups remain part of the existing AI route.
        if (Pattern.compile("(?i)\\bbrand\\b|\\bsales\\b").matcher(text).find()
                && !Pattern.compile("(?i)\\bsales order\\b").matcher(text).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use: Show <brand> sales as a table (or chart/metric). "
                    + "Brand reports support shipped orders across all dates only; columns are product ID, SKU, name, brand, units sold and sales amount (TRY).");
        }
        return null;
    }
}
