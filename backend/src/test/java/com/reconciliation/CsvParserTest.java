package com.reconciliation;

import com.reconciliation.model.TipoLancamento;
import com.reconciliation.parser.CsvParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    void preservesDecimalValuesAndCreditType() throws Exception {
        String csv = """
                Data,Descricao,Valor,Tipo
                2026-01-15,Recebimento,1500.00,CREDITO
                2026-01-16,Pagamento,-1500.00,DEBITO
                2026-01-17,Recebimento local,"1.500,00",CREDITO
                """;
        var file = new MockMultipartFile("arquivo", "extrato.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        var transactions = parser.parse(file);

        assertThat(transactions).hasSize(3);
        assertThat(transactions).allSatisfy(item ->
                assertThat(item.getValor()).isEqualByComparingTo(new BigDecimal("1500.00")));
        assertThat(transactions.get(0).getTipo()).isEqualTo(TipoLancamento.CREDITO);
        assertThat(transactions.get(1).getTipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(transactions.get(2).getTipo()).isEqualTo(TipoLancamento.CREDITO);
    }
}
