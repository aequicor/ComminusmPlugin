---
name: code-review-checklist
description: Pre-commit code review checklist filled by @CodeReviewer after each CodeWriter cycle. Use ONLY when reviewing code changes before commit — not for design reviews, not for requirements analysis.
---

# Code Review Checklist

@CodeReviewer fills this checklist after each @CodeWriter cycle. Walk through every item. Mark each `[ ]` as `[x]` when verified. If any item fails — describe the issue, reference the file:line, and return to @CodeWriter.

## Invocation

Called by @CodeReviewer automatically after each @CodeWriter stage completion. No manual trigger needed.

## Input

| Field | Required | Description |
|-------|----------|-------------|
| `stage_file` | Yes | Path to the stage file @CodeWriter implemented |
| `files_changed` | Yes | List of changed files (diff summary) |
| `corner_cases` | No | Path to corner case register (if available) |

## Functionality

- [ ] All acceptance criteria from the spec are addressed
- [ ] Edge cases are handled (null, empty, boundary values)
- [ ] Corner case register reviewed — every Critical item has a test
- [ ] Corner case register reviewed — every High item has an explicit decision (test or defer)
- [ ] Error states are handled with appropriate messages
- [ ] No regressions — existing functionality continues to work

## Code Quality

- [ ] Functions are small and single-purpose
- [ ] No magic numbers — use named constants
- [ ] Meaningful variable and function names
- [ ] No dead code, commented-out blocks, or unused imports
- [ ] Consistent formatting (auto-formatter applied)
- [ ] No patterns from forbidden list: `- !! operator (use requireNotNull/checkNotNull with message)
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
- Inheriting from a concrete class solely to reuse implementation (not to extend the contract)`

## Stub & Deferral Check (CRITICAL — blocking)

Regex scan over every changeset file: `TODO|FIXME|XXX|HACK|stage [0-9]|later|TBD`.

- [ ] No unpaired `TODO`, `FIXME`, `XXX`, `HACK`, "stage N", "later", or "TBD" in production code added/modified by this stage
- [ ] Every such marker (if any) is paired with a `.planning/DECISIONS.md` reference, an external tracker ID (`#123`, `JIRA-456`, `TC-NN`), or an explicit deferral entry already in the stage file
- [ ] No method body for an AC the stage was supposed to deliver is comment-only (`{ // TODO: implement… }`)
- [ ] No method body is silently noop (`fun foo() { /* ... */ }`) where the spec required behavior

Any unchecked box → CRITICAL issue, return to @CodeWriter (or escalate to @Main as BLOCKED). This rule fires on **in-changeset hits only** — pre-existing markers in untouched files belong to tech-debt, not the review.

## Architecture

- [ ] No circular dependencies between modules
- [ ] Correct layering: domain → service → controller/presentation
- [ ] Dependency injection used where appropriate
- [ ] Interfaces defined at module boundaries

## Testing

- [ ] Unit tests cover happy path + error cases
- [ ] Integration tests cover API contracts
- [ ] Tests are deterministic (no Thread.sleep, real network calls)
- [ ] All tests pass: `./gradlew :[module]:test`

## Security

- [ ] Input validation on all external inputs
- [ ] SQL via parameterized queries only
- [ ] No tokens, passwords, or PII in logs
- [ ] Authentication/authorization checks not weakened
- [ ] No sensitive data in error responses

## Performance

- [ ] No N+1 queries in loops
- [ ] Appropriate caching strategy
- [ ] Resources closed properly (connections, files, streams)
- [ ] No blocking calls on UI/main threads

## Documentation

- [ ] Public API documented
- [ ] Complex logic has inline comments explaining WHY, not WHAT
- [ ] Spec/requirements match implementation
- [ ] Any new guidelines saved to `vault/guidelines/[module]/`

## Output

Return a verdict:

```
VERDICT: [PASS | CRITICAL | HIGH | MEDIUM]
FILES_REVIEWED: [list]
ISSUES:
- [severity] [file:line] [description] → [fix suggestion]

CHANGES_SUMMARY: [1-2 sentences]
```

**PASS** → proceed to next stage.  
**CRITICAL/HIGH** → return to @CodeWriter with issues.  
**MEDIUM** → document and proceed (fix later).

## Error Handling

- If stage file is missing or empty → report `VERDICT: CRITICAL — stage file not found at [path]`. Do not proceed.
- If diff is empty (no code changes) → report `VERDICT: CRITICAL — @CodeWriter produced no changes for [stage]`. Do not proceed.
- If corner case register references a Critical item with no corresponding test → flag as HIGH issue, do not auto-approve.