---
genre: guideline
title: "Tech Debt: OrderMembersRepository — DB persistence not wired"
topic: tech-debt
triggers:
  - "tech debt"
  - "OrderMembersRepository"
  - "persistence"
confidence: high
source: human
updated: 2026-05-06T00:00:00Z
---

# Tech Debt: OrderMembersRepository — DB persistence not wired

**ID:** TD-comminusm-order-members-db-persistence
**Module:** comminusm
**Category:** todo
**Severity:** high
**Status:** open
**Discovered:** 2026-05-06
**Discovered by:** @Main during feat-communes (Stage 02 retro)

---

## Files

| Path | Lines | Note |
|------|-------|------|
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/repository/OrderMembersRepository.kt` | 10 | KDoc: `Serves as the runtime source of truth before Stage 02 persistence` |

---

## Description

Репозиторий в Stage 01 был задуман как in-memory placeholder, Stage 02 должен был подключить async-persistence через `DatabaseManager` (CRUD по таблице `order_members` + eager load на старте). Не подключено. Только in-memory `ConcurrentHashMap`.

Следствия:
- Cross-order grants не переживают рестарт плагина.
- Native order memberships тоже теряются — все игроки фактически выпадают из orders при следующем старте сервера, если только OrderService их не восстанавливает откуда-то ещё (требует проверки).
- Связано с TD-comminusm-startup-db-load-and-consistency-scan: даже когда startup-load появится, ему будет нечего грузить, пока этот репозиторий не пишет.

---

## Why not critical now

PO deferral. В pre-release продакшен-данных нет.

---

## Suggested fix

- Inject `DatabaseManager` в `OrderMembersRepository`.
- Все мутирующие методы (`addMember`, `removeMember`, etc.) — async-persist через `DatabaseManager.execute(...)` с parameterized query.
- Eager load на старте: `loadAll()` метод, вызывается из `CommuneStartupTask` (см. TD-comminusm-startup-db-load-and-consistency-scan) до consistency-scan.
- Тесты: round-trip persistence, concurrent-write safety, recovery после restart.
- DB schema: `CREATE TABLE order_members (order_id BIGINT, player_uuid VARCHAR(36), granted_at TIMESTAMP, granted_via VARCHAR(16), ...)` — уже описан в `communes-stage-01.md`.

---

## References

- Related spec: `vault/reference/comminusm/spec/communes.md` (раздел persistence)
- Related plan: `vault/how-to/comminusm/plans/communes-stage-01.md` (где указана DB schema)
- Related TD: TD-comminusm-startup-db-load-and-consistency-scan

---

## Resolution (filled by `/kit-techdebt`)

**Closed:**
**Fix commit:**
**Files changed:**
**Notes:**
