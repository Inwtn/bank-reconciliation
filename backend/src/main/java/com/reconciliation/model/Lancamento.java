package com.reconciliation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representa um lançamento financeiro interno do sistema (contas a pagar/receber).
 */
@Entity
@Table(name = "lancamentos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(length = 500)
    private String descricao;

    @Column(length = 50)
    private String documento; // número NF, boleto, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoLancamento tipo; // DEBITO ou CREDITO

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatusConciliacao status = StatusConciliacao.PENDENTE;

    @Column(name = "id_extrato_bancario")
    private Long idExtratoBancario; // referência ao registro do extrato que fez match

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
