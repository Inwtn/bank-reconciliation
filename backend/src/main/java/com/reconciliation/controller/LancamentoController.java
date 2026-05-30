package com.reconciliation.controller;

import com.reconciliation.dto.LancamentoRequestDTO;
import com.reconciliation.model.Lancamento;
import com.reconciliation.model.StatusConciliacao;
import com.reconciliation.service.LancamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/lancamentos")
@RequiredArgsConstructor
@Tag(name = "Lançamentos", description = "Gerenciamento de lançamentos financeiros (contas a pagar/receber)")
public class LancamentoController {

    private final LancamentoService lancamentoService;

    @PostMapping
    @Operation(summary = "Criar lançamento", description = "Registra um novo lançamento de conta a pagar ou receber.")
    public ResponseEntity<Lancamento> criar(@Valid @RequestBody LancamentoRequestDTO dto) {
        Lancamento criado = lancamentoService.criar(dto);
        return ResponseEntity
                .created(URI.create("/api/lancamentos/" + criado.getId()))
                .body(criado);
    }

    @GetMapping
    @Operation(summary = "Listar lançamentos")
    public ResponseEntity<List<Lancamento>> listar(
            @RequestParam(required = false) StatusConciliacao status) {
        if (status != null) {
            return ResponseEntity.ok(lancamentoService.listarPorStatus(status));
        }
        return ResponseEntity.ok(lancamentoService.listarTodos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar lançamento por ID")
    public ResponseEntity<Lancamento> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(lancamentoService.buscarPorId(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir lançamento")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        lancamentoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Estatísticas do dashboard")
    public ResponseEntity<LancamentoService.DashboardStats> stats() {
        return ResponseEntity.ok(lancamentoService.obterStats());
    }
}
