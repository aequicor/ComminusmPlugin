# CURRENT.md — ComminusmPlugin

> Bridge between sessions. @Main writes a checkpoint after every significant step.
> Agents read this file at startup before any tool call.

## Project

**ComminusmPlugin** — minecraft-paper-plugin

**Modules:** plugin

---

## Active Task

Реализация системы приватов «Ордер и Фронт» — Stage 0: Infrastructure

---

## Timeline

## 2026-04-30T12:47:01Z
- DONE: opencode-kit applied, project configured
- NEXT: first task (to be determined by PO)

## 2026-05-01T10:30:00Z
- DONE: brainstorming completed, spec written to docs/plugin/spec/2026-05-01-privatesystem-design.md
- NEXT: write implementation plan

## 2026-05-01T10:55:00Z
- DONE: plan written to docs/plugin/plans/2026-05-01-privatesystem-plan.md
- NEXT: execute Stage 0 (Infrastructure) via subagent-driven

## 2026-05-01T11:45:00Z
- DONE: all 19 tasks implemented (Stage 0-5), all tests passing, commits done
- DONE: fixed BlockListener — placement + right-click interactions blocked outside order/front zones
- NEXT: PO review / manual testing on Paper server

## 2026-05-01T12:15:00Z
- DONE: flag break confirmation menu (владелец ломает флаг → подтверждение Да/Нет)
- DONE: flag items cannot be dropped or moved to chests (FlagItemProtectionListener)
- DONE: inventory space check before giving flags
- DONE: fixed restore flag — now works with proper error messages in all cases
## 2026-05-03T12:00:00Z
- DONE: all 30 tests passing — fixed WorkFrontService.activate() Bukkit dependency for testability
- DONE: PDC cleanup added to WorkFrontService.deactivate()
- DONE: FrontMenu moveSlot saves radius before deactivation
- DONE: menu back-navigation preserves workFrontService reference (OrderMenu, OrderFlagListener, ComminusmPlugin)
- DONE: removed unused `dy` variable in OrderService.checkOverlap
- NEXT: PO manual testing
