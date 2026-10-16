package com.sahinoglu.kbeautyaierp;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.sahinoglu.kbeautyaierp.ai.*;
import com.sahinoglu.kbeautyaierp.inventory.InventoryRequest;
import com.sahinoglu.kbeautyaierp.inventory.InventoryService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.client.RestClientException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import tools.jackson.databind.ObjectMapper;

@Controller
public class WorkspaceController {
	private final ObjectProvider<AiUiService> ai;
	private final InventoryService inventory;
	private final ObjectMapper mapper;

	public WorkspaceController(ObjectProvider<AiUiService> ai, InventoryService inventory, ObjectMapper mapper) {
		this.ai = ai;
		this.inventory = inventory;
		this.mapper = mapper;
	}

	@GetMapping("/")
	public String index(HttpSession session, Model model) {
		UiResponse view = (UiResponse) session.getAttribute("view");
		model.addAttribute("view", view);
		model.addAttribute("viewId", session.getAttribute("viewId"));
		model.addAttribute("question", session.getAttribute("question"));
		model.addAttribute("aiEnabled", ai.getIfAvailable() != null);
		if (view != null) {
			model.addAttribute("json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(view));
			if (view.type() == UiType.CHART) {
				double maximum = view.rows().stream()
						.mapToDouble(row -> ((Number) row.get(view.chart().yKey())).doubleValue()).max().orElse(1);
				model.addAttribute("chartMaximum", Math.max(1, maximum));
			}
		}
		return "index";
	}

	@PostMapping("/views")
	public String createView(@RequestParam String message, HttpSession session, RedirectAttributes redirect) {
		session.setAttribute("question", message.length() <= 4000 ? message : message.substring(0, 4000));
		if (message.isBlank() || message.length() > 4000) {
			redirect.addFlashAttribute("error", "Enter a question of 1 to 4,000 characters.");
			return "redirect:/";
		}
		AiUiService service = ai.getIfAvailable();
		if (service == null) {
			redirect.addFlashAttribute("error", "Start the application with the ai profile to create views.");
			return "redirect:/";
		}
		try {
			UiResponse view = service.describe(new ChatRequest(message.strip()));
			session.setAttribute("view", view);
			session.setAttribute("viewId", UUID.randomUUID().toString());
			redirect.addFlashAttribute("notice", "View created.");
		} catch (ResponseStatusException exception) {
			redirect.addFlashAttribute("error", exception.getReason());
		} catch (RestClientException | NonTransientAiException | TransientAiException exception) {
			redirect.addFlashAttribute("error", "Could not reach the model. Check Ollama and try again.");
		}
		return "redirect:/";
	}

	@PostMapping("/stock/{productId}")
	public String saveStock(@PathVariable Long productId, @RequestParam String viewId,
			@Valid @ModelAttribute InventoryRequest stock, BindingResult errors,
			HttpSession session, RedirectAttributes redirect) {
		UiResponse view = currentView(session, viewId);
		if (view.type() != UiType.FORM || view.forms().stream().noneMatch(form -> form.productId().equals(productId))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Generate a stock form for this product first");
		}
		if (errors.hasErrors()) {
			redirect.addFlashAttribute("error", "Stock values must be whole numbers between 0 and 2147483647. Re-enter the values.");
			return "redirect:/";
		}
		try {
			inventory.update(productId, stock);
			// Keep this form's saved values visible without another model request.
			var forms = view.forms().stream().map(form -> form.productId().equals(productId)
					? new UiResponse.StockForm(form.productId(), form.productName(), "Set the total stock balance.",
							java.util.List.of(new UiResponse.Field("quantity", "Total stock quantity", stock.quantity()),
									new UiResponse.Field("minimumStockLevel", "Minimum stock level", stock.minimumStockLevel())))
					: form).toList();
			session.setAttribute("view", new UiResponse(view.type(), view.title(), view.source(), null, null,
					null, null, null, forms));
			redirect.addFlashAttribute("notice", "Stock saved. Generate a new report to see updated results.");
		} catch (ResponseStatusException exception) {
			redirect.addFlashAttribute("error", exception.getReason());
		} catch (org.springframework.dao.OptimisticLockingFailureException exception) {
			redirect.addFlashAttribute("error", "Stock changed in another request. Generate a fresh form and try again.");
		}
		return "redirect:/";
	}

	@GetMapping("/table.csv")
	public ResponseEntity<byte[]> download(@RequestParam String viewId, HttpSession session) {
		UiResponse view = currentView(session, viewId);
		if (view.type() != UiType.TABLE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Create a table before downloading CSV");
		}
		return ResponseEntity.ok().header("Content-Type", "text/csv;charset=UTF-8")
				.header("Content-Disposition", "attachment; filename=report.csv")
				.header("Cache-Control", "no-store")
				.body(TableCsv.write(view).getBytes(StandardCharsets.UTF_8));
	}

	private UiResponse currentView(HttpSession session, String id) {
		UiResponse view = (UiResponse) session.getAttribute("view");
		if (view == null || !id.equals(session.getAttribute("viewId"))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This view has expired or been replaced. Open the workspace and generate it again.");
		}
		return view;
	}
}
