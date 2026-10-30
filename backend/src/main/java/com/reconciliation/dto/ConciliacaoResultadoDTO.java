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
 * Complete result of one bank statement reconciliation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConciliacaoResultadoDTO {

    private Long idImportacao;
    private String nomeArquivo;
    private LocalDateTime dataProcessamento;

    // Totals
    private int totalRegistrosExtrato;
    private int totalConciliados;
    private int totalDivergentes;
    private int totalPendentesNoSistema;

    private BigDecimal valorTotalExtrato;
    private BigDecimal valorTotalConciliado;
    private BigDecimal valorTotalDivergente;

    // Match percentage
    private double percentualConciliado;

    // Detailed items
    private List<ItemConciliacaoDTO> itensConciliados;
    private List<ItemConciliacaoDTO> itensDivergentes;
    private List<LancamentoPendenteDTO> lancamentosPendentes;

    /**
     * One statement item with its reconciliation status.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemConciliacaoDTO {
        // Bank statement fields
        private Long idExtrato;
        private LocalDate dataExtrato;
        private BigDecimal valorExtrato;
        private String descricaoExtrato;
        private TipoLancamento tipoExtrato;
        private StatusConciliacao status;

        // Linked internal entry, when matched
        private Long idLancamento;
        private LocalDate dataLancamento;
        private BigDecimal valorLancamento;
        private String descricaoLancamento;
        private String documentoLancamento;

        // Amount difference for partial discrepancies
        private BigDecimal diferenca;
    }

    /**
     * Internal entry without a matching statement transaction.
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
