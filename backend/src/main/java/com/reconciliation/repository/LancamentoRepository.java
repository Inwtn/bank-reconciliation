package com.reconciliation.repository;

import com.reconciliation.model.Lancamento;
import com.reconciliation.model.StatusConciliacao;
import com.reconciliation.model.TipoLancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByStatus(StatusConciliacao status);

    List<Lancamento> findByDataLancamentoBetween(LocalDate inicio, LocalDate fim);

    /**
     * Busca um lançamento pelo valor, data e tipo para conciliação.
     * Usa uma tolerância de ±1 dia para datas.
     */
    @Query("""
        SELECT l FROM Lancamento l
        WHERE l.valor = :valor
        AND l.tipo = :tipo
        AND l.dataLancamento BETWEEN :dataInicio AND :dataFim
        AND l.status = 'PENDENTE'
        ORDER BY ABS(DATEDIFF(l.dataLancamento, :dataReferencia))
        """)
    List<Lancamento> findMatchCandidates(
            @Param("valor") BigDecimal valor,
            @Param("tipo") TipoLancamento tipo,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            @Param("dataReferencia") LocalDate dataReferencia
    );

    /**
     * Versão simplificada para match exato de data.
     */
    @Query("""
        SELECT l FROM Lancamento l
        WHERE l.valor = :valor
        AND l.tipo = :tipo
        AND l.dataLancamento = :data
        AND l.status = 'PENDENTE'
        """)
    Optional<Lancamento> findExactMatch(
            @Param("valor") BigDecimal valor,
            @Param("tipo") TipoLancamento tipo,
            @Param("data") LocalDate data
    );

    @Query("SELECT COUNT(l) FROM Lancamento l WHERE l.status = :status")
    long countByStatus(@Param("status") StatusConciliacao status);
}
