# API de Venda Concorrente

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data-JPA-007396?style=for-the-badge&logo=hibernate&logoColor=white)
![H2 Database](https://img.shields.io/badge/Database-H2%20in--memory-003B57?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Concluído-blue?style=for-the-badge)

API e motor de controle transacional desenvolvidos em Java 21 com Spring Boot focados na resolução do Problema 2 (Venda do Último Item em Estoque) da trilha de engenharia de software para raciocínio técnico. O projeto aborda a prevenção de venda duplicada (*overselling* e *lost update*) sob alta concorrência de requisições simultâneas disputando uma única unidade física.

## O Problema Investigado

> Cenário: Restou uma unidade de um produto, mas duas pessoas tentam comprá-la simultaneamente. Ambas consultam o estoque e recebem a informação de que há disponibilidade. Como impedir a venda duplicada?

Implementações ingênuas baseadas em leitura e escrita sequencial na aplicação (`if (estoque >= qtd)`) falham criticamente em ambientes multi-thread (*Time-of-check to time-of-use - TOCTOU*). Ambas as threads leem o saldo como disponível antes que qualquer uma grave a baixa, vendendo o mesmo produto duas vezes e gerando inconsistência de estoque negativo ou venda fantasma.

## Decisões Técnicas e Arquitetura

O sistema emprega a estratégia de **Pessimistic Locking (Bloqueio Pessimista de Linha)** no nível do motor relacional, coordenado por transações atômicas:

```text
Thread A (Cliente 1)               Banco de Dados (tb_produto)             Thread B (Cliente 2)
        │                                       │                                   │
        ├── 1. findByIdWithLock(id) ───────────►│                                   │
        │      (SELECT ... FOR UPDATE)          │                                   │
        │                                       ├── [Linha Bloqueada por A]         │
        │                                       │◄── 2. findByIdWithLock(id) ───────┤
        │                                       │       (Aguardando trava de A...)  │
        ├── 3. Valida: estoque >= 1             │                                   │
        ├── 4. Decrementa (1 - 1 = 0)           │                                   │
        ├── 5. COMMIT transação ───────────────►│                                   │
        │      (Libera a trava de linha)        ├── [Trava Liberada]                │
        │                                       ├── [Entrega linha atualizada] ────►│
        ▼                                       │                                   ├── 6. Valida: estoque == 0
  Compra Aprovada!                              │                                   ├── 7. Lança IllegalStateException
                                                │                                   ▼
                                                │                             Estoque Insuficiente!
```

### 1. Bloqueio Pessimista de Escrita (PESSIMISTIC_WRITE)
Ao utilizar a anotação `@Lock(LockModeType.PESSIMISTIC_WRITE)`, o Hibernate traduz a consulta para a instrução SQL `SELECT ... FOR UPDATE`. Isso força o banco de dados a obter um bloqueio exclusivo (*row-level lock*) no registro correspondente. Qualquer outra transação concorrente que tente ler o mesmo registro para escrita é automaticamente enfileirada no banco até que a transação detentora da trava execute `COMMIT` ou `ROLLBACK`.

### 2. Ciclo de Vida da Trava (@Transactional)
O bloqueio pessimista reside estritamente dentro dos limites de uma transação ativa. A anotação `@Transactional` na camada de serviço delimita o escopo de atomicidade: a busca, a validação de regra de negócio, a dedução do saldo e a persistência ocorrem em uma única janela de isolamento ininterrupta.

### 3. Vantagem em Cenários de Alta Colisão
Diferente do Bloqueio Otimista (`@Version`), que permitiria que todas as threads lessem livremente e forçaria exceções de colisão com retentativas contínuas (*rollback storms*), o Bloqueio Pessimista organiza o fluxo em fila de forma determinística, ideal para disputas pontuais de alta concorrência como o último item em estoque.

## Tecnologias Utilizadas

| Categoria | Tecnologia / Recurso |
| :--- | :--- |
| **Linguagem & Runtime** | Java 21 |
| **Framework Base** | Spring Boot, Injeção de Dependências |
| **Persistência & Concorrência** | Spring Data JPA, Hibernate, LockModeType.PESSIMISTIC_WRITE |
| **Banco de Dados** | H2 Database (em memória) |
| **Testes & Sincronização** | JUnit 5, Spring Boot Test, CountDownLatch, AtomicInteger, ExecutorService |
| **Produtividade** | Lombok |

## Estrutura do Projeto

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── example/
│   │           └── api_venda_concorrente/
│   │               ├── model/
│   │               │   └── Produto.java               # Entidade com ID IDENTITY e estoque
│   │               ├── repository/
│   │               │   └── ProdutoRepository.java     # Consulta com @Lock PESSIMISTIC_WRITE
│   │               ├── service/
│   │               │   └── ProdutoService.java        # Transação de baixa com validação
│   │               └── ApiVendaConcorrenteApplication.java
│   └── resources/
│       └── application.properties
└── test/
    └── java/
        └── com/
            └── example/
                └── api_venda_concorrente/
                    └── ProdutoIntegrationTest.java    # Teste de colisão com CountDownLatch
```

## Evidência de Testes e Concorrência

O comportamento concorrente foi comprovado empiricamente através de teste de integração real com `@SpringBootTest`:

1. **Simulação de Largada Simultânea (`CountDownLatch`):**
   - Um produto é cadastrado com estoque inicial de apenas `1` unidade.
   - Dois compradores concorrentes são despachados em threads separadas através de um `ExecutorService`.
   - Um `CountDownLatch(1)` sincroniza as duas threads para dispararem a solicitação de compra exatamente no mesmo instante.
2. **Resultados e Asserções:**
   - Compras com sucesso computadas (`AtomicInteger`): exatamente `1`.
   - Compras rejeitadas com erro de estoque (`AtomicInteger`): exatamente `1`.
   - Saldo final verificado no banco: rigorosamente `0` (sem saldo negativo e sem duplicação de venda).

Para executar a suite de testes:

```sh
./mvnw test
```

## Como Executar a Aplicação

### Pré-requisitos
- JDK 21 ou superior instalado
- Git

### Execução local
1. Clone o repositório:
```sh
git clone <url-do-repositorio>
cd api-venda-concorrente
```

2. Execute os testes automatizados via Maven Wrapper:
```sh
./mvnw test
```
