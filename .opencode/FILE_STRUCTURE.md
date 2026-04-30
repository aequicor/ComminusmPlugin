# File Structure — ComminusmPlugin Agent Reference

> Central path reference. Before creating any new file — check this table.
> All agents read this file instead of guessing paths.

---

## Planning (.planning/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.planning/CURRENT.md` | @Main | Active task, checkpoint entries |
| `.planning/DECISIONS.md` | @Main, humans | ADR — architectural decisions |
| `.planning/HISTORY.md` | @Main | CURRENT.md archive (rotation on >10 entries) |
| `.planning/bugs/BUG-NNN.md` | @debugger | Bug investigation report (root cause + failing test) |

---

## Documentation (docs/)

### Per-module structure

```
docs/[module]/
  requirements/[feature].md        ← business requirements (@Main)
  spec/[feature].md                 ← technical specification (@Main, @CodeWriter)
  spec/[feature]-test-plan.md       ← test plan Draft/Final (@QA)
  guidelines/[topic].md             ← project guidelines (@Main, @Designer, @CodeWriter)
  plans/[feature]-plan.md           ← implementation plan (@Main)
  plans/[feature]-stage-NN.md       ← stage file for @CodeWriter (@Main)
  reports/[bug-name].md             ← bug fix report (@BugFixer)
  documentation/*.md                ← general module docs
```

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| plugin | `:` | `src/main/kotlin/ru/kyamshanov/comminusm/` |

### System paths

| Path | Owner | Purpose |
|------|-------|---------|
| `docs/external-apis/[lib]-[version].md` | @CodeWriter, @BugFixer | External library documentation cache |
| `docs/_templates/test-plan.md` | (template) | Template for @QA |

---

## Agent Configuration (.opencode/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.opencode/_shared.md` | @PromptEngineer | Shared context for all agents |
| `.opencode/FILE_STRUCTURE.md` | @PromptEngineer | This file — path reference |
| `.opencode/agents/[Name].md` | @PromptEngineer | Individual agent prompt |
| `.opencode/skills/[name]/SKILL.md` | @PromptEngineer | Skill definition |

---

## Source Code

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| plugin | `:` | `src/main/kotlin/ru/kyamshanov/comminusm/` |

### Test roots

| Module | Test root |
|--------|----------|
| `plugin` | `src/test/kotlin/ru/kyamshanov/comminusm/` |

---

## Build and Verification

| Command | What it does |
|---------|-------------|
| `./gradlew [module]:build` | Full module build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Module tests |
| `./gradlew detekt` | Lint + code style |
