package com.reconciliation.repository;

import com.reconciliation.model.ExtratoBancario;
import com.reconciliation.model.StatusConciliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtratoBancarioRepository extends JpaRepository<ExtratoBancario, Long> {

    List<ExtratoBancario> findByIdImportacao(Long idImportacao);

    List<ExtratoBancario> findByStatusConciliacao(StatusConciliacao status);

    long countByIdImportacaoAndStatusConciliacao(Long idImportacao, StatusConciliacao status);
}
