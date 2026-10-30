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

    @NotNull(message = "Entry date is required")
    private LocalDate dataLancamento;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal valor;

    private String descricao;

    private String documento;

    @NotNull(message = "Type (DEBITO/CREDITO) is required")
    private TipoLancamento tipo;
}
