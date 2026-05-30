package com.reconciliation.model;

public enum StatusConciliacao {
    PENDENTE,       // Ainda não foi processado pela conciliação
    CONCILIADO,     // Match encontrado com sucesso
    DIVERGENTE,     // Encontrado no extrato mas sem correspondência no sistema
    IGNORADO        // Marcado manualmente para ignorar
}
