---
genre: guideline
title: "Tech Debt: Player join — AC-47 per-player check + CC-14 offline notifications"
topic: tech-debt
triggers:
  - "tech debt"
  - "player join"
  - "AC-47"
  - "CC-14"
confidence: high
source: human
updated: 2026-05-06T00:00:00Z
---

# Tech Debt: Player join — AC-47 per-player check + CC-14 offline notifications

**ID:** TD-comminusm-player-join-stale-member-and-offline-notif
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
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/listener/CommunePlayerListener.kt` | 46 | `// TODO: Implement consistency check (AC-47) - verify cross-order members are still valid` |
| `src/main/kotlin/ru/kyamshanov/comminusm/commune/listener/CommunePlayerListener.kt` | 47 | `// TODO: Implement offline notification delivery (CC-14) - check for pending messages` |

---

## Description

`onPlayerJoin` по плану Stage 02 должен делать на `PlayerJoinEvent` две вещи:
1. **Per-player slice AC-47** — проверить, что cross-order гранты входящего игрока всё ещё валидны (на случай, если состояние коммун изменилось пока игрок был offline).
2. **CC-14** — доставить накопленные пока игрок был offline сообщения/уведомления коммуны.

Обе ветки — TODO-заглушки. Следствия:
- Игрок, добавленный/удалённый из cross-order пока был offline, увидит устаревшие гранты при следующем входе.
- Все сообщения и уведомления коммуны, отправленные пока он был offline, теряются.

---

## Why not critical now

PO deferral. Влияние ограничено сценарием «изменения коммуны во время offline» — встречается, но не блокирует базовый сценарий использования.

---

## Suggested fix

- Per-player check: при join запросить у `CommuneService.getCommuneByPlayer(uuid)` актуальное состояние; сверить с `OrderMembersRepository`-грантами; расхождения — `CrossOrderMembershipService.revoke`.
- Offline-notif: завести очередь `Map<UUID, List<PendingNotification>>` (или таблицу `commune_pending_notifications`); при join — drain очереди, отправить, очистить.
- Тесты: добавление/удаление в cross-order пока target offline → корректное состояние при join; буферизованные уведомления доставляются ровно один раз.

---

## References

- Related spec: AC-47, CC-14 in `vault/reference/comminusm/spec/communes.md`
- Related plan: `vault/how-to/comminusm/plans/communes-stage-02.md`
- Related TD: TD-comminusm-startup-db-load-and-consistency-scan (полный AC-47 на старте)

---

## Resolution (filled by `/kit-techdebt`)

**Closed:**
**Fix commit:**
**Files changed:**
**Notes:**
