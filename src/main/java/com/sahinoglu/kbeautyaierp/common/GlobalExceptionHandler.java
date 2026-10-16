package com.sahinoglu.kbeautyaierp.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.RestClientException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({RestClientException.class, NonTransientAiException.class, TransientAiException.class})
    public ProblemDetail handleAiProviderError(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The Ollama request failed. Check that Ollama is running and the configured model is installed.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleBusinessError(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Stock or order changed in another request; reload and try again");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Data conflicts with a database constraint; check unique fields and references");
    }
}
