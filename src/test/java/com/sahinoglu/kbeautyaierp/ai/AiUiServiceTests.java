package com.sahinoglu.kbeautyaierp.ai;

import java.util.List;
import com.sahinoglu.kbeautyaierp.inventory.InventoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiUiServiceTests {
    private final ChatModel model = mock(ChatModel.class);
    private final ErpTools tools = mock(ErpTools.class);

    private AiUiService service(String json) {
        when(model.getOptions()).thenReturn(OllamaChatOptions.builder().model("qwen3:4b").build());
        when(model.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(json)))));
        return new AiUiService(ChatClient.builder(model), new UiViewService(tools));
    }

    @Test
    void structuredPlanSelectsViewAndBackendSuppliesTheData() {
        when(tools.getLowStockProducts()).thenReturn(List.of(
                new InventoryResponse(1L, 27L, "BIFIDA", "Serum", 0, 10)));
        var response = service("""
                {"type":"table","source":"low_stock","id":null}
                """).describe(new ChatRequest("Show low stock products"));
        assertThat(response.type()).isEqualTo(UiType.TABLE);
        assertThat(response.rows().getFirst()).containsEntry("quantity", 0);
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).contains("no conversation history");
        Object format = ((OllamaChatOptions) prompt.getValue().getOptions()).getFormat();
        assertThat(format).isInstanceOf(java.util.Map.class);
        var properties = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) format).get("properties");
        assertThat(properties.containsKey("title")).isFalse();
        assertThat(properties.containsKey("brand")).isFalse();
        assertThat(((java.util.Map<?, ?>) properties.get("source")).get("enum").toString())
                .doesNotContain("BRAND_SALES");
        assertThat(((java.util.Map<?, ?>) properties.get("id")).get("type"))
                .isEqualTo(List.of("integer", "null"));
        assertThat(((java.util.Map<?, ?>) properties.get("id")).get("enum"))
                .isEqualTo(java.util.Collections.singletonList(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not JSON", "{}", "null",
            "{\"type\":\"script\",\"source\":\"low_stock\",\"id\":null}",
            "{\"type\":\"table\",\"source\":\"sql\",\"id\":null}"})
    void malformedOrUnknownDescriptionsReturnBadGateway(String json) {
        assertThatThrownBy(() -> service(json).describe(new ChatRequest("Show low stock")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verifyNoInteractions(tools);
    }

    @Test
    void inventedIdDoesNotReachServices() {
        assertThatThrownBy(() -> service("""
                {"type":"metric","source":"product_stock","id":27}
                """).describe(new ChatRequest("Show stock for my serum")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(tools);
    }

    @Test
    void followUpWithoutDatasetIsUnsupported() {
        assertThatThrownBy(() -> service("""
                {"type":"chart","source":"unsupported","id":null}
                """).describe(new ChatRequest("Show the same data as a chart")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(tools);
    }

    @Test
    void providerSchemaEnumNamesCanBeDeserialized() {
        var converter = new org.springframework.ai.converter.BeanOutputConverter<>(UiPlan.class);
        assertThat(converter.getJsonSchema()).contains("\"TABLE\"", "\"LOW_STOCK\"");
        var plan = converter.convert("{\"type\":\"TABLE\",\"source\":\"LOW_STOCK\"}");
        assertThat(plan.type()).isEqualTo(UiType.TABLE);
        assertThat(plan.source()).isEqualTo(UiDataSource.LOW_STOCK);
        assertThat(plan.id()).isNull();
    }
    @ParameterizedTest
    @ValueSource(strings = {"Show low stock products as a table.",
            "Please show me low-stock products as a table!", "List low stock products"})
    void recoversExplicitLowStockRequestWhenModelRejectsIt(String message) {
        when(tools.getLowStockProducts()).thenReturn(List.of(
                new InventoryResponse(1L, 27L, "BIFIDA", "Serum", 0, 10)));
        var response = service("""
                {"type":"table","source":"unsupported","id":null}
                """).describe(new ChatRequest(message));
        assertThat(response.type()).isEqualTo(UiType.TABLE);
        assertThat(response.rows().getFirst()).containsEntry("productId", 27L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Show low stock products in Istanbul as a table.",
            "Show low stock products under 100 lira", "Do not show low stock products",
            "Show the same data as a table"})
    void fallbackDoesNotIgnoreFiltersNegationOrMissingContext(String message) {
        assertThatThrownBy(() -> service("""
                {"type":"table","source":"unsupported","id":null}
                """).describe(new ChatRequest(message)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(tools);
    }

    @Test
    void brandSalesUsesVerifiedBrandAndBackendLabelsEvenWithoutAModel() {
        when(tools.getBrandSales("Example Beauty")).thenReturn(List.of(
                new com.sahinoglu.kbeautyaierp.salesorder.BrandSalesResponse(27L, "BIFIDA", "Serum",
                        "Example Beauty", 5L, new java.math.BigDecimal("62.50"))));
        var response = service("{}").describe(new ChatRequest("Show Example Beauty sales as a table"));
        assertThat(response.columns()).contains("brand");
        assertThat(response.rows().getFirst()).containsEntry("brand", "Example Beauty");
        assertThat(response.title()).contains("Example Beauty", "Shipped orders", "All dates", "TRY");
        verify(model, never()).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Show Example Beauty sales as a table for last month",
            "Show Example Beauty sales including profit", "Show Example Beauty sales for cancelled orders"})
    void unsupportedSalesConditionsAreRejectedBeforeModelOrQuery(String message) {
        assertThatThrownBy(() -> service("{}").describe(new ChatRequest(message)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("shipped orders across all dates");
        verifyNoInteractions(tools);
        verify(model, never()).call(any(Prompt.class));
    }

}
