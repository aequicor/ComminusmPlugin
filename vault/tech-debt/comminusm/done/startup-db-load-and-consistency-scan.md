---
genre: guideline
title: "Tech Debt: Commune startup — DB load and AC-47 consistency scan"
topic: tech-debt
triggers:
  - "tech debt"
  - "commune startup"
  - "AC-47"
  - "§8.2"
confidence: high
source: human
updated: 2026-05-06T00:00:00Z
status: in-progress
---

# Tech Debt: Commune startup — DB load + AC-47 consistency scan

**ID:** TD-comminusm-startup-db-load-and-consistency-scan
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
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/listener/CommuneStartupTask.kt` | 64 | `loadCommunes()` тело — пустой `// TODO: Implement database load (§8.2 step 2)` |
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/listener/CommuneStartupTask.kt` | 88 | `performConsistencyCheck()` тело — пустой `// TODO: Implement consistency check (§8.2 step 3)` |

---

## Description

Stage 02 фичи `communes` по плану должен был реализовать onEnable()-стартап-последовательность из спеки §8.2:
1. Загрузить таблицы `communes`, `commune_orders`, `commune_invitations` в in-memory кэши `CommuneService`.
2. Прогнать AC-47 consistency-scan — пройти по каждому commune-grant'у и удалить тех cross-order участников, чей native-order больше не входит в коммуну (например, после dirty shutdown или ручного редактирования БД).

Оба метода зашиплены пустыми TODO-заглушками. Следствия:
- При каждом старте плагина состояние коммун пустое, независимо от данных в БД.
- Orphan cross-order members от предыдущих сессий никогда не вычищаются.
- AC-47 в спеке формально не реализован.

---

## Why not critical now

PO явно выбрал отложить (диалог `2026-05-06`: «Только разобраться, ничего не менять»). Фича в pre-release, продакшен-данных нет. Будет закрыто батчем вместе с остальными Stage 02 deferrals через `/kit-techdebt`.

---

## Suggested fix

- Внедрить `DatabaseManager` в `CommuneStartupTask` (DI через `ComminusmPlugin.onEnable`).
- `loadCommunes()`: async чтение трёх таблиц, наполнение `CommuneService` карт; перепланировать таймеры через `CommuneInvitationService` (см. TD-comminusm-invitation-timer-bukkit-scheduling).
- `performConsistencyCheck()`: для каждого `Commune.orderIds` × каждого `OrderMember(grantedVia="commune")` — проверить, что у игрока есть native order в этой же коммуне; если нет — `removeMemberSilently`.
- Тесты: orphan-detection (3+ сценария — нет native, native в другой коммуне, native deleted).

---

## References

- Related spec: `vault/reference/comminusm/spec/communes.md` §8.2, AC-47, CC-05
- Related plan: `vault/how-to/comminusm/plans/communes-stage-02.md`
- Related TC: см. AC-47-tagged строки в `vault/reference/comminusm/test-cases/communes-test-cases.md`
- Related TD: TD-comminusm-order-members-db-persistence (без неё грузить нечего), TD-comminusm-invitation-timer-bukkit-scheduling (восстановление таймеров после restart)

---

## Resolution (filled by `/kit-techdebt`)

**Closed:** 2026-05-07
**Fix commit:** 844d575
**Files changed:** CommuneStartupTask.kt, CommuneService.kt, DIContainer.kt, CommuneStartupTaskTest.kt
**Notes:** loadCommunes() загружает communes/commune_orders из SQLite и вызывает orderMembersRepository.loadAll(). performConsistencyCheck() реализует AC-47 orphan scan с O(N) алгоритмом. Добавлены null-checks для DB значений, split try-catch, sanitized logging.
