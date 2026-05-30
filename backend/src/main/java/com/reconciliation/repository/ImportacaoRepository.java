package com.reconciliation.repository;

import com.reconciliation.model.Importacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {
    List<Importacao> findAllByOrderByDataImportacaoDesc();
}
