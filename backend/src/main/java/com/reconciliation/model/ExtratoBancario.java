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
 * One transaction from an imported OFX or CSV bank statement.
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
    private String idTransacaoBanco; // OFX FITID or generated CSV transaction ID

    @Enumerated(EnumType.STRING)
    private TipoLancamento tipo; // DEBITO ou CREDITO

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatusConciliacao statusConciliacao = StatusConciliacao.PENDENTE;

    @Column(name = "id_importacao")
    private Long idImportacao; // import that produced this row

    @Column(name = "id_lancamento_conciliado")
    private Long idLancamentoConciliado; // linked internal entry

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
