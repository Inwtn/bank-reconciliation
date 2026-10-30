package com.reconciliation.model;

public enum StatusConciliacao {
    PENDENTE,       // Internal entry has not been matched
    CONCILIADO,     // Successfully matched
    DIVERGENTE,     // Statement transaction without a matching internal entry
    IGNORADO        // Manually marked to ignore
}
