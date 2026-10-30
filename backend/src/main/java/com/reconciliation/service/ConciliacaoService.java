package com.reconciliation.service;

import com.reconciliation.dto.ConciliacaoResultadoDTO;
import com.reconciliation.model.*;
import com.reconciliation.parser.CsvParser;
import com.reconciliation.parser.OfxParser;
import com.reconciliation.repository.ExtratoBancarioRepository;
import com.reconciliation.repository.ImportacaoRepository;
import com.reconciliation.repository.LancamentoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Main bank reconciliation engine.
 *
 * Matching algorithm:
 * 1. Import an OFX or CSV bank statement.
 * 2. Match each statement transaction by exact amount, debit/credit type, and date.
 * 3. If an exact-date match is absent, search within a one-day date tolerance.
 * 4. Mark both sides of a match as reconciled; unmatched statement rows as divergent.
 * 5. Leave unmatched internal entries pending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliacaoService {

    private static final int TOLERANCIA_DIAS = 1; // Date tolerance for matching

    private final LancamentoRepository lancamentoRepository;
    private final ExtratoBancarioRepository extratoRepository;
    private final ImportacaoRepository importacaoRepository;
    private final OfxParser ofxParser;
    private final CsvParser csvParser;
    private final MeterRegistry meterRegistry;
    private final ImportacaoTrackingService trackingService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Process the uploaded statement and run reconciliation.
     */
    public ConciliacaoResultadoDTO processarExtrato(MultipartFile file) throws Exception {
        long startedAt = System.nanoTime();
        log.info("Starting bank reconciliation");
        log.info("File: {} | Size: {} bytes", file.getOriginalFilename(), file.getSize());

        // Detect the format and create an import record.
        Importacao.FormatoArquivo formato = detectarFormato(file);
        Importacao importacao = trackingService.criar(file.getOriginalFilename(), formato);

        try {
            // Parse before opening the reconciliation transaction.
            List<ExtratoBancario> transacoesExtrato = parsearArquivo(file, formato);

            if (transacoesExtrato.isEmpty()) {
                throw new IllegalArgumentException("No transactions found in the file. Check its format.");
            }

            // Statement rows, matches and the completed status commit together.
            ConciliacaoResultadoDTO resposta = transactionTemplate.execute(status ->
                    conciliarNoBanco(importacao.getId(), transacoesExtrato));
            registrarMetrica("success", startedAt);
            return resposta;

        } catch (Exception e) {
            log.error("Reconciliation failed: {}", e.getMessage(), e);
            registrarMetrica("failure", startedAt);
            trackingService.marcarErro(importacao.getId(), e.getMessage());
            throw e;
        }
    }

    private ConciliacaoResultadoDTO conciliarNoBanco(Long importacaoId, List<ExtratoBancario> transacoes) {
        Importacao importacao = importacaoRepository.findById(importacaoId).orElseThrow();
        transacoes.forEach(t -> t.setIdImportacao(importacaoId));
        List<ExtratoBancario> extratosSalvos = extratoRepository.saveAll(transacoes);
        ConciliacaoResultado resultado = executarConciliacao(extratosSalvos);

        importacao.setTotalRegistros(extratosSalvos.size());
        importacao.setTotalConciliados(resultado.conciliados.size());
        importacao.setTotalDivergentes(resultado.divergentes.size());
        importacao.setStatus(Importacao.StatusImportacao.CONCLUIDO);
        importacaoRepository.save(importacao);
        return montarResultado(importacao, resultado);
    }

    private void registrarMetrica(String resultado, long startedAt) {
        meterRegistry.counter("bank.reconciliation.imports", "result", resultado).increment();
        meterRegistry.timer("bank.reconciliation.import.duration", "result", resultado)
                .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
    }

    /**
     * Match bank statement transactions with internal entries.
     */
    private ConciliacaoResultado executarConciliacao(List<ExtratoBancario> extratos) {
        log.info("Reconciling {} statement transactions", extratos.size());

        List<ExtratoBancario> conciliados = new ArrayList<>();
        List<ExtratoBancario> divergentes = new ArrayList<>();

        for (ExtratoBancario extrato : extratos) {
            log.debug("Processing: {} | {} | {}", extrato.getDataTransacao(), extrato.getValor(), extrato.getTipo());

            // Try the exact date first, then the tolerance window.
            Optional<Lancamento> match = buscarMatch(extrato);

            if (match.isPresent()) {
                // Match found in the internal records.
                Lancamento lancamento = match.get();
                log.debug("Match found: statement {} ↔ entry {}", extrato.getId(), lancamento.getId());

                extrato.setStatusConciliacao(StatusConciliacao.CONCILIADO);
                extrato.setIdLancamentoConciliado(lancamento.getId());

                lancamento.setStatus(StatusConciliacao.CONCILIADO);
                lancamento.setIdExtratoBancario(extrato.getId());

                lancamentoRepository.save(lancamento);
                extratoRepository.save(extrato);
                conciliados.add(extrato);
            } else {
                // Bank transaction without a corresponding internal entry.
                log.debug("No match: {} | {} | {}", extrato.getDataTransacao(), extrato.getValor(), extrato.getTipo());
                extrato.setStatusConciliacao(StatusConciliacao.DIVERGENTE);
                extratoRepository.save(extrato);
                divergentes.add(extrato);
            }
        }

        log.info("Reconciliation complete: {} matched | {} unmatched", conciliados.size(), divergentes.size());
        return new ConciliacaoResultado(conciliados, divergentes);
    }

    /**
     * Match by exact date first, then within the date tolerance.
     */
    private Optional<Lancamento> buscarMatch(ExtratoBancario extrato) {
        // First attempt: exact date.
        Optional<Lancamento> matchExato = lancamentoRepository.findExactMatch(
                extrato.getValor(),
                extrato.getTipo(),
                extrato.getDataTransacao()
        );

        if (matchExato.isPresent()) {
            return matchExato;
        }

        // Second attempt: nearby dates.
        LocalDate inicio = extrato.getDataTransacao().minusDays(TOLERANCIA_DIAS);
        LocalDate fim = extrato.getDataTransacao().plusDays(TOLERANCIA_DIAS);

        List<Lancamento> candidatos = lancamentoRepository.findMatchCandidates(
                extrato.getValor(),
                extrato.getTipo(),
                inicio,
                fim
        );

        // Choose the closest date.
        return candidatos.stream()
                .min((a, b) -> {
                    long diasA = Math.abs(ChronoUnit.DAYS.between(a.getDataLancamento(), extrato.getDataTransacao()));
                    long diasB = Math.abs(ChronoUnit.DAYS.between(b.getDataLancamento(), extrato.getDataTransacao()));
                    return Long.compare(diasA, diasB);
                });
    }

    private ConciliacaoResultadoDTO montarResultado(Importacao importacao, ConciliacaoResultado resultado) {
        // Find internal entries that remain pending.
        List<Lancamento> lancamentosPendentes = lancamentoRepository.findByStatus(StatusConciliacao.PENDENTE);

        // Calculate totals.
        BigDecimal valorTotalExtrato = BigDecimal.ZERO;
        BigDecimal valorTotalConciliado = BigDecimal.ZERO;
        BigDecimal valorTotalDivergente = BigDecimal.ZERO;

        List<ConciliacaoResultadoDTO.ItemConciliacaoDTO> itensConciliados = new ArrayList<>();
        List<ConciliacaoResultadoDTO.ItemConciliacaoDTO> itensDivergentes = new ArrayList<>();

        // Build matched items.
        for (ExtratoBancario extrato : resultado.conciliados) {
            valorTotalExtrato = valorTotalExtrato.add(extrato.getValor());
            valorTotalConciliado = valorTotalConciliado.add(extrato.getValor());

            Lancamento lancamento = lancamentoRepository.findById(extrato.getIdLancamentoConciliado()).orElse(null);
            itensConciliados.add(ConciliacaoResultadoDTO.ItemConciliacaoDTO.builder()
                    .idExtrato(extrato.getId())
                    .dataExtrato(extrato.getDataTransacao())
                    .valorExtrato(extrato.getValor())
                    .descricaoExtrato(extrato.getDescricao())
                    .tipoExtrato(extrato.getTipo())
                    .status(StatusConciliacao.CONCILIADO)
                    .idLancamento(lancamento != null ? lancamento.getId() : null)
                    .dataLancamento(lancamento != null ? lancamento.getDataLancamento() : null)
                    .valorLancamento(lancamento != null ? lancamento.getValor() : null)
                    .descricaoLancamento(lancamento != null ? lancamento.getDescricao() : null)
                    .documentoLancamento(lancamento != null ? lancamento.getDocumento() : null)
                    .diferenca(BigDecimal.ZERO)
                    .build());
        }

        // Build unmatched items.
        for (ExtratoBancario extrato : resultado.divergentes) {
            valorTotalExtrato = valorTotalExtrato.add(extrato.getValor());
            valorTotalDivergente = valorTotalDivergente.add(extrato.getValor());

            itensDivergentes.add(ConciliacaoResultadoDTO.ItemConciliacaoDTO.builder()
                    .idExtrato(extrato.getId())
                    .dataExtrato(extrato.getDataTransacao())
                    .valorExtrato(extrato.getValor())
                    .descricaoExtrato(extrato.getDescricao())
                    .tipoExtrato(extrato.getTipo())
                    .status(StatusConciliacao.DIVERGENTE)
                    .build());
        }

        // Build pending items.
        List<ConciliacaoResultadoDTO.LancamentoPendenteDTO> pendentes = lancamentosPendentes.stream()
                .map(l -> ConciliacaoResultadoDTO.LancamentoPendenteDTO.builder()
                        .id(l.getId())
                        .dataLancamento(l.getDataLancamento())
                        .valor(l.getValor())
                        .descricao(l.getDescricao())
                        .documento(l.getDocumento())
                        .tipo(l.getTipo())
                        .diasEmAberto(ChronoUnit.DAYS.between(l.getDataLancamento(), LocalDate.now()))
                        .build())
                .toList();

        int total = resultado.conciliados.size() + resultado.divergentes.size();
        double percentual = total > 0
                ? (double) resultado.conciliados.size() / total * 100
                : 0;

        return ConciliacaoResultadoDTO.builder()
                .idImportacao(importacao.getId())
                .nomeArquivo(importacao.getNomeArquivo())
                .dataProcessamento(LocalDateTime.now())
                .totalRegistrosExtrato(total)
                .totalConciliados(resultado.conciliados.size())
                .totalDivergentes(resultado.divergentes.size())
                .totalPendentesNoSistema(pendentes.size())
                .valorTotalExtrato(valorTotalExtrato.setScale(2, RoundingMode.HALF_UP))
                .valorTotalConciliado(valorTotalConciliado.setScale(2, RoundingMode.HALF_UP))
                .valorTotalDivergente(valorTotalDivergente.setScale(2, RoundingMode.HALF_UP))
                .percentualConciliado(Math.round(percentual * 100.0) / 100.0)
                .itensConciliados(itensConciliados)
                .itensDivergentes(itensDivergentes)
                .lancamentosPendentes(pendentes)
                .build();
    }

    private Importacao.FormatoArquivo detectarFormato(MultipartFile file) {
        String nome = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";
        if (nome.endsWith(".ofx")) {
            return Importacao.FormatoArquivo.OFX;
        }
        if (nome.endsWith(".csv")) return Importacao.FormatoArquivo.CSV;
        throw new IllegalArgumentException("Unsupported file format. Upload an OFX or CSV file.");
    }

    private List<ExtratoBancario> parsearArquivo(MultipartFile file, Importacao.FormatoArquivo formato) throws Exception {
        return switch (formato) {
            case OFX -> ofxParser.parse(file);
            case CSV -> csvParser.parse(file);
            case XLSX -> throw new IllegalArgumentException("XLSX is not supported. Export the statement as CSV.");
        };
    }

    /**
     * Internal record for matched and unmatched statement rows.
     */
    private record ConciliacaoResultado(
            List<ExtratoBancario> conciliados,
            List<ExtratoBancario> divergentes
    ) {}
}
