package com.reconciliation.service;

import com.reconciliation.dto.ConciliacaoResultadoDTO;
import com.reconciliation.model.*;
import com.reconciliation.parser.CsvParser;
import com.reconciliation.parser.OfxParser;
import com.reconciliation.repository.ExtratoBancarioRepository;
import com.reconciliation.repository.ImportacaoRepository;
import com.reconciliation.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Motor principal de conciliação bancária.
 *
 * Algoritmo de matching:
 * 1. Importa o extrato bancário (OFX ou CSV)
 * 2. Para cada transação do extrato, busca um lançamento no sistema com:
 *    - Mesmo valor (correspondência exata)
 *    - Mesmo tipo (débito/crédito)
 *    - Data igual ou próxima (tolerância configurável, padrão ±1 dia)
 * 3. Se encontrar match: marca ambos como CONCILIADO e vincula os registros
 * 4. Se não encontrar: marca o extrato como DIVERGENTE
 * 5. Lançamentos sem match ficam como PENDENTE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliacaoService {

    private static final int TOLERANCIA_DIAS = 1; // Tolerância de data para match

    private final LancamentoRepository lancamentoRepository;
    private final ExtratoBancarioRepository extratoRepository;
    private final ImportacaoRepository importacaoRepository;
    private final OfxParser ofxParser;
    private final CsvParser csvParser;

    /**
     * Processa o upload do extrato bancário e executa a conciliação.
     */
    @Transactional
    public ConciliacaoResultadoDTO processarExtrato(MultipartFile file) throws Exception {
        log.info("=== INICIANDO CONCILIAÇÃO BANCÁRIA ===");
        log.info("Arquivo: {} | Tamanho: {} bytes", file.getOriginalFilename(), file.getSize());

        // 1. Determina o formato e cria o registro de importação
        Importacao.FormatoArquivo formato = detectarFormato(file);
        Importacao importacao = criarImportacao(file.getOriginalFilename(), formato);

        try {
            // 2. Faz o parse do arquivo
            List<ExtratoBancario> transacoesExtrato = parsearArquivo(file, formato);

            if (transacoesExtrato.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma transação encontrada no arquivo. Verifique o formato.");
            }

            // 3. Salva as transações do extrato vinculadas à importação
            transacoesExtrato.forEach(t -> t.setIdImportacao(importacao.getId()));
            List<ExtratoBancario> extratosSalvos = extratoRepository.saveAll(transacoesExtrato);

            // 4. EXECUTA O MOTOR DE CONCILIAÇÃO (o coração do sistema)
            ConciliacaoResultado resultado = executarConciliacao(extratosSalvos);

            // 5. Atualiza a importação com os resultados
            importacao.setTotalRegistros(extratosSalvos.size());
            importacao.setTotalConciliados(resultado.conciliados.size());
            importacao.setTotalDivergentes(resultado.divergentes.size());
            importacao.setStatus(Importacao.StatusImportacao.CONCLUIDO);
            importacaoRepository.save(importacao);

            // 6. Monta e retorna o DTO de resultado
            return montarResultado(importacao, resultado);

        } catch (Exception e) {
            log.error("Erro durante conciliação: {}", e.getMessage(), e);
            importacao.setStatus(Importacao.StatusImportacao.ERRO);
            importacao.setMensagemErro(e.getMessage());
            importacaoRepository.save(importacao);
            throw e;
        }
    }

    /**
     * Motor de conciliação: cruza dados do extrato com lançamentos do sistema.
     */
    private ConciliacaoResultado executarConciliacao(List<ExtratoBancario> extratos) {
        log.info("Executando motor de conciliação para {} transações do extrato", extratos.size());

        List<ExtratoBancario> conciliados = new ArrayList<>();
        List<ExtratoBancario> divergentes = new ArrayList<>();

        for (ExtratoBancario extrato : extratos) {
            log.debug("Processando: {} | {} | {}", extrato.getDataTransacao(), extrato.getValor(), extrato.getTipo());

            // Busca match: primeiro tenta data exata, depois com tolerância
            Optional<Lancamento> match = buscarMatch(extrato);

            if (match.isPresent()) {
                // CONCILIADO: encontrou correspondência no sistema
                Lancamento lancamento = match.get();
                log.debug("✅ MATCH encontrado: Extrato {} ↔ Lançamento {}", extrato.getId(), lancamento.getId());

                extrato.setStatusConciliacao(StatusConciliacao.CONCILIADO);
                extrato.setIdLancamentoConciliado(lancamento.getId());

                lancamento.setStatus(StatusConciliacao.CONCILIADO);
                lancamento.setIdExtratoBancario(extrato.getId());

                lancamentoRepository.save(lancamento);
                extratoRepository.save(extrato);
                conciliados.add(extrato);
            } else {
                // DIVERGENTE: transação no extrato sem correspondência no sistema
                log.debug("❌ Sem match: {} | {} | {}", extrato.getDataTransacao(), extrato.getValor(), extrato.getTipo());
                extrato.setStatusConciliacao(StatusConciliacao.DIVERGENTE);
                extratoRepository.save(extrato);
                divergentes.add(extrato);
            }
        }

        log.info("Conciliação concluída: {} conciliados | {} divergentes", conciliados.size(), divergentes.size());
        return new ConciliacaoResultado(conciliados, divergentes);
    }

    /**
     * Estratégia de match com fallback:
     * 1. Match exato (mesmo dia, mesmo valor, mesmo tipo)
     * 2. Match com tolerância de ±N dias
     */
    private Optional<Lancamento> buscarMatch(ExtratoBancario extrato) {
        // Tentativa 1: match exato de data
        Optional<Lancamento> matchExato = lancamentoRepository.findExactMatch(
                extrato.getValor(),
                extrato.getTipo(),
                extrato.getDataTransacao()
        );

        if (matchExato.isPresent()) {
            return matchExato;
        }

        // Tentativa 2: match com tolerância de dias
        LocalDate inicio = extrato.getDataTransacao().minusDays(TOLERANCIA_DIAS);
        LocalDate fim = extrato.getDataTransacao().plusDays(TOLERANCIA_DIAS);

        List<Lancamento> candidatos = lancamentoRepository.findMatchCandidates(
                extrato.getValor(),
                extrato.getTipo(),
                inicio,
                fim,
                extrato.getDataTransacao()
        );

        // Pega o candidato com data mais próxima
        return candidatos.stream()
                .min((a, b) -> {
                    long diasA = Math.abs(ChronoUnit.DAYS.between(a.getDataLancamento(), extrato.getDataTransacao()));
                    long diasB = Math.abs(ChronoUnit.DAYS.between(b.getDataLancamento(), extrato.getDataTransacao()));
                    return Long.compare(diasA, diasB);
                });
    }

    private ConciliacaoResultadoDTO montarResultado(Importacao importacao, ConciliacaoResultado resultado) {
        // Busca lançamentos ainda pendentes no sistema
        List<Lancamento> lancamentosPendentes = lancamentoRepository.findByStatus(StatusConciliacao.PENDENTE);

        // Calcula totalizadores
        BigDecimal valorTotalExtrato = BigDecimal.ZERO;
        BigDecimal valorTotalConciliado = BigDecimal.ZERO;
        BigDecimal valorTotalDivergente = BigDecimal.ZERO;

        List<ConciliacaoResultadoDTO.ItemConciliacaoDTO> itensConciliados = new ArrayList<>();
        List<ConciliacaoResultadoDTO.ItemConciliacaoDTO> itensDivergentes = new ArrayList<>();

        // Processa conciliados
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

        // Processa divergentes
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

        // Monta lista de pendentes
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
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase()
                : "";

        if (nome.endsWith(".ofx") || contentType.contains("ofx")) {
            return Importacao.FormatoArquivo.OFX;
        } else if (nome.endsWith(".xlsx") || nome.endsWith(".xls")) {
            return Importacao.FormatoArquivo.XLSX;
        } else {
            return Importacao.FormatoArquivo.CSV; // padrão
        }
    }

    private List<ExtratoBancario> parsearArquivo(MultipartFile file, Importacao.FormatoArquivo formato) throws Exception {
        return switch (formato) {
            case OFX -> ofxParser.parse(file);
            case CSV -> csvParser.parse(file);
            case XLSX -> csvParser.parse(file); // XLSX pode ser exportado como CSV
        };
    }

    private Importacao criarImportacao(String nomeArquivo, Importacao.FormatoArquivo formato) {
        Importacao importacao = Importacao.builder()
                .nomeArquivo(nomeArquivo)
                .formatoArquivo(formato)
                .status(Importacao.StatusImportacao.PROCESSANDO)
                .build();
        return importacaoRepository.save(importacao);
    }

    /**
     * Record interno para encapsular o resultado da conciliação.
     */
    private record ConciliacaoResultado(
            List<ExtratoBancario> conciliados,
            List<ExtratoBancario> divergentes
    ) {}
}
