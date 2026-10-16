package com.sahinoglu.kbeautyaierp.ai;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("ai")
public class AiUiService {
    private final ChatClient chatClient;
    private final UiViewService views;
    private final BeanOutputConverter<UiPlan> converter = new BeanOutputConverter<>(UiPlan.class);

    private final Map<String, Object> schema = createSchema();

    public AiUiService(ChatClient.Builder builder, UiViewService views) {
        this.chatClient = builder.defaultSystem("""
                Choose one UI description for an ERP question. Return JSON only.
                type: TABLE, METRIC, CHART, or FORM. Default to TABLE unless another type is requested.
                source: LOW_STOCK, PRODUCT_STOCK, CUSTOMER_ORDERS, SALES_ORDER, TOP_SELLING, or UNSUPPORTED.
                id: a positive numeric ID explicitly supplied by the user, or null. Never invent IDs or use zero.
                LOW_STOCK and TOP_SELLING always use id null.
                product_stock needs a product ID, customer_orders a customer ID, sales_order an order ID.
                If a required ID is absent, use null so the backend can ask for it.
                This request has no conversation history. For references such as 'the same data' or 'these products'
                without an explicit dataset, choose unsupported. Do not guess prior context.
                Available views:
                low_stock: table of stock, chart of quantity by SKU, metric counting low-stock products,
                  or form for setting stock quantities of those products.
                product_stock: table of stock, chart of quantity by SKU, metric showing stock quantity,
                  or form for setting that product's stock quantity.
                customer_orders: table of orders, chart of order totals, or metric counting orders.
                sales_order: table of line items, chart of line totals, or metric showing the order total.
                top_selling: table of top 10 products, chart of shipped units, or metric summing units in the top 10.
                Sales rankings use shipped orders across all dates. Monetary values are in TRY.
                Example: Show low stock products as a table. -> TABLE, LOW_STOCK, id null.
                Explicit low stock requests are complete; no product ID or prior context is needed.
                Example: Create a restock form for low-stock products -> FORM, LOW_STOCK, id null.
                Example: Show stock for product 27 as a metric -> METRIC, PRODUCT_STOCK, id 27.
                Forms are stock-editing descriptions only; they do not submit changes.
                Choose unsupported for unavailable filters, calculations, actions, or unrelated questions.
                Do not generate data rows, values, HTML, JavaScript, URLs, or additional fields.
                /no_think
                """).build();
        this.views = views;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createSchema() {
        Map<String, Object> schema = converter.getJsonSchemaMap();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> id = (Map<String, Object>) properties.get("id");
        // Spring AI makes @Nullable optional but its generated type does not allow JSON null.
        id.put("type", List.of("integer", "null"));
        // Brand sales is handled by the explicit parser, not model routing.
        properties.remove("brand");
        ((Map<String, Object>) properties.get("source")).put("enum", List.of(
                "LOW_STOCK", "PRODUCT_STOCK", "CUSTOMER_ORDERS", "SALES_ORDER", "TOP_SELLING", "UNSUPPORTED"));
        schema.put("required", List.of("type", "source", "id"));
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaFor(String message) {
        Map<String, Object> requestSchema = new LinkedHashMap<>(schema);
        Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) schema.get("properties"));
        Map<String, Object> id = new LinkedHashMap<>((Map<String, Object>) properties.get("id"));
        List<Object> allowedIds = new ArrayList<>();
        allowedIds.add(null);
        var numbers = java.util.regex.Pattern.compile("\\b[0-9]+\\b").matcher(message);
        while (numbers.find()) {
            try {
                long value = Long.parseLong(numbers.group());
                if (value > 0 && !allowedIds.contains(value)) allowedIds.add(value);
            }
            catch (NumberFormatException ignored) {
                // Numbers outside the Long range cannot be ERP IDs.
            }
        }
        // Constrain generation to IDs in this question; a request without IDs permits only null.
        id.put("enum", allowedIds);
        properties.put("id", id);
        requestSchema.put("properties", properties);
        return requestSchema;
    }

    private UiPlan explicitLowStockPlan(String message) {
        String normalized = message.strip().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
        var match = java.util.regex.Pattern.compile(
                "(?:please )?(?:show|list)(?: me)? low[- ]stock products(?: as a (table|(?:bar )?chart|metric))?")
                .matcher(normalized);
        if (!match.matches()) return null;
        String requestedType = match.group(1);
        UiType type = requestedType == null || requestedType.equals("table") ? UiType.TABLE
                : requestedType.equals("metric") ? UiType.METRIC : UiType.CHART;
        return new UiPlan(type, UiDataSource.LOW_STOCK, null);
    }

    public UiResponse describe(ChatRequest request) {
        UiPlan explicit = BrandSalesRequest.parse(request.message());
        if (explicit != null) return views.render(explicit);
        String content = chatClient.prompt()
                .user(request.message())
                .options(OllamaChatOptions.builder().format(schemaFor(request.message())).temperature(0.0))
                .call().content();
        if (content != null && content.contains("</think>")) {
            content = content.substring(content.lastIndexOf("</think>") + "</think>".length()).strip();
        }
        if (content == null || content.isBlank() || content.contains("<think>")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model did not return a UI description");
        }
        UiPlan plan;
        try {
            plan = converter.convert(content);
        }
        catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned invalid UI JSON");
        }
        if (plan != null && plan.id() != null && !java.util.regex.Pattern
                .compile("\\b" + plan.id() + "\\b").matcher(request.message()).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Include the requested record ID explicitly in the question");
        }
        // Small models sometimes reject a complete, supported request. Only recover
        // whole, unambiguous requests here; never drop extra filters or infer context.
        if (plan != null && plan.source() == UiDataSource.UNSUPPORTED) {
            UiPlan fallback = explicitLowStockPlan(request.message());
            if (fallback != null) plan = fallback;
        }
        if (plan != null && (plan.source() == UiDataSource.BRAND_SALES || plan.brand() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use: Show <brand> sales as a table (or chart/metric). Only shipped orders across all dates are supported.");
        }
        return views.render(plan);
    }
}
