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
@Tag(name = "Conciliação Bancária", description = "Motor de conciliação de extratos bancários")
public class ConciliacaoController {

    private final ConciliacaoService conciliacaoService;
    private final ImportacaoRepository importacaoRepository;

    /**
     * Endpoint principal: recebe o arquivo de extrato e executa a conciliação.
     */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Importar extrato bancário",
        description = "Faz upload de um arquivo OFX ou CSV e executa a conciliação automática com os lançamentos do sistema."
    )
    public ResponseEntity<ConciliacaoResultadoDTO> importarExtrato(
            @RequestParam("arquivo") MultipartFile arquivo) {

        if (arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String nomeArquivo = arquivo.getOriginalFilename();
        log.info("Recebido arquivo para conciliação: {}", nomeArquivo);

        try {
            ConciliacaoResultadoDTO resultado = conciliacaoService.processarExtrato(arquivo);
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            log.warn("Arquivo inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erro ao processar extrato: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lista o histórico de todas as importações realizadas.
     */
    @GetMapping("/historico")
    @Operation(summary = "Histórico de importações")
    public ResponseEntity<List<Importacao>> listarHistorico() {
        return ResponseEntity.ok(importacaoRepository.findAllByOrderByDataImportacaoDesc());
    }

    /**
     * Retorna o resultado de uma importação específica.
     */
    @GetMapping("/historico/{id}")
    @Operation(summary = "Detalhes de uma importação")
    public ResponseEntity<Importacao> buscarImportacao(@PathVariable Long id) {
        return importacaoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Health check do sistema.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> status() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "servico", "Motor de Conciliação Bancária",
            "versao", "1.0.0"
        ));
    }
}
