# ⚡ Motor de Conciliação Bancária

Sistema de conciliação automática de extratos bancários com Spring Boot + PostgreSQL.

## 🏗️ Arquitetura

```
bank-reconciliation/
├── backend/                          # API Spring Boot
│   ├── src/main/java/com/reconciliation/
│   │   ├── model/                    # Entidades JPA
│   │   │   ├── Lancamento.java       # Contas a pagar/receber
│   │   │   ├── ExtratoBancario.java  # Linhas do extrato importado
│   │   │   ├── Importacao.java       # Histórico de importações
│   │   │   ├── TipoLancamento.java   # DEBITO | CREDITO
│   │   │   └── StatusConciliacao.java# PENDENTE | CONCILIADO | DIVERGENTE
│   │   ├── parser/
│   │   │   ├── OfxParser.java        # Parser de arquivos OFX (SGML/XML)
│   │   │   └── CsvParser.java        # Parser de arquivos CSV
│   │   ├── service/
│   │   │   ├── ConciliacaoService.java # ← MOTOR PRINCIPAL
│   │   │   └── LancamentoService.java
│   │   ├── controller/
│   │   │   ├── ConciliacaoController.java
│   │   │   └── LancamentoController.java
│   │   ├── dto/                      # DTOs de request/response
│   │   ├── repository/               # Repositórios JPA
│   │   └── config/                   # CORS, Swagger, Exception Handler
│   └── src/test/                     # Testes de integração
├── frontend/
│   └── dashboard.html                # Dashboard interativo
├── docker-compose.yml                # PostgreSQL + API
└── init.sql                          # Dados de exemplo
```

## 🚀 Como Rodar

### Pré-requisitos
- Java 17+
- Maven 3.9+
- Docker + Docker Compose (para PostgreSQL)

### 1. Subir o banco de dados
```bash
docker-compose up postgres -d
```

### 2. Rodar a API
```bash
cd backend
mvn spring-boot:run
```

A API sobe em `http://localhost:8080/api`

### 3. Rodar tudo com Docker
```bash
docker-compose up --build
```

## 🔌 Endpoints da API

### Conciliação
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/conciliacao/importar` | Upload do extrato (OFX/CSV) |
| `GET` | `/api/conciliacao/historico` | Lista todas as importações |
| `GET` | `/api/conciliacao/status` | Health check |

### Lançamentos
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/lancamentos` | Criar lançamento |
| `GET` | `/api/lancamentos` | Listar lançamentos |
| `GET` | `/api/lancamentos?status=PENDENTE` | Filtrar por status |
| `DELETE` | `/api/lancamentos/{id}` | Excluir lançamento |
| `GET` | `/api/lancamentos/dashboard/stats` | Estatísticas |

### Swagger UI
Acesse `http://localhost:8080/api/swagger-ui` para a documentação interativa.

## 📄 Formato dos Arquivos

### CSV
```csv
Data,Descricao,Valor,Tipo
2024-01-15,Pagamento Fornecedor ABC,-1500.00,DEBITO
2024-01-16,Recebimento Cliente XYZ,3200.00,CREDITO
```

**Colunas aceitas:**
- **Data**: `data`, `date`, `data_transacao` — Formatos: `yyyy-MM-dd`, `dd/MM/yyyy`
- **Descrição**: `descricao`, `description`, `historico`, `memo`
- **Valor**: `valor`, `value`, `amount` — Negativo = débito (alternativo ao campo Tipo)
- **Tipo**: `tipo`, `type` — `DEBITO` ou `CREDITO` (opcional se valor tiver sinal)

### OFX
Formato padrão SGML/OFX usado pelos bancos brasileiros. O parser extrai as tags `<STMTTRN>` automaticamente.

```xml
<STMTTRN>
  <TRNTYPE>DEBIT</TRNTYPE>
  <DTPOSTED>20240115</DTPOSTED>
  <TRNAMT>-1500.00</TRNAMT>
  <FITID>20240115001</FITID>
  <MEMO>Pagamento Fornecedor ABC</MEMO>
</STMTTRN>
```

## ⚙️ Algoritmo de Matching

```
Para cada transação do extrato:
  1. Busca MATCH EXATO: valor = valor AND tipo = tipo AND data = data (PENDENTE)
  2. Se não achar → Busca com TOLERÂNCIA ±1 dia
  3. Se encontrar candidatos → escolhe o de data mais próxima
  4. Se conciliado → ambos marcados como CONCILIADO, IDs vinculados
  5. Se sem match → extrato marcado como DIVERGENTE
  
Ao final → lançamentos ainda PENDENTE = não encontrados no extrato
```

### Tolerância de data
Configurável em `ConciliacaoService.java`:
```java
private static final int TOLERANCIA_DIAS = 1; // padrão: ±1 dia
```

## 🧪 Testes

```bash
cd backend
mvn test
```

Os testes cobrem:
- ✅ Match exato de data e valor
- ✅ Divergência quando não há correspondência
- ✅ Múltiplas transações com pendentes
- ✅ Lançamentos pendentes sem match

## 🐳 Variáveis de Ambiente

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bank_reconciliation
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

## 📊 Dashboard

Abra `frontend/dashboard.html` no navegador para o dashboard interativo que permite:
- Registrar lançamentos financeiros
- Importar extratos OFX/CSV
- Visualizar conciliados, divergentes e pendentes
- Acompanhar taxa de conciliação em tempo real
