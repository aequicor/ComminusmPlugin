---
genre: guideline
title: "Tech Debt: CommuneInvitation — BukkitTask auto-expiry not scheduled"
topic: tech-debt
triggers:
  - "tech debt"
  - "invitation"
  - "BukkitTask"
  - "expiry"
confidence: high
source: human
updated: 2026-05-06T00:00:00Z
status: in-progress
---

# Tech Debt: CommuneInvitation — BukkitTask auto-expiry not scheduled

**ID:** TD-comminusm-invitation-timer-bukkit-scheduling
**Module:** comminusm
**Category:** todo
**Severity:** medium
**Status:** fixed
**Discovered:** 2026-05-06
**Discovered by:** @Main during feat-communes (Stage 02 retro)
**Fixed:** 2026-05-07

---

## Files

| Path | Lines | Note |
|------|-------|------|
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/service/CommuneInvitationService.kt` | 14 | KDoc: `@param invitationTimers ... (for Stage 02+)` |
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/service/CommuneInvitationService.kt` | 52 | `// In Stage 02, a BukkitTask will be scheduled here / For now, just reserve the map entry` |

---

## Description

`createInvitation()` по спеке §6.2 должен планировать `BukkitTask` с auto-expiry приглашения (по плану — 30 минут / 500 секунд в зависимости от ревизии спеки). Сейчас только резервируется запись в `Map<UUID, BukkitTask>`, реальный таймер не создаётся. Cancel'ить тоже нечего.

Следствия:
- Приглашения никогда не истекают автоматически.
- Лидер ордена видит «висящие» приглашения от давно неактивных коммун, пока их кто-то вручную не declined.
- Спека §6.2 не выполнена.

---

## Why not critical now

PO deferral. Воздействие ограничено: накопление в UI, но никаких прав не предоставляется без ручного accept'а.

---

## Suggested fix

- В `createInvitation()`: после создания `Invitation` запланировать `Bukkit.getScheduler().runTaskLater(plugin, { expire(invitationId) }, EXPIRY_TICKS)`, сохранить `BukkitTask` в map.
- В `decline()`/`accept()`: `task.cancel()` перед удалением из map.
- В `CommuneStartupTask.loadCommunes()` (см. TD-comminusm-startup-db-load-and-consistency-scan): для каждого загруженного приглашения — пересчитать оставшееся время, перепланировать.
- Тесты: expiry через моканный scheduler, cancel при manual decline, recovery после restart.

---

## References

- Related spec: `vault/reference/comminusm/spec/communes.md` §6.2
- Related TD: TD-comminusm-startup-db-load-and-consistency-scan (recovery таймеров после restart)

---

## Resolution (filled by `/kit-techdebt`)

**Closed:** 2026-05-07
**Fix commit:** af1b40f
**Files changed:**
- `src/main/kotlin/ru/kyamshanov/comminusm/commune/service/CommuneInvitationService.kt` — implemented scheduleExpiry(), calculateExpiryTicks(), cancelTimer(); updated class signature
- `src/main/kotlin/ru/kyamshanov/comminusm/di/DIContainer.kt` — changed invitationTimers type to `ConcurrentHashMap<UUID, Int>`; passed plugin to CommuneInvitationService
- `src/test/kotlin/ru/kyamshanov/comminusm/commune/CommuneInvitationServiceTest.kt` — 8 regression tests for timer storage, cancellation, expiry, replacement
- `src/test/kotlin/ru/kyamshanov/comminusm/integration/CommuneSystemIntegrationTest.kt` — updated type and plugin parameter

**Notes:**
- Invitations now auto-expire via Bukkit scheduler after calculateExpiryTicks()
- In test mode (plugin=null), taskId stored as -1 (no real scheduling)
- Timer cancelled on manual decline via cancelInvitation() or on auto-expiry via expireInvitation()
- All tests pass, lint/detekt checks green
- Related TD: TD-comminusm-startup-db-load-and-consistency-scan (recovery of timers after server restart — future work)
