package com.reconciliation.service;

import com.reconciliation.dto.LancamentoRequestDTO;
import com.reconciliation.model.Lancamento;
import com.reconciliation.model.StatusConciliacao;
import com.reconciliation.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LancamentoService {

    private final LancamentoRepository lancamentoRepository;

    @Transactional
    public Lancamento criar(LancamentoRequestDTO dto) {
        Lancamento lancamento = Lancamento.builder()
                .dataLancamento(dto.getDataLancamento())
                .valor(dto.getValor())
                .descricao(dto.getDescricao())
                .documento(dto.getDocumento())
                .tipo(dto.getTipo())
                .status(StatusConciliacao.PENDENTE)
                .build();
        return lancamentoRepository.save(lancamento);
    }

    public List<Lancamento> listarTodos() {
        return lancamentoRepository.findAll();
    }

    public List<Lancamento> listarPorStatus(StatusConciliacao status) {
        return lancamentoRepository.findByStatus(status);
    }

    public Lancamento buscarPorId(Long id) {
        return lancamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lançamento não encontrado: " + id));
    }

    @Transactional
    public void excluir(Long id) {
        lancamentoRepository.deleteById(id);
    }

    public DashboardStats obterStats() {
        long total = lancamentoRepository.count();
        long conciliados = lancamentoRepository.countByStatus(StatusConciliacao.CONCILIADO);
        long pendentes = lancamentoRepository.countByStatus(StatusConciliacao.PENDENTE);
        long divergentes = lancamentoRepository.countByStatus(StatusConciliacao.DIVERGENTE);
        return new DashboardStats(total, conciliados, pendentes, divergentes);
    }

    public record DashboardStats(long total, long conciliados, long pendentes, long divergentes) {}
}
