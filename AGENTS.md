# AGENTS.md — ComminusmPlugin

> Universal coding-agent rules for this project (OpenCode host).
> Supported by: OpenCode, Cursor, GitHub Copilot, Windsurf, Devin, Google Jules,
> and all tools following the [AAIF AGENTS.md standard](https://agents.md).
> For Claude Code, the equivalent file is `CLAUDE.md` at this same root.

---

## Project Overview

First communism plugin

**Stack:** ComminusmPlugin вЂ” kotlin stack

---

## Modules

| Module | Gradle module | Docs | Responsibility |
|--------|---------------|------|----------------|
| `comminusm` | `вЂ”` | `vault/comminusm/` | Minecraft Paper plugin вЂ” communism-themed gameplay mechanics |


---

## Build and Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew` | Full project build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Run tests (replace `[module]` with module name) |
| `./gradlew detekt ktlintCheck` | Lint + code-style check |

**Always compile and run tests before marking a task complete.**

---

## Code Style — Hard Rules

### Forbidden Patterns

- !! operator (use requireNotNull/checkNotNull with message)
- GlobalScope.launch (always use a scoped coroutine)
- Thread.sleep in suspend code (use delay())
- Empty catch blocks
- Bare Exception/Throwable catch (catch specific types)
- lateinit outside DI containers, fragments, and tests
- runBlocking outside main and tests
- Hardcoded secrets or API keys in code (use environment variables)
- SQL string concatenation with user input (use parameterized queries)
- Logging sensitive data (passwords, tokens, PII)
- TODO/FIXME in production code without a tracking entry (issue or DECISIONS.md)
- Disabled/commented-out tests without an explanation
- Catching Throwable/Exception generically and swallowing it
- Hardcoded Bukkit ChatColor strings вЂ” use MiniMessage or component API
- Using deprecated Bukkit API (use Paper-adventure components, not legacy ChatColors)
- Blocking the main server thread вЂ” schedule async with Bukkit schedulers or coroutines
- Storing Player references past event scope (causes memory leaks)
- Calling Bukkit API from non-main thread without scheduler bouncing back to main
- Long-running task in event handler (offload to BukkitScheduler.runTaskAsynchronously)
- Class with more than one reason to change (god class / service class doing persistence + business logic + formatting simultaneously)
- Method longer than 30 lines that mixes abstraction levels (orchestration + low-level detail in same function)
- Repository class containing business rules or validation logic
- Use case / interactor class containing more than one business operation
- Switch/when on type tags or string type discriminators instead of polymorphism (adding a new type requires editing existing code)
- Feature flag inside domain logic instead of strategy/decorator injection
- Hardcoded algorithm selection inside a class that should delegate to a strategy
- Subclass that throws UnsupportedOperationException / NotImplementedError for inherited methods
- Subclass that weakens preconditions or strengthens postconditions of the parent contract
- Type-checking with instanceof/is inside a method that accepts a base type (violates substitutability)
- Interface with more than 5-7 methods that clients only partially implement (fat interface)
- Passing a full service/repository interface to a consumer that uses only one method
- Marker method implemented as a no-op (empty body) because the interface forced it
- Concrete class instantiated with 'new' / constructor call inside business logic (use DI / factory)
- Domain or use-case class importing from infrastructure layer (DB, HTTP, filesystem packages)
- Static/global access to shared mutable state from domain logic (singletons as hidden dependencies)
- Test that cannot run without a real database/network because a dependency was not inverted
- Presentation layer (controller, ViewModel, screen) containing business rules
- Domain entity importing framework annotations (ORM, serialization, DI) вЂ” keep entities pure
- Infrastructure class (repository impl, API client) containing business decisions
- Cross-layer import in wrong direction: inner layer importing outer layer package
- Duplicate business logic in two or more use cases instead of extracting a shared domain service
- Abstract base class or interface created speculatively with only one concrete implementation and no planned extension
- Over-engineered abstraction for a one-time operation (factory-of-factories, generic pipeline for a single fixed flow)
- Mutable public field on a domain entity or value object (use val / readonly / private setter)
- Method that mutates its argument instead of returning a new value (unexpected side effect)
- Shared mutable state accessed without synchronisation in concurrent context
- Long method chain on a foreign object reaching 3+ levels deep (a.b().c().doSomething()) вЂ” violates LoD
- Caller extracting data from an object and making decisions on its behalf instead of telling the object to act
- Deep inheritance hierarchy (3+ levels) for code reuse вЂ” prefer composition or delegation
- Inheriting from a concrete class solely to reuse implementation (not to extend the contract)

### Style

- Run `./gradlew detekt ktlintCheck` after every implementation block.
- Match the style of surrounding code, not personal preference.
- All public API must be documented.

---

## Conventions

### Planning Files

- `.planning/CURRENT.md` — local session pointer (gitignored). Holds `active_task: <slug>`. **Read before starting work.**
- `.planning/tasks/<slug>.md` — full state per task (committed, one file per task). @Main writes checkpoints here. **Read the file pointed to by `active_task` for full context.**
- `.planning/tasks/done/` — archived task files after CLOSE.
- `.planning/DECISIONS.md` — architectural decision log (ADR). Check before proposing structural changes. **Append-only — never delete entries.**

### Knowledge Vault (vault/)

Documentation lives in `vault/` indexed by [KnowledgeOS](https://github.com/aequicor/KnowledgeOS).
Structure follows [Diátaxis](https://diataxis.fr/) genre layout:

| Genre | Question | Module Content |
|-------|----------|---------------|
| `concepts/<module>/` | **Why?** How is it structured? | requirements/, plans/ |
| `reference/<module>/` | **What exists?** | spec/ (incl. test plans) |
| `how-to/<module>/` | **How to do X?** | Implementation stage files |
| `tutorials/<module>/` | **How to learn?** | Getting started, module docs |
| `guidelines/<module>/` | **What rules to follow?** | Conventions, patterns, reports/ |
| `guidelines/libs/` | **How to use library?** | External API cache per library+version |

Each module's docs follow: `vault/<genre>/<module>/{subdir}/`

### External Libraries

Before using any external library API:
1. Search `vault/guidelines/libs/` for cached documentation via KnowledgeOS `search_docs`.
2. If not found — look up the library from its canonical, authoritative source via `context7` or `webfetch`.
3. After resolving — write guideline to `vault/guidelines/libs/<lib>-<version>.md` via `write_guideline`.
4. **Never invent** method names, builder DSL methods, or annotation parameters.
5. If no documentation is available, state that clearly — do not guess.

---

## Boundaries

Do NOT modify without explicit instruction:
- `.opencode/` — AI agent configuration (use `@PromptEngineer` or edit manually)
- `.planning/DECISIONS.md` — append-only
- `opencode.json` — runtime configuration
- Any credential file: `.env*`, `~/.ssh/`, `~/.aws/`

---

## Tool Use Discipline

Three rules before any `edit` / `write` call — they prevent the most common context-burning errors:

1. **Read before Edit.** `edit <path>` requires a `read <path>` earlier in **this session**; memory of the file from another session/agent does not count. `write` to a brand-new file is fine without read; `write` to an existing file needs `read` first.
2. **No empty diffs.** `old_string` must differ from `new_string`; otherwise the call is rejected. Plan the diff you want, not the final file content.
3. **Paths from CWD, not memory.** Never paste absolute paths from docs, `AUTO_MEMORY.md`, or prior sessions — they may be from a different OS or developer's checkout. Default to project-relative paths (`vault/...`, `src/...`); derive absolute paths from `bash pwd` if needed.

Full rules + pre-flight checklist: `.opencode/_shared.md` → "Tool Use Discipline".

---

## Corner Cases

- Corner cases MUST be identified during planning, not discovered during implementation.
- Every feature has a corner case register at `vault/concepts/<module>/plans/<feature>-corner-cases.md`. Read it before implementing.
- Critical corner cases (data loss, security, system crash) require a test task in the implementation plan.
- Use the `corner-case-refinement` skill to systematically scan: input boundaries, state lifecycles, concurrency, error paths, scale limits, domain invariants.

## Testing

- Write a failing test before implementing when feasible (TDD preferred).
- Every Critical corner case from the corner case register MUST have a test.
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
- [ ] Lint passes: `./gradlew detekt ktlintCheck`
- [ ] No unexplained `TODO` or `FIXME` remains
- [ ] Changes committed to git with a descriptive message

