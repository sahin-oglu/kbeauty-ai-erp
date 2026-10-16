package com.sahinoglu.kbeautyaierp.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import com.sahinoglu.kbeautyaierp.inventory.InventoryResponse;
import com.sahinoglu.kbeautyaierp.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.model.chat=none")
@ActiveProfiles("ai")
class AiChatHttpTests {
    @Value("${local.server.port}") int port;
    @MockitoBean ChatModel model;
    @MockitoBean InventoryService inventory;
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    @BeforeEach
    void setUp() {
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void workspaceRendersWithoutScriptsOrStyles() throws Exception {
        var response = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Ask your ERP", "id=\"question-form\"", "action=\"/views")
                .doesNotContain("<script", "stylesheet", "th:action");
    }

    @Test
    void chatEndpointRunsTheToolLoopAndReturnsJson() throws Exception {
        when(inventory.findLowStock()).thenReturn(List.of(
                new InventoryResponse(1L, 7L, "CREAM-7", "Cream", 3, 5)));
        var call = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "getLowStockProducts", "{}"))).build();
        when(model.call(any(Prompt.class))).thenReturn(
                new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(call))),
                new org.springframework.ai.chat.model.ChatResponse(List.of(
                        new Generation(new AssistantMessage("Cream has 3 units; minimum stock is 5.")))));

        var response = post("{\"message\":\"Show low stock products\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"answer\":", "Cream has 3 units");
        verify(inventory).findLowStock();
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void blankMessageIsRejectedBeforeModelCall() throws Exception {
        var response = post("{\"message\":\"   \"}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("message");
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void swaggerIncludesChatEndpoint() throws Exception {
        var response = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/ai/chat", "/api/ai/ui", "ChatRequest", "ChatResponse", "UiResponse");
    }

    @Test
    void uiEndpointReturnsRenderableJsonWithRealRows() throws Exception {
        when(inventory.findLowStock()).thenReturn(List.of(
                new InventoryResponse(1L, 27L, "TG-BIFIDA", "Bifida Serum", 0, 10)));
        when(model.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(
                        "{\"type\":\"chart\",\"source\":\"low_stock\",\"id\":null}")))));
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ai/ui"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Chart low stock products\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"type\":\"chart\"", "\"kind\":\"bar\"", "TG-BIFIDA", "\"quantity\":0");
        assertThat(response.body()).doesNotContain("\"forms\"", "\"columns\"", "\"value\"");
    }

    @Test
    void uiEndpointRejectsBlankMessage() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ai/ui"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"   \"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        verify(model, never()).call(any(Prompt.class));
    }
    private HttpResponse<String> form(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void viewModel(String type) {
        when(inventory.findLowStock()).thenReturn(List.of(
                new InventoryResponse(1L, 27L, "CREAM", "<b>Cream</b>", 3, 10)));
        when(model.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(
                        "{\"type\":\"" + type + "\",\"source\":\"low_stock\",\"id\":null}")))));
    }

    private String viewId(String html) {
        var matcher = java.util.regex.Pattern.compile("viewId(?:=|\" value=\")([a-f0-9-]{36})").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"table", "chart", "metric", "form"})
    void htmlFormsRenderEveryViewWithoutJavaScript(String type) throws Exception {
        viewModel(type);
        var page = form("/views", "message=Show+low+stock");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Low stock products").doesNotContain("<script", "<b>Cream</b>");
        switch (type) {
            case "table" -> assertThat(page.body()).contains("<table>", "Download CSV", "<td>&lt;b&gt;Cream&lt;/b&gt;</td>", "<td>3</td>");
            case "chart" -> assertThat(page.body()).contains("<meter", "value=\"3\"");
            case "metric" -> assertThat(page.body()).contains("<strong>1</strong>", "products");
            case "form" -> assertThat(page.body()).contains("Save stock", "name=\"quantity\"", "value=\"3\"");
        }
    }

    @Test
    void csvUsesDisplayedSnapshotAndRejectsReplacedViews() throws Exception {
        viewModel("table");
        String id = viewId(form("/views", "message=Show+low+stock").body());
        when(inventory.findLowStock()).thenReturn(List.of());
        var csv = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/table.csv?viewId=" + id))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains("\"<b>Cream</b>\"", "\"3\",\"10\"");
        assertThat(csv.headers().firstValue("Content-Disposition").orElseThrow()).contains("attachment");
        form("/views", "message=Show+low+stock");
        var expired = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/table.csv?viewId=" + id))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(expired.statusCode()).isEqualTo(409);
    }

    @Test
    void stockFormValidatesAndSavesThroughExistingService() throws Exception {
        viewModel("form");
        String id = viewId(form("/views", "message=Show+low+stock").body());
        var invalid = form("/stock/27", "viewId=" + id + "&quantity=-1&minimumStockLevel=10");
        assertThat(invalid.body()).contains("Stock values must be whole numbers");
        verify(inventory, never()).update(anyLong(), any());
        var saved = form("/stock/27", "viewId=" + id + "&quantity=15&minimumStockLevel=10");
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(saved.body()).contains("Stock saved", "value=\"15\"");
        verify(inventory).update(27L, new com.sahinoglu.kbeautyaierp.inventory.InventoryRequest(15, 10));
    }

    @Test
    void invalidQuestionPreservesPreviousResult() throws Exception {
        viewModel("table");
        form("/views", "message=Show+low+stock");
        var response = form("/views", "message=+");
        assertThat(response.body()).contains("Enter a question", "Low stock products", "Download CSV");
    }

}
