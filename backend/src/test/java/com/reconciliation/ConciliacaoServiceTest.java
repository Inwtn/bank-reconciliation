package com.reconciliation.service;

import com.reconciliation.dto.LancamentoRequestDTO;
import com.reconciliation.model.Lancamento;
import com.reconciliation.model.StatusConciliacao;
import com.reconciliation.model.TipoLancamento;
import com.reconciliation.repository.ExtratoBancarioRepository;
import com.reconciliation.repository.ImportacaoRepository;
import com.reconciliation.repository.LancamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Testes de Integração - Motor de Conciliação")
class ConciliacaoServiceTest {

    @Autowired
    private ConciliacaoService conciliacaoService;

    @Autowired
    private LancamentoService lancamentoService;

    @Autowired
    private LancamentoRepository lancamentoRepository;

    @Autowired
    private ExtratoBancarioRepository extratoRepository;

    @Autowired
    private ImportacaoRepository importacaoRepository;

    @BeforeEach
    void limparBase() {
        extratoRepository.deleteAll();
        importacaoRepository.deleteAll();
        lancamentoRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve conciliar lançamento com match exato de data e valor")
    void deveConciliarComMatchExato() throws Exception {
        // ARRANGE: Cria lançamento no sistema
        lancamentoService.criar(LancamentoRequestDTO.builder()
                .dataLancamento(LocalDate.of(2024, 1, 15))
                .valor(new BigDecimal("1500.00"))
                .tipo(TipoLancamento.DEBITO)
                .descricao("Pagamento Fornecedor ABC")
                .documento("NF-001")
                .build());

        // Cria CSV com a mesma transação
        String csvContent = """
                Data,Descricao,Valor,Tipo
                2024-01-15,Pagamento Fornecedor ABC,-1500.00,DEBITO
                """;

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "extrato_jan.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // ACT
        var resultado = conciliacaoService.processarExtrato(arquivo);

        // ASSERT
        assertThat(resultado.getTotalConciliados()).isEqualTo(1);
        assertThat(resultado.getTotalDivergentes()).isEqualTo(0);
        assertThat(resultado.getPercentualConciliado()).isEqualTo(100.0);
        assertThat(resultado.getItensConciliados()).hasSize(1);

        // Verifica que o lançamento foi marcado como CONCILIADO no banco
        Lancamento lancamento = lancamentoRepository.findAll().get(0);
        assertThat(lancamento.getStatus()).isEqualTo(StatusConciliacao.CONCILIADO);
    }

    @Test
    @DisplayName("Deve marcar como DIVERGENTE transação sem lançamento correspondente")
    void deveMarcarDivergenteSemMatch() throws Exception {
        // ARRANGE: Nenhum lançamento no sistema
        String csvContent = """
                Data,Descricao,Valor,Tipo
                2024-01-20,Transacao Desconhecida,-800.00,DEBITO
                """;

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "extrato.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // ACT
        var resultado = conciliacaoService.processarExtrato(arquivo);

        // ASSERT
        assertThat(resultado.getTotalConciliados()).isEqualTo(0);
        assertThat(resultado.getTotalDivergentes()).isEqualTo(1);
        assertThat(resultado.getPercentualConciliado()).isEqualTo(0.0);
        assertThat(resultado.getItensDivergentes()).hasSize(1);
        assertThat(resultado.getItensDivergentes().get(0).getValorExtrato())
                .isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("Deve conciliar múltiplas transações e reportar pendentes")
    void deveConciliarMultiplasTransacoes() throws Exception {
        // ARRANGE: 3 lançamentos no sistema
        lancamentoService.criar(LancamentoRequestDTO.builder()
                .dataLancamento(LocalDate.of(2024, 2, 1))
                .valor(new BigDecimal("2000.00"))
                .tipo(TipoLancamento.CREDITO)
                .descricao("Recebimento Cliente X")
                .build());

        lancamentoService.criar(LancamentoRequestDTO.builder()
                .dataLancamento(LocalDate.of(2024, 2, 5))
                .valor(new BigDecimal("500.00"))
                .tipo(TipoLancamento.DEBITO)
                .descricao("Pagamento Aluguel")
                .build());

        // Terceiro lançamento que NÃO vai aparecer no extrato (ficará PENDENTE)
        lancamentoService.criar(LancamentoRequestDTO.builder()
                .dataLancamento(LocalDate.of(2024, 2, 10))
                .valor(new BigDecimal("300.00"))
                .tipo(TipoLancamento.DEBITO)
                .descricao("Parcela Financiamento")
                .build());

        // Extrato com 2 transações (match 1 e 2) + 1 desconhecida
        String csvContent = """
                Data,Descricao,Valor,Tipo
                2024-02-01,Recebimento Cliente X,2000.00,CREDITO
                2024-02-05,Pagamento Aluguel,-500.00,DEBITO
                2024-02-07,Taxa Bancaria Desconhecida,-45.00,DEBITO
                """;

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "extrato_fev.csv", "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // ACT
        var resultado = conciliacaoService.processarExtrato(arquivo);

        // ASSERT
        assertThat(resultado.getTotalRegistrosExtrato()).isEqualTo(3);
        assertThat(resultado.getTotalConciliados()).isEqualTo(2);
        assertThat(resultado.getTotalDivergentes()).isEqualTo(1);
        assertThat(resultado.getTotalPendentesNoSistema()).isEqualTo(1);  // "Parcela Financiamento"

        // Percentual correto: 2/3 = 66.67%
        assertThat(resultado.getPercentualConciliado()).isEqualTo(66.67);
    }

    @Test
    @DisplayName("Deve identificar lançamentos pendentes no sistema")
    void deveIdentificarLancamentosPendentes() throws Exception {
        // ARRANGE
        lancamentoService.criar(LancamentoRequestDTO.builder()
                .dataLancamento(LocalDate.now().minusDays(30))
                .valor(new BigDecimal("1200.00"))
                .tipo(TipoLancamento.DEBITO)
                .descricao("Conta de Energia - Vencida")
                .build());

        // Extrato vazio (sem match)
        String csvContent = "Data,Descricao,Valor,Tipo\n";
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "extrato_vazio.csv", "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // ACT & ASSERT
        // Arquivo vazio deve lançar exceção
        try {
            conciliacaoService.processarExtrato(arquivo);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Nenhuma transação encontrada");
        }

        // Lançamento ainda deve estar PENDENTE
        Lancamento lancamento = lancamentoRepository.findAll().get(0);
        assertThat(lancamento.getStatus()).isEqualTo(StatusConciliacao.PENDENTE);
    }
}
