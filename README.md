# dcbo-backend (RETIRADO)

> **REPOSITÓRIO RETIRADO.** Este repositório (`dcbo-backend`) já não é desenvolvido nem
> deployado. Toda a API do back-office migrou para o `catalog-backend`
> (https://github.com/opereira24/catalog-backend), o único backend do Diamond Cars.
> Não abrir PRs, não deployar, não apontar nenhum cliente (`dcbo`, `dc`) para este serviço.
>
> **Segurança:** se, apesar disto, alguém o arrancar, nunca sem `AUTH0_ISSUER_URI` e
> `AUTH0_AUDIENCE` reais. Os defaults de `src/main/resources/application.yml`
> (`https://placeholder.auth0.com/` e `https://placeholder-audience/`) aceitariam tokens
> emitidos por um tenant Auth0 que não é nosso.

## Mapeamento para o catalog-backend

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
