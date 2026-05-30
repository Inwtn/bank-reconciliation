package com.reconciliation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de cada importação de extrato bancário realizada.
 */
@Entity
@Table(name = "importacoes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Importacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato_arquivo")
    private FormatoArquivo formatoArquivo;

    @Column(name = "total_registros")
    private int totalRegistros;

    @Column(name = "total_conciliados")
    private int totalConciliados;

    @Column(name = "total_pendentes")
    private int totalPendentes;

    @Column(name = "total_divergentes")
    private int totalDivergentes;

    @Column(name = "data_importacao")
    private LocalDateTime dataImportacao;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatusImportacao status = StatusImportacao.PROCESSANDO;

    @Column(name = "mensagem_erro", length = 1000)
    private String mensagemErro;

    @PrePersist
    public void prePersist() {
        dataImportacao = LocalDateTime.now();
    }

    public enum FormatoArquivo {
        OFX, CSV, XLSX
    }

    public enum StatusImportacao {
        PROCESSANDO, CONCLUIDO, ERRO
    }
}
