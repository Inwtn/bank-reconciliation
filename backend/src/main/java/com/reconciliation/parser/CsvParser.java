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
 * Parser for CSV bank statements.
 *
 * Expected format with headers:
 * Date,Description,Amount,Type
 * 2026-10-29,Supplier payment,-1500.00,DEBIT
 * 2026-10-29,Customer payment,3000.00,CREDIT
 *
 * Signed amounts can also determine debit or credit when Type is absent.
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
        log.info("Parsing CSV statement: {}", file.getOriginalFilename());

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
                    log.warn("CSV line {} could not be parsed: {}", linha, e.getMessage());
                }
                linha++;
            }
        }

        log.info("CSV parsing complete: {} transactions found", transacoes.size());
        return transacoes;
    }

    private ExtratoBancario parsarLinha(CSVRecord record, int linha) {
        // Accept common English and Portuguese column names.
        String dataStr = getColuna(record, "data", "date", "data_transacao", "dt_lancamento");
        String descricao = getColuna(record, "descricao", "descricão", "description", "historico", "memo");
        String valorStr = getColuna(record, "valor", "value", "amount", "vlr_lancamento");
        String tipoStr = getColuna(record, "tipo", "type", "operacao");

        if (dataStr == null || valorStr == null) {
            log.warn("CSV line {} ignored: date or amount is missing", linha);
            return null;
        }

        LocalDate data = parsearData(dataStr);
        if (data == null) {
            log.warn("CSV line {} has an unrecognized date: {}", linha, dataStr);
            return null;
        }

        BigDecimal valor = new BigDecimal(normalizarValor(valorStr));

        TipoLancamento tipo;
        if (tipoStr != null && !tipoStr.isEmpty()) {
            String tipoNormalizado = tipoStr.trim().toUpperCase();
            if (tipoNormalizado.equals("D") || tipoNormalizado.startsWith("DEB")) {
                tipo = TipoLancamento.DEBITO;
            } else if (tipoNormalizado.equals("C") || tipoNormalizado.startsWith("CRED")) {
                tipo = TipoLancamento.CREDITO;
            } else {
                throw new IllegalArgumentException("Invalid transaction type: " + tipoStr);
            }
        } else {
            // Infer the type from the amount's sign.
            tipo = valor.compareTo(BigDecimal.ZERO) < 0
                    ? TipoLancamento.DEBITO
                    : TipoLancamento.CREDITO;
        }
        valor = valor.abs();

        return ExtratoBancario.builder()
                .dataTransacao(data)
                .valor(valor)
                .tipo(tipo)
                .descricao(descricao != null ? descricao : "No description")
                .idTransacaoBanco("CSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
    }

    private String normalizarValor(String original) {
        String valor = original.replaceAll("[R$\\s]", "");
        int ultimoPonto = valor.lastIndexOf('.');
        int ultimaVirgula = valor.lastIndexOf(',');

        if (ultimoPonto >= 0 && ultimaVirgula >= 0) {
            // The last separator is the decimal separator; the other is for thousands.
            return ultimoPonto > ultimaVirgula
                    ? valor.replace(",", "")
                    : valor.replace(".", "").replace(',', '.');
        }
        if (ultimaVirgula >= 0) {
            return valor.length() - ultimaVirgula - 1 == 3
                    ? valor.replace(",", "")
                    : valor.replace(',', '.');
        }
        if (ultimoPonto >= 0 && valor.length() - ultimoPonto - 1 == 3) {
            return valor.replace(".", "");
        }
        return valor;
    }

    private String getColuna(CSVRecord record, String... nomes) {
        for (String nome : nomes) {
            try {
                String valor = record.get(nome);
                if (valor != null && !valor.trim().isEmpty()) {
                    return valor.trim();
                }
            } catch (IllegalArgumentException ignored) {
                // This column is absent from the CSV.
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
