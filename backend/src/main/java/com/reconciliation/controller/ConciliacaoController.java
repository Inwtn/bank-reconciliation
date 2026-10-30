package com.reconciliation.controller;

import com.reconciliation.dto.ConciliacaoResultadoDTO;
import com.reconciliation.model.Importacao;
import com.reconciliation.repository.ImportacaoRepository;
import com.reconciliation.service.ConciliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/conciliacao")
@RequiredArgsConstructor
@Tag(name = "Bank reconciliation", description = "Import and reconcile bank statements")
public class ConciliacaoController {

    private final ConciliacaoService conciliacaoService;
    private final ImportacaoRepository importacaoRepository;

    /**
     * Receive a bank statement and run the reconciliation engine.
     */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Import a bank statement",
        description = "Upload an OFX or CSV file and reconcile it with internal financial entries."
    )
    public ResponseEntity<ConciliacaoResultadoDTO> importarExtrato(
            @RequestParam("arquivo") MultipartFile arquivo) {

        if (arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String nomeArquivo = arquivo.getOriginalFilename();
        log.info("Received statement for reconciliation: {}", nomeArquivo);

        try {
            ConciliacaoResultadoDTO resultado = conciliacaoService.processarExtrato(arquivo);
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid statement: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Could not process statement: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * List every statement import.
     */
    @GetMapping("/historico")
    @Operation(summary = "List import history")
    public ResponseEntity<List<Importacao>> listarHistorico() {
        return ResponseEntity.ok(importacaoRepository.findAllByOrderByDataImportacaoDesc());
    }

    /**
     * Return one import record.
     */
    @GetMapping("/historico/{id}")
    @Operation(summary = "Get an import record")
    public ResponseEntity<Importacao> buscarImportacao(@PathVariable Long id) {
        return importacaoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Service status endpoint.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> status() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "servico", "Bank Reconciliation Engine",
            "versao", "1.0.0"
        ));
    }
}
