package com.reconciliation;

import com.reconciliation.dto.LancamentoRequestDTO;
import com.reconciliation.model.Importacao;
import com.reconciliation.model.StatusConciliacao;
import com.reconciliation.model.TipoLancamento;
import com.reconciliation.repository.ExtratoBancarioRepository;
import com.reconciliation.repository.ImportacaoRepository;
import com.reconciliation.repository.LancamentoRepository;
import com.reconciliation.service.ConciliacaoService;
import com.reconciliation.service.LancamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PostgreSqlReconciliationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired ConciliacaoService conciliacaoService;
    @Autowired LancamentoService lancamentoService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired ExtratoBancarioRepository extratoRepository;
    @Autowired ImportacaoRepository importacaoRepository;

    @BeforeEach
    void clearDatabase() {
        extratoRepository.deleteAll();
        importacaoRepository.deleteAll();
        lancamentoRepository.deleteAll();
    }

    @Test
    void persistsMatchedStatementAndEntryInPostgres() throws Exception {
        var entry = lancamentoService.criar(LancamentoRequestDTO.builder()
                .dataLancamento(LocalDate.of(2026, 1, 15))
                .valor(new BigDecimal("1500.00"))
                .tipo(TipoLancamento.DEBITO)
                .descricao("Fornecedor")
                .build());
        var file = new MockMultipartFile("arquivo", "janeiro.csv", "text/csv",
                "Data,Descricao,Valor,Tipo\n2026-01-15,Fornecedor,-1500.00,DEBITO\n"
                        .getBytes(StandardCharsets.UTF_8));

        var result = conciliacaoService.processarExtrato(file);

        assertThat(result.getTotalConciliados()).isEqualTo(1);
        assertThat(importacaoRepository.findById(result.getIdImportacao())).isPresent();
        assertThat(extratoRepository.findAll()).hasSize(1);
        assertThat(extratoRepository.findAll().get(0).getIdLancamentoConciliado())
                .isEqualTo(entry.getId());
        assertThat(lancamentoRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusConciliacao.CONCILIADO);
    }

    @Test
    void persistsFailedImportWithoutPartialStatementRows() {
        var file = new MockMultipartFile("arquivo", "vazio.csv", "text/csv",
                "Data,Descricao,Valor,Tipo\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> conciliacaoService.processarExtrato(file))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(importacaoRepository.findAll()).hasSize(1);
        assertThat(importacaoRepository.findAll().get(0).getStatus())
                .isEqualTo(Importacao.StatusImportacao.ERRO);
        assertThat(extratoRepository.count()).isZero();
    }
}
