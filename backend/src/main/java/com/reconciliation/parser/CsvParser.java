package com.reconciliation.parser;

import com.reconciliation.model.ExtratoBancario;
import com.reconciliation.model.TipoLancamento;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parser para arquivos CSV de extrato bancário.
 *
 * Formato esperado (com cabeçalho):
 * Data,Descricao,Valor,Tipo
 * 2024-01-15,Pagamento Fornecedor ABC,-1500.00,DEBITO
 * 2024-01-16,Recebimento Cliente XYZ,3000.00,CREDITO
 *
 * Aceita também o padrão de valor negativo = débito, positivo = crédito.
 */
@Slf4j
@Component
public class CsvParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    public List<ExtratoBancario> parse(MultipartFile file) throws Exception {
        log.info("Iniciando parse CSV do arquivo: {}", file.getOriginalFilename());

        List<ExtratoBancario> transacoes = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            int linha = 2;
            for (CSVRecord record : csvParser) {
                try {
                    ExtratoBancario extrato = parsarLinha(record, linha);
                    if (extrato != null) {
                        transacoes.add(extrato);
                    }
                } catch (Exception e) {
                    log.warn("Erro na linha {}: {}", linha, e.getMessage());
                }
                linha++;
            }
        }

        log.info("Parse CSV concluído. {} transações encontradas.", transacoes.size());
        return transacoes;
    }

    private ExtratoBancario parsarLinha(CSVRecord record, int linha) {
        // Tenta diferentes nomes de colunas comuns
        String dataStr = getColuna(record, "data", "date", "data_transacao", "dt_lancamento");
        String descricao = getColuna(record, "descricao", "descricão", "description", "historico", "memo");
        String valorStr = getColuna(record, "valor", "value", "amount", "vlr_lancamento");
        String tipoStr = getColuna(record, "tipo", "type", "operacao");

        if (dataStr == null || valorStr == null) {
            log.warn("Linha {} ignorada: campos obrigatórios ausentes (data ou valor)", linha);
            return null;
        }

        LocalDate data = parsearData(dataStr);
        if (data == null) {
            log.warn("Linha {}: formato de data não reconhecido: {}", linha, dataStr);
            return null;
        }

        // Remove R$, espaços, pontos de milhar e normaliza vírgula decimal
        valorStr = valorStr.replaceAll("[R$\\s]", "")
                           .replaceAll("\\.", "")
                           .replace(",", ".");
        BigDecimal valor = new BigDecimal(valorStr);

        TipoLancamento tipo;
        if (tipoStr != null && !tipoStr.isEmpty()) {
            tipo = tipoStr.toUpperCase().contains("DEB") || tipoStr.toUpperCase().contains("D")
                    ? TipoLancamento.DEBITO
                    : TipoLancamento.CREDITO;
        } else {
            // Infere pelo sinal do valor
            tipo = valor.compareTo(BigDecimal.ZERO) < 0
                    ? TipoLancamento.DEBITO
                    : TipoLancamento.CREDITO;
        }
        valor = valor.abs();

        return ExtratoBancario.builder()
                .dataTransacao(data)
                .valor(valor)
                .tipo(tipo)
                .descricao(descricao != null ? descricao : "Sem descrição")
                .idTransacaoBanco("CSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
    }

    private String getColuna(CSVRecord record, String... nomes) {
        for (String nome : nomes) {
            try {
                String valor = record.get(nome);
                if (valor != null && !valor.trim().isEmpty()) {
                    return valor.trim();
                }
            } catch (IllegalArgumentException ignored) {
                // Coluna não existe neste CSV
            }
        }
        return null;
    }

    private LocalDate parsearData(String dataStr) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dataStr.trim(), formatter);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
