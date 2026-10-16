package com.sahinoglu.kbeautyaierp.ai;

import java.util.List;
import com.sahinoglu.kbeautyaierp.inventory.InventoryResponse;
import com.sahinoglu.kbeautyaierp.inventory.InventoryService;
import com.sahinoglu.kbeautyaierp.salesorder.SalesOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatServiceTests {
    private final InventoryService inventory = mock(InventoryService.class);
    private final SalesOrderService orders = mock(SalesOrderService.class);
    private final ErpTools tools = new ErpTools(inventory, orders);
    private final ChatModel model = mock(ChatModel.class);

    private AiChatService service() {
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        return new AiChatService(ChatClient.builder(model), tools);
    }

    private org.springframework.ai.chat.model.ChatResponse toolCall(String name, String arguments) {
        var message = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", name, arguments))).build();
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(message)));
    }

    private org.springframework.ai.chat.model.ChatResponse answer(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void modelCallsStockToolAndReceivesTheServiceResult() {
        when(inventory.findByProductId(7L)).thenReturn(
                new InventoryResponse(1L, 7L, "CREAM-7", "Cream", 3, 5));
        when(model.call(any(Prompt.class))).thenReturn(
                toolCall("getProductStock", "{\"productId\":7}"), answer("Product 7 has 3 units in stock."));

        ChatResponse response = service().chat(new ChatRequest("Show stock for product 7"));

        assertThat(response.answer()).isEqualTo("Product 7 has 3 units in stock.");
        verify(inventory).findByProductId(7L);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var toolMessage = prompts.getAllValues().get(1).getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolMessage.getResponses().getFirst().responseData())
                .contains("CREAM-7", "\"quantity\":3", "\"minimumStockLevel\":5");
    }

    @Test
    void missingStockIsSentBackAsAnErrorInsteadOfInventedZeroStock() {
        when(inventory.findByProductId(7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for product 7"));
        when(model.call(any(Prompt.class))).thenReturn(
                toolCall("getProductStock", "{\"productId\":7}"), answer("No inventory record exists for product 7."));
        service().chat(new ChatRequest("Stock for product 7"));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var toolMessage = prompts.getAllValues().get(1).getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolMessage.getResponses().getFirst().responseData()).contains("Inventory not found");
    }

    @Test
    void onlyTheSixReadToolsAreExposed() {
        assertThat(ToolCallbacks.from(tools)).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("getLowStockProducts", "getProductStock", "getCustomerOrders",
                        "getSalesOrder", "getTopSellingProducts", "getBrandSales");
    }

    @Test
    void invalidToolIdsAreRejectedBeforeCallingServices() {
        assertThatThrownBy(() -> tools.getProductStock(0L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> tools.getCustomerOrders(null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> tools.getSalesOrder(-1L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(inventory, orders);
    }

    @Test
    void emptyProviderResponseBecomesBadGateway() {
        when(model.call(any(Prompt.class))).thenReturn(answer(""));
        assertThatThrownBy(() -> service().chat(new ChatRequest("Hello")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void qwenReasoningIsExcludedFromTheAnswer() {
        when(model.call(any(Prompt.class))).thenReturn(answer("<think>Internal draft</think>\nNo low-stock products."));
        assertThat(service().chat(new ChatRequest("Show low stock")).answer())
                .isEqualTo("No low-stock products.");
    }

    @Test
    void qwenClosingTagWithoutOpeningTagIsHandled() {
        when(model.call(any(Prompt.class))).thenReturn(answer("Internal draft</think>\nNo low-stock products."));
        assertThat(service().chat(new ChatRequest("Show low stock")).answer())
                .isEqualTo("No low-stock products.");
    }

    @Test
    void unfinishedReasoningIsNotReturnedAsAnAnswer() {
        when(model.call(any(Prompt.class))).thenReturn(answer("<think>Unfinished draft"));
        assertThatThrownBy(() -> service().chat(new ChatRequest("Show low stock")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
