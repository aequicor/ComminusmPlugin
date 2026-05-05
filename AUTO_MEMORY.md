# AUTO_MEMORY.md — ComminusmPlugin

> Auto-memory: agents write learnings here across sessions.
> Loaded by all agents. Never manually edit — agents maintain this file.

---

## Learned Build Commands

| Command | Purpose | Discovered |
|---------|---------|------------|
| `./gradlew compileKotlin` | Quick compile | 2026-05-05T07:41:32Z |
| `./gradlew :[module]:test` | Run tests | 2026-05-05T07:41:32Z |
| `./gradlew detekt ktlintCheck` | Lint check | 2026-05-05T07:41:32Z |

---

## Debugging Insights

---

## API Pitfalls

---

## Useful Patterns

---

## Session Continuity

- Last task: feat-privates-orders-fronts (CLOSED)
- Last checkpoint: 2026-05-05T13:00:00Z
- Active plan: none

### Test Coverage Notes (2026-05-05)
Feature "Privates — Orders and Fronts" has 70 manual test cases but only ~12 are covered by existing unit tests (OrderServiceTest, OrderRepositoryTest, WorkFrontServiceTest, WorkFrontRepositoryTest). The remaining 58 test cases require a running Paper server, GUI interaction, chunk PDC state, or explosions — no automated tests exist for these. When adding coverage, target: listeners (BlockListener, OrderFlagListener, FrontFlagListener, ExplosionListener), GUI menus (PartyMenu, OrderMenu, FrontMenu, TreasuryMenu, AdminMenu), and chunk cache manager.
