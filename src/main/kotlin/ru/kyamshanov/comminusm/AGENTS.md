# AGENTS.md — plugin / ComminusmPlugin

> Nested module-level agent instructions for `plugin`.
> This file is closest to `src/main/kotlin/ru/kyamshanov/comminusm/` — it takes precedence over the root `AGENTS.md`.

---

## Module: plugin

**Responsibility:** Minecraft Paper server plugin — communism satire mechanics
**Source root:** `src/main/kotlin/ru/kyamshanov/comminusm/`
**Test root:** `src/test/kotlin/ru/kyamshanov/comminusm/`
**Gradle module:** `:`

---

## Module Build & Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew test` | Run all tests |
| `./gradlew detekt ktlintCheck` | Lint check |

---

## Module-Specific Conventions

- Follow AGENTS.md conventions at project root.
- Paper API: use Adventure components, not legacy ChatColor.
- All database access via SQLite (parameterized queries only).
- Use Bukkit schedulers or coroutines for async work — never block main thread.

---

## Module Dependencies

- `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` — compileOnly
- `org.jetbrains.kotlin:kotlin-stdlib-jdk8`
- `org.xerial:sqlite-jdbc:3.46.0.0`

---

## Module Docs

Docs path: `vault/`
- `requirements/` — business requirements
- `spec/` — technical specifications
- `guidelines/` — patterns and rules
- `plans/` — implementation plans
- `reports/` — bug fix reports
