package com.reconciliation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representa uma linha do extrato bancário importado (OFX ou CSV).
 */
@Entity
@Table(name = "extratos_bancarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtratoBancario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_transacao", nullable = false)
    private LocalDate dataTransacao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(length = 500)
    private String descricao;

    @Column(name = "id_transacao_banco", length = 100)
    private String idTransacaoBanco; // FITID do OFX ou ID único do CSV

    @Enumerated(EnumType.STRING)
    private TipoLancamento tipo; // DEBITO ou CREDITO

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatusConciliacao statusConciliacao = StatusConciliacao.PENDENTE;

    @Column(name = "id_importacao")
    private Long idImportacao; // qual importação gerou esse registro

    @Column(name = "id_lancamento_conciliado")
    private Long idLancamentoConciliado; // referência ao lançamento que fez match

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
