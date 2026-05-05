# File Structure — ComminusmPlugin Agent Reference

> Central path reference. Before creating any new file — check this table.
> All agents read this file instead of guessing paths.

---

## Planning (.planning/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.planning/CURRENT.md` | @Main | **Local session pointer (gitignored).** Holds `active_task: <slug>`. Each developer has their own. |
| `.planning/tasks/<slug>.md` | @Main | **Per-task state (committed).** Type, Module, Description, Timeline (DONE/NEXT/BLOCKED). One file per active task — no merge conflicts between team members. |
| `.planning/tasks/done/<slug>.md` | @Main | Archived task files after CLOSE. |
| `.planning/DECISIONS.md` | @Main, humans | ADR — architectural decisions |
| `.planning/bugs/BUG-NNN.md` | @debugger | Bug investigation report (root cause + failing test) |

---

## Knowledge Vault (vault/)

> Indexed by [KnowledgeOS](https://github.com/aequicor/KnowledgeOS).
> Structure follows [Diátaxis](https://diataxis.fr/).

### Per-module structure

```
vault/
  _INDEX.md                          ← vault map — entry point for agents
  _templates/                        ← document templates (bug-report, spec, test-plan, requirements)

  concepts/<module>/                 ← WHY and HOW it's structured (@Main)
    requirements/<feature>.md
    plans/<feature>-plan.md
    plans/<feature>-corner-cases.md   ← corner case register (@Main, corner-case-refinement)

   reference/<module>/                ← WHAT EXISTS — specs, schemas, test plans, test cases
     spec/<feature>.md
     spec/<feature>-test-plan.md      ← Draft/Final (@QA)
     test-cases/<feature>-test-cases.md ← Test cases with defect tracking (@TestRunner)

  how-to/<module>/                   ← HOW TO implement
    plans/<feature>-stage-NN.md      ← stage files (@Main → @CodeWriter)

  tutorials/<module>/                ← HOW TO LEARN
    documentation/*.md

  guidelines/<module>/               ← RULES — accumulated by agents
    <topic>.md
    reports/<bug-name>.md            ← bug fix reports (@BugFixer)

  guidelines/libs/                   ← external library API documentation cache
    <lib>-<version>.md               ← (@CodeWriter, @BugFixer)
```

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| comminusm | `—` | `src/main/kotlin/ru/kyamshanov/comminusm/` |

### System paths

| Path | Owner | Purpose |
|------|-------|---------|
| `vault/guidelines/libs/<lib>-<version>.md` | @CodeWriter, @BugFixer | External library documentation cache |
| `vault/_templates/test-plan.md` | (template) | Template for @QA |
| `vault/_templates/test-cases.md` | (template) | Template for @TestRunner |
| `vault/_templates/bug-report.md` | (template) | Template for @BugFixer |
| `vault/_templates/spec.md` | (template) | Template for @Main |
| `vault/_templates/requirements.md` | (template) | Template for @Main |

---

## Agent Configuration (.claude/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.claude/_shared.md` | @PromptEngineer | Shared context for all agents |
| `.claude/FILE_STRUCTURE.md` | @PromptEngineer | This file — path reference |
| `.claude/agents/[Name].md` | @PromptEngineer | Individual agent prompt |
| `.claude/skills/[name]/SKILL.md` | @PromptEngineer | Skill definition |
| `.claude/settings.json` | human (PO) | Runtime config for this host (Claude Code) |

---

## Source Code

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| comminusm | `—` | `src/main/kotlin/ru/kyamshanov/comminusm/` |

### Test roots

| Module | Test root |
|--------|----------|
| `comminusm` | `src/test/kotlin/ru/kyamshanov/comminusm/` |

---

## Build and Verification

| Command | What it does |
|---------|-------------|
| `./gradlew [module]:build` | Full module build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Module tests |
| `./gradlew detekt ktlintCheck` | Lint + code style |
