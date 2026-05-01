# AGENTS.md — ComminusmPlugin

> Universal coding-agent rules for this project.
> Supported by: Claude Code, OpenCode, Cursor, GitHub Copilot, Windsurf, Devin, Google Jules,
> and all tools following the [AAIF AGENTS.md standard](https://agents.md).

---

## Project Overview

ComminusmPlugin — Minecraft Paper server plugin with communism satire mechanics.

**Stack:** kotlin

---

## Modules

| Module | Gradle module | Docs | Responsibility |
|--------|---------------|------|----------------|
| `plugin` | `:` | `vault/` | Minecraft Paper server plugin — communism satire mechanics |

---

## Build and Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew` | Full project build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Run tests (replace `[module]` with module name) |
| `./gradlew detekt` | Lint + code-style check |

**Always compile and run tests before marking a task complete.**

---

## Code Style — Hard Rules

### Forbidden Patterns

- !! operator (use requireNotNull/checkNotNull with message)
- GlobalScope.launch (always use a scoped coroutine)
- Thread.sleep in suspend code (use delay())
- Empty catch blocks
- Bare Exception/Throwable catch (catch specific types)
- lateinit outside DI containers and tests
- runBlocking outside main and tests
- TODO() in production code without DECISIONS.md entry
- Raw SQL string concatenation (use parameterized queries)
- Logging tokens, passwords, PII data

### Style

- Run `./gradlew detekt` after every implementation block.
- Match the style of surrounding code, not personal preference.
- All public API must be documented.

---

## Conventions

### Planning Files

- `.planning/CURRENT.md` — active task and last checkpoint. **Read before starting work.**
- `.planning/DECISIONS.md` — architectural decision log (ADR). Check before proposing structural changes. **Append-only — never delete entries.**

### Module Docs Structure

- `plugin` — docs at `vault/`

Each module's docs follow: `docs/<module>/{requirements,spec,guidelines,plans,reports}/`

### External Libraries

Before using any external library API:
1. Check `docs/external-apis/` for cached documentation.
2. Look up the library from its canonical, authoritative source.
3. **Never invent** method names, builder DSL methods, or annotation parameters.
4. If no documentation is available, state that clearly — do not guess.

---

## Boundaries

Do NOT modify without explicit instruction:
- `.opencode/` — AI agent configuration (use `@PromptEngineer` or edit manually)
- `.planning/DECISIONS.md` — append-only
- `opencode.json` — runtime configuration
- Any credential file: `.env*`, `~/.ssh/`, `~/.aws/`

---

## Testing

- Write a failing test before implementing when feasible (TDD preferred).
- All existing tests must continue to pass after your change.
- Network calls in tests → use mocks or test doubles, **never real endpoints**.
- SQL only via parameterized queries — never concatenate user input into queries.

---

## Security

- **Never** write API keys, tokens, passwords, or secrets into code or commit messages.
- **Never** read credential files (`~/.ssh/`, `~/.aws/`, `.env*`) unless the user explicitly requests it.
- Flag any code handling auth, crypto, payments, or PII for review before commit.
- SQL: parameterized queries only. No string concatenation with user input. Ever.

---

## Commit Guidelines

One logical change per commit. Format: `<type>: <short description>`

**Types:** `feat` · `fix` · `refactor` · `test` · `docs` · `chore`

**Examples:**
- `feat: add JWT refresh token endpoint`
- `fix: null pointer in user login flow`
- `refactor: extract payment validation to service`
- `test: unit tests for auth token expiry`

Do not commit `TODO` or `FIXME` without a corresponding entry in `.planning/DECISIONS.md`.

---

## Definition of Done

A task is **complete** only when ALL of the following are true:

- [ ] Code compiles: `./gradlew compileKotlin`
- [ ] All tests pass (including new tests for any changed behaviour)
- [ ] Lint passes: `./gradlew detekt`
- [ ] No unexplained `TODO` or `FIXME` remains
- [ ] Changes committed to git with a descriptive message
