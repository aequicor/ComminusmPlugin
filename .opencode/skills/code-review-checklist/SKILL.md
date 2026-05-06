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
- Hardcoded Bukkit ChatColor strings — use MiniMessage or component API
- Using deprecated Bukkit API (use Paper-adventure components, not legacy ChatColors)
- Blocking the main server thread — schedule async with Bukkit schedulers or coroutines
- Storing Player references past event scope (causes memory leaks)
- Calling Bukkit API from non-main thread without scheduler bouncing back to main
- Long-running task in event handler (offload to BukkitScheduler.runTaskAsynchronously)
`

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