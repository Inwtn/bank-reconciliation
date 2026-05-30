-- =====================================================
-- Motor de Conciliação Bancária - Script de Inicialização
-- =====================================================

-- As tabelas são criadas pelo Hibernate (ddl-auto=update)
-- Este script insere dados de exemplo para demonstração

-- Aguarda o Hibernate criar as tabelas (este script roda primeiro)
-- Execute manualmente após o primeiro start se quiser dados de exemplo

-- Dados de exemplo: Lançamentos financeiros do sistema
INSERT INTO lancamentos (data_lancamento, valor, descricao, documento, tipo, status, created_at, updated_at)
VALUES
    ('2024-01-15', 1500.00, 'Pagamento Fornecedor ABC Ltda', 'NF-2024-001', 'DEBITO', 'PENDENTE', NOW(), NOW()),
    ('2024-01-18', 3200.00, 'Recebimento Cliente XYZ - NF 1042', 'NF-1042', 'CREDITO', 'PENDENTE', NOW(), NOW()),
    ('2024-01-22', 800.00, 'Aluguel Escritório Janeiro', 'ALUG-JAN', 'DEBITO', 'PENDENTE', NOW(), NOW()),
    ('2024-01-25', 5000.00, 'Projeto Alpha - Entrega 1', 'CONT-001', 'CREDITO', 'PENDENTE', NOW(), NOW()),
    ('2024-01-28', 450.00, 'Conta de Energia Elétrica', 'CELESC-JAN', 'DEBITO', 'PENDENTE', NOW(), NOW()),
    ('2024-02-05', 2750.00, 'Recebimento Cliente Beta', 'NF-1055', 'CREDITO', 'PENDENTE', NOW(), NOW()),
    ('2024-02-10', 1200.00, 'Pagamento Parcela Financiamento', 'FIN-2024-02', 'DEBITO', 'PENDENTE', NOW(), NOW()),
    ('2024-02-15', 600.00, 'Internet e Telefonia', 'VIVO-FEV', 'DEBITO', 'PENDENTE', NOW(), NOW())
ON CONFLICT DO NOTHING;
