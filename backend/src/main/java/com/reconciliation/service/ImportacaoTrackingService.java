package com.reconciliation.service;

import com.reconciliation.model.Importacao;
import com.reconciliation.repository.ImportacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportacaoTrackingService {

    private final ImportacaoRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Importacao criar(String nomeArquivo, Importacao.FormatoArquivo formato) {
        return repository.save(Importacao.builder()
                .nomeArquivo(nomeArquivo)
                .formatoArquivo(formato)
                .status(Importacao.StatusImportacao.PROCESSANDO)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarErro(Long id, String mensagem) {
        Importacao importacao = repository.findById(id).orElseThrow();
        importacao.setStatus(Importacao.StatusImportacao.ERRO);
        importacao.setMensagemErro(mensagem);
        repository.save(importacao);
    }
}
