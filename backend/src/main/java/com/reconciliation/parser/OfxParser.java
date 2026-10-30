package com.reconciliation.parser;

import com.reconciliation.model.ExtratoBancario;
import com.reconciliation.model.TipoLancamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for OFX (Open Financial Exchange) bank statements.
 * Supports the SGML/XML form used by many banks.
 */
@Slf4j
@Component
public class OfxParser {

    // OFX dates use YYYYMMDD or YYYYMMDDHHMMSS.
    private static final DateTimeFormatter OFX_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern STMTTRN_PATTERN =
            Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TRNTYPE_PATTERN =
            Pattern.compile("<TRNTYPE>(.*?)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern DTPOSTED_PATTERN =
            Pattern.compile("<DTPOSTED>(.*?)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRNAMT_PATTERN =
            Pattern.compile("<TRNAMT>(.*?)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern FITID_PATTERN =
            Pattern.compile("<FITID>(.*?)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMO_PATTERN =
            Pattern.compile("<MEMO>(.*?)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PATTERN =
            Pattern.compile("<NAME>(.*?)<", Pattern.CASE_INSENSITIVE);

    public List<ExtratoBancario> parse(MultipartFile file) throws Exception {
        log.info("Parsing OFX statement: {}", file.getOriginalFilename());

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        // Some banks use ISO-8859-1.
        if (!content.contains("<OFX>") && !content.contains("<ofx>")) {
            content = new String(file.getBytes(), "ISO-8859-1");
        }

        List<ExtratoBancario> transacoes = new ArrayList<>();
        Matcher stmtMatcher = STMTTRN_PATTERN.matcher(content);

        while (stmtMatcher.find()) {
            String bloco = stmtMatcher.group(1);
            try {
                ExtratoBancario extrato = parsarBloco(bloco);
                if (extrato != null) {
                    transacoes.add(extrato);
                }
            } catch (Exception e) {
                log.warn("Could not parse OFX transaction: {}. Block: {}", e.getMessage(), bloco);
            }
        }

        log.info("OFX parsing complete: {} transactions found", transacoes.size());
        return transacoes;
    }

    private ExtratoBancario parsarBloco(String bloco) {
        String tipoStr = extrair(TRNTYPE_PATTERN, bloco);
        String dataStr = extrair(DTPOSTED_PATTERN, bloco);
        String valorStr = extrair(TRNAMT_PATTERN, bloco);
        String fitid = extrair(FITID_PATTERN, bloco);
        String memo = extrair(MEMO_PATTERN, bloco);
        String name = extrair(NAME_PATTERN, bloco);

        if (dataStr == null || valorStr == null) {
            return null;
        }

        // Keep the first eight date characters (YYYYMMDD).
        if (dataStr.length() > 8) {
            dataStr = dataStr.substring(0, 8);
        }

        LocalDate data = LocalDate.parse(dataStr, OFX_DATE_FORMAT);
        BigDecimal valor = new BigDecimal(valorStr.trim().replace(",", "."));
        TipoLancamento tipo = valor.compareTo(BigDecimal.ZERO) >= 0
                ? TipoLancamento.CREDITO
                : TipoLancamento.DEBITO;
        valor = valor.abs();

        String descricao = memo != null ? memo : (name != null ? name : "No description");

        return ExtratoBancario.builder()
                .dataTransacao(data)
                .valor(valor)
                .tipo(tipo)
                .idTransacaoBanco(fitid)
                .descricao(descricao)
                .build();
    }

    private String extrair(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
