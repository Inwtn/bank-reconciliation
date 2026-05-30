package com.reconciliation.dto;

import com.reconciliation.model.TipoLancamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LancamentoRequestDTO {

    @NotNull(message = "Data do lançamento é obrigatória")
    private LocalDate dataLancamento;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser positivo")
    private BigDecimal valor;

    private String descricao;

    private String documento;

    @NotNull(message = "Tipo (DEBITO/CREDITO) é obrigatório")
    private TipoLancamento tipo;
}
