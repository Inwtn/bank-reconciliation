package com.reconciliation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> erros = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            erros.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", HttpStatus.BAD_REQUEST.value(),
            "erro", "Validação falhou",
            "campos", erros
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", 413,
            "erro", "Arquivo muito grande. Tamanho máximo permitido: 10MB"
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Erro de negócio: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "erro", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", 500,
            "erro", "Erro interno do servidor. Contate o suporte."
        ));
    }
}
