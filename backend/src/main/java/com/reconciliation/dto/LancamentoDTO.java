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

// DTO de Lançamento
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class LancamentoDTO {
    private Long id;
    private LocalDate dataLancamento;
    private BigDecimal valor;
    private String descricao;
    private String documento;
    private TipoLancamento tipo;
    private StatusConciliacao status;
    private Long idExtratoBancario;
    private LocalDateTime createdAt;
}
