# REPOSITÓRIO RETIRADO

> **REPOSITÓRIO RETIRADO.** Este repositório (`dcbo-backend`) já não é desenvolvido nem
> deployado. Toda a API do back-office migrou para o `catalog-backend`
> (https://github.com/opereira24/catalog-backend), o único backend do Diamond Cars.
> Não abrir PRs, não deployar, não apontar nenhum cliente (`dcbo`, `dc`) para este serviço.
>
> **Segurança:** se, apesar disto, alguém o arrancar, nunca sem `AUTH0_ISSUER_URI` e
> `AUTH0_AUDIENCE` reais. Os defaults de `src/main/resources/application.yml`
> (`https://placeholder.auth0.com/` e `https://placeholder-audience/`) aceitariam tokens
> emitidos por um tenant Auth0 que não é nosso.

Agentes: nenhuma task nova nasce neste repo; qualquer alteração à API do back-office é no `catalog-backend`.

## Mapeamento de domínios

Pacote raiz `pt.diamondcars.dcbobackend` → `pt.diamondcars.catalogbackend`; os subpacotes (`config`, `domain/*`, `service`, `web`, `web/dto`, `web/exception`) mantêm o nome. Todos os caminhos do back-office ganharam o prefixo `/api/backoffice` (`BackOfficeApi.PREFIX`).

| Domínio | Controller antigo (`dcbo-backend`, pacote `web`) | Caminho antigo | Controller novo (`catalog-backend`, pacote `web`) | Caminho novo |
|---|---|---|---|---|
| Carros | `CarController` | `/api/cars` | `BackOfficeCarController` | `/api/backoffice/cars` |
| Clientes | `ClientController` | `/api/clients` | `ClientController` | `/api/backoffice/clients` |
| Parceiros | `PartnerController` | `/api/partners` | `PartnerController` | `/api/backoffice/partners` |
| Transações | `TransactionController` | `/api/transactions` | `TransactionController` | `/api/backoffice/transactions` |
| Finanças | `FinanceController` | `/api/finances` | `FinanceController` | `/api/backoffice/finances` |
| Leads (back-office) | `LeadController` | `/api/leads` | `BackOfficeLeadController` | `/api/backoffice/leads` |
| Notificações | `NotificationController` | `/api/notifications` | `NotificationController` | `/api/backoffice/notifications` |
| Utilizadores | `AppUserController` | `/api/users` | `AppUserController` | `/api/backoffice/users` |
| Perfil (`/me`) | `AppUserController` | `/api/me` | `AppUserController` | `/api/backoffice/me` |
| Leads do site público | `internal.InternalLeadController` (`POST`, header com `CATALOG_SYNC_TOKEN`) | `/internal/leads` | sem correspondente: encaminhamento removido na TASK-006; o `catalog-backend` grava o lead direto em `POST /api/leads` (`PublicLeadController`) | — |

**Atenção:** `/api/cars` e `/api/leads` existem nos dois repos com significado diferente. No `dcbo-backend` eram o CRUD do back-office (JWT obrigatório); no `catalog-backend` são a API pública do site (`GET` de carros, `POST` de lead, sem JWT). Um cliente do back-office que usava `/api/cars` ou `/api/leads` passa para `/api/backoffice/cars` e `/api/backoffice/leads`, não fica no mesmo caminho.

Mapeamento completo endpoint a endpoint (método, caminho, perfil, DTO de pedido e de resposta, caminho de origem neste repo): no `catalog-backend`, `src/main/java/pt/diamondcars/catalogbackend/web/CLAUDE.md`, secção "Inventário de endpoints" (tabela verificada contra o código por `BackOfficeSurfaceTest#inventoryInWebClaudeMdMatchesTheMappings`).

---

# Factos técnicos herdados (histórico)

## dcbo-backend

Spring Boot / Java / Maven, PostgreSQL via Testcontainers em teste. Migrado de
`.claude/agent-memory` da equipa antiga (2026-09-30) — ver
`ai-structure/projects/diamondcars/_archive-old-team/` para o histórico completo. Convenção
de nomes de colunas/campos (ADR-001, PT/EN) está em
`ai-structure/projects/diamondcars/project.md` (é cross-repo, com `catalog-backend` e o
contrato JSON de `dc`/`dcbo`) — não repetida aqui.

- **Sem lint** (nem Checkstyle nem Spotless) — não inventar um comando de lint.
- **Docker Desktop nem sempre está a correr** nesta máquina antes de `./mvnw test`
  (Testcontainers precisa dele). `docker version` falha com erro de pipe se estiver parado —
  arrancar (`Docker Desktop.exe`) e esperar ~35-40s antes de repetir.
- **Diagnostics do IDE em `.java` são frequentemente stale logo após um `Edit`** (erros de
  sintaxe falsos). Não reagir de imediato — confirmar com `./mvnw -o -q compile` primeiro; só
  investigar se o compile real falhar.

### Testcontainers — singleton via `static final`

`@ServiceConnection` num campo `static final` de uma classe base abstrata
(`AbstractPostgresIntegrationTest`), inicializado num bloco estático, **sem**
`@Testcontainers`/`@Container` (essas anotações têm semântica por classe, não singleton). A
prova de que é mesmo singleton está no log do run (`grep -c "Creating container for image:
postgres"` = 1), não no código.

### BOM do Spring Boot renomeou artefactos Testcontainers

No BOM 4.1.1, Testcontainers é a versão **2.0.5**: `org.testcontainers:junit-jupiter` →
`testcontainers-junit-jupiter`, `:postgresql` → `testcontainers-postgresql` (os nomes antigos
não existem). A classe também mudou: `org.testcontainers.postgresql.PostgreSQLContainer`
(não genérica) substitui a antiga `org.testcontainers.containers.PostgreSQLContainer<SELF>`
(ainda existe no jar, mas `@Deprecated`). Auditar contra o `.pom` do BOM em `~/.m2`, nunca
contra documentação — textos de task antigos podem citar os nomes antigos.

### OAuth2/Auth0 — resolução preguiçosa do decoder

O Spring Boot embrulha o decoder baseado em `issuer-uri` num `SupplierJwtDecoder` — o
`contextLoads()` não vai buscar metadados OIDC ao arranque, mesmo com um `issuer-uri`
placeholder. Confirmar com `grep -inE "auth0|issuer|JwtDecoder"` sobre o log completo do run
(tem de dar 0 linhas) antes de assumir que há dependência de rede no arranque.

### Esquema derivado do React — auditar os sítios de escrita, não os schemas de validação

Em `dcbo` (frontend), `validateObject` só deixa passar as chaves do schema, mas
`addCar`/`addLead`/`updateLead` acrescentam uma whitelist manual de campos extra, e
`addTransaction`/`addNotification`/`addPartner` fazem spread do payload inteiro. O conjunto
real de campos persistidos = schema de validação ∪ whitelist manual ∪ payloads construídos em
`App.js`/`components/*-form.js`. Uma migração desenhada só a partir dos `*ValidationSchema`
perde campos load-bearing (já aconteceu: `transactions.categoria`, `leads.follow_up_date`,
`cars.commission_value`).

- Confirmar sempre cada `DEFAULT`/enum contra os valores reais que o frontend escreve/filtra
  (`grep` aos `status === '...'`) — defaults inventados ficam invisíveis no kanban.
- Datas civis (`<input type="date">`) → coluna `DATE`; instantes (`serverTimestamp()`) →
  `TIMESTAMPTZ`. Com o servidor em UTC+1, gravar uma data civil como `TIMESTAMPTZ` desloca o
  dia depois de um `.split('T')[0]` no formulário.
- Um teste de migração que só lê `information_schema.tables` não prova tipo de coluna nem
  UNIQUE nem `ON DELETE` — verificar via query a `information_schema.columns`, insert
  duplicado, delete do pai.

### Contadores portados do Firestore

O significado de um contador vem de onde o React chama o incremento, não do nome da coluna
(ex.: `incrementPartnerCars` só é chamado em `sellCar`, logo `totalCars` = carros vendidos,
não inventário). Confirmar sempre no código React de origem antes de portar a semântica.

### `gh pr review --approve` falha em PRs deste repo

`opereira24` é autor e reviewer do PR ao mesmo tempo — GitHub recusa auto-aprovação. Já
confirmado no PR #1. Usar `--request-changes` ou `--comment` quando for o mesmo autor, nunca
`--approve`.
