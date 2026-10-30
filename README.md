# Bank Reconciliation Engine

![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql)
![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=flat-square&logo=apachemaven)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=flat-square&logo=docker)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

[![Java CI](https://github.com/Inwtn/bank-reconciliation/actions/workflows/ci.yml/badge.svg)](https://github.com/Inwtn/bank-reconciliation/actions/workflows/ci.yml)

Motor de conciliação bancária automática que cruza extratos bancários (OFX/CSV) com os lançamentos financeiros registrados no sistema, identificando o que foi pago, o que está pendente e onde há divergências.

---

## O problema que resolve

Empresas que gerenciam fluxo de caixa precisam comparar manualmente o extrato bancário com suas contas a pagar e receber — um processo lento e sujeito a erros. Este sistema automatiza esse cruzamento de dados, entregando em segundos um relatório completo de conciliação.

---

## Funcionalidades

- Upload de extratos nos formatos OFX e CSV
- Matching automático por valor, tipo e data (com tolerância configurável de dias)
- Classificação de cada transação como Conciliado, Divergente ou Pendente
- Dashboard web para visualização dos resultados
- Histórico de todas as importações realizadas
- API REST documentada com Swagger UI
- Acesso por HTTP Basic com papéis `admin` (leitura e escrita) e `viewer` (somente leitura)
- Health check público e métricas Prometheus acessíveis apenas ao administrador
- Testes de integração com PostgreSQL real via Testcontainers e pipeline de CI

---

## Arquitetura

```
bank-reconciliation/
├── backend/
│   └── src/main/java/com/reconciliation/
│       ├── model/
│       │   ├── Lancamento.java           # Contas a pagar/receber
│       │   ├── ExtratoBancario.java      # Linhas do extrato importado
│       │   ├── Importacao.java           # Histórico de importações
│       │   ├── TipoLancamento.java       # DEBITO | CREDITO
│       │   └── StatusConciliacao.java    # PENDENTE | CONCILIADO | DIVERGENTE
│       ├── parser/
│       │   ├── OfxParser.java            # Parser de arquivos OFX (SGML/XML)
│       │   └── CsvParser.java            # Parser de arquivos CSV
│       ├── service/
│       │   ├── ConciliacaoService.java   # Motor principal de conciliação
│       │   └── LancamentoService.java
│       ├── controller/
│       │   ├── ConciliacaoController.java
│       │   └── LancamentoController.java
│       ├── dto/
│       ├── repository/
│       └── config/
├── frontend/
│   └── dashboard.html
├── docker-compose.yml
├── init.sql
└── README.md
```

---

## Algoritmo de Matching

Para cada transação do extrato bancário importado:

1. Busca match exato — mesmo valor, tipo e data com status PENDENTE no sistema
2. Se não encontrar — repete a busca com tolerância de ±1 dia
3. Se houver candidatos — escolhe o lançamento com data mais próxima
4. Se conciliado — ambos os registros são marcados como CONCILIADO e vinculados entre si
5. Se sem match — transação do extrato marcada como DIVERGENTE

Ao final, lançamentos que ainda estão PENDENTE são os que não apareceram no extrato.

A tolerância de dias é configurável em `ConciliacaoService.java`:

```java
private static final int TOLERANCIA_DIAS = 1;
```

---

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.2 |
| Banco de dados | PostgreSQL 15 |
| ORM | Spring Data JPA / Hibernate |
| Parse CSV | Apache Commons CSV |
| Parse Excel | Apache POI |
| Documentação | SpringDoc OpenAPI (Swagger) |
| Testes | JUnit 5 + Spring Boot Test |
| Containerização | Docker + Docker Compose |

---

## Como Rodar

### Pré-requisitos

- Java 17+
- Maven 3.9+
- Docker e Docker Compose

### 1. Clonar o repositório

```bash
git clone https://github.com/Inwtn/bank-reconciliation.git
cd bank-reconciliation
```

### 2. Configurar credenciais locais

Copie `.env.example` para `.env` e substitua os três valores de exemplo por senhas diferentes. O arquivo `.env` é ignorado pelo Git. As credenciais não têm valores padrão na API.

### 3. Subir a aplicação

```bash
docker compose up --build
```

A API estará em `http://localhost:8080/api`. O banco só fica disponível depois do health check do PostgreSQL.

Para executar a API fora do Docker, inicie o PostgreSQL e defina `APP_ADMIN_PASSWORD` e `APP_VIEWER_PASSWORD` no ambiente antes de usar Maven:

```bash
cd backend
mvn spring-boot:run
```

`frontend/dashboard.html` é uma demonstração local do fluxo de conciliação. Ela simula os dados no navegador e ainda não consome a API autenticada; use o Swagger UI ou um cliente HTTP para testar a API real.

### Autenticação e observabilidade

- Usuário `viewer`: leitura de lançamentos, histórico e documentação da API.
- Usuário `admin`: leitura, criação, importação, exclusão e acesso às métricas.
- `GET /api/actuator/health`: público, usado pelo health check do container.
- `GET /api/actuator/prometheus`: requer `admin`; inclui contadores e duração de importações por resultado.

Exemplos:

```bash
curl -u viewer:SUA_SENHA http://localhost:8080/api/lancamentos
curl -u admin:SUA_SENHA http://localhost:8080/api/actuator/prometheus
```

HTTP Basic deve ser usado sobre HTTPS fora do ambiente local. Para uma implantação pública, configure senhas pelo gerenciador de segredos da plataforma e não exponha o PostgreSQL diretamente.

---

## Endpoints

### Conciliacao

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/conciliacao/importar` | Upload do extrato (OFX ou CSV) |
| `GET` | `/api/conciliacao/historico` | Lista todas as importações |
| `GET` | `/api/conciliacao/status` | Health check |

### Lancamentos

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/lancamentos` | Registrar lançamento |
| `GET` | `/api/lancamentos` | Listar lançamentos |
| `GET` | `/api/lancamentos?status=PENDENTE` | Filtrar por status |
| `DELETE` | `/api/lancamentos/{id}` | Excluir lançamento |
| `GET` | `/api/lancamentos/dashboard/stats` | Estatísticas gerais |

Documentação interativa disponível em `http://localhost:8080/api/swagger-ui`, com autenticação.

---

## Formato dos Arquivos

### CSV

```csv
Data,Descricao,Valor,Tipo
2024-01-15,Pagamento Fornecedor ABC,-1500.00,DEBITO
2024-01-16,Recebimento Cliente XYZ,3200.00,CREDITO
```

Colunas aceitas:

- **Data** — `data`, `date`, `data_transacao`. Formatos: `yyyy-MM-dd` ou `dd/MM/yyyy`
- **Descricao** — `descricao`, `description`, `historico`, `memo`
- **Valor** — `valor`, `value`, `amount`. Valor negativo é interpretado como débito
- **Tipo** — `tipo`, `type`. Aceita `DEBITO` ou `CREDITO` (opcional se o valor tiver sinal)

### OFX

Formato SGML/OFX padrão dos bancos brasileiros. O parser extrai automaticamente as tags `<STMTTRN>`.

```xml
<STMTTRN>
  <TRNTYPE>DEBIT</TRNTYPE>
  <DTPOSTED>20240115</DTPOSTED>
  <TRNAMT>-1500.00</TRNAMT>
  <FITID>20240115001</FITID>
  <MEMO>Pagamento Fornecedor ABC</MEMO>
</STMTTRN>
```

---

## Testes

```bash
cd backend
mvn test
```

Os testes cobrem:

- Match exato de data e valor
- Transação sem correspondência marcada como divergente
- Multiplas transacoes com lançamentos pendentes
- Calculo correto do percentual de conciliacao
- Autenticação, permissões de leitura e escrita e acesso às métricas
- Persistência de uma conciliação em PostgreSQL 15 usando Testcontainers

`mvn verify` é executado no GitHub Actions a cada pull request e push para `main`. O teste com PostgreSQL exige Docker disponível no ambiente de testes.

---

## Variaveis de Ambiente

```env
DB_URL=jdbc:postgresql://localhost:5432/bank_reconciliation
DB_USER=postgres
DB_PASSWORD=uma-senha-local
APP_ADMIN_PASSWORD=uma-senha-forte
APP_VIEWER_PASSWORD=outra-senha-forte
```

---

## Licença

Distribuído sob a licença MIT. Veja `LICENSE` para mais informações.
