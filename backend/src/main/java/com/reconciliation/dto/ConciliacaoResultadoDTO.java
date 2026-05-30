package com.reconciliation.dto;

import com.reconciliation.model.StatusConciliacao;
import com.reconciliation.model.TipoLancamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO principal com o resultado completo de uma conciliação bancária.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConciliacaoResultadoDTO {

    private Long idImportacao;
    private String nomeArquivo;
    private LocalDateTime dataProcessamento;

    // Totalizadores
    private int totalRegistrosExtrato;
    private int totalConciliados;
    private int totalDivergentes;
    private int totalPendentesNoSistema;

    private BigDecimal valorTotalExtrato;
    private BigDecimal valorTotalConciliado;
    private BigDecimal valorTotalDivergente;

    // Percentual de conciliação
    private double percentualConciliado;

    // Itens detalhados
    private List<ItemConciliacaoDTO> itensConciliados;
    private List<ItemConciliacaoDTO> itensDivergentes;
    private List<LancamentoPendenteDTO> lancamentosPendentes;

    /**
     * Representa um item do extrato com seu status de conciliação.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemConciliacaoDTO {
        // Dados do extrato bancário
        private Long idExtrato;
        private LocalDate dataExtrato;
        private BigDecimal valorExtrato;
        private String descricaoExtrato;
        private TipoLancamento tipoExtrato;
        private StatusConciliacao status;

        // Dados do lançamento no sistema (se conciliado)
        private Long idLancamento;
        private LocalDate dataLancamento;
        private BigDecimal valorLancamento;
        private String descricaoLancamento;
        private String documentoLancamento;

        // Diferença de valor (para divergências parciais)
        private BigDecimal diferenca;
    }

    /**
     * Lançamento que existe no sistema mas não teve correspondência no extrato.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LancamentoPendenteDTO {
        private Long id;
        private LocalDate dataLancamento;
        private BigDecimal valor;
        private String descricao;
        private String documento;
        private TipoLancamento tipo;
        private long diasEmAberto;
    }
}
