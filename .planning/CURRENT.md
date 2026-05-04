# CURRENT.md — ComminusmPlugin

> Bridge between sessions. @Main writes a checkpoint after every significant step.
> Agents read this file at startup before any tool call.

## Project

**ComminusmPlugin** — Minecraft Paper server plugin — communism satire mechanics

**Modules:** plugin

---

## Active Task

**BUG:** Privat interaction fixes — чужые флаги фронта + взаимодействие вне зон

---

## Timeline

<!-- Entries added by @Main via /checkpoint command -->
<!-- Format:
## <ISO timestamp>
- DONE: <what completed>
- NEXT: <what's next>
- BLOCKED: <only if blocked>
-->

## 2026-05-04T07:45:00Z
- DONE: fixed Bug 1 (foreign front flag breaking — support-block bypass + orphaned banners)
- DONE: fixed Bug 2 (missing front zone checks in onBlockPlace/onPlayerInteract)
- DONE: build + tests pass; report at .vault/guidelines/plugin/reports/privat-interaction-fix.md
- NEXT: commit + push; PO manual testing on Paper server

## 2026-05-04T04:00:00Z
- DONE: fixed 2 privat bugs — front placement (anywhere except foreign orders), block interaction (own order/own front only)
- DONE: committed (e4f5a43) and pushed to origin/master
- NEXT: PO manual testing on Paper server

## 2026-05-04T03:09:02Z
- DONE: opencode-kit applied, project configured
- NEXT: first task (to be determined by PO)
